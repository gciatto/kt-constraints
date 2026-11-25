# Solvers and routes

Check three independent things: **capabilities** describe the kinds of operations a route offers;
**availability** checks whether its runtime can run here; **support** checks whether it can represent
the entire model and requested options. See [Kotlin solver API](solver-api.md).

`SolverDescriptor.id` is `engine/route`. Its capabilities describe an adapter's operations, independently of runtime availability and whole-model support. `SolverBackend.availability()` probes the runtime; `support(model, options)` returns unsupported constructs and required lossless decompositions. A compatible model must pass all three checks.

## Batch and incremental routes

In this table, **base** means `SATISFACTION`, `OPTIMIZATION`, `INTEGER_ARITHMETIC` and `GLOBAL_CONSTRAINTS`. **SMT** adds `INCREMENTAL_ASSERTIONS`, `BACKTRACKING`, `ASSUMPTIONS`, `UNSAT_CORES`, `REAL_ARITHMETIC` and `BITVECTORS`. These are exact enum names from `SolverCapability`.

| ID | Backend / engine binding | Execution platform | Descriptor capabilities |
|---|---|---|---|
| `z3/direct` | `Z3Backend`, bundled Z3 Java API | JVM | Base + SMT + `ENUMERATION` |
| `z3/wasm` | `Z3JsBackend.load()`, z3-solver Wasm | Node, browser | Base + SMT + `ENUMERATION` |
| `z3/native` | `Z3NativeBackend`, dynamically loaded Z3 C API | Native | Base + SMT + `ENUMERATION` |
| `cp-sat/direct` | `CpSatBackend`, OR-Tools CP-SAT | JVM | Base + `ENUMERATION` |
| `choco/direct` | `ChocoBackend`, Choco | JVM | Base + `ENUMERATION` |
| `scip/or-tools` | `OrToolsMipBackend.scip()`, OR-Tools MPSolver | JVM | Base |
| `highs/or-tools` | `OrToolsMipBackend.highs(limitMillis)`, OR-Tools MPSolver | JVM | Base |
| `gecode/minizinc` | `MiniZincBackend(GECODE)`, Gecode CLI configuration | JVM | Base + `ENUMERATION` |
| `chuffed/minizinc` | `MiniZincBackend(CHUFFED)`, Chuffed CLI configuration | JVM | Base + `ENUMERATION` |
| `cp-sat/minizinc` | `MiniZincBackend(CP_SAT)`, CP-SAT CLI configuration | JVM | Base + `ENUMERATION` |
| `highs/minizinc` | `MiniZincBackend(HIGHS)`, HiGHS CLI configuration | JVM | Base |
| `cbc/minizinc` | `MiniZincBackend(CBC)`, CBC CLI configuration | JVM | Base |
| `gecode/minizinc-js-cli` | `MiniZincJsBackend.loadNode(...)`, installed Gecode CLI | Node | Base + `ENUMERATION` |
| `chuffed/minizinc-wasm` | `MiniZincJsBackend.loadBrowser(...)`, Chuffed browser runtime | Browser | Base + `ENUMERATION` |
| `kotlin/direct` | `KotlinBackend` (TypeScript: `loadKotlin()`), pure-Kotlin engine | Common code: JVM, JS, Native | Base + SMT + `ENUMERATION` |

`ENUMERATION` denotes projected satisfaction enumeration: finite projected domains can be exhausted, while real-valued outputs may produce an endless stream of distinct rational witnesses. A route without it can still expose a stream containing a witness or improving optimization incumbents. Stream presence alone does not imply exhaustive enumeration.

No batch descriptor advertises `DOMAIN_PROPAGATION` or `ENTAILMENT`. Those belong to the separate store interface. `ChocoStore` uses the same `choco/direct` ID with a store-specific descriptor. It advertises `INCREMENTAL_ASSERTIONS`, `BACKTRACKING`, `DOMAIN_PROPAGATION`, `ENTAILMENT`, `INTEGER_ARITHMETIC` and `GLOBAL_CONSTRAINTS`, with `PROPAGATION` evidence. `SatisfiabilityStore` wraps an incremental solver and answers entailment by satisfiability checks; it reports `SATISFIABILITY` evidence and does not narrow domains by propagation.

`AutoBackend` has the descriptor `auto/fallback` or `auto/race`, advertising the batch and session capabilities that at least one of its routes declares; its solvers open sessions on the best incremental route. When no route is eligible, its solver throws `UnsupportedModelException`. When eligible attempts produce no answer, it returns `UNKNOWN` with that descriptor. Successful results retain the answering route's descriptor.

## Concrete backend entry points

All backend classes implement `availability`, `support`, `createSolver`, `isAvailable`, `descriptor` and `close` through the [backend contract](solver-api.md#backends-capabilities-and-availability).

| Entry point | Additional public surface |
|---|---|
| `Z3.load()` | Suspending common entry point: returns `Z3Backend` on the JVM, `Z3NativeBackend` on Kotlin/Native and `Z3JsBackend` on JavaScript. Close the returned backend to release its runtime |
| `Z3Backend()`, `Z3NativeBackend(libraryPath)` | `createSolver()` returns `IncrementalConstraintSolver`; companion `DESCRIPTOR` identifies the route before creating a solver |
| `Z3JsBackend.load()` | Suspending loader; `createSolver()` returns `IncrementalConstraintSolver`. `DESCRIPTOR` identifies `z3/wasm`; idempotent `close()` closes created solvers and terminates runtime workers |
| `CpSatBackend(workers, seed)` | Readable `workers` and `seed` defaults; companion `DESCRIPTOR` identifies `cp-sat/direct` |
| `ChocoBackend()` | Companion `DESCRIPTOR` identifies batch `choco/direct` |
| `ChocoStore()` | Companion `DESCRIPTOR` identifies its separate store capabilities under `choco/direct`; operations follow the [store contract](solver-api.md#constraint-stores) |
| `OrToolsMipBackend.scip()`, `.highs(solveLimitMillis)` | `SCIP` and `HIGHS` companion descriptors; `solveLimitMillis` is null for interruptible SCIP and the required positive engine limit for HiGHS |

The route settings are listed in [Configuration](configuration.md). A descriptor constant is metadata, not a runtime availability probe.

## Checked numeric subsets

These are adapter limits, often more conservative than an engine's advertised range. Checks cover expression intermediates and accumulated magnitudes, not just variable bounds. Rejection is explicit; values are never truncated to fit.

| Route | Integer subset | Other numeric support |
|---|---|---|
| All Z3 routes | Exact arbitrary-precision integer expressions over current finite variable domains; no fixed adapter magnitude bound | Exact rational linear reals, fixed-width bit-vectors |
| `kotlin/direct` | Arbitrary-precision finite integer domains and circuits, subject to compiler/work limits | Exact rational linear feasibility and bit-vectors |
| `cp-sat/direct` | Checked magnitude at most `2^61` | Boolean/integer only |
| `choco/direct`, `ChocoStore` | Checked magnitude at most `10^7` | Boolean/integer only |
| `scip/or-tools`, `highs/or-tools` | Checked margin `10^6` for literals, coefficients, bounds and derived rows; affine products only | Boolean/integer MIP lowering |
| `gecode/minizinc`, `gecode/minizinc-js-cli` | Symmetric maximum absolute integer `2,147,483,646` | Boolean/integer only |
| `chuffed/minizinc`, `chuffed/minizinc-wasm` | Symmetric maximum absolute integer `499,999,999`; ±500,000,000 are reserved sentinels | Boolean/integer only |
| `cp-sat/minizinc` | Symmetric maximum absolute integer `2,147,483,646` | Boolean/integer only |
| `highs/minizinc`, `cbc/minizinc` | Symmetric maximum absolute integer `1,000,000`; reject variable-by-variable products | Boolean/integer only |

MIP assignments are decoded and validated against the original model. Numerical engine proof evidence underlies optimality and infeasibility; these adapters do not supply exact rational proof certificates. A positive OR-Tools MIP relative gap yields feasible results, never optimal ones.

Pure-Kotlin compiler defaults are 16,384 bits per arithmetic circuit, 1,000,000 SAT variables and 4,000,000 compiler clauses; work is unlimited unless configured. Reaching limits yields `UNKNOWN` or an incumbent-bearing `FEASIBLE`, with resource termination. These settings do not bound all learned clauses or real-elimination rows.

## Runtime and selection rules

MiniZinc's configurations are selected explicitly; there is no substitution of the installation's default solver.

| Engine enum | Configuration ID |
|---|---|
| `GECODE` | `org.gecode.gecode` |
| `CHUFFED` | `org.chuffed.chuffed` |
| `CP_SAT` | `cp-sat` |
| `HIGHS` | `org.minizinc.mip.highs` |
| `CBC` | `org.minizinc.mip.coin-bc` |

Default registries list routes fastest first, according to the recorded benchmarks. JVM: CP-SAT, Choco, `kotlin/direct`, Z3, SCIP and HiGHS (60,000 ms backend limit), then all five MiniZinc routes when `MINIZINC_EXECUTABLE` is set. JS: `kotlin/direct`, Node Gecode when that variable is set, then Z3 Wasm; a Z3 Wasm module that cannot start is left out instead of failing the load. Native: `kotlin/direct`, Z3 Native, then the five MiniZinc routes when `MINIZINC_EXECUTABLE` is set. `AutoBackend.load()` keeps that order by default (`RankingPolicy.REGISTRATION_ORDER`): each model goes to the first route that has its capabilities, is available and supports it.

HiGHS through OR-Tools cannot be interrupted by OR-Tools: its constructor requires a positive per-solve limit. Closing returns promptly, but an abandoned engine call may run until that limit. MiniZinc JS permits one owning backend at a time; close it before loading another.

`:solve-highs` and `:solve-flatzinc` are reserved modules, not implemented routes. Native MiniZinc execution is available on Linux and macOS, not on Windows or iOS. See [Platform availability](platform-availability.md), [Configuration](configuration.md), [Choose a solver](../how-to/choose-a-solver.md) and [Engines and routes](../explanation/engines-and-routes.md).


## MiniZinc compilation API

`MiniZincCompiler(engine = MiniZincEngine.GECODE)` translates a model to deterministic MiniZinc source without starting a process. Use `support(model, options)` to inspect rejections, or `compile(model, options)` to obtain a `MiniZincModel`; unsupported inputs throw `UnsupportedModelException`. The engine argument selects the checked numeric subset as well as the target configuration.

| `MiniZincModel` member | Meaning |
|---|---|
| `model` | Original `ConstraintModel`, retained for full-assignment validation |
| `source` | Executable MiniZinc text |
| `engine` | Engine contract used during compilation |
| `sourceVariables` | Stable `v0`, `v1`, … identifiers mapped to source variables in declaration order |
| `close()` | No operation: this immutable representation owns neither processes nor temporary files |

`MiniZincEngine` exposes `engineId`, `configurationId`, `maximumInteger`, `isMip` and `descriptor`. `isMip` identifies affine, non-enumerating mixed integer programming routes (HiGHS and CBC). The configuration and bounds tables above list each enum value.

On the JVM, `MiniZincBackend(engine, executable)` probes that explicit configuration and creates its solver. `MiniZincGecodeBackend(executable = "minizinc")` is a compatibility entry point delegating to the Gecode configuration; it never substitutes another engine. `MiniZincJsBackend.engine` identifies the checked subset selected by `loadNode` or `loadBrowser`; its `close()` cancels active streams and releases its runtime after they exit. See [Configuration](configuration.md) for `MiniZincOptions` and [Use MiniZinc](../how-to/use-minizinc.md) for execution.

The common [Kotlin solver API](solver-api.md) explains lifecycle, descriptors, support reports and automatic selection. Backend implementers can consult [Backend SPI](spi.md) and the [generated API](/kt-constraints/api/index.html).
