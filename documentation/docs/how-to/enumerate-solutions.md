# Enumerate solutions

## 1. Use a solver with enumeration support

`AutoBackend.load().create()` can enumerate directly: `solutions(model)` delegates to the best
eligible route declaring `SolverCapability.ENUMERATION`. The complete Kotlin example below
includes model construction and resource cleanup. To choose a specific route instead, require
that capability when [selecting a backend](choose-a-solver.md); TypeScript callers inspect
the backend’s `capabilities`. A stream API alone does not promise exhaustive enumeration.
Optimization streams can report improving incumbents; mixed-integer programming (MIP) routes
may return only one witness or incumbent after solving.

## 2. Consume solution events and the completion event

Use a satisfaction model with finite output domains when you need a finite list.
Java and TypeScript use Z3; Kotlin uses `AutoBackend`. The Kotlin version projects onto `x`, so the two full assignments
with `x = 0` produce one output; its results are `0` and `1`.
Java and TypeScript enumerate an unconstrained variable over `0..2`, producing three outputs.

The Kotlin examples are complete helper functions. Call a `suspend` helper from a coroutine or another
suspending function. The shown imports also cover later Kotlin helpers on this page.

=== "Kotlin"

    ```kotlin
    --8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:imports"

    --8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:enumerate"
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
TypeScript imports `ModelBuilder` and `loadZ3` from the
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
