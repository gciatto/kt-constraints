# M4 design: direct Z3

M4 adds Z3 as a direct engine, distinct from every MiniZinc route. The first slice is the
JVM binding with incremental sessions; JS/Wasm and Native C follow as separate slices,
each advertised only after its own tests pass.

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

## Incremental sessions

`IncrementalConstraintSolver.openSession(model)` returns an `IncrementalSession` over the
model's variables; the model's constraints form the irremovable root frame. Sessions are
`AutoCloseable`, single-consumer and closed by their solver.

- `add` asserts a constraint in the current frame. Variables not declared by the model are
  rejected, and unsupported nodes throw `UnsupportedModelException` before any change.
- `push` returns an opaque `Checkpoint`; `popTo` restores the assertions present when it
  was pushed and invalidates it and every newer checkpoint. Stale checkpoints and those
  of another session are rejected with `IllegalArgumentException`. A shared SPI
  `CheckpointStack` implements this ownership logic once for all backends.
- `check(assumptions, timeoutMillis)` returns a `CheckResult`: `SATISFIABLE` with an
  assignment validated against every active constraint and assumption, `UNSATISFIABLE`,
  or `UNKNOWN` with its termination reason. Assumptions hold for that check only.
- With assumptions, an `UNSATISFIABLE` result may carry an unsat core: a subset of the
  assumptions sufficient for unsatisfiability, not necessarily minimal. Cores and
  assumptions are separate capabilities (`UNSAT_CORES`, `ASSUMPTIONS`).
- Z3 maps frames to native `push`/`pop` and assumptions to fresh indicator constants
  inside a temporary native frame. Repeated checks are not domain propagation: Z3 does
  not advertise `DOMAIN_PROPAGATION` or `ENTAILMENT`.

## Later slices

1. JS/Wasm through the official `z3-solver` npm package, loaded asynchronously.
2. Kotlin/Native through the Z3 C API on hosts with reproducible binaries.

## Reference evidence

- [Z3 Java API](https://z3prover.github.io/api/html/namespacecom_1_1microsoft_1_1z3.html)
- [z3-turnkey](https://github.com/tudo-aqua/z3-turnkey)
- [z3-solver npm package](https://www.npmjs.com/package/z3-solver)
