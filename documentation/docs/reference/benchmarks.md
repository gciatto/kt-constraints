# Benchmarks

Benchmarks are manual and never part of `check`. They record raw observations: there are no regression
thresholds until several reproducible baselines exist.

| Command | What it measures |
|---|---|
| `./gradlew :solve-auto:jvmBenchmark` | Catalogue families on every eligible bundled JVM route, plus `kotlin/direct` and `auto` |
| `./gradlew :solve-choco:storeBenchmark`, `./gradlew :solve-z3:jvmStoreBenchmark` | CLP-style constraint-store operations |
| `MINIZINC_EXECUTABLE=... ./gradlew :solve-minizinc:jvmBenchmark` | MiniZinc phases |
| `KT_CONSTRAINTS_PLATFORM_BENCHMARK=1 ./gradlew :solve-kotlin:<jvmTest\|jsNodeTest> -i` | The same catalogue on each target; rows start with `BENCH,` (Kotlin/JS prints them only with `-i`). For Native, run `:solve-kotlin:linkReleaseTestLinuxX64` and then `solve-kotlin/build/bin/linuxX64/releaseTest/test.kexe` with the variable set: the default Native test binary is an unoptimised debug build, about 10x slower |
| `KT_CONSTRAINTS_ROUTE_BENCHMARK=1 ./gradlew :solve-auto:<jvmTest\|jsNodeTest> --tests 'io.github.gciatto.ktconstraints.solve.auto.RouteBenchmarkSpec' -i` | Every bundled route of the target, plus `kotlin/direct`, over seeded instances; rows start with `ROUTE,` and give each route's rank under the default policy. `_SCALES` (default `1 2 3`), `_SEEDS` (3) and `_TIMEOUT` (ms, 10000) size it. For Native, run `:solve-auto:linkReleaseTestLinuxX64`, then `solve-auto/build/bin/linuxX64/releaseTest/test.kexe` with `KT_CONSTRAINTS_Z3_LIBRARY` pointing at `solve-z3/build/z3-native/linuxX64/libz3.so` |
| `./gradlew :solve-kotlin:jvmFamilyBenchmark` | `kotlin/direct` over seeded instances: median, minimum, maximum and timeouts (`BENCHMARK_SCALES`, `BENCHMARK_SEEDS`, `BENCHMARK_FAMILIES`) |

`jvmBenchmark` prints CSV, and writes it to the file named by `BENCHMARK_CSV` when that is set. `BENCHMARK_SCALE`
(default 1) grows sizes. Each family's known answer comes from a closed form, dynamic programming or
enumeration, and CP-SAT and Z3 reproduce it in `BenchmarkCatalogueSpec`, so the `correct` column is trustworthy.
The columns are:
- `build_ms`: model construction;
- `support_ms`: the route's support check, which runs its translation;
- `cold_ms`: the first solve on a fresh solver (after `backend.create()`);
- `warm_ms`: a second solve on the same solver.

For `auto` rows, `cold_ms` covers ranking plus a fallback portfolio run, and the route after `auto:` is the one
that answered. Comparing `auto` with the chosen route's `cold_ms` shows the selection overhead: about 10-15 ms here.

Randomly generated families (knapsack, assignment, integer program, single-machine scheduling) take a seed; seed 0
keeps the original instances, and `BenchmarkCatalogueSpec` confirms seed 1 too. Compare encodings over several seeds:
a single instance's search path can swing by 2-3x between equivalent encodings.

The unpublished `kotlin/direct` route is measured too, but never offered by `auto`; its observations and the
optimisations they drove are in [the pure-Kotlin solver](../explanation/pure-kotlin-solver.md). Known answers come from closed forms or
dynamic programming, never from enumerating permutations or grids, so large scales stay within memory.

The current catalogue also includes single-machine scheduling. The older baseline below records the families present in that run. The route benchmark includes backend creation in its fresh-solver time; its numbers therefore have a different timing boundary from `cold_ms`. No browser timings are recorded here.

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

## Route selection evidence (2026-11-22, scales 1-6, 3 seeds, 10 s timeout)

`RouteBenchmarkSpec` on one Linux x64 host: JVM 21, Node and a release `linuxX64` binary. Cells are median
milliseconds of a fresh solver, including setup. A `*` marks a median where some seeds timed out. MiniZinc routes are
in their own section below. Every proof matched the known answer.

Scale 3:

| family | JVM cp-sat | JVM choco | JVM z3 | JVM scip | JVM highs | JVM kotlin | Node z3 wasm | Node kotlin | Native z3 | Native kotlin |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| n-queens | 37 | 10 | 40 | 362 | 1076 | 7 | 425 | 20 | 44 | 9 |
| graph-coloring | 9 | 2 | 11 | 19 | 17 | 3 | 44 | 8 | 16 | 3 |
| pigeonhole | 3 | 0 | 1613 | 2110 | 5146 | 0 | 4061 | 1 | 1534 | 0 |
| knapsack | 5 | 2 | 18 | 6 | 11 | 1 | 74 | 2 | 22 | 0 |
| assignment | 12 | 2 | 122 | 236 | 87 | 16 | 584 | 31 | 133 | 22 |
| job-shop | 9 | 2 | 14 | 10 | 14 | 4 | 36 | 10 | 21 | 5 |
| bit-vectors | — | — | 5 | — | — | 0 | 10 | 1 | 10 | 0 |
| integer-program | 6 | 2 | 11 | 7 | 10 | 3 | 54 | 5 | 21 | 2 |
| single-machine | 16 | 392* | 106 | 432 | 727 | 78 | 217 | 138 | 110 | 100 |

Scale 6:

| family | JVM cp-sat | JVM choco | JVM z3 | JVM scip | JVM highs | JVM kotlin | Node z3 wasm | Node kotlin | Native z3 | Native kotlin |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| n-queens | 143 | 33 | 7419 | 6231 | 3856 | 21 | 4830 | 106 | 7492 | 50 |
| graph-coloring | 11 | 2 | 13 | 27 | 18 | 6 | 55 | 21 | 23 | 10 |
| pigeonhole | 3 | 0 | timeout | timeout | timeout | 1 | timeout | 2 | timeout | 2 |
| knapsack | 5 | 15 | 17 | 6 | 8 | 7 | 81 | 22 | 23 | 7 |
| assignment | 17 | 9 | 410 | 1735 | 2328 | 117 | 1743 | 209 | 433 | 153 |
| job-shop | 9 | 2 | 10 | 9 | 18 | 4 | 41 | 9 | 15 | 3 |
| bit-vectors | — | — | 5 | — | — | 0 | 11 | 2 | 16 | 0 |
| integer-program | 7 | 9 | 18 | 7 | 10 | 27 | 78 | 52 | 21 | 28 |
| single-machine | 31 | timeout | timeout | timeout | 10000* | 10000* | timeout | timeout | timeout | 10000* |

Selection rules suggested by this recorded run (not additional default-registry entries):

1. **JVM, `CP_GLOBALS`: CP-SAT first, as the default policy already ranks it.** It is never the fastest on easy
   instances (its fixed cost is about 5-10 ms), but it is the only route that stays flat on single-machine scheduling,
   an optimisation over reified disjunctions. Choco and Kotlin are faster on satisfaction and assignment, but Choco
   times out on scheduling from scale 3. With the default race parallelism of 2, CP-SAT and Choco together cover
   every family here.
2. **JVM, `CP_GLOBALS`: rank MIP and Z3 last.** Counting arguments (pigeonhole) and large `allDifferent` instances
   (n-queens, assignment) make SCIP, HiGHS and Z3 seconds slower or time out.
3. **JVM, `LINEAR_INTEGER`: no evidence for a change.** Every route answers these knapsack and integer-program
   instances within about 30 ms. The current MIP-first order is harmless, but these instances are too easy to justify
   it either; harder linear instances are needed.
4. **Node and Native: Kotlin first for every model it supports, with Z3 as fallback.** `kotlin/direct` beats Z3 Wasm
   on every Node row by 3-100x, mostly through Wasm's fixed cost of about 40 ms and its search on `allDifferent`. On
   Native it beats or ties the Z3 C API everywhere; scheduling is within noise. Z3 remains the route for what Kotlin
   rejects.
5. **Kotlin on the JVM, if it is ever registered:** rank it after CP-SAT and Choco but before Z3 and MIP on
   `CP_GLOBALS`. It is faster than Z3 on every JVM row.

Gaps: the catalogue has no `SMT_REAL`, `NONLINEAR_INTEGER` or `PURE_BOOLEAN` family, and its bit-vector instance is
trivial. Rules for those classes remain the untested defaults.

### MiniZinc routes (pinned 2.10.1 bundle, same run settings)

Set `MINIZINC_EXECUTABLE` to register them: the JVM gets the five engines, and Node gets Gecode through the CLI. The
number in brackets is the route's rank under the default policy (0 is tried first).

| family, scale | JVM cp-sat direct | JVM gecode | JVM chuffed | JVM cp-sat | JVM highs | JVM cbc | JVM highs or-tools | Node gecode CLI | Node kotlin |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| n-queens, 3 | 38 (0) | 263 (2) | 257 (3) | 410 (4) | 426 (8) | timeout (9) | 1091 (7) | 134 (0) | 22 |
| n-queens, 6 | 178 (0) | 581 (2) | timeout (3) | timeout (4) | 1036 (8) | timeout (9) | 3867 (7) | 149 (0) | 96 |
| pigeonhole, 6 | 5 (0) | 263 (2) | 244 (3) | 244 (4) | 287 (8) | 275 (9) | timeout (7) | 131 (0) | 1 |
| knapsack, 6 | 5 (4) | 267 (6) | 246 (7) | 257 (8) | 275 (2) | 269 (3) | 7 (1) | 132 (0) | 15 |
| assignment, 6 | 17 (0) | 262 (2) | 255 (3) | 295 (4) | 288 (8) | 283 (9) | 2347 (7) | 136 (0) | 202 |
| integer-program, 6 | 7 (4) | 264 (6) | 243 (7) | 254 (8) | 276 (2) | 274 (3) | 7 (1) | 134 (0) | 53 |
| single-machine, 3 | 16 (0) | 282 (2) | 251 (3) | 298 (4) | 668 (8) | 1288 (9) | 711 (7) | 148 (0) | 131 |
| single-machine, 6 | 33 (0) | 9776* (2) | 2799* (3) | 3890 (4) | timeout (8) | timeout (9) | 10000* (7) | 9639* (0) | timeout |

What they add to the rules:

6. **MiniZinc routes cost a fixed 240-290 ms on the JVM and about 130 ms through Node's CLI.** That is process start
   plus flattening, before any search. On small and medium models, they belong after every in-process route. The
   default policy breaks this on `LINEAR_INTEGER`: MIP affinity (+1.0) outweighs the compiled-route penalty (-0.5),
   so `highs/minizinc` and `cbc/minizinc` rank second and third, ahead of `cp-sat/direct` and `choco/direct`. Those
   two answer in 5 ms against about 270 ms.
7. **Flattening can dominate.** Chuffed and CP-SAT through MiniZinc time out on 28-queens, while CP-SAT direct
   solves it in 178 ms.
8. **MiniZinc's MIP decomposition of globals beats ours.** HiGHS through MiniZinc solves pigeonhole and assignment
   in under 300 ms, while `highs/or-tools`, fed this project's big-M rows, times out or takes seconds. This points at
   `solve-mip`'s lowering of `allDifferent`, not at HiGHS.
9. **On Node, order Kotlin, then Gecode through the CLI, then Z3 Wasm, when MiniZinc is installed.** Gecode's 130 ms
   floor loses to Z3 Wasm on tiny models. It wins on 28-queens, pigeonhole and assignment, where Z3 Wasm takes
   seconds. It finishes two of three scale-6 scheduling seeds, which neither Kotlin nor Z3 Wasm does.

These are observations from the stated hosts and versions, not performance guarantees. The pure-Kotlin route remains outside the default registries. See [Solvers and routes](solvers-and-routes.md) for the actual registered defaults and [Run tests and benchmarks](../how-to/contributing/run-tests-and-benchmarks.md) for reproduction steps.
