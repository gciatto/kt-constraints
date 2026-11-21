# Installation

Nothing is on Maven Central or npm yet: releases are disabled until `ENABLE_RELEASE=true` (see
[RELEASING.md](RELEASING.md)). Until then, build from source as below. [AVAILABILITY.md](AVAILABILITY.md) lists which
route runs where, with CI evidence.

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
| `kt-constraints-interop-js`       | JS                         | ES module with TypeScript declarations                      |

## From source

Requires JDK 17+. `./gradlew publishToMavenLocal` installs every artifact into `~/.m2`, versioned from git (for
example `0.1.0-archeo-1f4ddf4`); signing is skipped without a `signingKey`. Then:

```kotlin
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation("io.github.gciatto:kt-constraints-dsl:<version>")
    implementation("io.github.gciatto:kt-constraints-solve-choco:<version>")
}
```

Java projects add `kt-constraints-interop-java` plus the backend artifacts they use.

For TypeScript, once released: `npm install kt-constraints-interop-js` (an ES module with TypeScript declarations,
Node 22+). From source, `./gradlew :interop-js:jsNodeProductionLibraryDistribution` writes the same package to
`interop-js/build/dist/js/productionLibrary`; install it with `npm install --install-links <that path>`.

## Solver runtimes

| Route                          | What to install                                                                                   |
|--------------------------------|---------------------------------------------------------------------------------------------------|
| `choco/direct`                 | Nothing: Choco is a Java library.                                                                 |
| `cp-sat/direct`, `scip/or-tools`, `highs/or-tools` | Nothing on Linux x64/arm64, macOS x64/arm64, Windows x64: `ortools-java` bundles the native libraries. |
| `z3/direct`                    | Nothing on Linux, macOS and Windows: `z3-turnkey` bundles the native libraries.                   |
| `z3/wasm`                      | Nothing extra on Node (npm `z3-solver` is a dependency). Browsers must serve `z3-built.js`/`.wasm` and be cross-origin isolated (`Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Embedder-Policy: require-corp`). |
| `z3/native`                    | A Z3 shared library, passed to `Z3NativeBackend(path)` or set in `KT_CONSTRAINTS_Z3_LIBRARY`, else found on the loader path. |
| `*/minizinc` (JVM, Node)       | The MiniZinc bundle (CI uses 2.10.1) with the engine's solver configuration; pass the `minizinc` executable path. |
| `chuffed/minizinc-wasm`        | Serve MiniZinc's browser worker, Wasm and data files; pass their URLs to `loadMiniZincBrowser`.   |

A route whose runtime is missing reports itself unavailable with the loader's reason; it never falls back silently.
