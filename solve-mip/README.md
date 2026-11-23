# :solve-mip

SCIP (`scip/or-tools`) and HiGHS (`highs/or-tools`) on the JVM through OR-Tools' `MPSolver`, bundled by the
`com.google.ortools:ortools-java` 9.15 dependency for Linux, macOS and Windows. These are not the direct HiGHS
C API route, which stays reserved in `:solve-highs`.

- `MipLowering` (common code) lowers the Boolean/integer IR to an exact integer program: Booleans become 0/1
  variables, nested Boolean structure uses exact big-M reification over integer bounds, and all-different, tables,
  elements and domains with holes use declared lossless decompositions. Every literal, coefficient, domain bound
  and affine magnitude must stay within `10^6`; products need a fixed factor. Reals and bit-vectors are rejected.
- Each stream solves once with a zero relative gap, validates the rounded assignment exactly against the source
  model and evaluates the objective exactly. There is no enumeration. `OPTIMAL` and `INFEASIBLE` rest on the
  solver's numerical proof.
- SCIP is interruptible: close and cancellation stop the solve and join its thread.
- OR-Tools cannot interrupt HiGHS, so `OrToolsMipBackend.highs(limitMillis)` requires a per-solve limit. Close
  returns at once and the abandoned solve ends at that limit; hitting it reports `RESOURCE_LIMIT`. A time-limited
  HiGHS stop exposes no incumbent through OR-Tools, so it reports `UNKNOWN`.

```kotlin
OrToolsMipBackend.scip().create().use { solver ->
    val result = solver.solve(model)
}
```

See [the solvers and routes reference](https://gciatto.github.io/kt-constraints/reference/solvers-and-routes/).
