# Kt-Constraints roadmap

**Status:** exact modelling foundation and experimental JVM MiniZinc routes for Gecode, Chuffed, CP-SAT,
HiGHS and CBC are implemented. MIP support is a bounded affine integer subset, without exhaustive enumeration.
Other solver routes and stable language facades remain planned.

M2 adds resource-safe streams, exact assignment/result invariants, explicit capability-aware registration,
a capped test oracle, shared solver contracts and the first real MiniZinc/Gecode vertical slice.

This roadmap is a work breakdown for a self-contained, Kotlin Multiplatform constraint modelling / solving
framework. The project is independent of 2p-Kt. Similar Kotlin organization, testing and deployment conventions
are a deliberate compatibility preference, **not** a runtime dependency.

## Goals

1. Support satisfiability, constraint programming and mathematical optimization using a typed, immutable model.
2. Offer a capable collection of engines through MiniZinc and direct APIs: Z3, Choco, OR-Tools CP-SAT, Gecode,
   Chuffed, HiGHS and CBC. Backend *availability* is platform-dependent and explicitly advertised.
3. Support Kotlin/JVM/JS/Native modelling; Java and TypeScript facades should be genuinely ergonomic.
4. Make incremental solving, assumptions, reversible checkpoints, propagation and entailment possible for
   appropriate backends, without pretending MiniZinc batch solving implements CLP propagation.
5. Retain predictable semantics across backends and avoid hidden relaxation of numeric or global constraints.

## Non-goals

* Providing a new Prolog engine, unification library, or 2p-Kt adapter in this repository.
* Rewriting Z3/CP-SAT/Choco/Gecode propagation or branch-and-bound algorithms in pure Kotlin.
* Requiring every solver to be shipped on every platform.
* Claiming native propagation from a backend that only supports repeated batch satisfiability queries.

## Baseline architectural contracts

### Representation

- `Sort`: Bool, mathematical Integer, Real, Rational, Set, BitVector, Arrays, Strings, extensible compound sorts.
- `Domain`: finite intervals, sparse sets and complements, infinite numeric domains, optionality, typed enumerations.
- `Expr` family: immutable typed DAGs with variables, constants, operations; structural equality distinct from
  symbolic equality (`eq`). Preserves high-level operators for late lowering.
- `ConstraintScope`: owns variable identity (same printed name does not imply identity across scopes).
- `ConstraintModel`: immutable constraints/objective, metadata and provenance; snapshots and model hashing.
- Numeric semantics: unbounded mathematical integers where meaningful, checked lowering to bounded CP domains,
  exact rational/real support where available, explicit float approximations only when requested.

### Execution

- `ConstraintSolver`: solve a frozen model with `SolveOptions`, return `SolveResult`, optional solution stream.
- `IncrementalConstraintSolver`: assertion stack, assumptions, check, push/pop/checkpoints and session lifecycle.
- `PropagatingConstraintSolver`: domain reflection, entailment, propagation, reversible store operations.
- `SolutionStream`: resource-safe single-pass async consumption, cancellation, solution events and completion status.
- `SolveResult`: satisfiable/optimal/infeasible/unknown, separate termination reason (timeout, cancellation,
  resource limit), incumbent/assignment/objective/best bound, statistics and backend identity.
- Automatic backend selection is **capability- and route-aware**; unsuitable candidates are rejected, never guessed.

### Plugin/compile SPI

- `SolverBackend` and `SolverFactory` are separate from `ConstraintCompiler` / `BackendTransport`.
- Route identity: `engine = cp-sat, route = direct` and `engine = cp-sat, route = minizinc` remain distinguishable.
- `SupportReport`: supported constructs, required decompositions, lossless/lossy diagnostics, runtime availability.
- `CompilationOptions`: global constraints preservation, deterministic naming and variable projection.
- `CompiledModel`: backend-owned resource lifecycle, source mapping and cache invalidation contract.
- Backend-specific options are typed extensions; common options remain portable and immutable.

## P0 — Reliable standalone foundation (highest priority)

- [x] Initialize multi-module Gradle structure based on 2p-Kt / kt-math; Kotlin 2.4.21, kt-mpp 6.1.4.
- [x] Choose `gciatto/kt-constraints`, Maven `io.github.gciatto:kt-constraints-*`, package `io.github.gciatto.ktconstraints`.
- [x] Add initial immutable `Expr`/`Sort`/`IntDomain`/variable identity + `ConstraintScope`/`ConstraintModel`.
- [x] Add initial `ConstraintSolver` / `SolveOptions` / descriptor / `SolverBackend` contracts (not yet operational).
- [x] Add Kotest starter cases and source/test module wiring.
- [x] Add CI structure, release gating, docs, Gradle wrapper bootstrap and contributor/agent documentation.
- [x] Add exact integer literals/domains, Boolean/integer operations, preserved CP globals, folds and ground evaluation.
- [x] Add immutable assertion metadata, source labels and output projection; retain scope-owned variable identities.
- [x] Add reusable seeded representation contracts and a separately compiled Java core consumer.
- [x] Add resource-safe streams, support/compiler contracts, an exact finite test oracle and real JVM MiniZinc/Gecode conformance.
- [ ] Complete numeric types (BigInteger via kt-math or an equivalent MPP abstraction), sort checking, domain operations.
- [ ] Finalize AST coverage, normalization, canonicalization and algebraic simplifications.
- [ ] Complete immutable model metadata, objective semantics, variable projections, model cloning and diagnostics.
- [ ] Stabilize runtime SPIs, capability descriptors, support/lowering reports, closeable streams, and error taxonomy.
- [ ] Add Java/TypeScript usage-contract tests before freezing public API; confirm platform compilation.
- [ ] Extend `:test-core` contracts as the algebra grows; add IR round-trip checks when a portable format exists.
- [x] Add benchmark task and record baseline model-construction/compiler costs.

**P0 acceptance:** Core/DSL usable without a solver, immutable snapshots, explicit variable identity and sort rules;
contract tests run on JVM and JS, CI fully green, Maven artifact IDs verified in local POMs. No false backends.

## P1 — Broad CP engines and MiniZinc route

- [x] `:solve-minizinc`: checked source generation, JVM subprocess execution, JSON stream decoding,
  explicit engine configuration/discovery, startup validation and output projection.
- [x] Official JS runtime: Node/Gecode CLI and browser/Chuffed Wasm, with actual shared contracts.
- [ ] MiniZinc Native command transport.
- [ ] `:solve-flatzinc`: parse/generate the subset actually needed; no replacing MiniZinc's optimizer/decomposer.
- [x] Gecode via MiniZinc/FlatZinc: satisfiability and finite-domain constraints.
- [x] Chuffed via MiniZinc/FlatZinc: clause-learning CP integration.
- [x] OR-Tools CP-SAT via MiniZinc for the checked finite integer/Boolean subset.
- [x] `:solve-cpsat` direct JVM adapter (`cp-sat/direct`) for the Boolean/integer IR within an int64 margin.
- [ ] CP-SAT scheduling globals (intervals, `cumulative`, `noOverlap`) once the core IR has them.
- [x] HiGHS and CBC via MiniZinc for bounded affine integer/Boolean models.
- [x] `:solve-mip` SCIP and HiGHS through OR-Tools (`scip/or-tools`, `highs/or-tools`) over an exact common lowering.
- [ ] `:solve-highs` direct C API for LP/MIP and improved solution/bound support (postponed: no maintained JVM binding).
- [ ] Gurobi direct route once CI has a licence (the Maven jar ships no size-limited licence).
- [x] `:solve-choco` direct JVM adapter (`choco/direct`): FD variables, native globals, enumeration and optimization.
- [ ] Choco `PropagatingSession` (propagate, domain reflection, reversible world push/pop) once the common API exists.
- [ ] Define global-constraint registry (`allDifferent`, `element`, `table`, `cumulative`, `noOverlap`, `regular`,
  `globalCardinality`, graph constraints) with native translations and decompositions.
- [ ] Shared `:test-solve` tests for objective semantics, sat/unsat, limit/timeout, projection and result-stream closure.
- [ ] Cross-engine checks on n-queens, job-shop, knapsack, assignment, SAT and scheduling; record translation costs.

**P1 acceptance:** Models run via MiniZinc on desktop/server targets; direct JVM engines exposed by SPI;
Gecode/Chuffed/CP-SAT/HiGHS/CBC routes tested when installed; no backend capability overstated.

## P2 — SMT and portable execution

- [x] `:solve-z3`: direct JVM binding and native C integration; browser JS/Wasm binding and incremental assertion API.
  JVM binding, Wasm binding (Node and cross-origin-isolated browsers), desktop Native C API (Linux x64, macOS arm64,
  Windows x64; library loaded at run time) and incremental API implemented (M4).
- [ ] Add SMT sorts and constraints: integers, exact arithmetic, rationals, bitvectors, arrays, strings,
  uninterpreted symbols, quantified formula support only where semantically appropriate.
  Exact linear real arithmetic (`Rational`, `RealVar`, linear real nodes) and SMT-LIB fixed-width bit-vectors
  (any width; wrapping arithmetic, bitwise, shifts, signed/unsigned orders, integer conversion) implemented and
  solved by every Z3 route; real products, bit-vector division/extraction, arrays, strings and uninterpreted
  symbols pending.
- [x] Implement assumptions and unsat cores for engines supporting them, with source mapping (Z3 JVM, Wasm and Native; cores map to assumptions).
- [x] Implement SMT-style incremental sessions, push/pop and stable checkpoint ownership, rollback safety tests.
- [ ] JVM, Linux/macOS/Windows native, Android/iOS native (where packaging works), JS and Kotlin/Wasm JS.
- [ ] Add JS/TS `Promise` API and JVM Java builder/static factories + blocking/CompletionStage APIs.
  Java implemented (`Expressions` facade, `BlockingSolver` with blocking, `CompletableFuture` and iterator solving;
  javac-compiled consumer run against Choco, CP-SAT, Z3, SCIP and HiGHS); Java incremental sessions and JS pending.
- [ ] `:solve-portfolio`: deterministic ordered selection, optional parallel portfolio, evidence-aware incumbent handling.
- [ ] Automatic discovery of installed/embedded runtimes and solver version/capability metadata.
- [ ] CI host matrix + Java/JS/Native end-to-end usage tests, including solver bootstrap/install docs.

**P2 acceptance:** Z3 works incrementally on supported targets; CP/SMT models run with explicit runtime capability
reports, native resource cleanup verified, JS/TS and Java APIs validated independently of Kotlin DSL tests.

## P3 — Constraint store / CLP readiness

- [ ] Define `IncrementalSession`, `PropagatingSession`, `Checkpoint`, `ConstraintStore` and typed entailment.
- [ ] Distinguish native propagation from entailment by repeated satisfiability checks.
- [ ] Implement backtracking via reversible trail, persistent snapshots, or native push/pop, per backend.
- [ ] Variable binding/domain-change events, domain inspection, residual constraint projection and explanations.
- [ ] Define interaction between user-driven branching and backend search without duplicated choice points.
- [ ] Test nesting, fork isolation, replay, failures, cut-like pruning, infinite domains and resource cancellation.
- [ ] Benchmark constraint-added-per-resolution-step latency and rollback, not only batch benchmark instances.

**P3 acceptance:** An external logic engine can own and backtrack a constraint store via stable APIs, independently
of any specific Prolog implementation. Any future 2p-Kt integration belongs outside this repository.

## Backend integration matrix (target, not a claim of completion)

| Engine | Route | JVM | Native desktop | JS/Wasm | Incremental / propagation |
|---|---|---|---|---|---|
| MiniZinc | Compiler/runtime | CLI implemented | Planned | Node CLI and browser Chuffed Wasm implemented | Batch; not a propagating store |
| Gecode | MiniZinc/FlatZinc | Via runtime | Via runtime | Conditional | Native CP, no generic incremental API promised |
| Chuffed | MiniZinc/FlatZinc | Via runtime | Via runtime | Conditional | Native CP, no generic incremental API promised |
| CP-SAT | MiniZinc and direct | MiniZinc and direct JVM implemented | Custom wrapper | Via runtime if packaged | Incrementality not equivalent to CLP propagation |
| Choco | Direct JVM | Direct JVM implemented (`choco/direct`) | — | — | Batch only; propagation/backtracking planned |
| Z3 | Direct | Implemented (`z3/direct`) | C API implemented (`z3/native`; desktop, run-time loaded) | Wasm implemented (`z3/wasm`; Node, isolated browsers) | Incremental sessions implemented; no domain propagation |
| HiGHS | MiniZinc, OR-Tools and direct | MiniZinc and OR-Tools (`highs/or-tools`, limit-bounded) implemented; direct C API postponed | C API postponed | Wasm conditional | LP/MIP; not CLP(FD) propagation |
| SCIP | OR-Tools | Implemented (`scip/or-tools`) | — | — | MIP batch, interruptible |
| CBC | MiniZinc/FlatZinc | Via runtime | Via runtime | Conditional | MIP batch |

A backend is marked *available* only when its installed/embedded runtime passes a startup probe; marked
*compatible* only when model sorts, operations, ranges, semantics and requested execution capabilities pass checks.

## Cross-cutting quality and release criteria

- Kotlin commonMain is pure and lightweight; JVM and JS baseline required; Native as runtimes become integrated.
- Build via kt-mpp and Gradle version catalog; Kotest 6 `FunSpec` + parametrized tests (no kotlin.test).
- Verify publication to Maven Central Portal under `io.github.gciatto` with **prefixed** artifact IDs
  `kt-constraints-core`, `kt-constraints-model`, etc.; never publish generic `core` or `solve` artifacts.
- Separate benchmark compilation and solver execution; record versions, cold/warm starts and timeouts.
- Version with conventional commits + semantic-release, publish only after CI passes; GitHub release flag opt-in.
- The first publication includes only `:core`, `:model`, `:dsl`, `:solve` and `:solve-spi`; add backend artifacts
  to `publishedModules` only when functional and contract-tested.
- Any public API breaking change requires Kotlin, Java and TypeScript usage tests and versioning decision.

## References

- [2p-Kt develop](https://github.com/tuProlog/2p-kt/tree/develop), API and testing conventions.
- [kt-math](https://github.com/gciatto/kt-math), kt-mpp packaging and cross-platform build layout.
- [MiniZinc](https://www.minizinc.org/), [MiniZinc-JS](https://github.com/MiniZinc/minizinc-js),
  [OR-Tools](https://developers.google.com/optimization), [Z3](https://github.com/Z3Prover/z3),
  [Choco](https://github.com/chocoteam/choco-solver), [Gecode](https://www.gecode.org/),
  [HiGHS](https://github.com/ERGO-Code/HiGHS), [CBC](https://github.com/coin-or/Cbc).
