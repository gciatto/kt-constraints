# Release notes: first release

The first public release of Kt-Constraints: a Kotlin Multiplatform library for constraint programming and SMT,
with Java and TypeScript facades. Maven group `io.github.gciatto`, artifacts `kt-constraints-*`; npm package
`kt-constraints-interop-js`. Every claim below is backed by CI on the platforms listed in
[AVAILABILITY.md](AVAILABILITY.md).

## Modelling

- Immutable typed IR:
  - Booleans;
  - exact unbounded integers;
  - exact linear reals;
  - SMT-LIB fixed-width bit-vectors;
  - globals (`allDifferent`, `table`, `element`, cardinality).
- `ConstraintScope` with ownership checks; a Kotlin operator DSL; the Java `Expressions` facade.
- Deep expressions are handled without recursion. Model construction is linear in the number of constraints.

## Solving

| Route | Platforms |
|---|---|
| `z3/direct`, `z3/wasm`, `z3/native` | JVM; Node and cross-origin-isolated browsers; desktop Native |
| `cp-sat/direct`, `choco/direct` | JVM |
| `scip/or-tools`, `highs/or-tools` | JVM |
| MiniZinc: Gecode, Chuffed, CP-SAT, HiGHS, CBC | JVM CLI; Node CLI; browser Wasm (Chuffed) |

- Every route declares its capabilities, rejects unsupported models before execution with diagnostics, and
  passes the shared conformance and differential contracts.
- Incremental sessions (Z3) with checkpoints, assumptions and unsat cores.
- **Automatic selection:** `solveAuto`, and `SolverRegistry.rank` with feature-, capability- and platform-aware
  heuristics that explain their choices, plus an experimental hook for benchmark evidence.
- **Portfolios:** fallback and bounded race. Only a single backend's own result counts as a proof.

## Constraint stores for logic engines

- `ConstraintStore` for external logic engines:
  - variables declared at any time;
  - LIFO marks;
  - four-valued entailment with its evidence;
  - explanations;
  - forks;
  - residual projection.
- `ChocoStore` propagates natively (JVM). `SatisfiabilityStore` gives proofs over Z3 on every platform.
- [LOGIC-ENGINES.md](LOGIC-ENGINES.md) maps choice points, wakeups and labeling onto the store.

## Facades

- **Java (`:interop-java`):**
  - `BlockingSolver`: blocking, `CompletableFuture` and iterator solving;
  - `BlockingSession`;
  - `BlockingStore`;
  - `JavaNumbers`.
- **TypeScript (`kt-constraints-interop-js`):**
  - an ES module with checked declarations and `bigint` integers;
  - `Promise` solving, cancelled through `AbortSignal`;
  - `for await` enumeration;
  - sessions and stores.

## Known limits

- Kotlin/Wasm targets wait for a Wasm build of kt-math. iOS compiles but has no provisioned Z3 library.
- Choco, CP-SAT and SCIP translate deep expressions recursively; chains of about 20,000 nested operations overflow.
  Use `LinearSum` or the DSL's `sum` for long sums.
- Explanations are not minimal. `ChocoStore` explanations cost one replay per posted constraint.
- No benchmark-based ranking data ships yet. `PerformanceEvidence.NONE` is the default.

Details: [INSTALLATION.md](INSTALLATION.md), [ARCHITECTURE.md](ARCHITECTURE.md), [BENCHMARKS.md](BENCHMARKS.md).
