# Optimize an objective

## 1. Set one integer objective

Call `minimize(expression)` or `maximize(expression)` on the model builder.
These methods replace any previous objective; they do not create a multi-objective model.
Use `satisfy()` to clear it. Real expressions can constrain the model, but objectives
use integer expressions.

This example maximizes `x` subject to `x + y = 3` and `x, y` in `0..3`.
Kotlin uses automatic selection; Java and TypeScript use Z3. The Java example also shows
how to consume `solveAsync` through `CompletionStage`.

=== "Kotlin"

    ```kotlin
    --8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/ModelStylesSpec.kt:optimize"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:optimize"
    ```

=== "TypeScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-optimize.mts:optimize"
    ```

Kotlin imports `eq` and `plus` from the DSL, and `solveAuto` from the portfolio package.
Its assertions use Kotest. Java's `check` helper throws `AssertionError` on failure.
TypeScript imports `ModelBuilder`, `constant` and `loadZ3` from the
[installed package](install.md); its snippet runs inside a created solver's lifetime,
with solver and backend closed in `finally`.

## 2. Distinguish an incumbent from a proof

Read `objectiveValue` and `status` in Kotlin/Java, or `objective` and `status` in
TypeScript. `OPTIMAL` establishes the optimum. `FEASIBLE` gives an incumbent only;
a time or solution limit can stop search before proof. A supplied `bestBound` records
the backend's proven bound; do not substitute an absent bound with the incumbent value.
See [results](../reference/results-and-statuses.md) and [limits](limits-and-cancellation.md).

For improving incumbents while optimization runs, consume
[solution events](enumerate-solutions.md) instead of waiting for the final batch result.

## Minimize weighted preference violations

Keep mandatory constraints in `add`. Put preferences in `minimizeViolations` with
non-negative weights. Each false preference contributes its weight to the integer penalty.
This replaces the objective and does not assert the preferences as hard constraints.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/ModelStylesSpec.kt:soft"
```

Here choosing `x = 1` violates only the preference of weight 1, so the optimum penalty is 1.
Import `minimizeViolations` from the Kotlin DSL. The TypeScript builder offers
`minimizeViolations(constraints, weights)` with parallel expression and `bigint` weight arrays.
For a larger model, adapt [knapsack](problems/knapsack.md) or [nurse rostering](problems/nurse-rostering.md).
