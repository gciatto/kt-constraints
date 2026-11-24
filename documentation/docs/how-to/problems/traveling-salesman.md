# Minimize a traveling-salesman tour

Visit each of four cities exactly once and return to the starting city. The symmetric distances
are given in the model below. City indices run from zero to three.

## Model

These examples run in a suspending caller and use `ConstraintScope`, the Kotlin DSL, and
`solveAuto` from `:solve-auto`, which picks a bundled solver for your platform. See
[Install](../install.md) for the dependencies and [Choose a solver](../choose-a-solver.md) to pick one yourself.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/TravelingSalesman.kt:model"
```

## Modelling choices

`next[city]` identifies the next city, rather than the city visited at a particular tour position.
`circuit` requires a single cycle through every city, preventing separate subtours.
`allDifferent` alone would only enforce a permutation and could permit several cycles.

For each city, `element` selects the outgoing distance from its row of the matrix using a
zero-based index. Sum these four distances, including the return edge, to obtain the objective.
The successor domains keep every index inside its row.

## Solve

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/TravelingSalesman.kt:solve"
```

## Expected output

The first line is:

```text
OPTIMAL: length = 10
```

The successor list is either `[1, 2, 3, 0]` or `[3, 0, 1, 2]`: the optimal tour and its reverse.
For the first list, the route is 0 → 1 → 2 → 3 → 0 and costs 2 + 3 + 2 + 3 = 10.
Do not depend on which equally good tour the solver returns.

## Variations

- Replace the matrix with asymmetric distances for a directed tour.
- To display a route, start at city zero and follow its successor four times.
- Use `subCircuit` for an optional subset of cities; self-successors then denote excluded cities.
  Adapt the objective to charge zero for those exclusions if the matrix diagonal is not zero.

See [optimization](../optimize.md), [the modelling reference](../../reference/modelling-language.md),
and [global constraints](../../explanation/global-constraints.md).
