"""Exercise release replacement without contacting GitHub."""

from pathlib import Path
import tempfile
import unittest

from release import GitHub, metadata, publish
from unittest.mock import patch
from urllib.error import HTTPError


class FakeGitHub:
    def __init__(self, releases=None, latest=None, head="tested", tags=None):
        self.releases = dict(releases or {})
        self.latest = latest
        self.head = head
        self.tags = dict(tags or {})
        self.calls = []
        self.uploads = []
        self.fail_upload = False

    def request(self, method, endpoint, data=None, *, missing_ok=False):
        self.calls.append((method, endpoint, data))
        route = endpoint.removeprefix("/repos/owner/repo")
        if method == "GET":
            if not route:
                return {"default_branch": "main"}
            if route.startswith("/commits/"):
                return {"sha": self.head} if self.head else None
            if route.startswith("/releases?per_page=100&page="):
                page = int(route.rsplit("=", 1)[-1])
                return list(self.releases.values())[(page - 1) * 100:page * 100]
            if route == "/releases/latest":
                return self.releases.get(self.latest)
            if route.startswith("/git/ref/tags/"):
                return self.tags.get(route.removeprefix("/git/ref/tags/"))
        if method == "DELETE" and route.startswith("/releases/"):
            tag = next(tag for tag, release in self.releases.items() if str(release["id"]) == route.split("/")[-1])
            del self.releases[tag]
            return None
        if method == "PATCH" and route.startswith("/git/refs/tags/"):
            self.tags[route.removeprefix("/git/refs/tags/")] = data["sha"]
            return {}
        if method == "POST" and route == "/git/refs":
            self.tags[data["ref"].removeprefix("refs/tags/")] = data["sha"]
            return {}
        if method == "POST" and route == "/releases":
            release = {**data, "id": 99, "upload_url": "https://uploads.test/assets{?name,label}", "html_url": "https://github.test/release"}
            self.releases[data["tag_name"]] = release
            return release
        if method == "POST" and endpoint.startswith("https://uploads.test/"):
            if self.fail_upload:
                raise RuntimeError("Upload failed")
            self.uploads.append((endpoint, data))
            return {}
        if method == "PATCH" and route == "/releases/99":
            release = next(release for release in self.releases.values() if release["id"] == 99)
            release.update(data)
            if data["make_latest"] == "true":
                self.latest = release["tag_name"]
            return release
        raise AssertionError(f"Unexpected API call: {method} {endpoint}")


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "changelogs").mkdir()

    def inputs(self, version="1.1"):
        (self.root / "gradle.properties").write_text(
            f"mod_id=thaumictracker\nmod_version={version}\nminecraft_version=26.1.2\n", encoding="utf-8"
        )
        notes = self.root / "changelogs" / f"{version}.md"
        notes.write_text(f"# Thaumic Tracker {version}\n\n- Clearer guides.\n", encoding="utf-8")
        stem = f"thaumictracker-26.1.2-{version}"
        for name in (f"{stem}.jar", f"{stem}-sources.jar", f"Thaumic-Tracker-{version}.zip"):
            (self.root / name).write_bytes(b"tested artifact")
        return stem, notes

    def publish(self, api, version="1.1", ref="refs/heads/main"):
        stem, notes = self.inputs(version)
        return publish(api, "owner/repo", "tested", ref, version, stem, self.root, notes)

    def test_same_version_is_replaced_and_retagged(self):
        older = {"id": 1, "tag_name": "v1.0"}
        api = FakeGitHub({"v1.0": older, "v1.1": {"id": 2, "tag_name": "v1.1"}}, "v1.1", tags={"v1.0": "original", "v1.1": "old"})
        self.publish(api)
        self.assertIn(("DELETE", "/repos/owner/repo/releases/2", None), api.calls)
        self.assertEqual(api.releases["v1.0"], older)
        self.assertEqual(api.tags, {"v1.0": "original", "v1.1": "tested"})
        self.assertEqual(api.latest, "v1.1")
        self.assertIn("Clearer guides.", api.releases["v1.1"]["body"])
        self.assertEqual(len(api.uploads), 3)
        self.assertEqual(api.calls[-1][2], {"draft": False, "make_latest": "true"})

    def test_new_patch_and_minor_versions_preserve_previous_releases(self):
        for version in ("1.1.1", "1.2"):
            with self.subTest(version=version):
                old = {"id": 2, "tag_name": "v1.1"}
                api = FakeGitHub({"v1.1": old}, "v1.1", tags={"v1.1": "original"})
                self.publish(api, version)
                self.assertEqual(api.releases["v1.1"], old)
                self.assertEqual(api.tags["v1.1"], "original")
                self.assertEqual(api.latest, f"v{version}")
                self.assertFalse(any(method == "DELETE" for method, _, _ in api.calls))

    def test_older_tag_does_not_replace_latest_version(self):
        api = FakeGitHub({"v1.2": {"id": 3, "tag_name": "v1.2"}}, "v1.2", tags={"v1.1": "tested"})
        self.publish(api, ref="refs/tags/v1.1")
        self.assertEqual(api.latest, "v1.2")
        self.assertEqual(api.calls[-1][2]["make_latest"], "false")

    def test_stale_branch_or_tag_build_does_not_mutate_github(self):
        for ref in ("refs/heads/main", "refs/tags/v1.1"):
            with self.subTest(ref=ref):
                api = FakeGitHub(head="newer")
                self.assertIsNone(self.publish(api, ref=ref))
                self.assertTrue(all(method == "GET" for method, _, _ in api.calls))

    def test_unrelated_branch_or_mismatched_tag_cannot_publish(self):
        for ref in ("refs/heads/feature", "refs/tags/v1.2", "refs/pull/7/merge"):
            with self.subTest(ref=ref):
                api = FakeGitHub()
                with self.assertRaises(ValueError):
                    self.publish(api, ref=ref)
                self.assertTrue(all(method == "GET" for method, _, _ in api.calls))

    def test_missing_artifact_is_detected_before_deleting_release(self):
        stem, notes = self.inputs()
        (self.root / f"{stem}.jar").unlink()
        api = FakeGitHub({"v1.1": {"id": 2, "tag_name": "v1.1"}})
        with self.assertRaises(ValueError):
            publish(api, "owner/repo", "tested", "refs/heads/main", "1.1", stem, self.root, notes)
        self.assertEqual(api.calls, [])

    def test_failed_upload_keeps_release_unpublished(self):
        api = FakeGitHub()
        api.fail_upload = True
        with self.assertRaises(RuntimeError):
            self.publish(api)
        self.assertTrue(api.releases["v1.1"]["draft"])
        self.assertFalse(any(method == "PATCH" and route.endswith("/releases/99") for method, route, _ in api.calls))
        api.fail_upload = False
        self.publish(api)
        self.assertFalse(api.releases["v1.1"]["draft"])
        self.assertEqual(len(api.uploads), 3)

    def test_replacement_finds_release_beyond_first_page(self):
        releases = {f"v2.{number}": {"id": 100 + number, "tag_name": f"v2.{number}"} for number in range(100)}
        releases["v1.1"] = {"id": 2, "tag_name": "v1.1"}
        api = FakeGitHub(releases, "v2.99")
        self.publish(api)
        self.assertIn(("DELETE", "/repos/owner/repo/releases/2", None), api.calls)
        self.assertEqual(len(api.releases), 101)
        self.assertEqual(api.latest, "v2.99")

    def test_prerelease_does_not_become_latest(self):
        api = FakeGitHub({"v1.1": {"id": 2, "tag_name": "v1.1"}}, "v1.1")
        self.publish(api, version="1.2-beta")
        self.assertTrue(api.releases["v1.2-beta"]["prerelease"])
        self.assertEqual(api.latest, "v1.1")

    def test_missing_notes_or_invalid_version_fails_validation(self):
        self.inputs()
        (self.root / "changelogs/1.1.md").unlink()
        with self.assertRaises(ValueError):
            metadata(self.root)
        for version in ("../1.1", "1", "1.1\nmod_id=invalid-id"):
            (self.root / "gradle.properties").write_text(f"mod_id=thaumictracker\nmod_version={version}\nminecraft_version=26.1.2\n", encoding="utf-8")
            with self.assertRaises(ValueError):
                metadata(self.root)

    def test_valid_metadata(self):
        self.inputs()
        self.assertEqual(metadata(self.root)[:3], ("1.1", "v1.1", "thaumictracker-26.1.2-1.1"))

    def test_api_treats_only_404_as_missing(self):
        api = GitHub("test-token", "https://api.github.test")
        for status in (404, 403, 500):
            with self.subTest(status=status), patch("release.urlopen", side_effect=HTTPError("https://api.github.test", status, "failure", {}, None)):
                if status == 404:
                    self.assertIsNone(api.request("GET", "/release", missing_ok=True))
                else:
                    with self.assertRaises(RuntimeError):
                        api.request("GET", "/release", missing_ok=True)


if __name__ == "__main__":
    unittest.main()
