# Bootstrap verification — 2026-11-14

Baseline: `42e0d1f`, verified on macOS arm64 with OpenJDK 27.0.1. Work continues on `develop`.
This records M0 evidence; it does not mark P0 or any backend complete.

## Wrapper and build conventions

- Gradle remains pinned to 9.9.1. Its distribution SHA-256 matches the
  [official checksum](https://services.gradle.org/distributions/gradle-9.9.1-bin.zip.sha256).
- The committed wrapper JAR SHA-256 is
  `621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8`, matching the
  [official wrapper checksum](https://services.gradle.org/distributions/gradle-9.9.1-wrapper.jar.sha256).
- Restored the upstream UTF-8 default in both launcher scripts. They now match the
  [9.9.1 scripts](https://github.com/gradle/gradle/tree/v9.9.1), allowing Windows line endings.
- The existing kt-mpp templates, version catalog, Kotest/KSP wiring and publish-on-central 9.2.4
  pin follow the reference projects. No scaffold replacement or dependency upgrade was needed.
- `JAVA_HOME=<installed JDK 17> ./gradlew help` also passes. The extensive-test matrix was retained;
  this configuration check alone does not establish JDK 17 test coverage.

## Executed checks

| Command/check | Result |
|---|---|
| `./gradlew jvmTest jsTest --parallel --continue` | Passed; baseline has seven test leaves per platform |
| `./gradlew ktlintFormat` | Passed |
| `./gradlew ktlintCheck detektAll --parallel --continue` | Passed |
| `./gradlew check --parallel --continue` | Failed at six Apple Native test-link tasks; see below |
| `./gradlew :documentation:assembleSite` | Passed with MkDocs Material installed in a temporary virtual environment and a fresh Gradle process |
| Common metadata compilation for `:core`, `:model`, `:dsl` | Passed |
| JVM and Kotlin Multiplatform POM generation for all five publishable modules | Passed; ten POMs inspected |
| `:interop-java:compileKotlin`, `:interop-js:compileKotlinJs` | No source; these remain empty facades, not validated Java/TypeScript APIs |
| `./gradlew zipMavenCentralPortalPublication --dry-run` | Passed as part of the metadata/publication task-graph inspection; no publication performed |

The six failing tasks are `linkDebugTestIosSimulatorArm64` and `linkDebugTestMacosArm64` in each of
`:core`, `:model` and `:dsl`. All report `MissingXcodeException` from `xcrun xcodebuild -version`.
Only `/Library/Developer/CommandLineTools` is installed. Native compilation is exercised, but Apple
test linking/execution requires full Xcode. Linux and Windows Native execution require their CI hosts.
No tests were disabled in repository configuration to conceal this limitation.

Gradle also reports deprecated features before Gradle 10. Build scans were not published because
the terms of use have not been accepted; verification does not require publishing scans.

## Publication and runtime truthfulness

Every inspected POM has group `io.github.gciatto`, artifact `kt-constraints-<module>` (plus `-jvm`
for JVM), Apache-2.0 licensing, developer and SCM metadata. The five modules are `core`, `model`,
`dsl`, `solve` and `solve-spi`; their POMs contain no `it.unibo.tuprolog` dependency.

Backend, interop and test-utility modules remain outside `publishedModules`. No backend is operational.
The dispatcher requires a push to `master` and `ENABLE_RELEASE == 'true'` before releasing;
Pages similarly requires `master` and `ENABLE_PAGES == 'true'`. Neither variable was present when
queried through the GitHub API. No repository variables or publishing settings were changed.

## First M1 regression

Two new `ConstraintModelSpec` leaves reproduce caller mutation of model variables and constraints.
Both failed on JVM and JS before implementation and passed after model construction switched to
immutable Kotlin `buildList` snapshots. Tests and implementation are committed separately.
The final `jvmTest jsTest` run passes all nine test leaves per platform, and formatting, lint and
static analysis pass. Full `check` still fails only at the six Xcode-dependent link tasks above.
The following explicitly restricted check passes; it is not a replacement for Native CI:

```bash
./gradlew check --parallel --continue \
  -x :core:iosSimulatorArm64Test -x :model:iosSimulatorArm64Test -x :dsl:iosSimulatorArm64Test \
  -x :core:macosArm64Test -x :model:macosArm64Test -x :dsl:macosArm64Test
```

Custom expression/domain immutability, arbitrary precision, reusable algebra contracts and the remaining
M1–M8 work are still outstanding; no stable API or solver support is claimed.
