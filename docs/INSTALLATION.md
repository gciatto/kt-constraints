# Installation

Releases go to three places, all on GitHub:

- **Maven artifacts** (JVM, JS and Native variants of every library module):
  [GitHub Packages, Maven registry](https://github.com/gciatto?tab=packages&repo_name=kt-constraints),
  at `https://maven.pkg.github.com/gciatto/kt-constraints`.
- **npm package** of the TypeScript facade: `@gciatto/kt-constraints-interop-js` on GitHub Packages' npm registry.
- **C shared library and header** for Linux x64, macOS arm64 and Windows x64: zips attached to each
  [GitHub release](https://github.com/gciatto/kt-constraints/releases).

GitHub Packages requires authentication even for public packages: create a personal access token (classic) with the
`read:packages` scope. Maven Central and npmjs publication is prepared but not yet enabled.
[AVAILABILITY.md](AVAILABILITY.md) lists which solver route runs where, with CI evidence.

## Artifacts

Group `io.github.gciatto`. Multiplatform modules resolve to their platform variant (`-jvm`, `-js`, `-linuxx64`, ...)
through Gradle module metadata; Maven users name the variant explicitly.

| Artifact                          | Platforms                  | Use                                                         |
|-----------------------------------|----------------------------|-------------------------------------------------------------|
| `kt-constraints-core`             | JVM, JS, Native            | Expressions, sorts, domains, exact integers and rationals   |
| `kt-constraints-model`            | JVM, JS, Native            | `ConstraintScope`, `ConstraintModel`                        |
| `kt-constraints-dsl`              | JVM, JS, Native            | Kotlin operators; Java `Expressions` facade                 |
| `kt-constraints-solve`            | JVM, JS, Native            | Solver, session and result API                              |
| `kt-constraints-solve-spi`        | JVM, JS, Native            | Backend contract and registry                               |
| `kt-constraints-solve-z3`         | JVM, JS, Native            | Z3: direct JVM, Wasm on JS, C API on Native                 |
| `kt-constraints-solve-choco`      | JVM                        | Choco                                                       |
| `kt-constraints-solve-cpsat`      | JVM                        | CP-SAT (OR-Tools)                                           |
| `kt-constraints-solve-mip`        | JVM (routes); JS/Native carry only internal lowering | SCIP and HiGHS (OR-Tools)         |
| `kt-constraints-solve-minizinc`   | JVM, JS                    | MiniZinc CLI (JVM, Node) and browser Wasm                   |
| `kt-constraints-solve-portfolio`  | JVM, JS, Native            | `solveAuto`, default routes per platform, fallback and race |
| `kt-constraints-interop-java`     | JVM                        | `BlockingSolver`, `BlockingSession`, `BlockingStore`, `JavaNumbers` |
| `kt-constraints-interop-js`       | JS                         | Kotlin/JS klib; JS users take the npm package below         |

## Gradle

```kotlin
repositories {
    mavenCentral() // kt-math and the solver libraries
    maven("https://maven.pkg.github.com/gciatto/kt-constraints") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
dependencies {
    implementation("io.github.gciatto:kt-constraints-dsl:<version>")
    implementation("io.github.gciatto:kt-constraints-solve-portfolio:<version>")
}
```

Put `gpr.user` (your GitHub login) and `gpr.key` (the token) in `~/.gradle/gradle.properties`, or set the
environment variables; in GitHub Actions, `GITHUB_TOKEN` works for public packages. Java projects add
`kt-constraints-interop-java` plus the backend artifacts they use. Maven users add the same repository, with the
credentials in `settings.xml`, and name the platform variant explicitly (for example `kt-constraints-dsl-jvm`).

## npm

```bash
# .npmrc, next to package.json: only the @gciatto scope comes from GitHub Packages
echo '@gciatto:registry=https://npm.pkg.github.com' >> .npmrc
echo '//npm.pkg.github.com/:_authToken=${GITHUB_TOKEN}' >> .npmrc
npm install @gciatto/kt-constraints-interop-js
```

It is an ES module with TypeScript declarations, for Node 22+ and bundlers;
`import { ModelBuilder } from "@gciatto/kt-constraints-interop-js"`.

## C

Download `kt-constraints-c-<version>-<target>.zip` from the [releases](https://github.com/gciatto/kt-constraints/releases)
(`linuxX64`, `macosArm64`, `mingwX64`). Each holds the shared library (`libktconstraints.so`,
`libktconstraints.dylib`, or `ktconstraints.dll` with its `.def`) and its header. Solving uses Z3, loaded at run
time: install a Z3 shared library, then point `KT_CONSTRAINTS_Z3_LIBRARY` at it or put it on the loader path. See
[the C facade](../interop-c/README.md).

## From source

Requires JDK 17+. `./gradlew publishToMavenLocal` installs every artifact into `~/.m2`, versioned from git (for
example `1.0.0-archeo-1f4ddf4`); signing is skipped without a `signingKey`. Use `mavenLocal()` in place of the
GitHub Packages repository. `./gradlew :interop-js:jsNodeProductionLibraryDistribution` writes the npm package to
`interop-js/build/dist/js/productionLibrary` (install it with `npm install --install-links <that path>`), and
`./gradlew :interop-c:releaseAssets` writes the C zips that this host can build to `interop-c/build/distributions`.

## Solver runtimes

| Route                          | What to install                                                                                   |
|--------------------------------|---------------------------------------------------------------------------------------------------|
| `choco/direct`                 | Nothing: Choco is a Java library.                                                                 |
| `cp-sat/direct`, `scip/or-tools`, `highs/or-tools` | Nothing on Linux x64/arm64, macOS x64/arm64, Windows x64: `ortools-java` bundles the native libraries. |
| `z3/direct`                    | Nothing on Linux, macOS and Windows: `z3-turnkey` bundles the native libraries.                   |
| `z3/wasm`                      | Nothing extra on Node (npm `z3-solver` is a dependency). Browsers must serve `z3-built.js`/`.wasm` and be cross-origin isolated (`Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Embedder-Policy: require-corp`). |
| `z3/native` (and the C library) | A Z3 shared library, passed to `Z3NativeBackend(path)` or set in `KT_CONSTRAINTS_Z3_LIBRARY`, else found on the loader path. |
| `*/minizinc` (JVM, Node)       | The MiniZinc bundle (CI uses 2.10.1) with the engine's solver configuration; pass the `minizinc` executable path. |
| `chuffed/minizinc-wasm`        | Serve MiniZinc's browser worker, Wasm and data files; pass their URLs to `loadMiniZincBrowser`.   |

A route whose runtime is missing reports itself unavailable with the loader's reason; it never falls back silently.
