# :solve-minizinc

Experimental JVM and Linux/macOS Native execution of Gecode, Chuffed, CP-SAT, HiGHS and CBC through MiniZinc.
The checked compiler and JSON decoder are portable. The official JS runtime provides
Node/Gecode CLI and browser/Chuffed Wasm execution. Native Windows and iOS cannot start the executable, so
there `MiniZincBackend` reports itself unavailable.

```kotlin
val backend = MiniZincBackend(MiniZincEngine.CHUFFED, executable = "/path/to/minizinc")
val availability = backend.availability()
val support = backend.support(model)
backend.createSolver().use { solver ->
    val result = solver.solve(model)
}
```

`MiniZincGecodeBackend(executable)` remains source-compatible. Discovery uses the
selected engine's exact `--solvers-json` ID, followed by an actual startup probe;
configuration presence alone does not establish availability. No fallback is used.
Register backends explicitly with `SolverRegistry.of(...)` when selection is wanted.

| Engine | Route ID | Checked symmetric integer range | Complete satisfaction enumeration |
|---|---|---:|---|
| Gecode | `gecode/minizinc` | +/-2,147,483,646 | Yes |
| Chuffed | `chuffed/minizinc` | +/-499,999,999 | Yes |
| CP-SAT | `cp-sat/minizinc` | +/-2,147,483,646 | Yes |
| HiGHS | `highs/minizinc` | +/-1,000,000, affine arithmetic | No |
| CBC | `cbc/minizinc` | +/-1,000,000, affine arithmetic | No |

These are deliberately supported subsets, not complete engine limits. Every literal,
coefficient, domain and integer intermediate is checked. Chuffed's +/-500,000,000
values are reserved sentinels. MIP variable-by-variable products are rejected before
launch. MiniZinc handles flattening and global lowering; no independent FlatZinc parser
is required. All routes support the tested Boolean/integer nodes and CP globals.

Full returned assignments are independently checked before projection. Integer output
is parsed exactly; fractional output is rejected rather than rounded. HiGHS/CBC run
with zero relative/absolute objective gaps; optimality and infeasibility rely on the
numerical engine's proof evidence, rather than exact rational certificates. Their
streams produce a satisfaction witness or improving optimization incumbents, not all
satisfying assignments. Consumers must check `ENUMERATION` before requiring exhaustive
search. A validated MIP satisfaction witness and clean exit establish feasibility even
without a wire status. Other completion cases require a definitive status.

Malformed output, contradictory statuses and failed processes are errors. Stream close,
cancellation, timeout and limits retain the existing owned-process/workspace cleanup.
Messages and raw JSON statistics are retained on normal completion.

```bash
MINIZINC_EXECUTABLE=/path/to/minizinc \
  MINIZINC_ENGINES=gecode,chuffed,cp-sat,highs,cbc ./gradlew :solve-minizinc:jvmTest
MINIZINC_EXECUTABLE=/path/to/minizinc MINIZINC_BENCHMARK_ENGINE=chuffed \
  ./gradlew :solve-minizinc:jvmBenchmark
```

The default requested real runtime is Gecode. Other engine suites are explicitly skipped
unless requested by `MINIZINC_ENGINES`. Requested engines must pass; absence is a failure.
The two enumeration cases are explicitly disabled for each MIP engine. A dedicated
Ubuntu CI job downloads a checksum-pinned MiniZinc 2.10.1 bundle and requires all five.
Explicit runtime tests disable up-to-date/build-cache reuse.

The manual benchmark self-checks an exact optimum of 12, with a fresh native process
per sample. It separates model construction, translation, availability/setup, process
spawn, runtime wall, decode/validation and projection; native flatten/solve statistics
are reported independently. Raw observations are not regression thresholds or confidence
intervals. See [benchmarks](https://gciatto.github.io/kt-constraints/reference/benchmarks/).

## JavaScript execution

The pinned official npm runtime is 4.5.2. Its browser package contains Chuffed;
Node invokes an installed Gecode configuration through a native CLI.

```kotlin
// Kotlin/JS browser; serve the official package's three assets from your web server.
MiniZincJsBackend.loadBrowser(workerURL, wasmURL, dataURL).use { backend ->
    backend.createSolver().use { solver -> solver.solve(model) }
}
// Kotlin/JS on Node; executes a native CLI, not browser Wasm.
MiniZincJsBackend.loadNode("/path/to/minizinc").use { backend ->
    backend.createSolver().use { solver -> solver.solve(model) }
}
```

Factories suspend until initialization, discovery and a real startup solve succeed.
The routes are `chuffed/minizinc-wasm` and `gecode/minizinc-js-cli`. The backend owns
the upstream global pool and active streams; close it to release workers/processes.
Only one JS backend may own that pool at once. Backend close cancels active streams
and calls the upstream shutdown only after they exit (at most five seconds), because
its SIGKILL would otherwise preempt MiniZinc's own temporary-file cleanup; the next
load awaits that release. Individual solver close leaves the runtime available;
closing and reloading a backend is tested. A missing Node CLI fails loading with
`BackendUnavailableException`. Upstream limitation: a run cancelled before its CLI
process spawns leaves the SDK's small `mzn*` temporary directory behind.
In the browser the pool keeps one worker and each run first waits for it to finish loading,
so closing or cancelling never terminates a worker mid-load (which raised uncaught page errors). JS statistics inherit
the upstream API's Number semantics; integer assignments retain lossless string output.
The upstream event API has no backpressure, so large enumerations may buffer output;
consume or close streams promptly.

```bash
MINIZINC_EXECUTABLE=/path/to/minizinc ./gradlew :solve-minizinc:jsNodeTest
MINIZINC_BROWSER_TEST=true ./gradlew :solve-minizinc:jsBrowserTest
```

The browser task is explicitly enabled and uses ChromeHeadless (set `CHROME_BIN` when
Chrome is outside PATH). CI serves worker/Wasm/data from the pinned npm package and
runs actual shared contracts in Chrome. Runtime tasks require the transport contract
report, so a setup failure omitted by Kotest cannot silently pass. A missing requested
Node executable was verified to fail. Ordinary portable JS tests explicitly skip the
runtime spec when its environment is absent.

## Boundaries

This is Kotlin/JS invoking solver Wasm, not a Kotlin/Wasm or WASI target. Native Windows execution,
incremental sessions and native propagation remain planned.
