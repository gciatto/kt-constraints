# Automatic solver selection

`solveAuto` loads the routes bundled for the current platform, ranks eligible candidates and runs a sequential fallback portfolio. Automatic selection is a deterministic policy over registered routes, not a guarantee of the fastest solver for a model.

There are three separate decisions: whether a route is eligible, where it ranks, and how the portfolio executes the ranked candidates. Keeping them separate lets an application inspect or replace the policy without weakening support checks.

## Eligibility comes before preference

`SolverRegistry.rank` extracts `ModelFeatures` from declarations, constraints and the objective. These include sorts, nonlinear products, reification, globals, objective shape, expression counts and integer magnitude.

For each registered route, eligibility checks run in this order:

1. Required capabilities derived from those features, plus any explicitly requested capabilities.
2. Runtime availability.
3. The backend's support report for the whole model and options.

A failed stage rejects that route with diagnostics. A high ranking score can never override rejection. Registry selection does not instantiate a solver, although availability probes may inspect or exercise a runtime. Registration is explicit and immutable; there is no classpath search for arbitrary backends.

The feature classifier chooses one problem class, in precedence order: bit-vectors, reals, pure Boolean, globals or reification, nonlinear integers, then linear integers. This is a coarse description for ranking. The backend's support check remains responsible for the full expression semantics and numeric limits.

## The default score is a heuristic

`RankingPolicy.DEFAULT` sums three equally weighted heuristics. Higher totals rank first; ties retain registration order. Each contribution includes a reason, and the returned `Selection` contains candidates and route-specific rejections.

| Heuristic | Current behavior | Trade-off |
|---|---|---|
| Capability fit | Penalizes unused capabilities by `0.05` each, capped at `0.25` | A small preference for specialization, not a measured runtime cost |
| Feature affinity | Favors SMT for reals/bit-vectors, CP for globals and nonlinear integers, MIP for linear integer optimization, and CP-SAT for pure Boolean models | Engine paradigms are only a proxy for workload performance |
| Route overhead | Subtracts `0.5` when the route name contains `minizinc`; other routes score zero | Treats direct, OR-Tools, Wasm and Native routes alike, despite different startup costs |

Feature affinity uses engine IDs. An unrecognized engine gets no affinity contribution. In particular, the current affinity table does not classify `kotlin` into a paradigm.

`RankingPolicy.REGISTRATION_ORDER` supplies no scoring heuristics. Custom policies can append or replace heuristics and change their weights. The experimental `PerformanceEvidence` interface supplies expected times to an optional evidence heuristic. No benchmark evidence is included in the default policy, and there is no shipped learned timing model.

## Which routes automatic solving loads

| Platform | `DefaultBackends.load()` |
|---|---|
| JVM | CP-SAT, Choco, Z3, SCIP and HiGHS through OR-Tools; all five MiniZinc engine configurations when `MINIZINC_EXECUTABLE` is set |
| JavaScript | Asynchronously loaded Z3 Wasm; Gecode through the MiniZinc Node CLI when `MINIZINC_EXECUTABLE` is set |
| Kotlin/Native | Z3 through its runtime-loaded C library |

The JVM registration order starts with CP-SAT and Choco; ranking can change that order. The bundled HiGHS backend has a mandatory per-solve limit because the OR-Tools integration cannot interrupt that engine. Browser Chuffed through MiniZinc is available through explicit loading, not this default loader.

The experimental pure-Kotlin backend is absent from every default registry. Benchmark runners can add it explicitly; those benchmark candidates must not be mistaken for the routes offered by `solveAuto`.

`solveAuto` owns and closes the loaded backend bundle. Loading precedes portfolio execution; the fallback loop handles candidate execution failures, not failures that prevent the bundle from loading at all.

## Fallback and racing make different promises

`Portfolio.fallback` tries ranked candidates one at a time under a shared timeout budget. It advances after `UNKNOWN` or an execution failure. It returns the first non-`UNKNOWN` result, including a feasible optimization incumbent. A feasible answer after a timeout does not cause fallback to keep searching for an optimality proof.

`Portfolio.race` starts up to `parallelism` candidates concurrently, with a default of two. When an attempt ends without a decisive answer, the next ranked candidate can start. A decisive answer from one backend cancels the others: optimality, infeasibility, unboundedness, infeasible-or-unbounded, or a feasible witness for a satisfaction model.

For optimization, a merely feasible result is retained as an incumbent while the race continues. If no decisive result arrives, the best incumbent is returned without claiming optimality. Each winning proof status comes from one backend's result; the portfolio does not combine bounds or treat agreement among failures as proof.

Both modes record rejection and failure diagnostics in result metadata. The result retains the answering backend's descriptor. With no answer, the status is `UNKNOWN` and the descriptor identifies `portfolio/fallback` or `portfolio/race`. A timeout and a crash never establish infeasibility. Coroutine cancellation propagates rather than becoming an ordinary failed attempt.

## What the benchmark evidence suggests

The repository's measured catalogue supports several policy directions, with workload and platform qualifications:

- On JVM models classified as `CP_GLOBALS`, CP-SAT first and Choco alongside it in a race cover different strengths. Z3 and MIP should follow the CP routes for these measured families.
- For the measured linear integer families, there is insufficient evidence to change the current MIP preference. The instances are too easy to establish a general winner.
- On Node and Native, the measurements favor explicitly registered Kotlin first for models it supports, with Z3 available for rejected models. With MiniZinc installed on Node, the suggested order is Kotlin, Gecode CLI, then Z3 Wasm.
- If Kotlin is registered on the JVM, the measured globals families suggest placing it after CP-SAT and Choco and before Z3 and MIP.
- MiniZinc startup and flattening suggest putting compiled routes after in-process routes for small and medium models. The current additive policy does not enforce this: MIP affinity can put MiniZinc HiGHS/CBC ahead of direct CP routes for linear optimization.

These are observations and recommendations from the recorded runs, not changes already encoded in `RankingPolicy.DEFAULT`. The catalogue has gaps in real arithmetic, nonlinear integers and pure Boolean workloads, and its bit-vector case is too small to establish a broad rule. It also shows that MiniZinc's MIP global decompositions can outperform the project's direct MIP lowering, so a fixed route penalty cannot explain every workload.

Use the [benchmark reference](../reference/benchmarks.md) for measurements and limitations. For explicit route choice, policy configuration and portfolio use, see [choose a solver](../how-to/choose-a-solver.md).
