# Working on Thaumic Tracker

Use the checked-in Gradle wrapper with Java 25:

```sh
./gradlew build releaseArtifacts runGameTestServer -PsmokeTests
./gradlew runClientSmoke -PsmokeTests
```

The first command runs the Java checks and GameTests and stages the mod JAR,
sources JAR, and ZIP with guides and release history in `build/release/`.
The second checks the book button, HUD, and client synchronization in a disposable
development world. Smoke test code and data are excluded from release JARs.
Dependency versions are maintained in `gradle.properties`.

## Release automation

GitHub Actions builds and tests against both NeoForge versions in its matrix,
including the client smoke test on a virtual display. Pull requests and other
branches run checks. Successful builds on the default branch, or a matching
`v<version>` tag, can publish a release.

- Keep `mod_version` unchanged to replace that version's release and move its
  tag to the tested commit. Other versions' releases and tags are preserved.
- To publish a new version, change `mod_version`, add `changelogs/<version>.md`,
  and add it to [the release history](changelogs/README.md). New stable versions
  become the latest release; publishing an older tag does not displace a newer version.
- Release descriptions come from the matching changelog. Missing notes fail
  validation before the build. Files are uploaded to a draft before publication.
- Outdated builds are skipped when the branch or tag has already moved.

Run release automation tests locally with Python 3:

```sh
python -m unittest discover -s .github/scripts -p "test_*.py"
```

The workflow is in [.github/workflows/build.yml](.github/workflows/build.yml),
and publishing is handled by [.github/scripts/release.py](.github/scripts/release.py).
