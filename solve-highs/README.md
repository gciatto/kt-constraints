# :solve-highs

Direct HiGHS routes for linear and mixed-integer linear programs, beyond the JVM's `highs/or-tools`:

| Route | Backend | Platform | Runtime |
|---|---|---|---|
| `highs/native` | `HighsNativeBackend(libraryPath)` | Kotlin/Native | HiGHS C library loaded at run time from `KT_CONSTRAINTS_HIGHS_LIBRARY`, else `libhighs.so`/`.dylib`/`.dll` on the loader path |
| `highs/wasm` | `HighsJsBackend.load()` | JavaScript (Node and browsers) | the `highs` npm module (HiGHS compiled to WebAssembly) |

Both take the linear fragment of a model (see `:solve-linear`) and solve it in floating point. Models with reals
need `SolveOptions.numericTolerance`, and strict real inequalities are rejected. Every answer is read back exactly:
integer variables are rounded only within the integrality tolerance, and an answer exceeding a bound or constraint
beyond the feasibility tolerance (exactly zero for integer-only models) is an error.

The C library is not bundled. The CI pins HiGHS 1.16.0 from Julia's `HiGHS_jll` builds, with `libblastrampoline`
beside it. The Wasm route solves on the JavaScript thread: it stops at its time limit, not on cancellation.
