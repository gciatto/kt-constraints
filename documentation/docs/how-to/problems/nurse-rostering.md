# Build a roster with weighted preferences

Assign one nurse per day across three days using two nurses. Each nurse works at most two days
and never works on consecutive days. Both request the first day off: nurse 0's request has weight
two, and nurse 1's has weight one.

## Model

These examples run in a suspending caller and use `ConstraintScope`, the Kotlin DSL, and
`solveAuto` from `:solve-auto`, which picks a bundled solver for your platform. See
[Install](../install.md) for the dependencies and [Choose a solver](../choose-a-solver.md) to pick one yourself.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/NurseRostering.kt:model"
```

## Modelling choices

`work[nurse][day]` is an integer indicator: one means working, zero means off.
`count(IntLiteral(1), ...)` enforces daily coverage and each nurse's workload bound.
Adjacent indicators sum to at most one, enforcing a rest day after every working day.

Coverage, workload and rest are hard constraints added with `scope.add`.
`minimizeViolations` makes the two requests soft: it minimizes the sum of the weights of
false request expressions. The requests are not added as hard constraints, since satisfying
both would leave day zero uncovered. Weights must be non-negative. Calling this helper
replaces any earlier objective.

## Solve

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/NurseRostering.kt:solve"
```

## Expected output

```text
OPTIMAL: penalty = 1
[[0, 1, 0], [1, 0, 1]]
```

Nurse 0 has the first day off. Nurse 1 works that day, incurring penalty one. At least one
request must be violated to provide coverage; this roster attains the smaller penalty.

## Variations

- Add more preference expressions and weights to the same `minimizeViolations` call.
- For several shift types, use integer codes and count each code separately for coverage.
- Use `regular` with a finite automaton when permitted shift sequences are easier to describe
  as states and transitions than as adjacent-day rules.

See [optimization](../optimize.md), [the modelling reference](../../reference/modelling-language.md),
and [global constraints](../../explanation/global-constraints.md).
