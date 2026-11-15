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
  exactly the distinct projections. Optimization uses plain `Solver` checks: each
  incumbent adds a permanent strict objective bound, and tentative bounds are probed in a
  pushed frame, galloping (doubling steps) beyond the incumbent until one is refuted, then
  bisecting. Optimality is claimed only when the score right after the incumbent is refuted,
  so wide domains need a logarithmic number of checks. Z3 `Optimize` is not used: every
  domain is finite, so `UNBOUNDED` cannot arise, and refutation-based proofs need no
  separate trust in its optimality claims.
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

## JS/Wasm slice

The official `z3-solver` package runs Z3 as threaded Wasm (`SharedArrayBuffer`); Node
supports it directly, while browsers require cross-origin isolation (COOP/COEP headers).
To avoid two divergent implementations, translation, enumeration/optimization and session
logic move to `commonMain` behind an internal per-context port with suspending checks:
the JVM port wraps the Java API on its confined thread, and the JS port wraps the
high-level API. Its route is `z3/wasm`; a closeable, asynchronously loaded backend owns the
module's worker threads, terminated through the package's `killThreads` on close.
Browser support is advertised only after a cross-origin-isolated browser test passes.

Status: Node is implemented and runs the same solver and incremental contracts as the JVM.
The pinned package is 5.3.3, a newer Z3 than the JVM's 4.14.0 bundle; each route reports
only its own validated behaviour. Contexts are released by the package's garbage-collection
finalizers, as its API has no explicit context disposal. Interrupted checks leave sessions
usable on both platforms. Browsers run the same contracts when the page is cross-origin
isolated: the Karma setup serves `z3-built.js` as a page script and the Wasm with COOP/COEP
headers, and CI verifies it in headless Chrome. Applications must do the same.

## Kotlin/Native slice

Native targets reuse the shared port through Z3's C API. The C library is **loaded at run
time** (`dlopen` / `LoadLibraryEx`) by a small cinterop shim that declares only the stable
C prototypes it calls, instead of being linked into executables:

- Kotlin/Native links against an old glibc sysroot, while released `libz3` builds need a
  recent glibc and libstdc++; linking would either fail or produce binaries that cannot start
  where Z3 is absent. Run-time loading keeps every executable startable and turns a missing
  or incompatible library into a truthful `BackendAvailability` with the loader's reason.
- No Z3 headers or archives are downloaded at build time; the shim compiles on every native
  target from the repository alone.

The route is `z3/native`, with the same capabilities as the other routes. The library is
`KT_CONSTRAINTS_Z3_LIBRARY` when set, else the platform's default name on the loader path.
One library is loaded per process; a backend asking for a different path is unavailable.
Contexts are created with `Z3_mk_context` (term lifetime bound to the context, which the
solver-level push/pop never shortens) and confined to one dedicated thread; solvers, models,
parameters and vectors are reference counted explicitly. Z3's error handler is disabled and
every error is checked and raised as `BackendExecutionException`. Cancellation and close call
the thread-safe `Z3_interrupt`.

Status: implemented; the Linux x64 host runs the same solver, incremental and optimization
contracts as the JVM and Wasm routes, and CI runs them on Linux, macOS and Windows hosts.

Reproducible test binaries: the Linux x64, macOS arm64 and Windows x64 `libz3` of the same
pinned z3-turnkey artifact used on the JVM (Z3 4.14.0), extracted by Gradle for the native
test task of the matching host. iOS targets compile the route but no library is provisioned,
so it reports itself unavailable there; mobile packaging is deferred.

## Exact reals slice

The first sort beyond Boolean/integer is `Sort.Real`, restricted to **linear** real
arithmetic so every model value is rational and nothing is approximated:

- `Rational` (core) is an exact, always-normalized value: lowest terms, positive denominator,
  structural equality. kt-math's `BigRational` is not used because it does not normalize
  (`2/4 != 1/2`, `1/3 * 3` prints `3/3`), which breaks structural equality of IR nodes.
- Nodes: `RealVar` (unbounded; bounds are ordinary constraints), `RealLiteral`, `RealSum`,
  `RealDifference`, `RealNegation`, `RealScale` (rational coefficient times a real
  expression), `IntToReal`, and `RealEquality` / `RealLessThan` / `RealLessOrEqual`.
  There is no real product, division or real objective: products of variables can force
  irrational (algebraic) models, and a real supremum need not be attained.
- `Valuation` gains a real binding with a default of "absent", and `Assignment.of` a trailing
  `reals` map, so existing Kotlin and Java callers are unaffected.
- Routes without exact reals (MiniZinc, the finite oracle) reject models declaring or using
  them before execution. Z3 maps them to its `Real` sort on every route, reads model values
  as exact numerals, and validates each assignment with the evaluator like integer models.
  A non-rational model value is a `BackendExecutionException`, never a rounded number.

## Bit-vector slice

`Sort.BitVector(width)` follows SMT-LIB `QF_BV` semantics, never Kotlin machine integers:

- Values are unsigned `BigInteger`s in `[0, 2^width)`; a literal outside that range is
  rejected rather than truncated. Signed views use two's complement only where an operator
  says so. Every width is supported, including widths beyond 64 bits.
- Nodes: `BitVecVar`, `BitVecLiteral`, `BitVecBinary` with a `BitVecOperator` (wrapping add,
  subtract and multiply; and, or, xor; shift left, logical and arithmetic shift right, where
  shifting by the width or more yields zero or the sign fill), `BitVecNot`, `BitVecNegation`,
  `BitVecComparison` with a `BitVecRelation` (equality, unsigned and signed orderings), and
  `BitVecToInt` (unsigned or signed). Operand widths must match at construction.
- Division, remainder, concatenation and extraction are deferred: division by zero has
  backend-specific conventions that need their own conformance cases first.
- As with reals, MiniZinc and the finite oracle reject such models; Z3 maps them to its
  bit-vector theory on every route and assignments are validated by the evaluator.

## Reference evidence

- [Z3 Java API](https://z3prover.github.io/api/html/namespacecom_1_1microsoft_1_1z3.html)
- [z3-turnkey](https://github.com/tudo-aqua/z3-turnkey)
- [z3-solver npm package](https://www.npmjs.com/package/z3-solver)
