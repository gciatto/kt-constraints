# Modules and artifacts

All modules below are included by `settings.gradle.kts`. Publication eligibility comes from `publishedModules` in the root `build.gradle.kts`; it does not establish that a particular version has already been released.

“Portable” means JVM, JavaScript and the declared Native targets: `linuxX64`, `mingwX64`, `macosArm64`, `iosArm64` and `iosSimulatorArm64`. Target declarations do not establish engine availability: see [Platform availability](platform-availability.md).

| Gradle module | Purpose | Platforms | Maven publication | Maven coordinate (without version) |
|---|---|---|---|---|
| `:core` | Immutable expressions, sorts, exact numbers, domains and global nodes | Portable | Yes | `io.github.gciatto:kt-constraints-core` |
| `:model` | Model snapshots, metadata, objectives and ConstraintScope | Portable | Yes | `io.github.gciatto:kt-constraints-model` |
| `:dsl` | Kotlin operators and Java Expressions static facade | Portable | Yes | `io.github.gciatto:kt-constraints-dsl` |
| `:solve` | Batch, stream, session, store and result APIs | Portable | Yes | `io.github.gciatto:kt-constraints-solve` |
| `:solve-spi` | Backend/compiler SPI, support, registry, ranking and managed lifecycle | Portable | Yes | `io.github.gciatto:kt-constraints-solve-spi` |
| `:solve-z3` | Z3 Java, JS Wasm and Native C API adapters | Portable; runtime varies | Yes | `io.github.gciatto:kt-constraints-solve-z3` |
| `:solve-choco` | Choco batch solver and propagating ChocoStore | JVM | Yes | `io.github.gciatto:kt-constraints-solve-choco` |
| `:solve-cpsat` | Direct OR-Tools CP-SAT adapter | JVM | Yes | `io.github.gciatto:kt-constraints-solve-cpsat` |
| `:solve-mip` | MIP lowering; SCIP and HiGHS through OR-Tools | Portable IR; JVM execution | Yes | `io.github.gciatto:kt-constraints-solve-mip` |
| `:solve-minizinc` | MiniZinc compiler/decoder and runtime adapters | Portable IR; JVM and JS execution | Yes | `io.github.gciatto:kt-constraints-solve-minizinc` |
| `:solve-auto` | `AutoBackend`: automatic selection over the default routes, fallback/race strategies | Portable | Yes | `io.github.gciatto:kt-constraints-solve-auto` |
| `:solve-kotlin` | Pure-Kotlin solver and incremental sessions | Portable | Yes | `io.github.gciatto:kt-constraints-solve-kotlin` |
| `:solve-flatzinc` | Reserved module; no backend implementation | Portable declarations only | No | — |
| `:solve-highs` | Reserved direct HiGHS adapter; implemented route is in solve-mip | Portable declarations only | No | — |
| `:interop-java` | Blocking, future and iterator wrappers; Java number conversions | JVM | Yes | `io.github.gciatto:kt-constraints-interop-java` |
| `:interop-js` | Exported ES module with generated TypeScript declarations | JS | Yes | `io.github.gciatto:kt-constraints-interop-js` |
| `:interop-c` | Generated C header and shared library | Native desktop libraries | No | — |
| `:test-core` | Shared representation test utilities | Portable | No | — |
| `:test-solve` | Solver, session, store and differential contracts; benchmark catalogue | Portable | No | — |
| `:documentation` | Site and aggregated API documentation | Build tasks | No | — |

Multiplatform Maven publications also have platform variants, for example `io.github.gciatto:kt-constraints-core-jvm`. Kotlin Multiplatform resolves these from the unsuffixed coordinate. JVM-only modules use the unsuffixed artifact name.

The npm facade is published to GitHub Packages as `@gciatto/kt-constraints-interop-js`. The optional npmjs publication uses `kt-constraints-interop-js`. No other module has a separately published npm facade.

The C release assets are `kt-constraints-c-<version>-<target>.zip`, containing the library and generated header for `linuxX64`, `macosArm64` or `mingwX64`. They are not Maven publications.

See [Install](../how-to/install.md) and [Release](../how-to/contributing/release.md) for repositories and publication gates.
