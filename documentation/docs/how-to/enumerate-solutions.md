# Enumerate solutions

## 1. Use a route with enumeration support

Require `SolverCapability.ENUMERATION` when [selecting a Kotlin backend](choose-a-solver.md),
or inspect the TypeScript backend's `capabilities`. A stream API alone does not promise
exhaustive enumeration: optimization streams report improving incumbents, and MIP routes
may return a satisfaction witness without enumerating every assignment.

## 2. Consume solution events and the completion event

Use a satisfaction model with finite output domains when you need a finite list.
These examples use Z3. The Kotlin version projects onto `x`, so the two full assignments
with `x = 0` produce one output; its results are `0` and `1`.
Java and TypeScript enumerate an unconstrained variable over `0..2`, producing three outputs.

=== "Kotlin"

    ```kotlin
    --8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasksSpec.kt:enumerate"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:enumerate"
    ```

=== "TypeScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-enumerate.mts:enumerate"
    ```

Kotlin imports the stream event types from `io.github.gciatto.ktconstraints.solve.stream`.
The Kotlin assertions use Kotest. TypeScript imports `ModelBuilder` and `loadZ3` from the
[installed package](install.md); its example runs inside a solver lifetime enclosed by
`try/finally`, which closes both solver and backend.

`SolutionFound` carries an assignment. `SolveCompleted` carries the final result;
Kotlin `next()` returns `null` after that event. Java exposes the same distinction through
an iterator. TypeScript events carry either `solution` or the final `outcome`.
A completion event can describe timeout or cancellation, so inspect its termination reason
before treating the outputs as exhaustive. Solution order is not specified here.

## 3. Stop early when you have enough results

Pass a positive solution limit, or close the stream. Kotlin and Java `use`/try-with-resources
close unfinished streams. Leaving TypeScript `for await` with `break` closes the search.
An early stop does not establish that all solutions have been found. See
[limits and cancellation](limits-and-cancellation.md).

To deduplicate outputs, set the model projection before building it: Kotlin/Java use
`scope.project(...)`. The TypeScript facade currently does not expose model projection.
Constraints involving hidden variables still apply. See [model representation](../reference/modelling-language.md).
