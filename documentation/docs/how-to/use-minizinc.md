# Use MiniZinc

## 1. Install an engine configuration

On the JVM or Node, install the MiniZinc bundle with the solver you want to execute.
Check it with:

```sh
minizinc --solvers-json
```

On the JVM, add `kt-constraints-solve-minizinc` and select `MiniZincEngine.GECODE`,
`CHUFFED`, `CP_SAT`, `HIGHS` or `CBC`. The engine determines the route and checked numeric
subset. Check [the route reference](../reference/solvers-and-routes.md) for capabilities.
Do not infer availability from the presence of a configuration: the backend also runs a startup probe.

## 2. Probe and solve on the JVM

Set `MINIZINC_EXECUTABLE` to the CLI's absolute path, or ensure `minizinc` is on PATH.
This compiled example reports availability separately from whole-model support and only
executes an available, supported route:

The Kotlin examples are complete helper functions. Call a `suspend` helper from a coroutine or another
suspending function. The shown imports also cover later Kotlin helpers on this page.

```kotlin
--8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:imports"

--8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:minizinc"
```

For application diagnostics, read `availability.reason` when unavailable and
`support.unsupported` when unsupported. The backend does not silently switch engines. Java can wrap this same backend
in `BlockingSolver.of(backend)` as in [the programming-style guide](programming-styles.md).

To include MiniZinc routes in JVM `AutoBackend.load()`, set `MINIZINC_EXECUTABLE` before starting
the process. For a fixed candidate list, register the selected backend explicitly with
`SolverRegistry.of(...)`.

## 3. Select the JS transport when needed

On Kotlin/JS, await `MiniZincJsBackend.loadNode(executable)` for Node/Gecode, or
`MiniZincJsBackend.loadBrowser(workerURL, wasmURL, dataURL)` for browser/Chuffed.
TypeScript exposes the same loaders as `loadMiniZincNode` and `loadMiniZincBrowser` from
the [npm facade](install.md). Pass absolute asset URLs to the browser loader.

The JS routes are `gecode/minizinc-js-cli` and `chuffed/minizinc-wasm`. Node runs a
native executable; the browser uses worker/Wasm/data assets from the MiniZinc runtime.
See the browser setup below for MiniZinc assets and
[TypeScript configuration](../reference/typescript-api.md) for loader signatures.
Only one MiniZinc JS backend may own the runtime pool at a time. Close solvers, then the
loaded backend; await a fresh load before reusing the runtime after shutdown.

## 4. Check the result and release resources

Use the [batch and stream APIs](programming-styles.md). MiniZinc routes do not expose
incremental sessions or propagating stores. HiGHS/CBC streams are not exhaustive
satisfaction enumeration; require the enumeration capability when you need every solution.
Consume or close streams promptly, especially on JS where upstream output can buffer.

Integer ranges and intermediate expressions are checked before launch. Reals and bit-vectors
are rejected by these routes. Inspect the support report rather than reducing values to fit.
For HiGHS/CBC, proof statuses rely on the numerical engine's evidence, not exact rational
certificates. Malformed output or a failed process is an error, not an infeasibility proof.
MiniZinc execution is not implemented on Kotlin/Native; use [Z3 Native](native-and-z3.md).

## Browser runtime assets

Install the [TypeScript facade](install.md) in your browser application and bundle your entry point
as an ES module. Copy `minizinc-worker.js`, `minizinc.wasm` and `minizinc.data` from the
`minizinc/dist` directory of the installed runtime into the directory served with your page.
The repository’s `prepareTypescriptBrowserUsage` task copies exactly these files.
Serve the page and assets over HTTP with cross-origin isolation; the
[browser tutorial](../tutorials/browser-scheduling.md) describes the server headers. Its Z3
asset names do not replace MiniZinc’s three files.

This setup comes from the browser boundary test. The import uses the repository’s local package
name; installed applications use `@gciatto/kt-constraints-interop-js`.

```typescript
--8<-- "interop-js/src/test/typescript/browser.mts:imports"

--8<-- "interop-js/src/test/typescript/browser.mts:minizinc-browser"
```

The worker loads from a blob, so pass absolute URLs. The loaded route is `chuffed/minizinc-wasm`.
Create a solver with `miniZinc.createSolver()`, solve your model in `try/finally`, then close the solver
and `miniZinc`. The boundary test exercises this loader and cleanup in Chromium.
