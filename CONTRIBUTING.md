# Working on Thaumic Tracker

On the `1.21.1` branch, use the checked-in Gradle wrapper with Java 21:

```sh
./gradlew build releaseArtifacts runGameTestServer -PsmokeTests
./gradlew runClientSmoke -PsmokeTests
```

The first command runs the Java checks and GameTests and stages the mod JAR,
sources JAR, and ZIP with guides and release history in `build/release/`.
The second checks the book button, HUD, and client synchronization in a disposable
development world. Smoke test code and data are excluded from release JARs.
Dependency versions are maintained in `gradle.properties`.

## Thaumaturge integration

Tracker 1.1 targets Thaumaturge 1.1. Thaumaturge's
[update notes](https://github.com/Leclowndu93150/Thaumaturge/blob/c71c2a275ca12278984bee4c7b3918e41f17033f/changelog.md)
cover book scrolling, text overflow fixes, research tree changes, and new entries.
These improve the book without replacing the pinned requirements HUD.
Keep reading research from the registry instead of maintaining entry-specific rules.
Multiblock diagrams and recipe displays describe crafting, not stage requirements;
they should not become extra checkboxes in the tracker.

The client-only accessor reads `entryId` from the Thaumaturge 1.21.1 research screen. Aspect queries use `AspectPoolAccess`. Readiness and
craft checks remain delegated to `ResearchManager` so Tracker follows the server.
The GameTest covers real crystal components and the new armor trim research,
multiple theory notes across stages, observation costs, and server advancement.
The client smoke test verifies the accessor, mouse routing, resizing and synchronization.

## Release automation

GitHub Actions builds and tests against both NeoForge versions in its matrix,
including the client smoke test on a virtual display. Successful builds on
`main` (Minecraft 26.1.2) and `1.21.1` can publish releases, as can matching tags.
Use `v<version>` for `main` and `v<version>-mc1.21.1` for the 1.21.1 build.
Pull requests and other branches run checks.

- Keep `mod_version` unchanged to replace that version's release and move its
  tag to the tested commit. Releases for other mod or Minecraft versions are
  preserved.
- To publish a new version, change `mod_version`, add `changelogs/<version>.md`,
  and add it to [the release history](changelogs/README.md). New stable versions
  become the latest release. When mod versions tie, the `main` release takes
  precedence for GitHub's Latest badge.
- Release descriptions come from the matching changelog. Missing notes fail
  validation before the build. Files are uploaded to a draft before publication.
- Outdated builds are skipped when the branch or tag has already moved.

Run release automation tests locally with Python 3:

```sh
python -m unittest discover -s .github/scripts -p "test_*.py"
```

The workflow is in [.github/workflows/build.yml](.github/workflows/build.yml),
and publishing is handled by [.github/scripts/release.py](.github/scripts/release.py).

### CurseForge

After the GitHub release is public, the same job submits the tested playable JAR
to [Thaumic Tracker on CurseForge](https://www.curseforge.com/minecraft/mc-mods/thaumic-tracker).
It sets the branch's Minecraft version, NeoForge, the client environment, the
matching changelog, and Thaumaturge as a required dependency. Sources and the
ZIP stay on GitHub.

Store the CurseForge author API token in the repository's Actions secret
`CURSEFORGE_TOKEN`. If this step fails, the GitHub release stays available.

A hidden comment in the GitHub release notes records file IDs and JAR digests.
Preserve it when editing the notes. Reruns skip identical JARs. A changed JAR
gets a new CurseForge file. If a run stops after CurseForge accepts the file
and before the receipt is saved, check the CurseForge author dashboard before
retrying. CurseForge reviews files before they appear publicly.
