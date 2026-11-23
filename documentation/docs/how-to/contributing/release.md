# Release

Release publication is opt-in. The dispatcher publishes only after successful verification, extensive tests and documentation on a push to `master` with `ENABLE_RELEASE=true`. This guide describes the current `release.config.js`, publication scripts and Actions workflow; a local dry run does not publish.

## 1. Verify publication contents

Review root `publishedModules` against [Modules and artifacts](../../reference/modules-and-artifacts.md). Exclude reserved modules, test utilities and the experimental unpublished solver. The C facade ships as release ZIPs rather than Maven publications.

Run these commands sequentially:

1. `./gradlew publishAllPublicationsToGithubRepository --dry-run`
2. `./gradlew publishAllPublicationsToProjectLocalRepository`
3. `./gradlew zipMavenCentralPortalPublication --dry-run`
4. `./scripts/publish-npm.sh -PforceVersion=0.0.0 github --dry-run`
5. `./scripts/publish-npm.sh -PforceVersion=0.0.0 npmjs --dry-run`
6. `./gradlew :interop-c:releaseAssets`

Inspect generated POMs from the local publication: group `io.github.gciatto`, artifact IDs `kt-constraints-<module>` and correctly prefixed target variants such as `kt-constraints-core-jvm`. Check license, SCM, developer metadata and dependencies. Maven task-graph dry runs alone do not inspect these files.

Confirm the GitHub npm preview names `@gciatto/kt-constraints-interop-js`, while npmjs uses `kt-constraints-interop-js`. C assets must be named `kt-constraints-c-<version>-<target>.zip` and contain their matching shared library and generated header. `releaseAssets` produces only targets linkable by the current host; the release job runs on macOS to build Apple klibs and all three desktop C assets.

## 2. Validate the release candidate

Follow [Run tests and benchmarks](run-tests-and-benchmarks.md) for checks, real-engine suites and language consumers. Run the C consumer on Linux and macOS with `./gradlew :interop-c:nativeUsageTest`. Confirm the [CI pipeline](../../reference/ci-pipeline.md) passes across JVM, Node, browser and desktop Native targets.

Keep the standard Gradle wrapper scripts, wrapper JAR and distribution checksum committed. Version information is computed from Git/Conventional Commits; release commands pass `-PforceVersion=<computed-version>` so every target shares the release version.

## 3. Configure credentials and destinations

| Destination | Enabled by | Credentials |
|---|---|---|
| GitHub Maven Packages at `maven.pkg.github.com/gciatto/kt-constraints` | `ENABLE_RELEASE=true` on the gated master push | Workflow `GITHUB_TOKEN`, mapped to `ORG_GRADLE_PROJECT_otherMavenUsername` and `ORG_GRADLE_PROJECT_otherMavenPassword` |
| GitHub npm Packages | Same release gate | `GITHUB_PACKAGES_TOKEN` mapped from workflow `GITHUB_TOKEN` |
| GitHub release C ZIPs | Same release gate | `GITHUB_TOKEN` with contents write |
| Maven Central | Release gate plus `ENABLE_MAVEN_CENTRAL=true` | `MAVEN_USERNAME`, `MAVEN_PASSWORD`, `SIGNING_KEY`, `SIGNING_PASSWORD` secrets |
| npmjs | Release gate plus `ENABLE_NPMJS=true` | `NPM_TOKEN` secret |

The dispatcher gives the release workflow contents and packages write permissions. GitHub Packages accepts unsigned Maven artifacts; Maven Central requires signing. CI maps Central credentials and signing secrets into `ORG_GRADLE_PROJECT_mavenCentralUsername`, `ORG_GRADLE_PROJECT_mavenCentralPassword`, `ORG_GRADLE_PROJECT_signingKey` and `ORG_GRADLE_PROJECT_signingPassword`.

Keep tokens out of Gradle properties and committed npm configuration. The npm script reads its token from the environment, writes temporary authentication configuration and cleans it up. Root properties contain only empty or placeholder credential values.

Enable optional destinations only after their credentials and signing configuration are present and their dry runs list the intended artifacts.

## 4. Enable and observe the automated release

Set repository variable `ENABLE_RELEASE=true` after the preceding checks. Push release-worthy Conventional Commits to `master` through the repository's review process. The workflow runs `npm install` and `npx semantic-release` on macOS with JDK 21 and Node 22.

The semantic-release configuration prepares C assets, publishes Maven artifacts to GitHub Packages, publishes the scoped npm package and attaches the C ZIPs to the GitHub release. It invokes Central and npmjs scripts only when their additional variables are true. Inspect Actions summaries and destination registries for the computed version.

`scripts/publish-maven-central.sh` first publishes locally and attempts the Central ZIP/release tasks. On failure it retries only modules whose ZIP or release task was reported failed, avoiding blanket re-upload of successful publications. Diagnose failures before manually repeating publication commands.

## 5. Enable documentation deployment separately

In repository Settings → Pages, select **GitHub Actions** as the build/deployment source, then set `ENABLE_PAGES=true`. The docs workflow builds and uploads its artifact regardless of this gate; deployment additionally requires `master`. See [CI pipeline](../../reference/ci-pipeline.md).
