# :solve-auto

`AutoBackend`: a solver backend that picks, per model, among other routes. Use it like any other backend:

```kotlin
AutoBackend.load().use { auto -> auto.withSolver { it.solve(model) } }
```

- `AutoBackend.load()` registers the routes bundled for the current platform:
  - JVM: CP-SAT, Choco, Z3, SCIP and HiGHS, plus the MiniZinc engines when `MINIZINC_EXECUTABLE` is set;
  - JS: Z3 Wasm, plus MiniZinc's Node CLI route when `MINIZINC_EXECUTABLE` is set;
  - Native: Z3;
  - every platform: pure Kotlin, as the last resort.
- On the JVM and Native, `loadAutoBackend()` (Java: `AutoBackends.loadAutoBackend()`) does the same on the calling thread, for code
  that cannot suspend; pair it with [`:solve-blocking`](../solve-blocking/README.md).
- `AutoBackend(listOf(...))` chooses among explicitly given routes instead.
- `AutoStrategy.Fallback` (default, route `auto/fallback`) tries ranked routes one at a time under one deadline, moving
  on only after `UNKNOWN` or a failure.
- `AutoStrategy.Race(parallelism = 2)` (route `auto/race`) runs a bounded number of routes at once. The first proof
  wins and cancels the rest; otherwise the best incumbent is returned without claiming optimality.
- `solutions(...)` streams from the best-ranked route that supports enumeration, and `openSession(model)` (through
  `withIncrementalSolver` or `createIncrementalSolver()`) opens the session on the best-ranked route with incremental
  assertions. Stores are not offered: pick a concrete route, e.g. through `auto.registry.select(...)`.

Results name the answering route and list every skipped or failed one in `metadata.messages`. A model no route can
take is rejected with `UnsupportedModelException`.

Ranking is extensible: implement `RankingHeuristic` and pass `RankingPolicy.DEFAULT.with(heuristic, weight)` to
`AutoBackend`. Benchmark-driven ranking plugs in through `Heuristics.evidence(PerformanceEvidence)`, which is
experimental.

`./gradlew :solve-auto:jvmBenchmark` runs the benchmark catalogue on every eligible route (see
[benchmarks](https://gciatto.github.io/kt-constraints/reference/benchmarks/)).
