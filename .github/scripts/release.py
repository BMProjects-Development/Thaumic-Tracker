"""Validate release inputs and publish the artifacts tested by GitHub Actions."""

import json
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.error import HTTPError
from urllib.parse import quote, urlencode
from urllib.request import Request, urlopen

import curseforge


BACKPORT = "1.21.1"
BACKPORT_SUFFIX = f"-mc{BACKPORT}"
VERSION = re.compile(r"[0-9]+(?:\.[0-9]+)+(?:-[A-Za-z0-9][A-Za-z0-9.-]*)?")


def metadata(root):
    properties = dict(
        line.split("=", 1)
        for line in (root / "gradle.properties").read_text(encoding="utf-8").splitlines()
        if "=" in line and not line.lstrip().startswith("#")
    )
    version = properties["mod_version"].strip()
    mod_id = properties["mod_id"].strip()
    minecraft = properties["minecraft_version"].strip()
    if not VERSION.fullmatch(version):
        raise ValueError("Invalid mod_version")
    if not re.fullmatch(r"[a-z][a-z0-9_]*", mod_id):
        raise ValueError("Invalid mod_id")
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)+", minecraft):
        raise ValueError("Invalid minecraft_version")
    notes = root / "changelogs" / f"{version}.md"
    if not notes.is_file() or not notes.read_text(encoding="utf-8").strip():
        raise ValueError(f"Add release notes in changelogs/{version}.md")
    return version, release_tag(version, minecraft), f"{mod_id}-{minecraft}-{version}", notes, minecraft


def release_tag(version, minecraft):
    return f"v{version}{BACKPORT_SUFFIX if minecraft == BACKPORT else ''}"


class GitHub:
    def __init__(self, token, api_url):
        self.token = token
        self.api_url = api_url.rstrip("/")

    def request(self, method, endpoint, data=None, *, missing_ok=False):
        url = endpoint if endpoint.startswith("https://") else self.api_url + endpoint
        binary = isinstance(data, bytes)
        body = data if binary else json.dumps(data).encode() if data is not None else None
        request = Request(url, data=body, method=method, headers={
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/octet-stream" if binary else "application/json",
            "User-Agent": "Thaumic-Tracker-release",
        })
        try:
            with urlopen(request, timeout=60) as response:
                content = response.read()
                return json.loads(content) if content else None
        except HTTPError as error:
            if missing_ok and error.code == 404:
                return None
            raise RuntimeError(f"GitHub {method} {endpoint}: HTTP {error.code}") from error


def version_key(tag):
    version = tag.removeprefix("v").removesuffix(BACKPORT_SUFFIX)
    if not VERSION.fullmatch(version):
        return (), False
    numbers = [int(part) for part in version.split("-", 1)[0].split(".")]
    while numbers and numbers[-1] == 0:
        numbers.pop()
    return tuple(numbers), not tag.endswith(BACKPORT_SUFFIX)


def find_release(api, base, tag):
    # Listing includes drafts left by interrupted uploads, unlike a tag lookup.
    page = 1
    while True:
        releases = api.request("GET", f"{base}/releases?per_page=100&page={page}")
        for release in releases:
            if release["tag_name"] == tag:
                return release
        if len(releases) < 100:
            return None
        page += 1


def is_current(api, base, ref, sha):
    ref_name = ref.removeprefix("refs/")
    head = api.request("GET", f"{base}/commits/{quote(ref_name, safe='')}", missing_ok=True)
    if head is None or head["sha"] != sha:
        print("Skipping an outdated build: the remote ref no longer points to this commit.")
        return False
    return True


def publish(api, repository, sha, ref, version, stem, artifact_dir, notes, minecraft):
    tag = release_tag(version, minecraft)
    files = [artifact_dir / name for name in (
        f"{stem}.jar", f"{stem}-sources.jar", f"Thaumic-Tracker-{version}.zip"
    )]
    for file in files:
        if not file.is_file() or file.stat().st_size == 0:
            raise ValueError(f"Missing or empty release artifact: {file}")
    body = notes.read_text(encoding="utf-8").strip()
    if not body:
        raise ValueError("Release notes are empty")

    base = f"/repos/{repository}"
    default_branch = api.request("GET", base)["default_branch"]
    branch = BACKPORT if minecraft == BACKPORT else default_branch
    if ref not in (f"refs/heads/{branch}", f"refs/tags/{tag}"):
        raise ValueError("Only the release branch for this Minecraft version or the matching tag can publish")
    if not is_current(api, base, ref, sha):
        return None

    existing = find_release(api, base, tag)
    latest = api.request("GET", f"{base}/releases/latest", missing_ok=True)
    current_tag = api.request("GET", f"{base}/git/ref/tags/{tag}", missing_ok=True)
    if existing and existing.get("immutable"):
        raise ValueError(f"Release {tag} is immutable; GitHub does not allow replacing it")
    prerelease = "-" in version
    # Keep the default Minecraft release as Latest when the mod versions tie.
    make_latest = not prerelease and (
        latest is None or version_key(tag) >= version_key(latest["tag_name"])
    )

    uploads = curseforge.uploads_in(existing.get("body")) if existing else {}
    draft = {
        "tag_name": tag,
        "target_commitish": sha,
        "name": f"Thaumic Tracker {version} (Minecraft {minecraft})",
        "body": curseforge.with_uploads(body, uploads),
        "draft": True,
        "prerelease": prerelease,
    }
    # Keep the upload receipts on the same release even if replacing assets fails.
    if existing:
        release = api.request("PATCH", f"{base}/releases/{existing['id']}", draft)
        for asset in release["assets"]:
            api.request("DELETE", f"{base}/releases/assets/{asset['id']}")
    if current_tag:
        api.request("PATCH", f"{base}/git/refs/tags/{tag}", {"sha": sha, "force": True})
    else:
        api.request("POST", f"{base}/git/refs", {"ref": f"refs/tags/{tag}", "sha": sha})

    # Upload every tested artifact before making the release visible.
    if not existing:
        release = api.request("POST", f"{base}/releases", draft)
    upload_url = release["upload_url"].split("{", 1)[0]
    for file in files:
        api.request("POST", upload_url + "?" + urlencode({"name": file.name}), file.read_bytes())
    published = api.request("PATCH", f"{base}/releases/{release['id']}", {
        "draft": False, "make_latest": "true" if make_latest else "false"
    })
    return published["html_url"]


def main():
    root = Path(__file__).resolve().parents[2]
    version, tag, stem, notes, minecraft = metadata(root)
    ref = os.environ["GITHUB_REF"]
    if ref.startswith("refs/tags/") and ref != f"refs/tags/{tag}":
        raise ValueError(f"Expected release tag {tag} for this Minecraft and mod version")
    if len(sys.argv) != 2 or sys.argv[1] not in ("check", "publish", "publish-curseforge"):
        raise ValueError("Usage: release.py check|publish|publish-curseforge")
    if sys.argv[1] == "check":
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"version={version}\ntag={tag}\nstem={stem}\n")
        return
    sha = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    api = GitHub(os.environ["GH_TOKEN"], os.environ.get("GITHUB_API_URL", "https://api.github.com"))
    repository = os.environ["GITHUB_REPOSITORY"]
    if sys.argv[1] == "publish-curseforge":
        base = f"/repos/{repository}"
        tag_ref = f"refs/tags/{tag}"
        if is_current(api, base, ref, sha) and (ref == tag_ref or is_current(api, base, tag_ref, sha)):
            url = curseforge.publish(api, base, tag, version, minecraft, root / "release" / f"{stem}.jar",
                                    notes.read_text(encoding="utf-8").strip())
            print(f"CurseForge file for Thaumic Tracker {version}: {url}")
            if os.environ.get("GITHUB_STEP_SUMMARY"):
                with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
                    summary.write(f"[Thaumic Tracker {version} file on CurseForge]({url}).\n")
        return
    url = publish(api, repository, sha, ref, version, stem, root / "release", notes, minecraft)
    if url:
        print(f"Published Thaumic Tracker {version}: {url}")
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
                output.write("published=true\n")
        if os.environ.get("GITHUB_STEP_SUMMARY"):
            with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
                summary.write(f"Published [Thaumic Tracker {version}]({url}) from `{sha}`.\n")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError) as error:
        sys.exit(str(error))
