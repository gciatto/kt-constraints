# Backend availability by platform

Each ✅ below records real-engine test coverage described by the repository validation record or the CI jobs below. Requested runtime suites fail when their engine is missing or misbehaves. Every job is in `.github/workflows/test-extensively.yml`. — means no implementation for this platform; a blank cell means runtime coverage has not been established.

| Route (`engine/route`)        | JVM Linux | JVM macOS arm64 | JVM Windows | Node | Chromium | Native Linux x64 | Native macOS arm64 | Native Windows x64 |
|-------------------------------|:---------:|:---------------:|:-----------:|:----:|:--------:|:----------------:|:------------------:|:------------------:|
| `z3/direct`                   | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `cp-sat/direct`               | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `choco/direct`                | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `scip/or-tools`               | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `highs/or-tools`              | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `z3/wasm`                     | —         | —               | —           | ✅   | ✅       | —                | —                  | —                  |
| `z3/native`                   | —         | —               | —           | —    | —        | ✅               | ✅                 | ✅                 |
| `gecode/minizinc`             | ✅        |                 |             | —    | —        | — | — | — |
| `chuffed/minizinc`            | ✅        |                 |             | —    | —        | — | — | — |
| `cp-sat/minizinc`             | ✅        |                 |             | —    | —        | — | — | — |
| `highs/minizinc`              | ✅        |                 |             | —    | —        | — | — | — |
| `cbc/minizinc`                | ✅        |                 |             | —    | —        | — | — | — |
| `gecode/minizinc-js-cli`      | —         | —               | —           | ✅   | —        | —                | —                  | —                  |
| `chuffed/minizinc-wasm`       | —         | —               | —           | — | ✅       | —                | —                  | —                  |
| `kotlin/direct`              | ✅        |                 |             | ✅   |          | ✅               |                    |                    |

Native MiniZinc cells are marked — because command execution from Kotlin/Native is not implemented. The Node cell of `chuffed/minizinc-wasm` is marked — because that route runs MiniZinc's browser build.

`kotlin/direct` has an implementation in `commonMain` and unconditional shared conformance tests, executed locally on
JVM Linux, Node and Linux x64 Native; the other cells await CI evidence from the jobs that now include the module. The production implementation is portable; that alone does not prove the remaining platform cells.

## Facades

| Facade                                   | JVM Linux | JVM macOS arm64 | JVM Windows | Node | Chromium |
|------------------------------------------|:---------:|:---------------:|:-----------:|:----:|:--------:|
| Java (`:interop-java`), on the verified JVM routes above | ✅ | ✅     | ✅          | —    | —        |
| Java, `kotlin/direct`                    | ✅        |               |             | —    | —        |
| JavaScript facade tests, `kotlin/direct` | —         | —             | —           | ✅   |          |
| TypeScript (`:interop-js`), `z3/wasm`    | —         | —               | —           | ✅   | ✅       |
| TypeScript, `gecode/minizinc-js-cli`     | —         | —               | —           | ✅   | —        |
| TypeScript, `chuffed/minizinc-wasm`      | —         | —               | —           | — | ✅       |
| Kotlin `:solve-blocking`, `kotlin/direct` (also Linux x64 Native) | ✅ |   |             | —    | —        |
| Java `BlockingStore` (Choco, Z3)         | ✅        | ✅              | ✅          | —    | —        |
| TypeScript `Store`, `z3/wasm`            | —         | —               | —           | ✅   | ✅       |

## Constraint stores

| Store                                    | JVM Linux | JVM macOS arm64 | JVM Windows | Node | Chromium | Native Linux x64 | Native macOS arm64 | Native Windows x64 |
|------------------------------------------|:---------:|:---------------:|:-----------:|:----:|:--------:|:----------------:|:------------------:|:------------------:|
| `ChocoStore` (propagating)               | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `SatisfiabilityStore` over Z3            | ✅        | ✅              | ✅          | ✅   | ✅       | ✅               | ✅                 | ✅                 |

Both run the shared `storeContract` inside the suites listed below (`ChocoSpec`, `Z3Spec`, `Z3JsSpec`, `Z3NativeSpec`).

## Evidence

- **JVM** (`test-jvm`: Ubuntu, macOS arm64 and Windows, each on JDK 17 and 21): `./gradlew jvmTest`. The suites for
  the direct, OR-Tools and Java-facade routes are unconditional: an unavailable engine fails them. They are
  `Z3Spec`, `CpSatSpec`, `ChocoSpec`, `ScipSpec`, `HighsSpec` and `JavaUsageSpec`.
- **MiniZinc CLI** (`test-minizinc`: Ubuntu with the pinned MiniZinc 2.10.1 bundle). It runs the JVM five-engine
  suites, the Kotlin/JS Node CLI suite, and the TypeScript checks against the CLI route. These suites only run when
  `MINIZINC_EXECUTABLE` is set, which is why the other jobs prove nothing about MiniZinc.
- **Node** (`test-js`: Ubuntu, Node 22): `./gradlew jsTest`. It runs `Z3JsSpec` and the TypeScript checks
  (`interop-js/src/test/typescript`), type-checked by `tsc` and executed against the packaged library.
- **Chromium** (`test-minizinc-browser`: Ubuntu, Google Chrome headless). It runs the Chuffed and Z3 Wasm Kotest
  suites through Karma and the bundled TypeScript checks, served with cross-origin isolation.
- **Native** (`test-native`: `linuxX64` on Ubuntu, `macosArm64` on macOS, `mingwX64` on Windows). The Z3 C API
  suite loads the Z3 library that CI extracts from the pinned `z3-turnkey`. The build fails unless the suite
  actually ran, rather than only reporting the library as unavailable.

Untested and therefore unclaimed: iOS and Android, Kotlin/Wasm, browsers other than Chromium, Node versions other
than 22, and macOS x64.

## C library

The `:interop-c` shared library is configured for `linuxX64`, `macosArm64` and `mingwX64`. CI compiles and executes its C consumer on Linux and macOS; the Windows job does not run `nativeUsageTest`. Building a Windows release asset is separate from executing a C consumer there.

See [C API](c-api.md), [CI pipeline](ci-pipeline.md) and [Solvers and routes](solvers-and-routes.md). Runtime availability must still be probed on your machine; CI coverage does not install an engine into your application.
