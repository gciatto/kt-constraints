# Benchmarks

Benchmarks are manual and never part of `check`. They record raw observations: there are no regression
thresholds until several reproducible baselines exist (M8 design).

| Command | What it measures |
|---|---|
| `./gradlew :solve-portfolio:jvmBenchmark` | Eight families on every eligible bundled JVM route, plus `auto` |
| `./gradlew :solve-choco:storeBenchmark`, `./gradlew :solve-z3:jvmStoreBenchmark` | CLP-style constraint-store operations |
| `MINIZINC_EXECUTABLE=... ./gradlew :solve-minizinc:jvmBenchmark` | MiniZinc phases |
| `./gradlew :solve-kotlin:jvmFamilyBenchmark` | `kotlin/direct` over seeded instances: median, minimum, maximum and timeouts (`BENCHMARK_SCALES`, `BENCHMARK_SEEDS`, `BENCHMARK_FAMILIES`) |

`jvmBenchmark` prints CSV, and writes it to the file named by `BENCHMARK_CSV` when that is set. `BENCHMARK_SCALE`
(default 1) grows sizes. Each family's known answer comes from a closed form, dynamic programming or
enumeration, and CP-SAT and Z3 reproduce it in `BenchmarkCatalogueSpec`, so the `correct` column is trustworthy.
The columns are:
- `build_ms`: model construction;
- `support_ms`: the route's support check, which runs its translation;
- `cold_ms`: the first solve on a fresh solver;
- `warm_ms`: a second solve on the same solver.

For `auto` rows, `cold_ms` covers ranking plus a fallback portfolio run, and the route after `auto:` is the one
that answered. Comparing `auto` with the chosen route's `cold_ms` shows the selection overhead: about 10-15 ms here.

Randomly generated families (knapsack, assignment, integer program, single-machine scheduling) take a seed; seed 0
keeps the original instances, and `BenchmarkCatalogueSpec` confirms seed 1 too. Compare encodings over several seeds:
a single instance's search path can swing by 2-3x between equivalent encodings.

The unpublished `kotlin/direct` route is measured too, but never offered by `auto`; its observations and the
optimisations they drove are in [PURE-KOTLIN.md](PURE-KOTLIN.md#performance). Known answers come from closed forms or
dynamic programming, never from enumerating permutations or grids, so large scales stay within memory.

## Baseline observation (2026-11-21, scale 1, 30 s timeout)

Host: 16 logical CPUs, Intel(R) Xeon(R) Gold 6226R CPU @ 2.90GHz, openjdk version "21.0.13.1" 2026-11-17. One run, so expect noise.

```text
family           size  class           route               status      objective  correct  build_ms  support_ms  cold_ms  warm_ms
n-queens         8     CP_GLOBALS      cp-sat/direct       FEASIBLE               true     22.11     0.84        53.95    16.55
n-queens         8     CP_GLOBALS      choco/direct        FEASIBLE               true     22.11     0.66        40.83    13.96
n-queens         8     CP_GLOBALS      z3/direct           FEASIBLE               true     22.11     0.20        34.98    18.09
n-queens         8     CP_GLOBALS      scip/or-tools       FEASIBLE               true     22.11     3.91        67.12    56.37
n-queens         8     CP_GLOBALS      highs/or-tools      FEASIBLE               true     22.11     2.48        74.99    76.83
n-queens         8     CP_GLOBALS      auto:cp-sat/direct  FEASIBLE               true     22.11     0.00        30.94    30.94
graph-coloring   7     CP_GLOBALS      cp-sat/direct       OPTIMAL     2          true     1.61      0.11        14.60    7.09
graph-coloring   7     CP_GLOBALS      choco/direct        OPTIMAL     2          true     1.61      0.06        10.26    2.90
graph-coloring   7     CP_GLOBALS      z3/direct           OPTIMAL     2          true     1.61      0.04        15.60    14.41
graph-coloring   7     CP_GLOBALS      scip/or-tools       OPTIMAL     2          true     1.61      0.32        10.98    9.83
graph-coloring   7     CP_GLOBALS      highs/or-tools      OPTIMAL     2          true     1.61      0.19        14.32    14.95
graph-coloring   7     CP_GLOBALS      auto:cp-sat/direct  OPTIMAL     2          true     1.61      0.00        23.13    23.13
pigeonhole       5     CP_GLOBALS      cp-sat/direct       INFEASIBLE             true     0.71      0.02        3.45     0.70
pigeonhole       5     CP_GLOBALS      choco/direct        INFEASIBLE             true     0.71      0.02        6.64     0.50
pigeonhole       5     CP_GLOBALS      z3/direct           INFEASIBLE             true     0.71      0.05        24.96    18.27
pigeonhole       5     CP_GLOBALS      scip/or-tools       INFEASIBLE             true     0.71      0.35        22.87    23.69
pigeonhole       5     CP_GLOBALS      highs/or-tools      INFEASIBLE             true     0.71      0.30        232.18   225.29
pigeonhole       5     CP_GLOBALS      auto:cp-sat/direct  INFEASIBLE             true     0.71      0.00        12.72    12.72
knapsack         10    LINEAR_INTEGER  scip/or-tools       OPTIMAL     98         true     4.52      0.08        4.49     3.75
knapsack         10    LINEAR_INTEGER  highs/or-tools      OPTIMAL     98         true     4.52      0.07        8.43     9.97
knapsack         10    LINEAR_INTEGER  cp-sat/direct       OPTIMAL     98         true     4.52      0.08        3.37     3.39
knapsack         10    LINEAR_INTEGER  choco/direct        OPTIMAL     98         true     4.52      0.05        12.44    5.90
knapsack         10    LINEAR_INTEGER  z3/direct           OPTIMAL     98         true     4.52      0.03        19.13    18.51
knapsack         10    LINEAR_INTEGER  auto:scip/or-tools  OPTIMAL     98         true     4.52      0.00        16.30    16.30
assignment       5     CP_GLOBALS      cp-sat/direct       OPTIMAL     58         true     2.70      0.07        9.43     6.39
assignment       5     CP_GLOBALS      choco/direct        OPTIMAL     58         true     2.70      0.10        10.08    3.07
assignment       5     CP_GLOBALS      z3/direct           OPTIMAL     58         true     2.70      0.04        51.11    50.65
assignment       5     CP_GLOBALS      scip/or-tools       OPTIMAL     58         true     2.70      0.74        17.21    16.66
assignment       5     CP_GLOBALS      highs/or-tools      OPTIMAL     58         true     2.70      0.65        18.04    22.12
assignment       5     CP_GLOBALS      auto:cp-sat/direct  OPTIMAL     58         true     2.70      0.00        18.61    18.61
job-shop         3     CP_GLOBALS      cp-sat/direct       OPTIMAL     11         true     4.25      0.08        7.12     6.94
job-shop         3     CP_GLOBALS      choco/direct        OPTIMAL     11         true     4.25      0.08        9.10     4.09
job-shop         3     CP_GLOBALS      z3/direct           OPTIMAL     11         true     4.25      0.05        13.89    14.01
job-shop         3     CP_GLOBALS      scip/or-tools       OPTIMAL     11         true     4.25      0.24        7.01     6.35
job-shop         3     CP_GLOBALS      highs/or-tools      OPTIMAL     11         true     4.25      0.21        18.47    14.55
job-shop         3     CP_GLOBALS      auto:cp-sat/direct  OPTIMAL     11         true     4.25      0.00        19.86    19.86
bit-vectors      8     SMT_BITVECTOR   z3/direct           FEASIBLE               true     2.70      0.01        10.36    8.87
bit-vectors      8     SMT_BITVECTOR   auto:z3/direct      FEASIBLE               true     2.70      0.00        9.05     9.05
integer-program  4     LINEAR_INTEGER  scip/or-tools       OPTIMAL     57         true     3.84      0.04        5.33     4.08
integer-program  4     LINEAR_INTEGER  highs/or-tools      OPTIMAL     57         true     3.84      0.04        11.48    14.17
integer-program  4     LINEAR_INTEGER  cp-sat/direct       OPTIMAL     57         true     3.84      0.09        5.37     4.94
integer-program  4     LINEAR_INTEGER  choco/direct        OPTIMAL     57         true     3.84      0.04        2.07     1.56
integer-program  4     LINEAR_INTEGER  z3/direct           OPTIMAL     57         true     3.84      0.02        9.47     9.06
integer-program  4     LINEAR_INTEGER  auto:scip/or-tools  OPTIMAL     57         true     3.84      0.00        16.76    16.76
```
