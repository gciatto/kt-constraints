# :solve-portfolio

Automatic solving over the routes bundled for the current platform.

- `solveAuto(model)` loads `DefaultBackends`, ranks them by `RankingPolicy.DEFAULT` and runs them as a fallback
  portfolio.
- `Portfolio(registry, policy).fallback(model)` tries ranked routes one at a time under one deadline, moving on only
  after `UNKNOWN` or a failure.
- `Portfolio(registry, policy).race(model, parallelism = 2)` runs a bounded number of routes at once. The first proof
  wins and cancels the rest; otherwise the best incumbent is returned without claiming optimality.
- `DefaultBackends.load()`:
  - JVM: CP-SAT, Choco, Z3, SCIP and HiGHS, plus the MiniZinc engines when `MINIZINC_EXECUTABLE` is set;
  - JS: Z3 Wasm, plus MiniZinc's Node CLI route when `MINIZINC_EXECUTABLE` is set;
  - Native: Z3.

Results name the answering route and list every skipped or failed one in `metadata.messages`.

Ranking is extensible: implement `RankingHeuristic` and add it with `RankingPolicy.DEFAULT.with(heuristic, weight)`.
Benchmark-driven ranking plugs in through `Heuristics.evidence(PerformanceEvidence)`, which is experimental.

`./gradlew :solve-portfolio:jvmBenchmark` runs the benchmark catalogue on every eligible route (see
`docs/BENCHMARKS.md`).
