"""Upload the tested mod JAR through the CurseForge author API."""

from hashlib import sha256
import json
import os
import re
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from uuid import uuid4


PROJECT = 1736726
FILES_URL = "https://www.curseforge.com/minecraft/mc-mods/thaumic-tracker/files"
RECEIPT = re.compile(r"\n?<!-- thaumictracker-curseforge (\{[^\n]*\}) -->")
# The 26.1.2 JAR uploaded manually before automation has this verified digest.
EXISTING_UPLOADS = {
    "v1.1": {"e4d638ee9ed2d1bcdb3a078ceca93635c5e7c285b225092a066af01548b37b4d": 9432570},
}


def uploads_in(body):
    matches = RECEIPT.findall(body or "")
    if len(matches) > 1:
        raise ValueError("Multiple CurseForge upload receipts in GitHub release notes")
    uploads = json.loads(matches[0]) if matches else {}
    if not isinstance(uploads, dict) or any(
        not re.fullmatch(r"[a-f0-9]{64}", digest) or type(file_id) is not int or file_id <= 0
        for digest, file_id in uploads.items()
    ):
        raise ValueError("Invalid CurseForge upload receipt in GitHub release notes")
    return uploads


def with_uploads(notes, uploads):
    if not uploads:
        return notes
    return notes + "\n\n<!-- thaumictracker-curseforge " + json.dumps(uploads, sort_keys=True) + " -->"


class CurseForge:
    def __init__(self, token):
        if not token:
            raise ValueError("Add the CurseForge author API token as the CURSEFORGE_TOKEN Actions secret")
        self.token = token

    def request(self, endpoint, body=None, content_type="application/json"):
        request = Request("https://minecraft.curseforge.com/api/" + endpoint, data=body, headers={
            "X-Api-Token": self.token,
            "Content-Type": content_type,
            "User-Agent": "Thaumic-Tracker-release",
        })
        try:
            with urlopen(request, timeout=120) as response:
                return json.load(response)
        except HTTPError as error:
            detail = error.read(4096).decode("utf-8", errors="replace")
            detail = " ".join(detail.replace(self.token, "[redacted]").split())[:500]
            raise RuntimeError(f"CurseForge {endpoint}: HTTP {error.code} {detail}") from None
        except URLError as error:
            raise RuntimeError(f"CurseForge {endpoint}: {error.reason}") from None

    def upload(self, filename, jar, metadata):
        boundary = "thaumictracker-" + uuid4().hex
        body = (
            f'--{boundary}\r\nContent-Disposition: form-data; name="metadata"\r\n'
            'Content-Type: application/json\r\n\r\n'
        ).encode() + json.dumps(metadata).encode() + (
            f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{filename}"\r\n'
            'Content-Type: application/java-archive\r\n\r\n'
        ).encode() + jar + f"\r\n--{boundary}--\r\n".encode()
        result = self.request(f"projects/{PROJECT}/upload-file", body, f"multipart/form-data; boundary={boundary}")
        file_id = result.get("id") if isinstance(result, dict) else None
        if type(file_id) is not int or file_id <= 0:
            raise RuntimeError("CurseForge upload did not return a valid file ID")
        return file_id


def game_version_ids(versions, types, minecraft):
    ids = []
    for name, category in ((minecraft, "minecraft"), ("NeoForge", "modloader"),
                           ("Client", "environment")):
        type_ids = {row["id"] for row in types if row["slug"].lower().startswith(category)}
        matches = {row["id"] for row in versions if row["name"] == name and row["gameVersionTypeID"] in type_ids}
        if len(matches) != 1:
            raise ValueError(f"Cannot uniquely resolve CurseForge {category} version {name}: {sorted(matches)}")
        ids.append(matches.pop())
    return ids


def publish(api, base, tag, version, minecraft, jar_path, notes):
    release = api.request("GET", f"{base}/releases/tags/{tag}")
    jar = jar_path.read_bytes()
    digest = sha256(jar).hexdigest()
    if release["draft"] or not any(
        asset["name"] == jar_path.name and asset.get("digest") == f"sha256:{digest}"
        for asset in release["assets"]
    ):
        raise ValueError("The tested mod JAR must match the published GitHub release asset")

    recorded = uploads_in(release.get("body"))
    if digest in recorded:
        return f"{FILES_URL}/{recorded[digest]}"
    uploads = {**EXISTING_UPLOADS.get(tag, {}), **recorded}
    file_id = uploads.get(digest)
    if file_id is None:
        client = CurseForge(os.environ.get("CURSEFORGE_TOKEN", ""))
        game_versions = game_version_ids(client.request("game/versions"), client.request("game/version-types"), minecraft)
        filename = f"{jar_path.stem}-{digest[:12]}.jar" if uploads else jar_path.name
        file_id = client.upload(filename, jar, {
            "displayName": f"Thaumic Tracker {version} (Minecraft {minecraft}, NeoForge)",
            "changelog": notes,
            "changelogType": "markdown",
            "gameVersions": game_versions,
            "releaseType": "beta" if "-" in version else "release",
            "isMarkedForManualRelease": False,
            "relations": {"projects": [
                {"slug": "thaumaturge", "type": "requiredDependency"}
            ]},
        })
        print(f"CurseForge accepted the JAR for review: {FILES_URL}/{file_id}", flush=True)
    uploads[digest] = file_id
    api.request("PATCH", f"{base}/releases/{release['id']}", {"body": with_uploads(notes, uploads)})
    return f"{FILES_URL}/{file_id}"
