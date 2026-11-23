# Use incremental sessions

## 1. Start from a satisfaction model

Choose a route with `INCREMENTAL_ASSERTIONS`; Z3 supports sessions on JVM, JS and Native.
Kotlin's `IncrementalConstraintSolver.openSession(model)` opens a session over the model's
variables and assertions. Java and TypeScript expose `solver.openSession(model)`.
Use a satisfaction model: session checks are satisfiability queries, not optimization runs.

## 2. Add a reversible frame and check it

Take a checkpoint with `push()`, add trial assertions, and restore with `popTo(checkpoint)`.
This example temporarily contradicts `x >= 1`, then restores the original assertions.
Kotlin and TypeScript also check a temporary assumption and inspect its unsat core.

=== "Kotlin"

    ```kotlin
    --8<-- "solve-portfolio/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/howto/SolverTasksSpec.kt:session"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:session"
    ```

=== "TypeScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-session.mts:session"
    ```

The Kotlin version uses `Z3Backend` and Kotest assertions; Java's `check` helper throws on
a wrong result. TypeScript imports `ModelBuilder`, `constant` and `loadZ3` from the
[installed package](install.md), and runs the snippet inside a created solver's lifetime.
Close the outer solver and loaded JS backend as well as the session.

## 3. Interpret the check and restore safely

`CheckStatus` is `SATISFIABLE`, `UNSATISFIABLE` or `UNKNOWN`. An assignment is available
for a satisfiable check. Assumptions apply to that check only; they are not posted assertions.
On supporting routes, an unsat core is a subset of those assumptions, not necessarily a
minimal subset. Keep their expression objects if you need to map the core to application labels.

A checkpoint belongs to one session. Restoring it invalidates that checkpoint and all
newer ones. Do not reuse stale checkpoints or transfer them to another session.
Variables introduced with `declare` in a frame are forgotten when that frame is popped.
Await each check before starting another operation on the same session.

Set per-check `timeoutMillis` and use [cancellation](limits-and-cancellation.md) for
interactive workloads. If you need domain inspection and application-owned search,
use a [constraint store](constraint-stores.md). Repeated session checks do not constitute
native domain propagation. See [solving modes](../explanation/solving-modes.md).
