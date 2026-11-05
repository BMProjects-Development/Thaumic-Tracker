"""Exercise the author API upload without publishing any files."""

from email.parser import BytesParser
from email.policy import default
from hashlib import sha256
from io import BytesIO
import json
import os
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import Mock, patch

import curseforge


class CurseForgeTests(unittest.TestCase):
    def setUp(self):
        directory = self.enterContext(TemporaryDirectory())
        self.jar = Path(directory) / "thaumictracker-1.21.1-1.3.jar"
        self.jar.write_bytes(b"tested playable JAR")
        self.digest = sha256(self.jar.read_bytes()).hexdigest()
        self.release = {"id": 10, "draft": False, "body": "Release notes", "assets": [
            {"name": self.jar.name, "digest": f"sha256:{self.digest}"},
        ]}
        self.api = Mock()
        self.api.request.side_effect = self.github

    def github(self, method, endpoint, data=None):
        if method == "GET" and endpoint == "/repos/owner/repo/releases/tags/v1.3-mc1.21.1":
            return self.release
        if method == "PATCH" and endpoint == "/repos/owner/repo/releases/10":
            self.release.update(data)
            return self.release
        self.fail(f"Unexpected GitHub request {method} {endpoint}")

    def publish(self):
        return curseforge.publish(self.api, "/repos/owner/repo", "v1.3-mc1.21.1", "1.3", "1.21.1",
                                  self.jar, "Release notes")

    def test_upload_uses_tested_jar_and_retry_reuses_receipt(self):
        versions = [
            {"id": 21, "name": "1.21.1", "gameVersionTypeID": 1},
            {"id": 22, "name": "NeoForge", "gameVersionTypeID": 2},
            {"id": 23, "name": "Client", "gameVersionTypeID": 3},
            {"id": 24, "name": "Server", "gameVersionTypeID": 3},
            {"id": 99, "name": "1.21.1", "gameVersionTypeID": 9},
        ]
        types = [{"id": number, "slug": slug} for number, slug in
                 [(1, "minecraft-1-21"), (2, "modloader"), (3, "environment"), (9, "bukkit")]]
        responses = [BytesIO(json.dumps(body).encode()) for body in (versions, types, {"id": 1234})]
        with patch.dict(os.environ, CURSEFORGE_TOKEN="author-token"), \
                patch("curseforge.urlopen", side_effect=responses) as send:
            self.assertEqual(self.publish(), f"{curseforge.FILES_URL}/1234")
            self.assertEqual(self.publish(), f"{curseforge.FILES_URL}/1234")
        self.assertEqual(send.call_count, 3)
        request = send.call_args.args[0]
        self.assertEqual(request.full_url, "https://minecraft.curseforge.com/api/projects/1736726/upload-file")
        self.assertEqual(request.get_header("X-api-token"), "author-token")
        multipart = BytesParser(policy=default).parsebytes(
            f"Content-Type: {request.get_header('Content-type')}\r\n\r\n".encode() + request.data
        )
        metadata_part, file_part = multipart.iter_parts()
        metadata = json.loads(metadata_part.get_payload(decode=True))
        self.assertEqual(metadata["gameVersions"], [21, 22, 23])
        self.assertEqual(metadata["changelog"], "Release notes")
        self.assertEqual(metadata["releaseType"], "release")
        self.assertFalse(metadata["isMarkedForManualRelease"])
        self.assertEqual(metadata["relations"]["projects"], [
            {"slug": "thaumaturge", "type": "requiredDependency"}
        ])
        self.assertEqual(file_part.get_filename(), self.jar.name)
        self.assertEqual(file_part.get_payload(decode=True), self.jar.read_bytes())
        self.assertEqual(curseforge.uploads_in(self.release["body"]), {self.digest: 1234})

    def test_changed_jar_under_same_version_gets_its_own_file(self):
        previous = {"a" * 64: 1234}
        self.release["body"] = curseforge.with_uploads("Release notes", previous)
        with patch("curseforge.CurseForge") as client, patch("curseforge.game_version_ids", return_value=[21]):
            client.return_value.upload.return_value = 5678
            self.assertEqual(self.publish(), f"{curseforge.FILES_URL}/5678")
            filename = client.return_value.upload.call_args.args[0]
        self.assertEqual(filename, f"{self.jar.stem}-{self.digest[:12]}.jar")
        self.assertEqual(curseforge.uploads_in(self.release["body"]), {**previous, self.digest: 5678})

    def test_known_manual_upload_is_recorded_without_resubmission(self):
        with patch.dict(curseforge.EXISTING_UPLOADS, {"v1.1": {self.digest: 9432570}}), \
                patch("curseforge.urlopen") as send:
            self.api.request.side_effect = [self.release, None]
            url = curseforge.publish(self.api, "/repos/owner/repo", "v1.1", "1.1", "26.1.2",
                                     self.jar, "Release notes")
            self.assertEqual(url, f"{curseforge.FILES_URL}/9432570")
        send.assert_not_called()
        self.assertEqual(curseforge.uploads_in(self.api.request.call_args.args[2]["body"]), {self.digest: 9432570})

    def test_mismatched_jar_or_draft_never_reaches_curseforge(self):
        with patch("curseforge.urlopen") as send:
            self.release["draft"] = True
            with self.assertRaisesRegex(ValueError, "match the published GitHub release"):
                self.publish()
            self.release["draft"] = False
            self.jar.write_bytes(b"other JAR")
            with self.assertRaisesRegex(ValueError, "match the published GitHub release"):
                self.publish()
        send.assert_not_called()

    def test_missing_secret_names_required_setup(self):
        with patch.dict(os.environ, CURSEFORGE_TOKEN=""), patch("curseforge.urlopen") as send:
            with self.assertRaisesRegex(ValueError, "CURSEFORGE_TOKEN"):
                self.publish()
        send.assert_not_called()
