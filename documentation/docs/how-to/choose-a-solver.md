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

For automatic batch selection, call `solveAuto(model)` from
`io.github.gciatto.ktconstraints.solve.portfolio`. It loads the platform's default routes,
ranks eligible ones and tries them under a shared time budget. Inspect the result's
`descriptor.id` and `metadata.messages` to see who answered and why routes were skipped.
On JS, asynchronous runtime initialization happens before ranking and can throw; handle
loading failures as well as returned result statuses.

To control the candidate set or require a capability, use an explicit registry. This JVM
example requires enumeration before selecting a backend:

```kotlin
--8<-- "solve-portfolio/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/howto/SolverTasksSpec.kt:selection"
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

Use `Portfolio(registry).fallback(...)` to try ranked candidates sequentially. It moves
on after `UNKNOWN` or failure, but returns an available feasible result. Use
`Portfolio(registry).race(...)` to run several candidates concurrently, subject to
`parallelism`; a proof stops the others. A race can retain a feasible optimization
incumbent without calling it optimal.

See [automatic selection](../explanation/automatic-solver-selection.md) for ranking and
evidence rules. Neither a missing runtime nor exhausted fallback attempts proves infeasibility.
