# M5 design: direct CP/MIP adapters

M5 adds direct routes to engines that M3 already reaches through MiniZinc. Each direct
route is a separate backend with its own descriptor and conformance gate, and is checked
against the finite oracle (and, where both run, the MiniZinc route) on shared models.

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
