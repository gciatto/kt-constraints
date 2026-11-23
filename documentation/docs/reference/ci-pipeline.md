# CI pipeline

The repository has four workflows under `.github/workflows`. The dispatcher coordinates reusable workflows; each reusable workflow also has a manual entry point.

## Workflow graph and gates

| Workflow | Trigger / role | Dependencies |
|---|---|---|
| `dispatcher.yml` (`CI/CD`) | Pushes to `master` and `develop`, pull requests, manual dispatch | Starts build verification; tests and docs follow build |
| `build-and-deploy.yml` | Reusable `mode` input defaults to `verify`; manual dispatch offers verification | Verify runs on Ubuntu; release runs on macOS |
| `test-extensively.yml` | Reusable or manual platform matrix | Dispatcher waits for build first |
| `docs.yml` | Reusable or manual documentation build | Dispatcher waits for build first |

Dispatcher release runs only for a push to `master` with repository variable `ENABLE_RELEASE == 'true'`, after successful build, extensive tests and docs. The dispatcher always emits an overall summary; a skipped opt-in release is reported separately.

## Verification

The build job uses Temurin JDK 21 and performs:

| Step | Command / check |
|---|---|
| Style and static analysis | `./gradlew ktlintCheck detektAll --parallel --continue` |
| Build, tests and coverage | `./gradlew check assemble koverXmlReport --parallel --continue` |
| Publication task graphs | GitHub Maven, project-local Maven and Central ZIP tasks with `--dry-run` |
| C asset generation | `./gradlew :interop-c:releaseAssets` (Linux and Windows libraries on Ubuntu) |
| npm publication preview | Node 22; `publish-npm.sh` for GitHub and npmjs with version `0.0.0-ci` and `--dry-run` |

The npm preview builds the distribution before asking npm to dry-run publication. Task-graph dry runs do not themselves validate generated POM contents or upload artifacts.

## Extensive test jobs

| Job | Environment | Coverage |
|---|---|---|
| `test-jvm` | Ubuntu, macOS, Windows × JDK 17 and 21 | `jvmTest --parallel --continue`; direct solvers, OR-Tools MIP and Java boundary suites |
| `test-js` | Ubuntu, Node 22 | `jsTest --parallel --continue`; Kotlin/JS and packaged TypeScript consumer checks |
| `test-native` | Ubuntu `linuxX64`, macOS `macosArm64`, Windows `mingwX64` | Core/model/DSL/solve/SPI, test utilities, Kotlin solver, MiniZinc portable representation and Z3 Native target tests |
| `test-minizinc` | Ubuntu, checksum-pinned MiniZinc 2.10.1 bundle | Five requested JVM engines, Node Gecode and TypeScript CLI checks |
| `test-minizinc-browser` | Ubuntu, headless Chrome | Chuffed MiniZinc Wasm, Z3 Wasm and packaged TypeScript browser checks |

Native tests extract the matching pinned Z3 C library. The Native job also runs the C consumer on Linux and macOS; Windows skips that step. Native MiniZinc tests cover portable code, not a Native execution route.

The MiniZinc job sets `MINIZINC_EXECUTABLE=minizinc` and `MINIZINC_ENGINES=gecode,chuffed,cp-sat,highs,cbc`; it installs `libegl1` required by bundled Gecode. Browser steps set `MINIZINC_BROWSER_TEST`, `Z3_BROWSER_TEST` or `INTEROP_BROWSER_TEST` and `CHROME_BIN=/usr/bin/google-chrome`. Browser reports are uploaded on failure. Runtime suites check that the actual contract report exists so omitted setup failures cannot silently pass.

See [Platform availability](platform-availability.md) for the route-by-platform matrix and its unclaimed targets.

## Documentation and release

Docs uses Python 3.12, `mkdocs-material`, JDK 21 and `:documentation:assembleSite`, which builds the site strictly and copies aggregated Dokka HTML under `api/`. The build uploads the Pages artifact. Deployment requires `master` and `ENABLE_PAGES == 'true'`, with a `github-pages` environment.

Release uses JDK 21 and Node 22 on macOS. `semantic-release` computes the version from Conventional Commits, prepares C ZIPs and publishes the configured targets. GitHub Maven/npm and release assets are enabled with the release gate; Maven Central and npmjs additionally require `ENABLE_MAVEN_CENTRAL` and `ENABLE_NPMJS`. Credentials come from Actions secrets mapped to environment variables. The dispatcher grants release write permissions for contents, packages, ID tokens, issues and pull requests. See [Release](../how-to/contributing/release.md) for preparation and credentials.
