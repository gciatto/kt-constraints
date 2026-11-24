# Prove a pigeonhole model infeasible

Place three pigeons into two holes, with no hole shared. This tiny instance has no solution:
three distinct hole indices cannot be chosen from the two values zero and one.

## Model

These examples run in a suspending caller and use `ConstraintScope`, the Kotlin DSL, and
`AutoBackend` from `:solve-auto`, which picks a bundled solver for your platform. See
[Install](../install.md) for the dependencies and [Choose a solver](../choose-a-solver.md) to pick one yourself.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Pigeonhole.kt:imports"

--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Pigeonhole.kt:model"
```

## Modelling choices

One variable records each pigeon's hole. `allDifferent` requires every pair to use different
holes. Keeping the domain `0..1` is essential: allowing a third hole changes the answer.

## Solve

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Pigeonhole.kt:solve"
```

## Expected output

```text
INFEASIBLE
```

There is no assignment. `INFEASIBLE` reports a proof that the model has no solution.
`UNKNOWN`, including an interrupted search without a witness, would not establish this.
See [results and statuses](../../reference/results-and-statuses.md).

## Explain the conflict with an incremental session

!!! note "Before this section"

    Read [Use incremental sessions](../incremental-sessions.md). An **unsat core** is a sufficient
    subset of temporary assumptions that makes the root assertions inconsistent.

Use a backend supporting incremental checks, assumptions and unsat cores, such as Z3. `AutoBackend` delegates batch solving and enumeration; use its `registry` to select a route with
`INCREMENTAL_ASSERTIONS` for sessions.

Start a new, satisfiable root model containing only the variables and their domains. Express
the three pairwise separation rules as temporary assumptions. The following application helper
also rechecks the core and checks each subset formed by omitting one pairwise rule:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Pigeonhole.kt:session"
```

The printed output is:

```text
UNSATISFIABLE
Conflicting pairwise requirements: 3
SATISFIABLE
```

An unsat core is a sufficient subset of the assumptions, relative to the root assertions;
it is not generally guaranteed to be minimal. Here all three pairwise rules are necessary.
Putting the already-infeasible `allDifferent` constraint in the root model would obscure this
explanation: the root alone would be inconsistent, so no assumptions would be needed.
The last check demonstrates that assumptions do not persist between checks.

## Variations

- Give three pigeons three holes to obtain a feasible model.
- Use `push`, `add` and `popTo` to try assertions that last until rollback; use
  assumptions when you need a core identifying the requirements involved in a check.
- Keep the instance small when using this as a diagnostic test; mathematical infeasibility
  does not imply that large instances are cheap for every backend.

See [incremental sessions](../incremental-sessions.md),
[solver selection](../choose-a-solver.md), and [solving modes](../../explanation/solving-modes.md).
