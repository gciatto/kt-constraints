# Minimize the cost of an assignment

Assign each worker exactly one task and each task to exactly one worker, minimizing total cost. Use three workers with cost rows `[9, 2, 7]`, `[6, 4, 3]` and `[5, 8, 1]`, indexed by task.

## 1. Build the model

Use `:dsl` for `sum` and the global constraints; the arithmetic and comparison operators are members of the `:core` expressions. The examples solve with `AutoBackend` from `:solve-auto`, which picks a bundled solver for your platform. See [Install](../install.md) for the dependencies and [Choose a solver](../choose-a-solver.md) to pick one yourself.

Imports used by the example:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Assignment.kt:imports"
```

Store the assigned task index in one variable per worker. Select its cost with `element` and enforce distinct tasks with `allDifferent`.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Assignment.kt:model"
```

`element` uses a zero-based index: each task domain must stay within its cost row. With three workers and three possible distinct task values, every task is used once. The `IntLiteral` entries turn the fixed costs into symbolic operands; `costs[worker][task[worker]]` cannot index a Kotlin collection because the task is not yet a concrete integer.

## 2. Solve and read the result

Minimize the sum of the selected costs and require `OPTIMAL`. The resulting task vector is in worker order: worker 0 takes task 1, worker 1 takes task 0, and worker 2 takes task 2.

See [Optimize](../optimize.md) for handling a result that has an assignment without a proof of optimality.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Assignment.kt:solve"
```

Call the helper from a suspending caller with the sample data:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/AssignmentSpec.kt:example"
```

Expected output:

```text
Tasks by worker: [1, 0, 2]; cost: 9
```

The solve call suspends; run it in a coroutine. See [Results and statuses](../../reference/results-and-statuses.md) before treating a result as a proof.

## Variations

- With more tasks than workers, widen the task domains and keep `allDifferent`; some tasks remain unused.
- Remove a forbidden task from a worker’s domain, or add a symbolic inequality for it.
- A Boolean worker-by-task matrix is an alternative: use `exactly(1, ...)` on each row and column and minimize the sum of cost times `toInt()` of each decision.
- If tasks have capacities rather than unique ownership, replace `allDifferent` with counts, using `globalCardinality` and bounded count variables.

See the [modelling language reference](../../reference/modelling-language.md), [global constraints explanation](../../explanation/global-constraints.md), and [API reference](/kt-constraints/api/index.html) for the operators and their semantics.
