# :solve-minizinc

Experimental MiniZinc/Gecode route. The compiler and JSON decoder are portable; CLI execution is JVM-only.
The module remains unpublished pending packaging decisions; there is no JS/Native engine implementation yet.

`MiniZincGecodeBackend(executable)` discovers Gecode, probes a real solve, reports whole-model compatibility
and creates a closeable solver. Engine/route identity is `gecode/minizinc`. Install MiniZinc with Gecode and
pass its executable path, or use `minizinc` from PATH. No shell interpolation or model-name interpolation is used.

The supported IR includes finite integer/Boolean operations, all-different, zero-based element, allowed-tuple
tables, weighted sums and Boolean cardinality. Integer expressions are conservatively range-checked against
[Gecode's integer limits](https://www.gecode.org/doc-latest/reference/gecode_2int_8hh_source).
An unprovable range is rejected instead of truncated. MiniZinc handles flattening and global lowering.

The JSON stream is parsed with full source assignments independently validated before projection. Messages
and raw JSON statistics are retained at successful process completion. Early close, cancellation, owned deadlines
and solution limits release child processes, executors and temporary files. Incomplete runs never claim optimality.

Run real conformance tests with `MINIZINC_EXECUTABLE=/path/to/minizinc ./gradlew :solve-minizinc:jvmTest`.
Without that variable the real-runtime spec is explicitly skipped; pure compiler/protocol tests still run.
CI installs MiniZinc/Gecode in a dedicated Ubuntu job. `:solve-minizinc:jvmBenchmark` reports raw phase samples
and native-reported flatten/solve times; it is excluded from routine verification.
