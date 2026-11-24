# Pack items into the fewest bins

Place every item in one bin without exceeding its capacity, and minimize the number of occupied bins. Use item sizes `[4, 3, 2]` and capacity 5; three candidate bins are enough because each item fits alone.

## 1. Build the model

Use `:dsl` for `sum` and the global constraints; the arithmetic and comparison operators are members of the `:core` expressions. The examples solve with `AutoBackend` from `:solve-auto`, which picks a bundled solver for your platform. See [Install](../install.md) for the dependencies and [Choose a solver](../choose-a-solver.md) to pick one yourself.

Imports used by the example:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/BinPacking.kt:imports"
```

Create a bin index per item and a load variable per candidate bin. The load domains impose capacity; `binPacking` equates each load with the sum of sizes assigned to that bin.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/BinPacking.kt:model"
```

The builder preserves the global by default. Pass `useDecomposition = true` to post its exact variable-free
decomposition instead. Both forms are tested. `nValue(binOf)` minimizes distinct occupied bins, including
bins that hold only zero-sized items.

## 2. Solve and read the result

Require `OPTIMAL` and read both placements and loads. Two bins suffice, with loads 4 and 5; the third candidate stays empty. Bin labels can vary, so print sorted loads for a stable comparison.

See [Optimize](../optimize.md) for optimality checks.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/BinPacking.kt:solve"
```

Call `binPackingExample()` from a suspending caller to use the global with the sample data.
Its `useDecomposition` parameter defaults to `false`; pass `true` for the decomposition:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/BinPacking.kt:example"
```

Expected output:

```text
Bins: 2; sorted loads: [0, 4, 5]
```

The solve call suspends; run it in a coroutine. See [Results and statuses](../../reference/results-and-statuses.md) before treating a result as a proof.

## Variations

- For feasibility with a fixed number of bins, create only that many load variables, restrict bin indices accordingly, and omit the objective.
- For different capacities, give each load its own upper bound.
- For positive-sized items, counting positive loads is another occupied-bin objective. With zero-sized items, `nValue(binOf)` also counts bins holding only those items; choose the definition your application needs.
- For identical bins, fixing the first item’s bin to 0 removes some label symmetry. Do not impose label symmetry when capacities or costs distinguish bins.

See the [modelling language reference](../../reference/modelling-language.md), [global constraints explanation](../../explanation/global-constraints.md), and [API reference](/kt-constraints/api/index.html) for the operators and their semantics.
