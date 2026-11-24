# MiniZinc

## What it is

MiniZinc is a modelling and compilation system. This backend generates MiniZinc source; MiniZinc flattens that source into the solver-specific form used by the selected engine. **Flattening** replaces high-level expressions and globals with lower-level variables and constraints. Its cost and the resulting decomposition can differ substantially from a direct binding.

## Routes and classes

All implemented routes belong to `:solve-minizinc`, artifact `kt-constraints-solve-minizinc`.

| Route | Kotlin API | Engine configuration | Search family |
|---|---|---|---|
| `gecode/minizinc` | `MiniZincBackend(MiniZincEngine.GECODE)` | `org.gecode.gecode` | Finite-domain CP |
| `chuffed/minizinc` | `MiniZincBackend(MiniZincEngine.CHUFFED)` | `org.chuffed.chuffed` | CP |
| `cp-sat/minizinc` | `MiniZincBackend(MiniZincEngine.CP_SAT)` | `cp-sat` | CP-SAT |
| `highs/minizinc` | `MiniZincBackend(MiniZincEngine.HIGHS)` | `org.minizinc.mip.highs` | MIP |
| `cbc/minizinc` | `MiniZincBackend(MiniZincEngine.CBC)` | `org.minizinc.mip.coin-bc` | MIP |
| `gecode/minizinc-js-cli` | `MiniZincJsBackend.loadNode(...)` | Gecode through installed CLI | Finite-domain CP |
| `chuffed/minizinc-wasm` | `MiniZincJsBackend.loadBrowser(...)` | Browser Chuffed runtime | CP |

`MiniZincGecodeBackend` is a source-compatible JVM entry point delegating to the Gecode backend. It does not fall back to another engine.

The public `MiniZincCompiler` implements `ConstraintCompiler<MiniZincModel>`. It emits MiniZinc source, with source-variable mappings and a selected engine, rather than implementing a standalone FlatZinc transport. **FlatZinc** is MiniZinc's lower-level interchange language. The reserved `:solve-flatzinc` module has no operational adapter; it is not a dependency required to execute these routes. See the [compiler source](https://github.com/gciatto/kt-constraints/blob/develop/solve-minizinc/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/minizinc/MiniZincCompiler.kt) and [architecture](../architecture.md).

## Platforms and runtime requirements

JVM execution requires an installed MiniZinc executable and the exact selected configuration; availability includes configuration discovery and a startup probe. Automatic defaults add all five configurations only when `MINIZINC_EXECUTABLE` is set. Direct construction accepts an executable path or defaults to `minizinc`.

Node executes the installed CLI using Gecode. Its automatic loader adds that route when `MINIZINC_EXECUTABLE` is set. Browser execution requires explicit absolute worker, Wasm and data asset URLs and loads Chuffed. Browser MiniZinc is never added by `AutoBackend.load()`.

Only one JS MiniZinc backend may own the runtime pool at a time. Close solvers and then the loaded backend; await a fresh load before reuse after shutdown. Kotlin/Native command transport is not implemented. The [platform reference](../../reference/platform-availability.md) distinguishes the implementation from operating-system test coverage.

## Capabilities

All routes support Boolean/integer batch satisfaction and integer optimization. Gecode, Chuffed and CP-SAT routes additionally support exhaustive projected satisfaction enumeration. HiGHS and CBC do not: their streams may contain a witness or optimization incumbents without enumerating every satisfying assignment.

None exposes incremental assertion sessions, assumptions, unsatisfiable cores or stores. Full assignments are validated before projection. HiGHS/CBC proof statuses rely on numerical engine evidence, not exact rational certificates. See [solvers and routes](../../reference/solvers-and-routes.md).

## Supported modelling features and numeric limits

The compiler supports Boolean formulas, finite integer domains, arithmetic, element, tables, cardinalities and built-in structural globals, through emitted MiniZinc expressions and exact decompositions. Engine-specific flattening then determines the low-level encoding.

| Engine | Maximum absolute integer | Extra restriction |
|---|---:|---|
| Gecode | 2,147,483,646 | Also applies to Node CLI |
| Chuffed | 499,999,999 | Also applies to browser Wasm; excludes reserved sentinel values |
| CP-SAT | 2,147,483,646 | Deliberately smaller than the direct CP-SAT margin |
| HiGHS, CBC | 1,000,000 | Multiplication must be affine: at least one factor has a fixed value |

Bounds cover literals, domains and intermediates. Reals, bit-vectors, unsupported expressions and out-of-range arithmetic are rejected before execution. MiniZinc's broader language is not automatically part of this adapter's model API. These limits come from [MiniZincEngine](https://github.com/gciatto/kt-constraints/blob/develop/solve-minizinc/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/minizinc/MiniZincEngine.kt) and its lowering.

## Options type

`MiniZincOptions` implements `BackendOptions` for the JVM CLI routes: optional `randomSeed`, optional positive `threads`, and `freeSearch = false`. The CLI uses seed, parallelism and free-search flags; CBC skips the seed. The JS routes do not apply these tuning options yet. Ordinary `SolveOptions` time and solution limits remain separate. See the [options source](https://github.com/gciatto/kt-constraints/blob/develop/solve-minizinc/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/minizinc/MiniZincOptions.kt).

## Performance

Recorded runs show a fixed process/flattening cost on small models, and flattening can dominate larger ones. Direct CP-SAT beats its MiniZinc route on the measured large queens case. Conversely, MiniZinc's MIP decomposition can outperform this project's direct MIP lowering on globals. This is why the route matters even when the engine ID matches. See [benchmarks](../../reference/benchmarks.md) for timings and bundle versions; no universal route ordering follows from these examples.

## How to use it directly

[Use MiniZinc](../../how-to/use-minizinc.md) quotes a tested JVM availability/support/solve example and explains JS loading and cleanup. [MiniZinc browser setup](../../how-to/use-minizinc.md#browser-runtime-assets) covers its worker, Wasm and data assets with a tested TypeScript loader. Require enumeration explicitly when you need every projected satisfaction assignment.
