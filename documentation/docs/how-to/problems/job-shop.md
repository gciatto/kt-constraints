# Minimize a job-shop makespan

Schedule two jobs on two machines. Each operation runs without interruption; a machine handles
one operation at a time. Time starts at zero.

| Job | First operation | Second operation |
| --- | --- | --- |
| A | Machine 0, duration 2 | Machine 1, duration 1 |
| B | Machine 1, duration 2 | Machine 0, duration 1 |

## Model

These examples run in a suspending caller and use `ConstraintScope`, the Kotlin DSL, and
`solveAuto` from `:solve-portfolio`, which picks a bundled solver for your platform. See
[Install](../install.md) for the dependencies and [Choose a solver](../choose-a-solver.md) to pick one yourself.

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/problems/JobShop.kt:model"
```

## Modelling choices

Each variable is an operation's start time. The precedence inequalities force the second
operation to wait for the first. `disjunctive` prevents overlap on each machine; operations
may touch at their endpoints. It returns a `Cumulative` node with unit demands and capacity one.
The DSL has no separate one-dimensional `noOverlap` function; use `disjunctive` for that rule.
`noOverlap2D` concerns rectangles instead.

The makespan is the maximum of the two final completion times. Start domains `0..3` contain
an optimal schedule for this instance; choose a justified horizon when changing the data.

## Solve

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/problems/JobShop.kt:solve"
```

## Expected output

```text
OPTIMAL: makespan = 3
[0, 2, 0, 2]
```

The start times are in the order A on machine 0, A on machine 1, B on machine 1, B on machine 0.
Each job needs three time units, so a makespan below three is impossible.

## Variations

- For a resource with capacity greater than one, use `cumulative(starts, durations, demands, capacity)`.
  Durations are positive exact `BigInteger` constants; demands are non-negative constants.
- For more jobs, group operations by machine and add one `disjunctive` constraint per group.
- Add release times or completion deadlines as inequalities on starts or ends.

See [optimization](../optimize.md), [global constraints](../../explanation/global-constraints.md),
and [solver selection](../choose-a-solver.md).
