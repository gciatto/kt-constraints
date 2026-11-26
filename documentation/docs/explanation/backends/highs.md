# HiGHS C API and Wasm

`:solve-highs` (artifact `kt-constraints-solve-highs`) provides two direct HiGHS routes. They share exact
`LinearProgram` lowering and answer validation, while execution uses platform bindings.

| Route | Entry point | Runtime |
|---|---|---|
| `highs/native` | `HighsNativeBackend(libraryPath)` | HiGHS shared library loaded through its C API |
| `highs/wasm` | `HighsJsBackend.load()` | Asynchronously initialized `highs` npm Wasm module |

These differ from [HiGHS through OR-Tools](or-tools-mip.md) on JVM and [HiGHS through MiniZinc](minizinc.md).
Kotlin/JS can load the Wasm backend directly; the TypeScript facade has no HiGHS loader.

## Models and evidence

Both descriptors declare `SATISFACTION`, `OPTIMIZATION`, `INTEGER_ARITHMETIC`, `REAL_ARITHMETIC` and
`FLOATING_POINT_REALS`. They accept conjunctions of affine relations and integer or real objectives, without
reified relations, globals, bit-vectors, variable products or integer domains with holes. They offer neither
exhaustive enumeration nor incremental sessions or stores. Streams expose at most one returned incumbent and
then completion, rather than live improvements from HiGHS.

Real models require explicit `SolveOptions.numericTolerance`. Without it they are rejected, including during
automatic selection. Integer-only programs can run without this consent and must satisfy their rows exactly
after decoding. Strict real inequalities are rejected. Numerical bounds and proof statuses come from HiGHS;
these routes do not return rational proof certificates. See [solve LP and MILP models](../../how-to/linear-programs.md)
for exact examples, tolerance consent and result interpretation.

## Runtime and limits

Native uses `KT_CONSTRAINTS_HIGHS_LIBRARY`, an explicit constructor path, or the platform loader name
(`libhighs.so`, `libhighs.dylib`, `libhighs.dll`). Availability loads the library; it does not install one.
Only one HiGHS library path can be loaded per process. Native execution runs on an IO thread and uses HiGHS
interrupt callbacks for cancellation.

The Wasm solve occupies the JavaScript thread synchronously and cannot be interrupted during execution.
`SolveOptions.timeoutMillis` supplies the engine time limit; use it to bound that call. Cancellation or close
cannot preempt a running JS solve. A null timeout imposes no caller limit. Neither direct route has the mandatory
constructor limit of the OR-Tools HiGHS wrapper. Both direct routes set relative and absolute MIP gaps to zero.

Automatic defaults try these routes after Kotlin and before Z3 on JS and Native. JS loading omits a module that
fails initialization. Runtime availability and model support still apply separately. See [selection](selection.md),
[configuration](../../reference/configuration.md), [platform coverage](../../reference/platform-availability.md)
and [recorded benchmarks](../../reference/benchmarks.md).
