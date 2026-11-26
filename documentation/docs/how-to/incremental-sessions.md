# Use incremental sessions

## 1. Start from a satisfaction model

Choose a route with `INCREMENTAL_ASSERTIONS`; Z3 supports sessions on JVM, JS and Native.
Kotlin's `IncrementalConstraintSolver.openSession(model)` opens a session over the model's
variables and assertions. Java and TypeScript expose `solver.openSession(model)`.
Session checks are satisfiability queries; to optimize, pass an objective to `optimize` (section 4).

## 2. Add a reversible frame and check it

Take a checkpoint with `push()`, add trial assertions, and restore with `popTo(checkpoint)`.
This example temporarily contradicts `x >= 1`, then restores the original assertions.
Kotlin and TypeScript also check a temporary assumption and inspect its unsat core.

The Kotlin examples are complete helper functions. Call a `suspend` helper from a coroutine or another
suspending function. The shown imports also cover later Kotlin helpers on this page.

=== "Kotlin"

    ```kotlin
    --8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:imports"

    --8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:session"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:session"
    ```

=== "TypeScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-session.mts:session"
    ```

The Kotlin version uses `Z3Backend` and standard `check`; Java's `check` helper throws on
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

## 4. Re-optimize as the session changes

Routes with `INCREMENTAL_OPTIMIZATION` (the pure-Kotlin route and Z3) optimize the active assertions on demand:
`session.optimize(objective, assumptions, timeoutMillis)` in Kotlin and Java, and `minimize`, `maximize`,
`minimizeReal` and `maximizeReal` in TypeScript. The objective and the assumptions hold for that call only, so the
same session can be edited, checked and optimized again. The result is a `SolveResult` (TypeScript: `Outcome`) with
the meaning of a batch solve: `OPTIMAL` with the optimum, `FEASIBLE` with an incumbent and the bound proven so far
when a limit stops it, `INFEASIBLE`, `UNBOUNDED` or `UNKNOWN`.

=== "Kotlin"

    ```kotlin
    --8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:session-optimization"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:session-optimization"
    ```

=== "TypeScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-session.mts:session-optimization"
    ```

The pure-Kotlin route keeps its work between calls. Integer objectives over models with logic or globals are searched
on the session's own SAT engine, with every objective bound passed as an assumption, so learned clauses and compiled
definitions carry over to later checks and optimizations. Linear models, and every real objective, are optimized
exactly by branch and cut over the current assertions. Z3 optimizes a snapshot of the session with a fresh context
and confirms every claim, as batch solves do; it does not yet reuse the session's own context. A non-linear real
objective is rejected, as in batch solving.

