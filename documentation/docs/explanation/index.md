# Explanation

Contents in navigation order. Follow the [suggested reading order](../index.md#suggested-reading-order) for a learning path.

| Page | Contents |
|---|---|
| [Global constraints](global-constraints.md) | Preserved model structure and exact decompositions |
| [Engines and routes](engines-and-routes.md) | Search engines and the integration paths that reach them |
| [Backends](backends/index.md) | Route comparison and problem fit |
| [Z3](backends/z3.md) | SMT routes, exact arithmetic and incremental checks |
| [CP-SAT](backends/cp-sat.md) | Direct finite-domain solving and checked integer limits |
| [Choco](backends/choco.md) | JVM finite-domain solving and a separate propagating store |
| [HiGHS C API and Wasm](backends/highs.md) | Direct Native/JS LP/MILP routes with explicit tolerance consent |
| [OR-Tools MIP](backends/or-tools-mip.md) | Bounded affine optimization and numerical evidence |
| [MiniZinc](backends/minizinc.md) | Engine configurations, compilation and execution transports |
| [Pure Kotlin](backends/kotlin.md) | Portable backend and its limits |
| [AutoBackend](backends/auto.md) | Automatic batch execution and enumeration ownership |
| [Automatic selection](backends/selection.md) | Loading, eligibility, explained scores and execution strategies |
| [Solving modes](solving-modes.md) | Batch search, retained assertions and reversible stores |
| [Principles](principles.md) | Immutable meaning, identity and exact semantics |
| [Architecture](architecture.md) | Module boundaries and execution flow |
| [The pure-Kotlin solver](pure-kotlin-solver.md) | SAT encodings, exact arithmetic, sessions and validation |
| [History and status](history-and-status.md) | Implemented features and remaining limits |

For continuous and mixed-integer optimization across languages, see [solve LP and MILP models](../how-to/linear-programs.md).
