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

A `ConstraintSolver` performs one-shot solving. An `IncrementalSession` offers assertion scopes and satisfiability
queries. A `PropagatingSession` is a stronger capability, reporting domain reductions and entailment. It is incorrect
to advertise general CLP propagation merely because a backend implements SMT push/pop.

## Execution and resource ownership

Any backend-owning solver and session must be `AutoCloseable`, cancellable, and explicit about life cycle.
Streams must be single-pass, close automatically when exhausted, and expose terminal status separately from
incumbent solutions. Native solver contexts and JS Wasm runtimes are never owned by common AST objects.

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
