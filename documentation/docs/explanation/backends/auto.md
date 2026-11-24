# AutoBackend

## What it is

`AutoBackend` selects among explicitly registered backends for each model. It ranks eligible routes, then runs them sequentially or concurrently. It is itself a `SolverBackend`, so callers create and close its solvers through the same batch API. It does not implement another search engine.

## Routes and classes

`AutoBackend` and `AutoStrategy` belong to `:solve-auto`, artifact `kt-constraints-solve-auto`.

| Descriptor | Strategy | Behavior |
|---|---|---|
| `auto/fallback` | `AutoStrategy.Fallback`, the default | Try ranked candidates sequentially; advance after `UNKNOWN` or failure |
| `auto/race` | `AutoStrategy.Race(parallelism = 2)` | Run a bounded number concurrently; stop on a decisive result, otherwise retain the best feasible optimization incumbent |

An incumbent is a feasible assignment found so far, without an optimality proof. A successful result keeps the answering route's descriptor. No eligible route causes `UnsupportedModelException`; when eligible attempts produce no answer, the automatic descriptor identifies the `UNKNOWN` result. `Portfolio` and `AutoSolver` are internal implementation details; there is no public `Portfolio` or `solveAuto` entry point.

## Platforms and runtime requirements

`AutoBackend.load()` loads the platform bundle: direct CP-SAT, Choco, Z3 and OR-Tools SCIP/HiGHS on JVM; Z3 Wasm on JS; Native Z3 on Kotlin/Native. `MINIZINC_EXECUTABLE` enables five additional JVM MiniZinc configurations or Node Gecode CLI. Kotlin and browser MiniZinc are excluded. See [the exact loading order](selection.md#1-load-the-platform-defaults).

Loading JS runtimes is asynchronous and may fail before candidate execution begins. Closing the automatic backend closes its closeable registered backends and their loaded runtimes. Callers supplying their own list must account for that ownership.

## Capabilities

The descriptor is the union of registered route capabilities restricted to these batch categories: satisfaction, optimization, enumeration, integer arithmetic, real arithmetic, bit-vectors and globals. The union is not a promise that one route supports every combination. Whole-model eligibility must still find a single suitable candidate.

Enumeration selects one highest-ranked eligible route declaring `ENUMERATION`, even for a race strategy. It does not retry or merge streams. The returned stream owns its underlying solver, and closing the automatic solver closes its open enumeration streams. Closing does not stop an automatic batch `solve` already in progress: cancel the coroutine running it. Structured concurrency keeps the candidate work within that calling coroutine.

Automatic solvers also open incremental sessions: `openSession(model)` ranks the routes that declare incremental assertions and opens the session on the best one, so assumptions and unsatisfiable cores are available when that route offers them. Closing the session closes the route's solver. Stores are built over an incremental solver with `SatisfiabilityStore.of(auto.createIncrementalSolver())`. See [the implementation](https://github.com/gciatto/kt-constraints/blob/develop/solve-auto/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/auto/AutoBackend.kt).

## Supported modelling features and numeric limits

The accepted subset depends on the registered backends. Availability is true if any route is available. `support` accepts a model if any route passes capability, availability and full support checks; otherwise it reports the route rejections. No numeric bound or decomposition is invented by the automatic backend.

Fallback returns the first non-`UNKNOWN` result, including a feasible optimization incumbent. A race retains incumbents while seeking a decisive result. Neither combines several failures into proof. If no route is eligible, solve throws `UnsupportedModelException`; if eligible attempts produce no answer, it returns `UNKNOWN`. Cancellation propagates. Read [the complete selection procedure](selection.md) for deadlines, scoring and result diagnostics.

## Options type

There is no automatic `BackendOptions` implementation. Configure `AutoStrategy` and `RankingPolicy` when constructing/loading the backend. Per-call `SolveOptions`, including typed backend options, reach candidate support checks and execution. Each attempt receives the remaining shared timeout budget.

`AutoBackend.registry` exposes ranked candidates, scores and rejections for inspection. It does not change registrations dynamically. Custom registry/backend lists are explicit; there is no classpath discovery.

## Performance

Default ranking uses fixed feature heuristics; no measured timing evidence is loaded. Fallback adds selection and failed-attempt cost. Racing can cover complementary solver strengths but runs several attempts and cancels losers according to their interruption behavior. The recorded JVM catalogue supports pairing CP-SAT with Choco on the measured global families; it also exposes cases where MiniZinc's ranking penalty is too small to offset startup cost. See [benchmarks](../../reference/benchmarks.md) and [benchmark evidence versus shipped policy](selection.md#what-the-benchmark-evidence-suggests).

## How to use it directly

[Choose a solver](../../how-to/choose-a-solver.md) covers loading, explicit candidates, ranking and strategies, with tested registry code. [Your first Kotlin model](../../tutorials/kotlin-first-model.md) quotes tested `AutoBackend.load()` usage. Use [set limits and cancel](../../how-to/limits-and-cancellation.md) for shared execution controls.
