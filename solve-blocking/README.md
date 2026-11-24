# :solve-blocking

Blocking wrappers over the suspending solver API, for Kotlin code that is not a coroutine and for Java (which gets
this module through `:interop-java`). Targets: JVM and Kotlin/Native. JavaScript has no blocking threads; use the
suspending API or the Promise-based `:interop-js` there.

- `BlockingSolver` owns one `ConstraintSolver`: `solve`, `solutions` (a `BlockingSolutionStream`) and `openSession`
  (a `BlockingSession`, or `UnsupportedOperationException` for routes without incremental sessions).
- `BlockingSolutionStream` is an `Iterator<SolutionEvent>` and `AutoCloseable`: solutions, then one `SolveCompleted`.
- `BlockingSession` adds, pushes, pops and checks with optional assumptions and timeout.
- `BlockingStore` owns one `ConstraintStore`: declarations, `constrain`, `mark`/`undoTo`, `entails`, `explain`, `fork`.
- On the JVM, `solveAsync`, `checkAsync`, `constrainAsync` and `entailsAsync` return `CompletableFuture`s; cancelling
  one cancels its work, and closing the owner cancels pending futures.

Kotlin entry points are `SolverBackend.withBlockingSolver { }`, `ConstraintSolver.blocking()` and
`ConstraintStore.blocking()`; Java uses `BlockingSolver.of(backend)`, `BlockingSolver.of(solver)` and
`BlockingStore.of(store)`. A `BlockingSolver` never closes the backend it was created from: close it yourself.

```kotlin
loadAutoBackend().use { backend -> // JVM; on Native, e.g. Z3NativeBackend()
    backend.withBlockingSolver { solver ->
        solver.solutions(model).use { stream ->
            for (event in stream) println(event)
        }
    }
}
```

```java
try (AutoBackend backend = AutoBackends.load();
     BlockingSolver solver = BlockingSolver.of(backend);
     BlockingSolutionStream stream = solver.solutions(model)) {
    while (stream.hasNext()) System.out.println(stream.next());
}
```

Tests in `src/commonTest` run the wrappers over the pure-Kotlin solver on the JVM and Native; the Java boundary is
tested in `:interop-java`.
