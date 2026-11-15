# :solve-z3

Direct Z3 route `z3/direct` on the JVM, using the official Java API bundled with native
libraries for Linux, macOS and Windows by `tools.aqua:z3-turnkey`. It is distinct from
every MiniZinc route. JS/Wasm and Kotlin/Native bindings are planned; publication stays disabled.

```kotlin
Z3Backend().create().use { solver ->
    val result = solver.solve(model)                 // batch: satisfy, optimize, enumerate
    solver.openSession(model).use { session ->       // incremental
        val checkpoint = session.push()
        session.add(constraint)
        val check = session.check(assumptions = listOf(guess))
        check.unsatCore                              // subset of the assumptions, when UNSAT
        session.popTo(checkpoint)
    }
}
```

- Integers are Z3's unbounded `Int`: literals of any size are exact; there is no range limit.
- Supports every current Boolean/integer IR node; globals use lossless decompositions listed
  in the support report. Element indices must be in range, as on every other route.
- Optimization is an improving search with strict bounds; only a final UNSAT check proves
  optimality, so unbounded objectives yield incumbents at the deadline, never `UNBOUNDED`.
- Enumeration blocks each projected output assignment: no duplicate projections.
- Every returned assignment is independently validated before projection.
- Each stream and session owns one Z3 context on a dedicated thread. Deadlines and
  cancellation interrupt native checks; `unknown` is never reported as unsatisfiable.
- Sessions: checkpoints belong to one session and become stale once popped; assumptions hold
  for one check. Capabilities: incremental assertions, backtracking, assumptions and unsat
  cores. Repeated checks are not domain propagation or entailment.

```bash
./gradlew :solve-z3:jvmTest
```
