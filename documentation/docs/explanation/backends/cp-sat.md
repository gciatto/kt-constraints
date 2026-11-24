# CP-SAT

## What it is

OR-Tools CP-SAT combines Boolean satisfiability search with finite-domain integer constraints. It fits combinatorial models such as assignment and scheduling, provided the adapter can represent every expression within its checked range.

## Routes and classes

`CpSatBackend` in `:solve-cpsat`, artifact `kt-constraints-solve-cpsat`, exposes `cp-sat/direct`. It builds OR-Tools objects on the JVM and is included in JVM automatic defaults. The separate `cp-sat/minizinc` route uses a smaller numeric subset and a compilation/process path; see [MiniZinc](minizinc.md).

## Platforms and runtime requirements

The direct adapter is JVM-only and uses OR-Tools' bundled native libraries. Availability includes loading those libraries. See [platform availability](../../reference/platform-availability.md) for tested JVM operating systems; the direct route is not a Node, browser or Kotlin/Native backend.

## Capabilities

Batch satisfaction, integer optimization and exhaustive projected satisfaction enumeration are supported. Satisfaction enumeration always uses one search worker. An optimization stream can emit improving **incumbents**, meaning feasible assignments found so far; only an appropriate final status establishes optimality.

The backend does not expose incremental assertion sessions, assumptions, unsatisfiable cores or a constraint store. See [solvers and routes](../../reference/solvers-and-routes.md) for the descriptor capabilities.

## Supported modelling features and numeric limits

The checked subset contains Boolean formulas, finite integer domains, sums, differences, products, weighted sums, min/max, element, tables, cardinalities and built-in structural globals. Top-level globals can use native translations; nested globals can require exact decompositions. A nested predicate is one used beneath a Boolean connective, such as negation or implication.

The [support implementation](https://github.com/gciatto/kt-constraints/blob/develop/solve-cpsat/src/main/kotlin/io/github/gciatto/ktconstraints/solve/cpsat/translation/CpSatSupport.kt) sets a conservative magnitude margin of 2^61. Checks cover expression bounds and accumulated magnitudes, not just domain endpoints. This leaves room for intermediate comparisons within signed 64-bit arithmetic. Out-of-range models, real arithmetic, bit-vectors and unsupported expression classes are rejected. The model's arbitrary-precision integers are never silently narrowed.

## Options type

`CpSatOptions` supplies optional non-negative `workers`, optional `randomSeed`, and an `Assignment` of hints, empty by default. Non-null options override `CpSatBackend(workers = 0, seed = null)` defaults; zero workers requests the engine default for optimization. Hints suggest values to try first and need not form a consistent assignment. Satisfaction enumeration still uses one worker. See [configuration](../../reference/configuration.md).

## Performance

The recorded JVM catalogue favors CP-SAT for the harder single-machine scheduling family. Choco is often faster on easy instances, so the default CP-SAT-first ranking is not a prediction of the fastest route for every model. Direct CP-SAT also avoids the startup and flattening cost measured for its MiniZinc route. See [benchmarks](../../reference/benchmarks.md) for numbers and run conditions.

## How to use it directly

[Choose a solver](../../how-to/choose-a-solver.md) quotes a tested Z3 registry example that you can adapt by registering this backend. [Optimize](../../how-to/optimize.md), [enumerate solutions](../../how-to/enumerate-solutions.md) and [set limits and cancel](../../how-to/limits-and-cancellation.md) explain the execution APIs with tested examples.
