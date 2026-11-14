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

Integer expressions, finite-domain bounds and assignment values use kt-math's immutable multiplatform
`BigInteger`. Long and Int factories are convenience inputs; conversion to a bounded backend must be checked.
`Sort.BitVector` is a separate bitwise sort.

Finite integer domains are canonical unions of sorted, disjoint, non-adjacent closed intervals. Sparse values
are singleton intervals; empty domains have no bounds and cardinality zero. Explicit inverted intervals are
invalid, while an empty Kotlin range denotes the empty domain. Union, intersection and cardinality operate on
intervals without enumerating their values. Infinite domains are not implemented by this finite-domain type.

`allDifferent` etc. remain IR nodes until a compiler decides whether native global support, lossless decomposition
or rejection is appropriate.

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
