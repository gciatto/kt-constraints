# Maximize value in a knapsack

Select each item at most once, keep total weight within capacity, and maximize total value. Use weights `[2, 3, 4]`, values `[3, 4, 5]`, and capacity 5.

## 1. Build the model

Use `:dsl` for `sum` and the global constraints; the arithmetic and comparison operators are members of the `:core` expressions. The examples solve with `AutoBackend` from `:solve-auto`, which picks a bundled solver for your platform. See [Install](../install.md) for the dependencies and [Choose a solver](../choose-a-solver.md) to pick one yourself.

Imports used by the example:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Knapsack.kt:imports"
```

Use a `0..1` integer decision for each item and two weighted sums.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Knapsack.kt:model"
```

A decision of 1 includes an item and 0 excludes it. Multiplication by a constant and `sum` suffice; no packing global is needed for this single-capacity selection problem. Boolean variables with `toInt()` are an alternative when the selection also participates in logical constraints.

## 2. Solve and read the result

Maximization is part of the model. Check `OPTIMAL` before presenting the value as the maximum; a limited solve may instead return a feasible incumbent. Read the chosen items through the assignment.

See [Optimize](../optimize.md) for objective semantics and [Set limits and cancel](../limits-and-cancellation.md) for bounded execution.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Knapsack.kt:solve"
```

Call the helper from a suspending caller with the sample data:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/KnapsackSpec.kt:example"
```

Expected output:

```text
Items: [0, 1]; value: 7
```

The solve call suspends; run it in a coroutine. See [Results and statuses](../../reference/results-and-statuses.md) before treating a result as a proof.

## Variations

- For bounded multiplicities, change each decision domain to `0..stock`; the same weighted sums still apply.
- For multiple capacities, add a weighted-sum bound for every resource.
- To require exactly a given number of items, constrain the sum of the binary decisions.
- For incompatible items, add a sum bound of 1 over their decisions. A `table` constraint can instead list allowed combinations for a small group.

See the [modelling language reference](../../reference/modelling-language.md), [global constraints explanation](../../explanation/global-constraints.md), and [API reference](/kt-constraints/api/index.html) for the operators and their semantics.
