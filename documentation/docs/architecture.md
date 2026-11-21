# Architecture

The project is standalone. Compatibility with 2p-Kt means conventions, not a dependency.

```text
Kotlin DSL / Java builder / JS facade
              |
          Typed AST
     (:core :model :dsl)
              |
       Solver interfaces
     (:solve :solve-spi)
        /            \
MiniZinc/FlatZinc    Direct backend adapters
  |                 |        |       |       |
Gecode/Chuffed    Z3/Choco  CP-SAT   HiGHS   ...
CP-SAT/HiGHS/CBC
              |
   SolveResult / SolutionStream
              |
      Incremental session
      Propagating session
      (capabilities only)
```

## Non-negotiable separations

The IR is not MiniZinc syntax. MiniZinc is a *compiler/transport route*, while an engine such as CP-SAT can be
reached by several routes. API selection must identify both engine and route.

A frozen model is immutable and serializable/cachable by design. A session is stateful and potentially
thread-confined. `ConstraintScope` allocates semantic variable identities; name reuse across scopes cannot alias.

Model construction copies declaration and constraint collections into Kotlin `buildList` snapshots. Their
contents cannot be changed through mutable collection casts, and subsequent builder edits cannot affect them.
This freezes the collections; custom expression and domain implementations must themselves be immutable.

Each assertion has an optional unique name, immutable string annotations and an optional source location.
The existing Boolean constraint list remains a projection of those entries. Output projection defaults to all
declared variables; an explicit projection preserves its supplied order and may be empty. Scope ownership and
typed expression sorts are validated before accepting assertions, objectives or projections. Satisfaction is
represented by a null objective and `satisfy()` explicitly clears a previous objective.

Integer expressions, finite-domain bounds and assignment values use kt-math's immutable multiplatform
`BigInteger`. Long and Int factories are convenience inputs; conversion to a bounded backend must be checked.
`Sort.BitVector` is a separate bitwise sort.

Real expressions are linear: unbounded `RealVar`s, exact `Rational` literals (normalized, so structural equality
is numeric), sums, differences, negation, rational scaling and an explicit `IntToReal` embedding, compared with
real equality and orderings; the DSL offers these as operators over real expressions, `Rational`s and machine
integers, and `Rational.parse` reads `p`, `p/q` and decimals such as `0.1` exactly. Without products of variables every model value is rational, so evaluation and
assignments stay exact. Objectives remain integer, because a real supremum need not be attained. Routes without
exact reals (MiniZinc floats, the finite oracle) reject models that declare or use them.

Bit-vectors follow SMT-LIB `QF_BV`: values are unsigned `BigInteger`s below `2^width` for any positive width,
literals out of range are rejected, arithmetic wraps, shifts saturate at the width, and signed relations and the
signed integer conversion read two's complement. Operand widths are checked at construction. Division,
remainder, concatenation and extraction are deferred until their conventions have conformance cases.

Finite integer domains are canonical unions of sorted, disjoint, non-adjacent closed intervals. Sparse values
are singleton intervals; empty domains have no bounds and cardinality zero. Explicit inverted intervals are
invalid, while an empty Kotlin range denotes the empty domain. Union, intersection and cardinality operate on
intervals without enumerating their values. Infinite domains are not implemented by this finite-domain type.

Expression construction preserves the typed AST and structural equality without eager simplification.
Ordered `children` and a bottom-up fold expose traversal without depending on a backend. The ground evaluator
uses exact integer arithmetic and classical Boolean semantics; variables and unknown extension nodes are
rejected explicitly. Division and modulo are deferred until their cross-backend semantics are specified.

`allDifferent` etc. remain IR nodes until a compiler decides whether native global support, lossless decomposition
or rejection is appropriate.

The first globals are all-different, zero-based element selection, allowed-tuple tables, exact weighted sums,
and Boolean cardinalities. Their operand and tuple collections are detached immutable snapshots with structural
equality. An element expression requires a non-empty array and rejects an out-of-range ground index; tables
validate tuple arity. Empty all-different is true and an empty allowed-tuple table is false, including at arity zero.

`IntMin`/`IntMax` and the 0/1 conversion `BoolToInt` are integer nodes. `Inverse`, `Circuit` (zero-based successors)
and `Cumulative` (constant positive durations, non-negative demands and capacity) implement
`DecomposableConstraint`: each carries an exact decomposition over primitive nodes that introduces no variables.
Every route posts them natively at top level where it can (Choco, CP-SAT, MiniZinc) and lowers the decomposition
below a connective, where native forms cannot be reified, and on Z3 and MIP routes. Counting, ordering and
scheduling helpers (`count`, `among`, `globalCardinality`, `increasing`, `decreasing`, `lexLess`, `lexLessOrEqual`,
`disjunctive`, `abs`) are DSL functions over these nodes, not separate IR.

## Portable representation design

No serialization format or portable model fingerprint is implemented yet. A future format must encode exact
integers losslessly, preserve declaration/operand order and globals, and use model-local variable indices.
Decoding must allocate a fresh identity owner and remap all references, objectives and output variables.
The current in-memory owner object and hash codes must never serve as serialized identity or a portable cache
key. Round-trip tests will accompany an actual versioned format; none are claimed for this milestone.

A `ConstraintSolver` performs one-shot solving. An `IncrementalSession` offers assertion scopes and satisfiability
queries. A `PropagatingSession` is a stronger capability, reporting domain reductions and entailment. It is incorrect
to advertise general CLP propagation merely because a backend implements SMT push/pop.

## Execution and resource ownership

Any backend-owning solver and session must be `AutoCloseable`, cancellable, and explicit about life cycle.
Streams must be single-pass, close automatically when exhausted, and expose terminal status separately from
incumbent solutions. Native solver contexts and JS Wasm runtimes are never owned by common AST objects.

M2 result contracts keep search status separate from termination. Feasible/optimal results require an
assignment, and proof statuses require completed search rather than a timeout/cancellation. Integer objective
values and bounds remain exact. Assignment factories snapshot typed maps and reject out-of-domain values;
solver descriptors snapshot their capabilities and identify each engine/route with a distinct `engine/route` ID.

Pure evaluation may bind variables through a solver-independent typed `Valuation`; an `Assignment` supplies
those bindings. Missing or out-of-domain values are errors. Undefined element indices have a distinct error
so a finite test oracle can reject invalid candidates without swallowing unsupported-node failures.

Streams emit solution/incumbent events and exactly one completion event, then return null. Cancellation and
close preserve the first definitive terminal result and release resources once, including during an in-flight
read. Only one consumer may call `next` at a time. Closing a solver cancels its active streams. Shared lifecycle
implementations use portable atomic state; coroutine cancellation is propagated rather than reported as UNSAT.
Batch `solve` is lazy: for a satisfaction model it returns the first validated solution as a completed
`FEASIBLE` result and closes the stream, which stops the search; enumerating more solutions is the stream's job.
Optimization reads the stream until its completion, so optimality still needs the backend's proof.

The unpublished finite test oracle enumerates at most 10,000 candidates across at most 64 variables. It checks
the whole supported IR before enumeration, uses exact sparse domains, validates all full assignments, and only
then projects public outputs. Satisfaction enumeration removes duplicate projections; optimization emits strictly
improving incumbents. Reaching a limit or timeout retains an incumbent without claiming optimality. Undefined
element indices invalidate a candidate; other evaluation errors propagate. The oracle is never auto-registered.

## Automatic selection and portfolios

`SolverRegistry.rank` (M8, `docs/M8-DESIGN.md`) first rejects backends that lack a capability the model's
features require, are unavailable, or report part of the model unsupported. It then scores the rest with a
`RankingPolicy`, a list of weighted `RankingHeuristic`s:
- capability fit;
- feature affinity, matching the problem class to SMT, CP or MIP engines;
- route overhead;
- an optional benchmark-evidence heuristic over the experimental `PerformanceEvidence` interface.

Ties keep registration order, and every score carries its reason. `:solve-portfolio` bundles each platform's
routes (`DefaultBackends`) and runs them as a fallback or a bounded race. Only a single backend's own result is a
proof; timeouts and failures are reported, never treated as evidence.

## Platform boundaries

- `commonMain`: pure typed IR, compiler contracts, generic descriptors.
- `jvmMain`: native JNI/JNA/Java engine wrappers, subprocess MiniZinc where available.
- `nativeMain`: C interop wrappers and optional external command transports on desktop Native.
- `jsMain` / Wasm: MiniZinc-JS, Z3-Wasm interop, Promise-friendly facades.
- Platform discovery is explicit and validates executables/libs rather than assuming presence.

## Backend conformance

Backends register a descriptor and a compiler/transport pair. Compilation returns diagnostics and a supported
subset description. Conformance tests independently verify constructs and runtime lifecycle. A backend not yet
implemented or present must never be selected by `auto`.

Registry registration is explicit and immutable on every platform. Selection preserves registration order and
checks required capabilities, runtime availability and the whole-model support report before returning a backend.
An objective additionally requires optimization capability. Rejections retain route-scoped diagnostics; unknown
or incompatible backends are never substituted silently and selection does not allocate solver resources.

The first real route is Gecode through the JVM MiniZinc CLI. Its compiler emits deterministic internal names,
preserves source-variable mappings and lets MiniZinc lower globals. It proves every integer intermediate fits
Gecode's symmetric range `-2147483646..2147483646`; otherwise it rejects the whole model. Array selection uses
zero-based arrays with independent index-definedness constraints. CLI output contains full assignments for
independent validation before public projection. Runtime startup and temporary files belong to the stream,
not to the common compiled representation. M3 adds explicit Chuffed, CP-SAT, HiGHS and CBC configurations. Each is discovered by exact configuration ID
and validated with an actual startup solve. The existing Gecode entry point remains source-compatible.
Chuffed excludes its reserved +/-500,000,000 sentinels; CP-SAT initially shares Gecode's conservative range.
The direct `cp-sat/direct` route (M5) instead bounds every integer subexpression and accumulated linear
magnitude by `2^61`, so each posted relation fits CP-SAT's int64 arithmetic; see `docs/M5-DESIGN.md`.
The direct `choco/direct` route bounds them by `10^7` (inside Choco's `IntVar.MAX_INT_BOUND`), reifies nested
globals natively and builds one Choco model per stream, advanced one solution per read. `scip/or-tools` and
`highs/or-tools` share a common exact lowering to integer programs within `10^6`; HiGHS solves carry a mandatory
backend limit because OR-Tools cannot interrupt them.
HiGHS/CBC use a +/-1,000,000 affine integer subset, zero objective gaps and exact returned-assignment
validation; their proof statuses rely on numerical MIP evidence. They do not advertise exhaustive enumeration.
A validated MIP satisfaction witness plus successful exit establishes feasibility even without a terminal status.
No-witness completion and optimization still require explicit statuses. JS execution uses the official npm runtime: Node/Gecode is `gecode/minizinc-js-cli`, while browser/Chuffed
is `chuffed/minizinc-wasm`. A closeable, asynchronously loaded backend owns the global upstream pool;
only one backend may own that pool at a time. Backend close cancels active streams, then shuts down the
pool once they exit (bounded grace) so CLI temporary-file cleanup is not preempted; the next load awaits
that release. Individual solver close leaves the shared runtime usable. Node and browser reuse the checked
compiler, exact assignment decoder and shared contracts. JS statistics inherit upstream Number
semantics, while assignment integers remain in a lossless JSON string. Native command execution remains planned.

M4 adds direct JVM Z3 (`z3/direct`, bundled by `z3-turnkey`), a route distinct from every MiniZinc route.
Integers map to Z3's unbounded `Int`, so no numeric range is imposed. Globals use declared lossless decompositions
and the same element-definedness rule. Optimization gallops and then bisects tentative objective bounds over
`Solver` checks; optimality is claimed only once the next better score is refuted. Each stream or session owns one Z3 context confined to a dedicated thread;
close and coroutine cancellation interrupt the native call, and the context is closed on its own thread.

Incremental sessions (`IncrementalConstraintSolver.openSession`) treat the model as an irremovable root frame.
A shared `CheckpointStack` rejects stale and foreign checkpoints before any native change. Assumptions hold for a
single check, and unsat cores are subsets of those assumptions, not necessarily minimal. Satisfiable checks are
validated against every active constraint and assumption. Repeated checks are not advertised as propagation.

M7 adds `ConstraintStore`, the logical store an external logic engine owns (`docs/M7-DESIGN.md`). Variables are
declared on the store at any time; marks backtrack in LIFO order and invalidate newer marks; a failed store stays
failed until undone. Entailment is four-valued (`ENTAILED`, `DISENTAILED`, `UNDETERMINED`, `UNKNOWN`) and carries
its `Evidence`. `SatisfiabilityStore` runs over any incremental session, so every definite answer is a proof but
domains never narrow. `ChocoStore` propagates natively, reporting shrunk domains, and decides entailment by
refutation through propagation, which may be `UNKNOWN` but is never wrong. Residual projection is sound, not
minimal. `docs/LOGIC-ENGINES.md` maps choice points, wakeups and labeling onto the store.
Translation, streams and sessions are common code behind an internal per-context port, so the Wasm route
`z3/wasm` (official `z3-solver` module, owned and terminated by a closeable `Z3JsBackend`) shares them on Node
and in cross-origin-isolated browsers, and the Kotlin/Native route `z3/native` shares them through a cinterop
shim that loads the Z3 C library at run time, so a missing library is a reported unavailability, not a link failure.
