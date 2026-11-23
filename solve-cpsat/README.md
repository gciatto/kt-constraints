# :solve-cpsat

Direct OR-Tools CP-SAT route `cp-sat/direct` on the JVM, distinct from `cp-sat/minizinc`. It uses the
official `com.google.ortools:ortools-java` 9.15 API, which bundles native libraries for Linux
(x86-64, aarch64), macOS (x86-64, aarch64) and Windows (x86-64); availability is an actual native load
and startup solve. Other Kotlin targets have no backend in this module.

- Booleans and integers only; every integer subexpression and accumulated linear magnitude must stay
  within `2^61`, otherwise the whole model is reported unsupported before execution. Reals and
  bit-vectors are rejected.
- Top-level globals are native CP-SAT constraints; nested Boolean expressions are reified in both
  directions, nested all-different and table constraints use declared lossless decompositions.
- Satisfaction streams enumerate distinct projections on one worker. Optimization uses CP-SAT's search,
  emits strictly improving incumbents with exactly evaluated objectives, and reports `OPTIMAL` only on
  CP-SAT's proof. `CpSatBackend(workers, seed)` fixes optimization workers and the random seed.
- No incremental sessions or scheduling globals yet.

```kotlin
CpSatBackend().create().use { solver ->
    val result = solver.solve(model)
}
```

See [the solvers and routes reference](https://gciatto.github.io/kt-constraints/reference/solvers-and-routes/).
