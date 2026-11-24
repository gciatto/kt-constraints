# Backend SPI

The service provider interface (SPI) in `:solve-spi` connects solver-independent models to engine adapters. Start with [Add a backend](../how-to/contributing/add-a-backend.md) for the implementation workflow, and [Kotlin solver API](solver-api.md#backends-capabilities-and-availability) for `SolverBackend`, `BackendAvailability`, `SupportReport`, descriptors and registry selection. A backend creates solvers with `createSolver()` and is `AutoCloseable`: override `close()` only when it owns a runtime to release. The [generated API](/kt-constraints/api/index.html) lists exhaustive signatures.

## Compilation and errors

A `ConstraintCompiler<C : CompiledModel>` separates translation from execution. `support(model, options)` assesses whether the entire input can be represented faithfully. `compile(model, options)` produces an owned compiled representation without starting the engine, or throws `UnsupportedModelException`. `CompiledModel.sourceVariables` maps backend identifiers back to original variables; `close()` releases owned resources.

| Exception | Meaning and useful data |
|---|---|
| `UnsupportedModelException` | Unsupported semantics, ranges or options; `report` is the full `SupportReport` |
| `BackendUnavailableException` | Runtime cannot start; optional `cause` retains the original failure |
| `BackendExecutionException` | Engine, protocol or transport failed; optional `cause` retains the original failure |
| `NoCompatibleBackendException` | Registry has no eligible backend; `rejections` maps route IDs to reasons |

These errors do not establish that a mathematical model has no solutions.

`BoundedIntegerSupport(limit, margin, nestedGlobalsDecompose)` is a reusable whole-model check for Boolean/integer adapters with bounded engine arithmetic. `report(model)`, `report(constraint)` and `report(variable)` assess the corresponding input. `bounds(expression)` computes an `IntegerBounds` for supported integer nodes; empty domains use zero for this range calculation. This range calculation is not a satisfiability check.

`IntegerBounds.min` and `max` bound expression values; `magnitude` also tracks accumulated absolute intermediate values. `fits(limit)` checks all three quantities against the symmetric limit. Use it to reject arithmetic that could exceed an engine's exact representation, including intermediate sums. The diagnostic `margin` describes that limit; `nestedGlobalsDecompose` controls reporting of exact lowerings for globals below top-level conjunctions.

Adapters using mixed integer programming (MIP) translate integer and Boolean models into linear rows. Constraint programming (CP) adapters can use structured constraints and domain propagation; satisfiability modulo theories (SMT) adapters combine logical search with theories such as arithmetic and bit-vectors. These names describe engine families, not universal support guarantees.

## Features used for selection

`ModelFeatures.of(model)` extracts structural facts without solving. A **reified** relation is used as a Boolean expression inside a connective, rather than asserted only as a top-level conjunction.

| Feature | Meaning |
|---|---|
| `usesBooleans`, `usesIntegers`, `usesReals`, `usesBitVectors` | Sort families occurring in declarations or expressions |
| `nonlinear` | A product of two variable factors occurs in a constraint or objective |
| `reified` | A relation or global occurs under a connective other than top-level conjunction |
| `globals` | Set of `GlobalKind` tags |
| `objective` | `ObjectiveKind.NONE`, `LINEAR` or `NONLINEAR`; this describes shape, not minimization versus maximization |
| `variables`, `constraints`, `nodes` | Declared variable count, top-level assertion count, and expression occurrences across assertions and objective |
| `maxMagnitude` | Largest absolute integer literal, coefficient or domain bound; not a bound on all computed intermediates |
| `problemClass` | Coarse family used by ranking and performance evidence |

`GlobalKind` tags are `ALL_DIFFERENT`, `TABLE`, `ELEMENT`, `CARDINALITY`, `MIN_MAX`, `INVERSE`, `CIRCUIT`, `CUMULATIVE`, `REGULAR`, `SUBCIRCUIT`, `BIN_PACKING` and `NO_OVERLAP_2D`. They describe the matching [modelling constructs](modelling-language.md); derived helpers can appear as their underlying expressions.

| `ProblemClass` | Classification |
|---|---|
| `PURE_BOOLEAN` | No integer expressions after excluding real and bit-vector families |
| `LINEAR_INTEGER` | Linear integer model with top-level relations |
| `NONLINEAR_INTEGER` | Integer model with nonlinear expressions or objective |
| `CP_GLOBALS` | Integer model with globals or reified relations |
| `SMT_REAL` | Exact reals, possibly mixed with integers |
| `SMT_BITVECTOR` | Bit-vectors |

Classification checks bit-vectors first, then reals, pure Booleans, globals/reification, nonlinear arithmetic, and finally linear integers. A class is a selection hint; only the backend support check establishes compatibility.

## Ranking extension points

A **heuristic** is a preference rule, not a correctness test. Registry eligibility checks run before these rules. Larger scores rank first; equal totals retain registration order.

| Type/member | How to use it |
|---|---|
| `RankingHeuristic.name` | Stable diagnostic name for a custom rule |
| `RankingHeuristic.score(features, descriptor, platform)` | Return `Score`, or null for no opinion |
| `Score(value, reason)` | Unweighted numeric preference, conventionally around −1 to 1, and readable explanation |
| `WeightedHeuristic(heuristic, weight = 1.0)` | Multiply one rule's score by the chosen weight |
| `Contribution(heuristic, weight, score)` | Recorded rule name, weight and score in a candidate's diagnostics |
| `RankingPolicy(heuristics)` | Copy an ordered list of weighted rules; expose it through `heuristics` |
| `policy.with(heuristic, weight)` | Return a policy with an appended rule |
| `policy.without(name)` | Return a policy excluding every rule with that name |
| `RankingPolicy.DEFAULT` | Equally weighted capability fit, feature affinity and route overhead; no benchmark evidence |
| `RankingPolicy.REGISTRATION_ORDER` | Empty policy; eligible routes retain registration order |

`Candidate.total` sums each contribution's weight times its score value; `explanation` formats their reasons. Inspect these alongside `Selection.rejections` to distinguish preference from rejection.

| Built-in `Heuristics` member | Preference |
|---|---|
| `capabilityFit` | Small penalty for capabilities the model does not need |
| `featureAffinity` | Match problem class to engine family inferred from engine ID; unknown engines receive no opinion |
| `routeOverhead` | Penalize routes whose name contains `minizinc` for the compilation step |
| `requiredCapabilities(features)` | Derive satisfaction, objective, numeric and global requirements; additional caller requirements remain separate |
| `evidence(performanceEvidence)` | Experimental rule preferring measured shorter expected times |

`PerformanceEvidence.expectedMillis(problemClass, route, features)` returns an expected duration or null when unknown. `PerformanceEvidence.NONE` always returns null. Both the evidence interface and `Heuristics.evidence` require `ExperimentalKtConstraintsApi` opt-in. Attach evidence explicitly with `with`; default ranking does not load measurements. See [Benchmarks](benchmarks.md).

## Shared lifecycle helpers

These helpers are for backend implementations; application code normally uses the solver interfaces.

| Type/member | Responsibility |
|---|---|
| `ManagedConstraintSolver` | Implement `descriptor` and protected `openStream(model, options, onClosed)`. Shared `solve` consumes the stream; satisfaction stops at the first witness. Shared `solutions` tracks owned streams |
| `ManagedConstraintSolver.isClosed`, `onClose()` | Protected state and cleanup hook for extra resources such as sessions; shared `close` rejects new resources and closes active streams |
| `ManagedSolutionStream` | Implement protected `readNext()` and `release()`; shared reads enforce one consumer, terminal-event delivery and cancellation |
| `ManagedSolutionStream.stop(reason)` | Protected termination for an external deadline/resource stop; retain an incumbent and preserve prior completion |
| `ManagedSolutionStream.release()` | Unblock reads and prevent late acquisition; called once by the winning terminal transition and may race with a read |
| `CheckpointStack.depth`, `push()`, `popTo(checkpoint)` | Track session frames and reject stale/foreign checkpoints; `popTo` returns how many engine frames to remove |
| `StoreGuard.isClosed`, `requireOpen()` | Track closure and reject operations after it |
| `StoreGuard.adopt(variable)`, `release(variables)`, `requireOwned(variables)` | Track live declarations and reject foreign or rolled-back variables |
| `StoreGuard.close()` | Mark closed; return true only for the first close |

Keep each `CheckpointStack` and `StoreGuard` confined to its session or store. They enforce bookkeeping, not engine rollback: the adapter must restore the corresponding engine state.

`SatisfiabilityStore.of(incrementalSolver)` is the shared store adapter over repeated satisfiability checks. It owns the solver and uses session frames for marks; its public store semantics are described in [Kotlin solver API](solver-api.md#constraint-stores).
