# Choose a solver

## 1. Choose the engine and execution route

Start with the [route reference](../reference/solvers-and-routes.md) and
[platform availability](../reference/platform-availability.md).

- Use Z3 for arbitrary-precision integer literals, exact linear reals, bit-vectors or incremental sessions.
- On the JVM, consider CP-SAT or Choco for finite-domain combinatorial models. Use Choco's
  store when you need native domain propagation.
- For bounded affine integer models, consider SCIP or HiGHS through OR-Tools.
- Use [MiniZinc](use-minizinc.md) when you want one of its installed engine configurations.

Choose a route as well as an engine: `cp-sat/direct` and `cp-sat/minizinc` have different
runtime requirements and support checks. Do not choose solely by a capability name;
numeric ranges and individual operations can still make the model unsupported.

## 2. Check availability and support

For automatic batch selection, use `AutoBackend` from `io.github.gciatto.ktconstraints.solve.auto`
like any other backend: `AutoBackend.load()` registers the platform's default routes, and its
solvers rank eligible ones and try them under a shared time budget. Inspect the result's
`descriptor.id` and `metadata.messages` to see who answered and why routes were skipped.
On JS, asynchronous runtime initialization happens before ranking and can throw; handle
loading failures as well as returned result statuses.

To control the candidate set or require a capability, use an explicit registry. This JVM
example requires enumeration before selecting a backend:

The Kotlin examples are complete helper functions. Call a `suspend` helper from a coroutine or another
suspending function. The shown imports also cover later Kotlin helpers on this page.

```kotlin
--8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:imports"

--8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:selection"
```

`selection.rejections` gives reasons for rejected routes. `selection.best` fails if no
candidate qualifies. `availability()` probes the runtime; `support(model, options)` checks
the whole model and requested options, and reports any lossless decompositions.
Pass the same options during selection and execution.

Java can use the same backend checks before wrapping the selected backend in
`BlockingSolver.of(...)`. In TypeScript, load a route with `loadZ3`, `loadMiniZincNode` or
`loadMiniZincBrowser`; inspect its `id` and `capabilities`. Loading and solving reject on
runtime or support failures; the facade does not expose the Kotlin registry.

## 3. Decide whether to try multiple routes

Build an `AutoBackend` over the routes you want and use it like any other backend.
`AutoBackend(listOf(...))`, or `AutoBackend.load()` for the routes bundled with your platform,
defaults to `AutoStrategy.Fallback`: it tries ranked candidates sequentially, moving on after
`UNKNOWN` or failure, but returns an available feasible result. With `AutoStrategy.Race(parallelism)`
it runs several candidates concurrently; a proof stops the others. A race can retain a feasible
optimization incumbent without calling it optimal. Pass a `RankingPolicy` to change the order.

See [automatic selection](../explanation/backends/selection.md) for ranking and
evidence rules. Neither a missing runtime nor exhausted fallback attempts proves infeasibility.
