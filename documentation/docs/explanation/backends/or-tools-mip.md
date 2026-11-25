# SCIP and HiGHS through OR-Tools

## What it is

Mixed-integer programming (MIP) represents constraints as linear equalities or inequalities, such as `2*x + y <= 10`, with integer decisions. These adapters use OR-Tools' `MPSolver` interface to SCIP or HiGHS. Integer models use a bounded Boolean/integer lowering. Models with reals become linear programs with continuous columns, solved in floating point only when the caller sets `SolveOptions.numericTolerance`; each solution is re-checked exactly against that tolerance, and strict real inequalities are rejected. See [linear and mixed-integer programs](../../how-to/linear-programs.md).

## Routes and classes

Both routes belong to `:solve-mip`, artifact `kt-constraints-solve-mip`, and share the project's MIP lowering.

| Route | Kotlin factory | Execution difference |
|---|---|---|
| `scip/or-tools` | `OrToolsMipBackend.scip()` | Interruptible through OR-Tools |
| `highs/or-tools` | `OrToolsMipBackend.highs(solveLimitMillis)` | Requires a positive backend time limit |

Both are JVM automatic defaults; the bundled HiGHS instance has a 60,000 ms backend limit. HiGHS through MiniZinc is a [different route](minizinc.md). The reserved `:solve-highs` module does not implement a direct HiGHS C API backend.

## Platforms and runtime requirements

These are JVM routes using OR-Tools' bundled native libraries. Availability loads the libraries, checks that the requested engine exists in the build, and solves a startup probe. See [platform availability](../../reference/platform-availability.md) for tested systems.

SCIP supports interruption. OR-Tools cannot interrupt this HiGHS integration: closing returns promptly, but an abandoned engine call may continue until its backend limit. This matters when choosing time limits and racing several routes. See the [backend implementation](https://github.com/gciatto/kt-constraints/blob/develop/solve-mip/src/jvmMain/kotlin/io/github/gciatto/ktconstraints/solve/mip/OrToolsMipBackend.kt).

## Capabilities

Both support batch satisfaction and integer optimization. The stream emits at most one returned witness or incumbent after the native solve finishes, then a completion event; it does not expose intermediate improvements. Neither descriptor promises exhaustive satisfaction enumeration. Neither offers incremental sessions, assumptions, unsatisfiable cores or stores.

Returned integer assignments are validated against the source model. Optimality and infeasibility statuses rely on numerical engine evidence, rather than exact rational proof certificates. See [results and statuses](../../reference/results-and-statuses.md).

## Supported modelling features and numeric limits

The lowering accepts Boolean logic and bounded affine integer arithmetic. **Affine** means a constant plus a weighted sum of variables; products must reduce to multiplication by a constant. Globals and reified relations can become auxiliary variables and big-M rows. A **big-M row** uses a sufficiently large bound to enable or disable a linear restriction according to a Boolean decision. These transformations can be much larger than the source constraint.

The checked margin is 10^6 for literals, coefficients, bounds and derived rows. Reals, bit-vectors, genuinely nonlinear variable products, unsupported nodes and out-of-range derived expressions are rejected. Integer source representation remains exact; acceptance by the adapter does not make the underlying numerical proof rationally certified. See [numeric subsets](../../reference/solvers-and-routes.md#checked-numeric-subsets) and the [shared lowering](https://github.com/gciatto/kt-constraints/blob/develop/solve-mip/src/commonMain/kotlin/io/github/gciatto/ktconstraints/solve/mip/program/MipBuilder.kt).

## Options type

`MipOptions` provides optional positive `threads` and optional non-negative `relativeGap`; both default to null. The **relative gap** measures the difference between the incumbent objective and the proven bound, relative to the objective’s magnitude. A positive relative gap prevents reporting `OPTIMAL`. The HiGHS factory additionally requires a positive `solveLimitMillis`, independent of the caller's `SolveOptions`. See [configuration](../../reference/configuration.md).

## Performance

The default ranking favors MIP for linear integer optimization, but the recorded linear instances are too easy to establish a general advantage. Large all-different decompositions can make the direct MIP routes much slower than CP. HiGHS through MiniZinc sometimes beats HiGHS through OR-Tools on these models, pointing to differences in lowering rather than engine identity alone. See [benchmarks](../../reference/benchmarks.md) for numbers and limits of that evidence.

## How to use it directly

[Choose a solver](../../how-to/choose-a-solver.md) quotes a tested Z3 registry example that you can adapt by registering this backend. [Optimize](../../how-to/optimize.md) explains result evidence and [set limits and cancel](../../how-to/limits-and-cancellation.md) covers the HiGHS interruption restriction. Check support with the same options you pass to execution.
