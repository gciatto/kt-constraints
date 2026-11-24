# Automatic solver selection

`AutoBackend` makes three decisions for each model: which registered routes are eligible, how to rank them, and how to execute the ranked candidates. A **route** identifies both an engine and its adapter, such as `cp-sat/direct`. Automatic selection is a deterministic preference policy; it does not predict the fastest route for every instance.

This walkthrough follows `SolverRegistry.rank`, `ModelFeatures`, `Heuristics`, `RankingPolicy`, and the internal `Portfolio` and `AutoSolver`. Use [AutoBackend](auto.md) for the public API overview and [choose a solver](../../how-to/choose-a-solver.md) for tested usage examples. There is no public `Portfolio` or `solveAuto` API.

## 1. Load the platform defaults

`AutoBackend.load()` loads an explicit platform bundle, then builds an immutable registry. It does not scan the classpath for backends.

| Platform | Registration order | Optional additions |
|---|---|---|
| JVM | `cp-sat/direct`, `choco/direct`, `z3/direct`, `scip/or-tools`, `highs/or-tools` | If `MINIZINC_EXECUTABLE` is set: `gecode/minizinc`, `chuffed/minizinc`, `cp-sat/minizinc`, `highs/minizinc`, `cbc/minizinc`, in that order |
| JS | `z3/wasm`, asynchronously loaded | On Node, Gecode `gecode/minizinc-js-cli` when `MINIZINC_EXECUTABLE` is set |
| Native | `z3/native` | None |

The default HiGHS instance has a mandatory 60,000 ms backend limit because OR-Tools cannot interrupt it. Browser Chuffed through MiniZinc requires explicit loading. The pure-Kotlin engine is absent from every default registry. Installing its module does not add it to selection; supply a backend list explicitly to include it.

Loading happens before a solve. A JS runtime initialization failure can throw before the portfolio exists; fallback does not recover from a failure of `AutoBackend.load()` itself. Availability and support are checked later for each model. Closing `AutoBackend` closes its closeable registered backends and their runtimes.

Sources: [JVM defaults](https://github.com/gciatto/kt-constraints/blob/develop/solve-auto/src/jvmMain/kotlin/io/github/gciatto/ktconstraints/solve/auto/DefaultBackends.jvm.kt), [JS defaults](https://github.com/gciatto/kt-constraints/blob/develop/solve-auto/src/jsMain/kotlin/io/github/gciatto/ktconstraints/solve/auto/DefaultBackends.js.kt), [Native defaults](https://github.com/gciatto/kt-constraints/blob/develop/solve-auto/src/nativeMain/kotlin/io/github/gciatto/ktconstraints/solve/auto/DefaultBackends.native.kt).

## 2. Extract features and classify the model

`ModelFeatures.of(model)` inspects declarations, assertions and the objective. It records Boolean, integer, real and bit-vector use; globals; reification; variable-dependent products; objective shape; variable, constraint and expression-node counts; and the largest absolute visited `IntLiteral`, `LinearSum` coefficient or constant, or integer domain bound. Constants stored only in global-node metadata, such as cumulative durations, demands and capacity, are not included. Integer magnitude is a feature, not a substitute for the backend's intermediate-range checks.

**Reification** means a relation's truth is used inside a Boolean formula. The feature walk treats the outer conjunction spine as ordinary assertions, rather than counting it as reification. Products whose two operands contain variables set the nonlinear flag. The objective is classified as absent, linear or nonlinear using that same product test; these are coarse features, not a full proof of mathematical linearity.

The first matching row determines `problemClass`:

| Priority | Condition | Class |
|---|---|---|
| 1 | Bit-vectors occur | `SMT_BITVECTOR` |
| 2 | Reals occur | `SMT_REAL` |
| 3 | No integers occur | `PURE_BOOLEAN` |
| 4 | A global or reification occurs | `CP_GLOBALS` |
| 5 | Nonlinear integer products or a nonlinear objective occur | `NONLINEAR_INTEGER` |
| 6 | Otherwise | `LINEAR_INTEGER` |

For example, a model mixing reals and globals is classified as `SMT_REAL`, but still requires global-constraint support. Classification influences preference; full support inspection decides whether a translation is faithful.

Source: [ModelFeatures](https://github.com/gciatto/kt-constraints/blob/develop/solve-spi/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/spi/feature/ModelFeatures.kt).

## 3. Reject ineligible routes before scoring

First derive required capabilities: always `SATISFACTION`, plus `OPTIMIZATION` for an objective, `INTEGER_ARITHMETIC` for integers, `REAL_ARITHMETIC` for reals, `BITVECTORS` for bit-vectors, and `GLOBAL_CONSTRAINTS` when globals occur. Add any capabilities explicitly requested from the registry. Enumeration routing adds `ENUMERATION`.

For each registered backend, `SolverRegistry.rank` checks these stages in order and stops at the first failing stage:

| Stage | Check | Rejection reported |
|---|---|---|
| 1 | Descriptor contains every required capability | One `Missing capability: …` message per missing capability, in enum order |
| 2 | `availability()` says the runtime is available | Its availability reason |
| 3 | `support(model, options)` accepts the whole model | The support report's `unsupported` messages |

A decomposition listed by a support report is not a rejection. A high score cannot override missing capabilities, an unavailable runtime or unsupported semantics. Selection does not create a solver, although probes can load libraries or run startup checks. Exceptions thrown during ranking itself are outside the per-attempt execution handler.

`rank` returns a `Selection` with features, ordered candidates and a rejection map keyed by route ID. It can return an empty candidate list. Reading `Selection.best` or calling registry `select` then throws `NoCompatibleBackendException`. Automatic execution instead turns an empty ranking into `UnsupportedModelException` with every route rejection. Duplicate IDs are rejected when constructing the registry.

Source: [SolverRegistry](https://github.com/gciatto/kt-constraints/blob/develop/solve-spi/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/spi/registry/SolverRegistry.kt).

## 4. Score the eligible routes

For each candidate, total score is the sum of `weight × score.value` across heuristics with an opinion. A null opinion adds no contribution. Higher totals rank first; equal totals retain registration order. Each contribution retains the heuristic name, weight, raw score and reason, available through `Candidate.contributions` and `Candidate.explanation`.

`RankingPolicy.DEFAULT` uses the following three heuristics with weight **1.0 each**:

| Heuristic | Exact score |
|---|---|
| `capability-fit` | `-min(0.05 × unused, 0.25)`, where unused is the number of descriptor capabilities outside the model-derived required set |
| `feature-affinity` | The table below, using engine ID and problem class |
| `route-overhead` | `-0.5` if the route string contains `minizinc`; otherwise `0.0` |

This is a specialization preference, not evidence that extra capabilities make a solver slower.

Capability fit uses only the model-derived set, not extra capabilities requested from the registry. Route overhead assigns the same zero to direct, OR-Tools, Wasm and Native routes; it does not model their different startup costs.

For affinity, engine IDs `z3` and `cvc5` count as SMT; `choco`, `cp-sat`, `gecode` and `chuffed` count as CP; `scip`, `highs`, `cbc` and `gurobi` count as MIP. These are ID categories, not additional implemented backends. Unknown engines, including `kotlin`, receive no affinity contribution.

| Problem class | SMT | CP | MIP |
|---|---:|---:|---:|
| `SMT_REAL`, `SMT_BITVECTOR` | +1.0 | -1.0 | -1.0 |
| `LINEAR_INTEGER`, satisfaction | 0.0 | +0.5 | +0.5 |
| `LINEAR_INTEGER`, optimization | 0.0 | +0.5 | +1.0 |
| `NONLINEAR_INTEGER` | +0.5 | +1.0 | -1.0 |
| `CP_GLOBALS` | +0.25 | +1.0 | -0.5 |
| `PURE_BOOLEAN` | +0.5 | +1.0 for `cp-sat`, otherwise +0.5 | 0.0 |

`RankingPolicy.REGISTRATION_ORDER` has no heuristics: every eligible candidate scores zero. Eligibility checks still apply. Custom `RankingHeuristic` implementations can be added with `with`, removed by name with `without`, or supplied in a replacement `RankingPolicy` as weighted entries. They change preference, not eligibility.

The experimental `PerformanceEvidence` hook returns expected milliseconds for a problem class, route ID and model features, or null when unknown. `Heuristics.evidence` converts a known time to `-log10(1 + expectedMillis) / 3.0`, multiplied by its policy weight. `PerformanceEvidence.NONE` always returns null. No timing dataset or evidence heuristic is part of `DEFAULT`; benchmark recommendations below are not already encoded in the loader or policy.

Sources: [Heuristics](https://github.com/gciatto/kt-constraints/blob/develop/solve-spi/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/spi/ranking/Heuristics.kt), [RankingPolicy](https://github.com/gciatto/kt-constraints/blob/develop/solve-spi/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/spi/ranking/RankingPolicy.kt), [PerformanceEvidence](https://github.com/gciatto/kt-constraints/blob/develop/solve-spi/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/spi/ranking/PerformanceEvidence.kt).

For example, in a linear integer optimization model SCIP has one unused capability, globals,
so its capability-fit score is −0.05. Each additional unused capability subtracts another 0.05,
up to the 0.25 cap; this accounts for the −0.10 and −0.25 entries in the worked scores below.

## 5. Execute under one shared deadline

Each solve starts a monotonic deadline before ranking. A null timeout is unlimited; otherwise remaining whole milliseconds are rounded down and clamped at zero. Before starting each attempt, execution checks that the budget remains and passes the remaining time through `SolveOptions.timeoutMillis`. Ranking and earlier attempts therefore consume the same budget. This is budget propagation to the backends, not a separate mechanism that can forcibly interrupt every engine.

Each attempt creates a fresh solver and closes it after solving. Ordinary execution exceptions become diagnostics. Coroutine `CancellationException` propagates to the caller rather than triggering another attempt.

| Strategy | Next attempt | Return condition |
|---|---|---|
| `Fallback` | Start the next ranked route after `UNKNOWN` or an execution exception, while budget remains | First non-`UNKNOWN` result, even a merely feasible optimization result |
| `Race(parallelism)` | Start up to the positive parallelism limit (default 2); refill a slot when an attempt ends without a decisive result, while budget remains | First decisive result; otherwise best feasible optimization incumbent after active attempts finish |

Race uses concurrent coroutines. It treats `OPTIMAL`, `INFEASIBLE`, `UNBOUNDED` and `INFEASIBLE_OR_UNBOUNDED` as decisive, and `FEASIBLE` as decisive only for a satisfaction model. The ambiguous infeasible-or-unbounded status remains ambiguous in the returned result. A **proof** here means the answering backend's own status/evidence, not a new certificate constructed by the automatic backend.

For optimization, `FEASIBLE` is retained as an incumbent while racing continues. Objective values choose the larger incumbent for maximization or the smaller for minimization; equal values keep the current incumbent. The first feasible candidate initializes the incumbent; a candidate missing an objective value cannot replace an incumbent that has one. The race cancels other jobs after a decisive answer, while actual engine interruption follows each route's contract. In particular, OR-Tools HiGHS may keep running until its own limit.

Fallback does not continue searching for optimality after returning a feasible incumbent. Neither strategy combines bounds, treats timeouts as infeasibility, or turns agreement among failures into proof.

Source: [Portfolio](https://github.com/gciatto/kt-constraints/blob/develop/solve-auto/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/auto/Portfolio.kt) and [Deadline](https://github.com/gciatto/kt-constraints/blob/develop/solve-auto/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/auto/Deadline.kt).

## 6. Route enumeration to one backend

`AutoSolver.solutions` repeats ranking with `ENUMERATION` required and selects the highest-ranked eligible route. It creates that solver and wraps its stream so closing the stream also closes the solver. Closing the automatic solver closes its owned streams.

Enumeration does not use fallback or racing, even when `AutoStrategy.Race` was selected. There is no stream merging or retry after an enumeration failure. If no enumerator is eligible, it throws `UnsupportedModelException`. Sessions are routed the same way: `openSession` ranks the routes that declare incremental assertions and opens the session on the best one, without fallback, racing or retry.

Source: [AutoSolver](https://github.com/gciatto/kt-constraints/blob/develop/solve-auto/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/auto/AutoSolver.kt).

## 7. Read the result and diagnostics

An answering result keeps its route descriptor, status, termination reason, assignment, objective and statistics. `metadata.messages` keeps its own messages, then adds accumulated route rejections and failed/unknown attempts, followed by `auto: answered by …`.

Rejections have the form `route-id rejected: reasons`; execution failures use `route-id failed: reason`; unknown attempts use `route-id: UNKNOWN (terminationReason)`. These messages describe observed attempts, not a full ranking trace or a log of every canceled loser. Inspect `registry.rank` for score explanations.

If eligible attempts produce no answer, the result is `UNKNOWN`, without an assignment, from `auto/fallback` or `auto/race`. Its termination reason is `TIMEOUT` when the shared deadline has expired, otherwise `COMPLETED`; messages end with `auto: no backend answered`. An exhausted list of failures can therefore give `UNKNOWN` with `COMPLETED`, which does not prove the model infeasible. If a race returns an incumbent instead, it retains that candidate's termination reason.

Distinguish this from no eligible routes, which throws `UnsupportedModelException`, and from runtime loading, ranking or caller cancellation failures, which can propagate as exceptions. See [results and statuses](../../reference/results-and-statuses.md).

## Worked JVM examples

These are the three models in [AutoBackendJvmSpec](https://github.com/gciatto/kt-constraints/blob/develop/solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/AutoBackendJvmSpec.kt). Assume the default JVM engines are available and use default options and policy. The winners are ranks before execution, not guarantees that a race finishes in that order.

| Model | Features and required capabilities | Scores and winner |
|---|---|---|
| Integers x, y in 0..9, x + y ≤ 10; maximize 2x + y | `LINEAR_INTEGER`; satisfaction, optimization, integer arithmetic | SCIP and HiGHS each score `-0.05 + 1.0 + 0 = 0.95`; SCIP wins the registration-order tie. CP-SAT and Choco score `-0.10 + 0.5 = 0.40`; Z3 scores `-0.25 + 0 = -0.25`. The solve test proves optimum 19. |
| Three integers in 0..2, all different; no objective | `CP_GLOBALS`; satisfaction, integer arithmetic, globals | CP-SAT and Choco each score `-0.10 + 1.0 = 0.90`; CP-SAT wins their tie. Z3 scores `-0.25 + 0.25 = 0`; direct MIP scores `-0.05 - 0.5 = -0.55`. |
| Real r constrained by 1/2 < r; no objective | `SMT_REAL`; satisfaction, real arithmetic | Other default engines lack real arithmetic and are rejected before scoring. Z3 scores `-0.25 + 1.0 = 0.75` and wins. |

If MiniZinc defaults are enabled and available, HiGHS/CBC score `0.45` on the first model, below direct MIP but above the direct CP routes. MiniZinc CP routes score `0.40` on the all-different model, below direct CP. MiniZinc routes lack real arithmetic and remain ineligible for the third model. These examples show both the deterministic tie rule and the current policy's willingness to put a compiled MIP route ahead of direct CP for linear optimization.

## What the benchmark evidence suggests

The repository's measured catalogue supports several policy directions, with workload and platform qualifications:

- On JVM models classified as `CP_GLOBALS`, CP-SAT first and Choco alongside it in a race cover different strengths. Z3 and MIP should follow the CP routes for these measured families.
- For the measured linear integer families, there is insufficient evidence to change the current MIP preference. The instances are too easy to establish a general winner.
- On Node and Native, the measurements favor explicitly registered Kotlin first for models it supports, with Z3 available for rejected models. With MiniZinc installed on Node, the suggested order is Kotlin, Gecode CLI, then Z3 Wasm.
- If Kotlin is registered on the JVM, the measured globals families suggest placing it after CP-SAT and Choco and before Z3 and MIP.
- MiniZinc startup and flattening suggest putting compiled routes after in-process routes for small and medium models. The current additive policy does not enforce this: MIP affinity can put MiniZinc HiGHS/CBC ahead of direct CP routes for linear optimization.

These are observations and recommendations from the recorded runs, not changes already encoded in `RankingPolicy.DEFAULT`. The catalogue has gaps in real arithmetic, nonlinear integers and pure Boolean workloads, and its bit-vector case is too small to establish a broad rule. It also shows that MiniZinc's MIP global decompositions can outperform the project's direct MIP lowering, so a fixed route penalty cannot explain every workload.

Use the [benchmark reference](../../reference/benchmarks.md) for measurements and limitations. For explicit route choice, policy configuration and strategies, see [choose a solver](../../how-to/choose-a-solver.md).
