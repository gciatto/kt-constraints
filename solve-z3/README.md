# :solve-z3

Direct Z3 routes, distinct from every MiniZinc route:

- `z3/direct` on the JVM: the official Java API with Linux, macOS and Windows native
  libraries bundled by `tools.aqua:z3-turnkey` 4.14.0.
- `z3/wasm` on Kotlin/JS: the official `z3-solver` 5.3.3 threaded Wasm module, verified on Node
  and in headless Chrome. Browser pages must load `z3-built.js` as a script (it defines
  `initZ3`), serve `z3-built.wasm` beside it, and be cross-origin isolated (COOP `same-origin`,
  COEP `require-corp`) for `SharedArrayBuffer`; see `karma.config.d/z3.js`.
- `z3/native` on Kotlin/Native: the Z3 C API, loaded at run time from `KT_CONSTRAINTS_Z3_LIBRARY`
  (else `libz3.so` / `libz3.dylib` / `libz3.dll` on the loader path). Nothing links against Z3,
  so a missing or incompatible library makes the backend report itself unavailable with the
  loader's reason. Tests use the Linux x64, macOS arm64 and Windows x64 libraries of the same
  z3-turnkey artifact as the JVM; iOS compiles but has no provisioned library.

Both share one translation, stream and session implementation behind an internal platform port.

```kotlin
Z3Backend().createSolver().use { solver ->
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
- Linear reals are Z3's `Real`, with exact `Rational` literals and model values; a model value
  that is not a rational numeral fails as a backend error instead of being rounded.
- Bit-vectors are Z3's fixed-width `BitVec` with SMT-LIB semantics; values are exact unsigned
  integers of any width.
- Supports every current Boolean/integer IR node; globals use lossless decompositions listed
  in the support report. Element indices must be in range, as on every other route.
- Optimization gallops beyond each incumbent, then bisects, with tentative bounds in pushed
  frames; optimality is claimed only once the next better score is refuted (UNSAT). Domains
  are finite, so objectives are never unbounded.
- Enumeration blocks each projected output assignment: no duplicate projections.
- Every returned assignment is independently validated before projection.
- Each stream and session owns one Z3 context on a dedicated thread. Deadlines and
  cancellation interrupt native checks; `unknown` is never reported as unsatisfiable.
- Sessions: checkpoints belong to one session and become stale once popped; assumptions hold
  for one check. Capabilities: incremental assertions, backtracking, assumptions and unsat
  cores. Repeated checks are not domain propagation or entailment.

From common code, `Z3.load()` loads whichever of these routes the running platform has; close the returned
backend to release its runtime:

```kotlin
Z3.load().use { z3 -> z3.withSolver { solver -> solver.solve(model) } }
```

```kotlin
// Kotlin/JS: owns the module's worker threads; close it to terminate them.
Z3JsBackend.load().use { backend -> backend.createSolver().use { solver -> solver.solve(model) } }
```

```kotlin
// Kotlin/Native: pass the library path explicitly or set KT_CONSTRAINTS_Z3_LIBRARY.
Z3NativeBackend("/opt/z3/lib/libz3.so").createSolver().use { solver -> solver.solve(model) }
```

```bash
./gradlew :solve-z3:jvmTest :solve-z3:jsNodeTest :solve-z3:linuxX64Test   # or macosArm64Test / mingwX64Test
Z3_BROWSER_TEST=true ./gradlew :solve-z3:jsBrowserTest   # set CHROME_BIN when Chrome is outside PATH
```
