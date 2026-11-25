# Benchmarks

Benchmarks are manual and never part of `check`. They record raw observations: there are no regression
thresholds until several reproducible baselines exist.

## Run measurements

JVM: the route catalogue, store operations, MiniZinc phases and pure-Kotlin families have separate tasks.

```sh
./gradlew :solve-auto:jvmBenchmark
./gradlew :solve-choco:storeBenchmark :solve-z3:jvmStoreBenchmark
MINIZINC_EXECUTABLE=/path/to/minizinc ./gradlew :solve-minizinc:jvmBenchmark
./gradlew :solve-kotlin:jvmFamilyBenchmark
KT_CONSTRAINTS_PLATFORM_BENCHMARK=1 ./gradlew :solve-kotlin:jvmTest -i
KT_CONSTRAINTS_ROUTE_BENCHMARK=1 ./gradlew :solve-auto:jvmTest --rerun -i
```

Node: `-i` makes Kotlin/JS print the measurement rows.

```sh
KT_CONSTRAINTS_PLATFORM_BENCHMARK=1 ./gradlew :solve-kotlin:jsNodeTest -i
KT_CONSTRAINTS_ROUTE_BENCHMARK=1 ./gradlew :solve-auto:jsNodeTest -i
```

Native on Linux x64: build release test executables first, then run them with the benchmark
environment. Default debug test binaries are unoptimized; use release binaries for comparisons.

```sh
./gradlew :solve-kotlin:linkReleaseTestLinuxX64 :solve-auto:linkReleaseTestLinuxX64
KT_CONSTRAINTS_PLATFORM_BENCHMARK=1 solve-kotlin/build/bin/linuxX64/releaseTest/test.kexe
KT_CONSTRAINTS_ROUTE_BENCHMARK=1 KT_CONSTRAINTS_Z3_LIBRARY=solve-z3/build/z3-native/linuxX64/libz3.so solve-auto/build/bin/linuxX64/releaseTest/test.kexe
```

Provision the Z3 shared library before the Native route run; the path above is the repository’s
provisioned test-library location. Platform rows start with `BENCH,`; route rows start with
`ROUTE,` and include each route’s position in the order `AutoBackend.load()` tries them (runs before
2026-11-25 recorded the default-policy rank instead). Route benchmarks cover the bundled routes, `kotlin/direct`
included; set `MINIZINC_EXECUTABLE` to add the MiniZinc routes on the JVM, Node and Native.

| Route benchmark variable | Default | Meaning |
|---|---|---|
| `KT_CONSTRAINTS_ROUTE_BENCHMARK_SCALES` | `1 2 3` | Instance scales |
| `KT_CONSTRAINTS_ROUTE_BENCHMARK_SEEDS` | `3` | Seeds per scale |
| `KT_CONSTRAINTS_ROUTE_BENCHMARK_TIMEOUT` | `10000` | Milliseconds per solve |
| `KT_CONSTRAINTS_ROUTE_BENCHMARK_FAMILIES` | `catalogue linear` | `catalogue` (CP/SAT families), `linear` (LP/MILP), or both |
| `KT_CONSTRAINTS_ROUTE_BENCHMARK_ROUTES` | all | Route ids to measure, such as `kotlin/direct` |

A route that runs more than 2 s past its timeout prints a `ROUTE-OVERRUN,` row. Set `KT_CONSTRAINTS_HIGHS_LIBRARY` to
include the HiGHS route on Native.

The family benchmark uses `BENCHMARK_SCALES`, `BENCHMARK_SEEDS` and `BENCHMARK_FAMILIES`.

`jvmBenchmark` prints CSV, and writes it to the file named by `BENCHMARK_CSV` when that is set. `BENCHMARK_SCALE`
(default 1) grows sizes. Each family's known answer comes from a closed form, dynamic programming or
enumeration, and CP-SAT and Z3 reproduce it in `BenchmarkCatalogueSpec`, so the `correct` column is trustworthy.
The columns are:
- `build_ms`: model construction;
- `support_ms`: the route's support check, which runs its translation;
- `cold_ms`: the first solve on a fresh solver (after `backend.createSolver()`);
- `warm_ms`: a second solve on the same solver.

For `auto` rows, `cold_ms` covers ranking plus a run of the fallback automatic backend, and the route after `auto:` is the one
that answered. Comparing `auto` with the chosen route's `cold_ms` shows the selection overhead: about 10-15 ms here.

Randomly generated families (knapsack, assignment, integer program, single-machine scheduling) take a seed; seed 0
keeps the original instances, and `BenchmarkCatalogueSpec` confirms seed 1 too. Compare encodings over several seeds:
a single instance's search path can swing by 2-3x between equivalent encodings.

The `kotlin/direct` route is measured too, and `AutoBackend.load()` registers it at the position these measurements earn; its observations and the
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

## Route selection evidence (2026-11-25, scales 3 and 6, 3 seeds, 10 s timeout)

`RouteBenchmarkSpec` on one Linux x64 host: JVM 21, Node and a release `linuxX64` binary, with HiGHS 1.15.1 for the
Native route. Cells are median milliseconds of a fresh solver, including setup. A `*` marks a median where some seeds
timed out. MiniZinc routes are in their own section below. Every proof matched the known answer. This run replaced
the 2026-11-22 one (scales 1-6) after the pure-Kotlin and `Rational` optimisations; the other routes moved within
noise. HiGHS Wasm and Native take only the linear families.

Scale 3:

| family | JVM kotlin | JVM z3 | JVM scip | JVM highs | JVM cp-sat | JVM choco | Node kotlin | Node z3 wasm | Node highs wasm | Native kotlin | Native z3 | Native highs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| n-queens | 14 | 40 | 375 | 1100 | 42 | 28 | 23 | 450 | — | 10 | 38 | — |
| graph-coloring | 4 | 17 | 18 | 21 | 10 | 3 | 9 | 50 | — | 2 | 10 | — |
| pigeonhole | 1 | 1522 | 2107 | 5361 | 3 | 0 | 0 | 4135 | — | 0 | 1541 | — |
| knapsack | 2 | 17 | 6 | 9 | 5 | 6 | 2 | 78 | 8 | 0 | 15 | 8 |
| assignment | 17 | 80 | 239 | 88 | 12 | 4 | 37 | 609 | — | 24 | 84 | — |
| job-shop | 5 | 17 | 10 | 19 | 9 | 3 | 9 | 41 | — | 5 | 9 | — |
| bit-vectors | 0 | 9 | — | — | — | — | 1 | 10 | — | 0 | 4 | — |
| integer-program | 3 | 18 | 7 | 9 | 6 | 3 | 8 | 55 | 7 | 2 | 10 | 9 |
| single-machine | 88 | 119 | 434 | 733 | 15 | 394* | 155 | 220 | — | 105 | 117 | — |

Scale 6:

| family | JVM kotlin | JVM z3 | JVM scip | JVM highs | JVM cp-sat | JVM choco | Node kotlin | Node z3 wasm | Node highs wasm | Native kotlin | Native z3 | Native highs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| n-queens | 34 | 1053 | 6245 | 3887 | 149 | 46 | 96 | 4942 | — | 45 | 1066 | — |
| graph-coloring | 6 | 18 | 26 | 13 | 13 | 2 | 24 | 62 | — | 8 | 15 | — |
| pigeonhole | 0 | timeout | timeout | timeout | 2 | 0 | 1 | timeout | — | 0 | timeout | — |
| knapsack | 8 | 34 | 6 | 9 | 5 | 12 | 18 | 84 | 8 | 6 | 34 | 8 |
| assignment | 115 | 229 | 1737 | 2367 | 16 | 7 | 240 | 1792 | — | 154 | 226 | — |
| job-shop | 4 | 11 | 10 | 18 | 8 | 2 | 10 | 38 | — | 3 | 9 | — |
| bit-vectors | 1 | 8 | — | — | — | — | 2 | 11 | — | 3 | 8 | — |
| integer-program | 28 | 16 | 7 | 7 | 8 | 9 | 54 | 84 | 7 | 28 | 14 | 8 |
| single-machine | 10000* | timeout | timeout | 9637* | 34 | timeout | timeout | timeout | — | 10000* | timeout | — |

Selection rules suggested by this recorded run; the default registration orders follow rules 1, 2, 4, 5, 6, 9, 10, 11, 12 and 13:

1. **JVM, `CP_GLOBALS`: CP-SAT first, as the default policy already ranks it.** It is never the fastest on easy
   instances (its fixed cost is about 5-10 ms), but it is the only route that stays flat on single-machine scheduling,
   an optimisation over reified disjunctions. Choco and Kotlin are faster on satisfaction and assignment, but Choco
   times out on scheduling from scale 3. With the default race parallelism of 2, CP-SAT and Choco together cover
   every family here.
2. **JVM, `CP_GLOBALS`: rank MIP and Z3 last.** Counting arguments (pigeonhole) and large `allDifferent` instances
   (n-queens, assignment) make SCIP, HiGHS and Z3 seconds slower or time out.
3. **JVM, `LINEAR_INTEGER`: no evidence for a change.** Every route answers these knapsack and integer-program
   instances within about 30 ms. A MIP-first order would be harmless, but these instances are too easy to justify
   it either; harder linear instances are needed.
4. **Node and Native: Kotlin first for every model it supports, then HiGHS, then Z3.** `kotlin/direct` beats Z3 Wasm
   on every Node row by 3-100x, mostly through Wasm's fixed cost of about 40 ms and its search on `allDifferent`. On
   Native it beats or ties the Z3 C API everywhere; scheduling is within noise. HiGHS only takes linear models, and
   beats Z3 on every one of them (rule 12). Z3 remains the route for the rest of what Kotlin rejects.
5. **Kotlin on the JVM:** rank it after CP-SAT and Choco but before Z3 and MIP on
   `CP_GLOBALS`. It is faster than Z3 on every JVM row.

Gaps: the catalogue has no `NONLINEAR_INTEGER` or `PURE_BOOLEAN` family, and its bit-vector instance is trivial.
Rules for those classes remain the untested defaults. The linear families below are the only real-valued ones.

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

### Native MiniZinc routes (2026-11-25, pinned 2.10.1 bundle, scales 3 and 6, 3 seeds, 10 s timeout)

`RouteBenchmarkSpec` from a release `linuxX64` binary, with `MINIZINC_EXECUTABLE` set, on the host of the 2026-11-21
baseline. Cells are median milliseconds; the number in brackets is the route's position in the order
`AutoBackend.load()` tries them. A `*` marks a median where some seeds timed out. Every proof matched the known answer.

| family, scale | kotlin | z3 native | gecode | chuffed | cp-sat | highs | cbc |
|---|---:|---:|---:|---:|---:|---:|---:|
| n-queens, 3 | 17 (0) | 40 (1) | 307 (2) | 288 (3) | 530 (4) | 475 (5) | timeout (6) |
| n-queens, 6 | 48 (0) | 7456 (1) | 298 (2) | timeout (3) | timeout (4) | 1052 (5) | timeout (6) |
| pigeonhole, 6 | 0 (0) | timeout (1) | 271 (2) | 251 (3) | 249 (4) | 286 (5) | 281 (6) |
| knapsack, 6 | 7 (0) | 25 (1) | 269 (2) | 249 (3) | 261 (4) | 277 (5) | 273 (6) |
| assignment, 6 | 156 (0) | 407 (1) | 268 (2) | 258 (3) | 297 (4) | 289 (5) | 284 (6) |
| job-shop, 6 | 5 (0) | 15 (1) | 264 (2) | 235 (3) | 252 (4) | 279 (5) | 277 (6) |
| integer-program, 6 | 28 (0) | 18 (1) | 267 (2) | 249 (3) | 255 (4) | 276 (5) | 269 (6) |
| single-machine, 3 | 102 (0) | 110 (1) | 279 (2) | 254 (3) | 297 (4) | 669 (5) | 1297 (6) |
| single-machine, 6 | 10000* (0) | timeout (1) | 9794* (2) | 2783* (3) | 3919 (4) | timeout (5) | timeout (6) |

10. **Native MiniZinc routes cost a fixed 235-300 ms, as on the JVM.** They belong after Kotlin and Z3 Native, which
    answer most rows in under 50 ms. Z3 Native loses to Gecode only on large `allDifferent` and counting models
    (28-queens, scale-6 pigeonhole), which Kotlin answers first anyway. Only CP-SAT through MiniZinc finishes every
    scale-6 scheduling seed.

## Linear and mixed-integer programs (2026-11-25, scales 4-16, 3 seeds, 30 s timeout)

`KT_CONSTRAINTS_ROUTE_BENCHMARK_FAMILIES=linear` runs `LinearCatalogue`, on the same host and binaries as above:

- `lp-planted`: dense LPs with `8·scale` columns and `5·scale` rows, built around a planted optimal primal-dual pair;
- `milp-knapsack`: 0/1 knapsacks with rational profits, solved by dynamic programming;
- `facility-location`: uncapacitated facility location with continuous shares, solved by enumerating open sets.

Every route gets `numericTolerance = NumericTolerance()`, so floating-point routes take part. Exact routes must
match the optimum exactly; floating-point ones within a relative 1e-4. Every answer did, and no route overran its
timeout by more than 2 s. The size column of a `ROUTE,` row is the column count for `lp-planted` and the knapsack, and
facilities × customers for facility location.

Scale 8:

| family | JVM kotlin | JVM z3 | JVM scip | JVM highs | Node kotlin | Node z3 wasm | Node highs wasm | Native kotlin | Native z3 | Native highs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| lp-planted | 47 | 30000* | 26 | 21 | 78 | timeout | 53 | 90 | 30000* | 30 |
| milp-knapsack | 11 | timeout | 8 | 25 | 34 | timeout | 10 | 11 | timeout | 17 |
| facility-location | 91 | 1558 | 18 | 36 | 144 | 2092 | 30 | 58 | 1556 | 31 |

Scale 16:

| family | JVM kotlin | JVM z3 | JVM scip | JVM highs | Node kotlin | Node z3 wasm | Node highs wasm | Native kotlin | Native z3 | Native highs |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| lp-planted | 311 | timeout | 69 | 56 | 577 | timeout | 197 | 767 | timeout | 136 |
| milp-knapsack | 2 | timeout | 8 | 25 | 13 | timeout | 14 | 3 | timeout | 18 |
| facility-location | 215 | timeout | 39 | 69 | 441 | timeout | 78 | 302 | timeout | 42 |

11. **Exact LP and MILP: Kotlin, never Z3.** `kotlin/direct` is the fastest exact route on every row and platform,
    by 10-1000x. Z3 times out from scale 8 on the dense LPs and knapsacks; its optimizer also needs every claim
    confirmed (see [Z3](../explanation/backends/z3.md)). Kotlin already comes before Z3 everywhere.
12. **With a tolerance, floating-point routes are 2-6x faster than Kotlin on dense LPs and facility location at scale
    16.** Kotlin stays within 1 s on every row and is fastest on the knapsacks, where one round of Gomory cuts
    closes the gap. It also returns exact answers. The fixed order keeps Kotlin first; on Node and Native, HiGHS now
    comes before Z3, because it wins every linear row both accept.
13. **JVM: keep SCIP and HiGHS after Z3.** They beat Z3 on linear rows, but are 2-20x slower on the `allDifferent`
    and counting models they also accept (n-queens, pigeonhole, assignment). A fixed order cannot tell those classes
    apart.

These are observations from the stated hosts and versions, not performance guarantees. See [Solvers and routes](solvers-and-routes.md) for the actual registered defaults and [Run tests and benchmarks](../how-to/contributing/run-tests-and-benchmarks.md) for reproduction steps.
