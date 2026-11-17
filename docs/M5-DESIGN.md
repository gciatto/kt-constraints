# M5 design: direct CP/MIP adapters

M5 adds direct routes to engines that M3 already reaches through MiniZinc. Each direct
route is a separate backend with its own descriptor and conformance gate, and is checked
against the finite oracle (and, where both run, the MiniZinc route) on shared models.

## M5a: direct Choco (JVM)

- Route: `choco/direct`, a pure-Java engine (`org.choco-solver:choco-solver` 6.0.2,
  BSD-3-Clause, Java 17). Availability is a trivial solve, so a broken classpath is a
  reported unavailability. Only `jvmMain` has a backend.
- **Integers are 32-bit.** Choco variables live within `±IntVar.MAX_INT_BOUND`
  (`21474836`). Every literal, domain bound, coefficient, subexpression interval and
  accumulated linear magnitude must stay within `10^7`, so the difference of the two sides
  of any relation still fits Choco's bounds; anything else is reported unsupported before
  execution. Domains with holes are an interval variable plus `notMember` gaps; an empty
  domain posts `false`.
- **Booleans and globals.** A top-level constraint posts one native constraint (`scalar`
  for linear relations, `sum` for cardinalities, `allDifferent`, `table`, clause
  constraints). A nested Boolean expression is that same constraint reified by Choco
  (`Constraint.reify()`, an equivalence), so nested globals keep native semantics and no
  decomposition is declared. Sums, differences, negations and linear sums are flattened
  into one linear form; products and elements become auxiliary variables over their exact
  intervals (`times`, `element` with index offset 0, posted at top level for the shared
  element-definedness rule). Reals and bit-vectors are rejected.
- **Search.** Each stream builds its own `Model`, so no solver state is shared between
  calls, models or threads. Every `next` runs `Solver.solve()` once on the default
  dispatcher, so the search advances only when the consumer asks. Satisfaction streams
  enumerate distinct projections; optimization relies on Choco's strict objective cuts,
  and `OPTIMAL` is claimed only when the search ends without a stop criterion firing.
  Solutions are read from the source variables, validated with the pure evaluator and
  scored exactly.
- **Termination.** One stop criterion observes close/cancellation (including thread
  interruption from coroutine cancellation) and the stream deadline; a search stopped by
  the deadline reports `TIMEOUT`, never infeasibility. A solution limit stops after the
  limit-th solution (`RESOURCE_LIMIT`). No thread outlives a read.
- **Propagating sessions are deferred.** Choco's public API does expose what a
  `PropagatingSession` needs (`Solver.propagate()`, domain reflection on variables, and
  `getEnvironment().worldPush()/worldPop()` for reversible state), but the common
  session API does not exist yet and must first be designed against Kotlin, Java and
  TypeScript usage examples. This slice offers batch solving and enumeration only.

## M5b: direct OR-Tools CP-SAT (JVM)

- Route: `cp-sat/direct`, distinct from `cp-sat/minizinc`. Runtime:
  `com.google.ortools:ortools-java` (Apache-2.0), which bundles the native library for
  Linux x86-64/aarch64, macOS x86-64/aarch64 and Windows x86-64. Availability is a real
  `Loader.loadNativeLibraries()` plus a trivial solve, never classpath presence. The
  module is multiplatform for layout consistency, but only `jvmMain` has a backend.
- **Integers are machine integers.** CP-SAT works on `int64` and rejects models whose
  linear expressions may overflow. The support report computes, without native code, an
  exact interval for every integer subexpression and a magnitude bound for every sum
  (`Σ |coefficient| · max |term|`, products by their prefix products). Any literal,
  domain bound, coefficient, interval end or magnitude beyond `2^61` is reported as
  unsupported, so every relation `lhs - rhs (+ 1)` still fits in `int64`. The margin is
  deliberately conservative; `CpModel.validate()` is a second check during compilation,
  and its failures are reported as `UnsupportedModelException` before any search.
- **Booleans are literals.** Top-level conjunctions, disjunctions, relations, tables,
  all-different and cardinalities post native constraints. A nested Boolean expression
  is reified into a fresh literal `r` with both directions posted (`r ⇒ e`, `¬r ⇒ ¬e`),
  so every auxiliary literal is functionally determined by the source variables.
  Equivalence uses `xor(a, c, r)`; relations and cardinalities use enforced linear
  constraints; a nested table is reified as a disjunction of tuple conjunctions and a
  nested all-different as pairwise disequalities (both declared lossless decompositions).
- **Integer subexpressions are affine.** Sums, differences, negations and linear sums
  build `LinearExpr`s directly. A product becomes a fresh variable over its exact interval
  constrained by `addMultiplicationEquality`; an element becomes a fresh variable over the
  hull of its values constrained by `addElement`, whose index range is enforced at top
  level, matching the shared element-definedness semantics of Z3, MiniZinc and the oracle.
  Reals and bit-vectors are rejected.
- **Search.** One `CpSolver` per stream runs on its own thread. Each solution callback
  reads all source variables, validates the full assignment with the pure evaluator,
  evaluates the objective exactly (CP-SAT's `double` objective is never used) and hands
  the projected solution to the consumer through a rendezvous channel, so the search
  waits while the consumer has not asked for more.
  - Satisfaction enumerates with `enumerate_all_solutions` on a single worker (CP-SAT's
    documented restriction). Auxiliary literals and variables are determined by the
    source variables, so only hidden source variables can repeat a projection; repeated
    projections are skipped. A completed enumeration reports `FEASIBLE` with the last
    solution, or `INFEASIBLE` when none exists.
  - Optimization uses CP-SAT's own search; callbacks report improving incumbents and
    only strictly improving ones are emitted. `OPTIMAL` is claimed only when CP-SAT
    proves optimality, and its objective is the exact evaluated one.
  - `CpSatBackend(workers, seed)` fixes the worker count and random seed for
    optimization (CP-SAT defaults otherwise); satisfaction always uses one worker.
- **Termination.** A solution limit stops the search after the limit-th solution has been
  consumed (`RESOURCE_LIMIT`); the remaining stream budget becomes `max_time_in_seconds`,
  and a search that stops without proof under a deadline reports `TIMEOUT`, never
  infeasibility. Cancellation and close stop the search, release a callback waiting on
  the consumer and join the search thread, so no search outlives its stream.
- **Out of scope for this slice:** scheduling globals (intervals, `cumulative`,
  `noOverlap`) need new core IR first; incremental sessions are not offered because CP-SAT
  has no incremental solving API.

## M5c: SCIP and HiGHS through OR-Tools (JVM)

- Routes: `scip/or-tools` and `highs/or-tools`, in the new module `:solve-mip`. Both use
  OR-Tools' `MPSolver`, already bundled by `ortools-java` for every CI platform (HiGHS
  1.12, MIT; SCIP, Apache-2.0), so they are not the direct HiGHS C API: `:solve-highs`
  stays reserved for that route, which is postponed with the other native bindings.
  Gurobi needs a licence that CI cannot provide yet, so it is deferred.
- **Exact linear lowering (common code).** `MipLowering` turns the Boolean/integer IR
  into a pure integer program (`MipProgram`: integer variables with exact bounds, linear
  rows with exact integer coefficients, an affine objective). It is common code, so a
  future native HiGHS route can reuse it, and the support report is the lowering itself.
  - Integers stay within `±10^6` (the MiniZinc MIP margin): every literal, coefficient,
    domain bound and affine magnitude, so every big-M term is exactly representable and
    far from the solvers' tolerances. Products need one fixed factor; reals and
    bit-vectors are rejected.
  - Booleans are 0/1 variables. A nested relation `e ≤ 0` over `e ∈ [lo, hi]` becomes a
    binary `r` with `e + hi·r ≤ hi` and `e − (lo − 1)·r ≥ 1`, so `r` is determined both
    ways; strict relations use integrality (`e < 0` is `e + 1 ≤ 0`), equality is the
    conjunction of two inequalities, and conjunctions, disjunctions and cardinalities
    use their standard exact linear forms.
  - Declared lossless decompositions: domains with holes select one interval with
    binaries; all-different is pairwise disequality; a table is a disjunction of tuple
    conjunctions; an element selects its index with top-level one-hot binaries (the
    shared element-definedness rule) and links the value with exact big-M rows.
- **Search.** One `MPSolver` per stream solves once on its own daemon thread with a zero
  relative gap. The rounded solution is validated exactly against the source model before
  it is emitted, and the objective is evaluated exactly. There is no enumeration: a
  stream emits at most one solution and then its completion. `OPTIMAL` and `INFEASIBLE`
  rest on the solver's numerical proof, as on the MiniZinc MIP routes.
- **Termination.** The stream deadline becomes the solver time limit and a stop under it
  reports `TIMEOUT`. SCIP supports `interruptSolve`, so close and cancellation stop the
  search and join its thread. OR-Tools cannot interrupt HiGHS (`MPSolver` says so, and
  `ModelBuilder`'s interrupt does not stop it), so `OrToolsMipBackend.highs(limit)`
  requires a positive per-solve time limit: every HiGHS solve is bounded, close returns
  at once, and the abandoned solve ends at that limit. Hitting the backend limit before
  the stream deadline reports `RESOURCE_LIMIT`. OR-Tools reports a HiGHS stop at its time
  limit with a status its Java enum cannot map, so such a stop exposes no incumbent and
  reports `UNKNOWN`; the stream never guesses a solution or infeasibility from it.

