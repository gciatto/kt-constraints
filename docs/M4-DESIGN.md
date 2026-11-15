# M4 design: direct Z3

M4 adds Z3 as a direct engine, distinct from every MiniZinc route. The first slice is the
JVM binding; incremental sessions, JS/Wasm and Native C follow as separate slices, each
advertised only after its own tests pass.

## JVM batch route

- Route: `z3/direct`. Runtime: `tools.aqua:z3-turnkey` (MIT), which bundles the official
  Z3 Java API with Linux, macOS and Windows native libraries; no system install is needed.
  Availability is a real native load plus a trivial check, never classpath presence.
- Integers map to Z3's unbounded mathematical `Int`. Literals and coefficients of any size
  are translated exactly, so no numeric range is imposed; returned numerals are parsed as
  exact `BigInteger` values. Domains are asserted as interval disjunctions.
- Globals use lossless decompositions declared in the support report: `distinct` for
  all-different, an `ite` chain for zero-based element, a tuple disjunction for tables and
  a pseudo-Boolean sum for cardinalities. Each element index adds the same top-level
  definedness constraint as the MiniZinc route and the finite oracle. Unknown nodes are
  rejected before execution.
- Satisfaction enumeration blocks each projected output assignment, so streams contain
  exactly the distinct projections. Optimization is a linear improving search with
  `Solver` checks: each incumbent adds a strict objective bound, and only a final UNSAT
  check proves optimality. Z3 `Optimize` is not used until its semantics are separately
  tested; unbounded objectives are therefore reported as feasible incumbents at timeout,
  never as `UNBOUNDED`.
- Every full model is validated with the pure evaluator before projection.
- Each stream owns one Z3 `Context`, confined to one dedicated thread. The deadline is the
  remaining stream budget passed as Z3's `timeout` per check. Cancellation interrupts the
  context from any thread; the context is closed on its own thread once the active check
  returns. An `unknown` result is never reported as infeasible.

## Later slices

1. `IncrementalConstraintSolver`/`IncrementalSession`: assertion scopes, checkpoints owned
   by exactly one session, assumptions and optional unsat cores. Repeated `check` is not
   advertised as domain propagation.
2. JS/Wasm through the official `z3-solver` npm package, loaded asynchronously.
3. Kotlin/Native through the Z3 C API on hosts with reproducible binaries.

## Reference evidence

- [Z3 Java API](https://z3prover.github.io/api/html/namespacecom_1_1microsoft_1_1z3.html)
- [z3-turnkey](https://github.com/tudo-aqua/z3-turnkey)
- [z3-solver npm package](https://www.npmjs.com/package/z3-solver)
