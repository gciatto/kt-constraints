# Principles

A constraint model describes what an answer must satisfy. Choosing an engine, starting a process or allocating a native context comes later. Keeping those decisions separate lets the same model be inspected and solved through different routes without changing its meaning.

## Immutable meaning, explicit mutable state

`Expr`, `Sort`, `Domain`, `Variable` and `ConstraintModel` belong to the solver-independent representation. Expressions describe symbolic operations; they do not own solver variables or runtime handles. `ConstraintScope` is the short-lived mutable model builder. Building snapshots its declarations, assertions and output projection, so later builder edits cannot change an existing model. Custom expression and domain implementations must honor the same immutability contract.

Execution state has a different lifetime. Solvers, streams, incremental sessions and constraint stores own resources and must be closed. A session can add assertions or restore a checkpoint without mutating the original model. This separation keeps native libraries, subprocesses and JavaScript runtimes out of common model objects.

Immutability makes reuse possible; it does not imply a portable serialization format. Model serialization and portable fingerprints are not implemented. See [architecture](architecture.md).

## Names label variables; IDs identify them

A `VariableId` combines an identity owner with a declaration index. Two variables called `x`, declared in different scopes, are distinct even if their names and domains match. The builder checks ownership before accepting assertions, objectives or output variables.

This prevents unrelated model fragments from becoming coupled through a reused label. Names remain useful for display and diagnostics, while compilers can generate internal identifiers independently. Store declarations use their own identity owner for the same reason.

Object equality and symbolic equality serve different purposes: `==` compares Kotlin objects, while `eq` constructs a constraint. A comparison in the model must remain an expression that the backend can interpret.

## Preserve exact semantics across boundaries

Integer literals, finite-domain bounds and integer assignment values use arbitrary-precision `BigInteger`. A finite domain can contain values outside machine-integer ranges. The mathematical integer sort is distinct from a fixed-width bit-vector sort: bit-vector arithmetic wraps by definition; integer arithmetic does not.

The current real expression language is linear and uses exact `Rational` values. A decimal such as `0.1` can be represented as `1/10`. Integer objectives are supported; real objectives are not part of the current model API.

A backend may impose tighter bounds than the representation. Its support check must reject values or intermediate expressions it cannot represent. Silently truncating an integer, substituting a floating-point real or dropping an assertion would change the set of answers. Explicit rejection preserves the opportunity to choose another route.

Exact representation and exact witness checking do not make every engine's reasoning exact. MIP routes validate returned integer assignments against the source model, but their optimality and infeasibility statuses rely on numerical solver evidence. They do not provide rational proof certificates. See [engines and routes](engines-and-routes.md).

## Keep global structure until lowering

`AllDifferent`, `Circuit`, `Cumulative` and other globals remain abstract nodes in the model. A compiler can then use a native global, choose an equivalent decomposition or reject unsupported semantics. Decomposing during model construction would remove information that another engine could exploit for propagation or compilation.

Equivalent encodings can have very different costs. Preserving a global keeps that choice at the point where the engine and route are known. [Global constraints](global-constraints.md) explains the distinction between decompositions valid in any Boolean context and smaller forms that introduce hidden variables.

## Describe the route and the evidence

An engine and its execution route are separate identities. CP-SAT reached directly and CP-SAT reached through MiniZinc have different compilers, runtime requirements and supported ranges. Results identify both through a descriptor such as `cp-sat/direct` or `cp-sat/minizinc`.

Capabilities are separate too. Batch search, incremental assertion frames and domain propagation have different contracts. A push/pop interface does not establish that a backend can narrow domains or report propagation changes. The [solving modes](solving-modes.md) therefore use distinct APIs.

Search status and termination reason answer different questions. A feasible assignment can survive a timeout, but a timeout cannot establish optimality or infeasibility. Streams distinguish solution events from their final completion event. Automatic backends preserve the answering route's evidence rather than deriving a proof from several failed attempts.

Backend declarations are checked through shared contracts in `:test-solve`, including assignment validity, execution lifecycle and capability-specific behavior. Availability and whole-model support are checked separately: an installed solver can still be unsuitable for a particular model.
