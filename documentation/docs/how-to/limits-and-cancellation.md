# Set limits and cancel a search

## Set the execution budget

Pass `SolveOptions(timeoutMillis = ..., solutionLimit = ...)` to Kotlin batch APIs.
Timeouts are non-negative milliseconds; a solution limit must be positive. Omitted values
mean no limit requested through that option. Check whole-model support with the same options:
some routes require a finite budget or restrict limits.

A zero budget must not produce a feasibility claim:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/ModelStylesSpec.kt:limits"
```

For Java, pass `new SolveOptions(1_000L, null)` to `solve` or `solveAsync`;
see the [tested asynchronous optimization example](optimize.md).
For TypeScript, the positional arguments are `solve(model, timeoutMillis?, solutionLimit?, signal?)`.
Session checks accept a per-check timeout, rather than batch `SolveOptions`.

Read both the result status and termination reason. A timeout with an incumbent may be
`FEASIBLE` with `TIMEOUT`; it is not an optimality proof. Without a witness or proof,
expect `UNKNOWN`. See [results and statuses](../reference/results-and-statuses.md).

## Cancel work owned by your application

=== "Kotlin"

    Cancel the coroutine running `solve` or `check`, or explicitly cancel an open stream.
    Keep the solver and stream in `use` blocks. This JVM example stops after its first event:

    ```kotlin
    --8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasksSpec.kt:cancel"
    ```

=== "Java"

    Cancel the `CompletableFuture` returned by `solveAsync` with `cancel(true)`:

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:cancel"
    ```

    This example cancels a pigeonhole refutation immediately. Its `check` helper throws
    on a wrong result.
    Closing `BlockingSolver` cancels its pending futures and closes the solver.
    For blocking calls, interrupt the calling thread. Close `BlockingSolutionStream`
    when leaving iteration early; try-with-resources handles this on exceptions too.

=== "TypeScript"

    Supply an `AbortSignal`. Aborting rejects the solve Promise; handle the rejection.
    Here the signal is already aborted, so the example does not depend on search timing:

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-cancel.mts:cancel"
    ```

    The example runs inside a loaded Z3 backend with a solver created by `backend.create()`;
    both are closed in its enclosing `finally` block. For a running search, call
    `controller.abort()` from your cancel button or another application event.

A Java future wait timeout does not itself cancel that future: use the solver's time
budget or cancel explicitly. TypeScript `Promise.race` likewise does not stop its losing
search. When stopping a stream early, close it rather than simply abandoning it.

See [enumeration](enumerate-solutions.md) for completion events and
[configuration](../reference/configuration.md) for backend-specific tuning.
