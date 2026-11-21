# Backend availability by platform

Each ✅ below is backed by a CI job that runs the route's real engine and fails when the engine is missing or
misbehaves. Every job is in `.github/workflows/test-extensively.yml`. A blank cell means the route is untested
there: it may work, but nothing proves it. A route marked — cannot exist on that platform.

| Route (`engine/route`)        | JVM Linux | JVM macOS arm64 | JVM Windows | Node | Chromium | Native Linux x64 | Native macOS arm64 | Native Windows x64 |
|-------------------------------|:---------:|:---------------:|:-----------:|:----:|:--------:|:----------------:|:------------------:|:------------------:|
| `z3/direct`                   | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `cp-sat/direct`               | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `choco/direct`                | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `scip/or-tools`               | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `highs/or-tools`              | ✅        | ✅              | ✅          | —    | —        | —                | —                  | —                  |
| `z3/wasm`                     | —         | —               | —           | ✅   | ✅       | —                | —                  | —                  |
| `z3/native`                   | —         | —               | —           | —    | —        | ✅               | ✅                 | ✅                 |
| `gecode/minizinc`             | ✅        |                 |             | —    | —        |                  |                    |                    |
| `chuffed/minizinc`            | ✅        |                 |             | —    | —        |                  |                    |                    |
| `cp-sat/minizinc`             | ✅        |                 |             | —    | —        |                  |                    |                    |
| `highs/minizinc`              | ✅        |                 |             | —    | —        |                  |                    |                    |
| `cbc/minizinc`                | ✅        |                 |             | —    | —        |                  |                    |                    |
| `gecode/minizinc-js-cli`      | —         | —               | —           | ✅   | —        | —                | —                  | —                  |
| `chuffed/minizinc-wasm`       | —         | —               | —           |      | ✅       | —                | —                  | —                  |
| `kotlin/direct`              |           |                 |             |      |          |                  |                    |                    |

Native MiniZinc cells are blank rather than "—" because command execution from Kotlin/Native is planned but not
implemented. The Node cell of `chuffed/minizinc-wasm` is blank because that route runs MiniZinc's browser build.

`kotlin/direct` has an implementation in `commonMain` and unconditional shared conformance tests. Its cells
remain blank: this delivery could not fetch Gradle 9.9.1, so none of its platform tests executed. Existing JVM
and JS jobs discover it, and the Native matrix now names it explicitly. See [the validation record](PURE-KOTLIN-VALIDATION.md).

## Facades

| Facade                                   | JVM Linux | JVM macOS arm64 | JVM Windows | Node | Chromium |
|------------------------------------------|:---------:|:---------------:|:-----------:|:----:|:--------:|
| Java (`:interop-java`), on the verified JVM routes above | ✅ | ✅     | ✅          | —    | —        |
| Java, `kotlin/direct`                    |           |               |             | —    | —        |
| JavaScript facade tests, `kotlin/direct` | —         | —             | —           |      |          |
| TypeScript (`:interop-js`), `z3/wasm`    | —         | —               | —           | ✅   | ✅       |
| TypeScript, `gecode/minizinc-js-cli`     | —         | —               | —           | ✅   | —        |
| TypeScript, `chuffed/minizinc-wasm`      | —         | —               | —           |      | ✅       |
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
