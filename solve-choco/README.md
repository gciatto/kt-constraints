# :solve-choco

Direct Choco route `choco/direct` on the JVM, using the pure-Java `org.choco-solver:choco-solver` 6.0
API (Java 17). Availability is an actual startup solve. Other Kotlin targets have no backend in this module.

- Booleans and integers only; every literal, coefficient, domain bound, subexpression interval and
  accumulated linear magnitude must stay within `10^7` (inside Choco's `IntVar.MAX_INT_BOUND`),
  otherwise the whole model is reported unsupported before execution. Reals and bit-vectors are rejected.
- Each Boolean expression is one native Choco constraint (`scalar`, `sum`, `allDifferent`, `table`,
  clauses); nested ones, globals included, are reified by Choco itself, so no decomposition is declared.
  Products and elements use auxiliary variables; element index ranges are enforced at top level.
- Every stream owns its own Choco model. Each read resumes the search for one more solution, so
  enumeration is lazy. Optimization emits strictly improving incumbents with exactly evaluated
  objectives and reports `OPTIMAL` only when the search ends without being stopped.
- Close, coroutine cancellation and deadlines stop an in-flight search; a stopped search never
  reports infeasibility.
- No propagating or incremental sessions yet.

```kotlin
ChocoBackend().createSolver().use { solver ->
    val result = solver.solve(model)
}
```

See [the solvers and routes reference](https://gciatto.github.io/kt-constraints/reference/solvers-and-routes/).
