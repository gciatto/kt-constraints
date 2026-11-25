# Pure Kotlin

## What it is

This engine combines a finite arithmetic compiler, Boolean satisfiability search and an exact rational linear feasibility checker in common Kotlin code. It needs no external solver runtime, but still depends on project modules, arbitrary-precision arithmetic and coroutines. See [the pure-Kotlin solver](../pure-kotlin-solver.md) for its implementation and limitations.

## Routes and classes

`KotlinBackend` provides `kotlin/direct` in the published `:solve-kotlin` module (`kt-constraints-solve-kotlin`), on every platform. TypeScript loads it with `loadKotlin()`. It is registered by `AutoBackend.load()` on every platform at its benchmarked position: first on JS and Native, and after CP-SAT and Choco on the JVM. Routes after it answer the models it rejects.

## Platforms and runtime requirements

Production code is in `commonMain` for JVM, JS and Native, with no solver executable, JNI library or solver Wasm binary. Existing measurements cover JVM Linux, Node and Linux x64 Native. Common source does not establish browser packaging or test coverage on every target; see [platform availability](../../reference/platform-availability.md). There is no published TypeScript loader for this engine.

## Capabilities

The descriptor declares batch satisfaction, integer optimization, exhaustive projected satisfaction enumeration, incremental assertions, backtracking, assumptions and unsatisfiable cores. Its incremental solver can be wrapped in the generic `SatisfiabilityStore`; this does not expose native domain propagation. Capability declarations remain subject to model support and compilation/work limits.

## Supported modelling features and numeric limits

The route accepts finite arbitrary-precision integers, Boolean formulas, exact rational linear real feasibility, integer-to-real embedding, bit-vectors and built-in globals through native compiler forms or exact decomposition. Integer objectives may include bounded nonlinear expressions. Real objectives over linear and mixed-integer programs are optimized exactly by its simplex and branch and bound. It does not offer unrestricted integer search or nonlinear real arithmetic.

There is no fixed machine-integer magnitude margin. Encoding width, compiler size and work limits can still stop a structurally supported model. The [internals page](../pure-kotlin-solver.md) describes the supported fragment and search costs in detail.

## Options type

There is no Kotlin-specific `BackendOptions` type. Construct `KotlinBackend` with `KotlinConfiguration`: defaults are `maxBitWidth = 16_384`, `maxVariables = 1_000_000`, `maxClauses = 4_000_000`, and `workLimit = null`. Size limits must be positive; work limit may be zero. Work includes compilation and arithmetic checks as well as search; these settings do not guarantee a total memory bound. Use `SolveOptions` for execution limits. See [configuration](../../reference/configuration.md).

## Performance

The recorded catalogue often favors this route over Z3 on Node and Native, and on small JVM models. Hard scheduling, larger arithmetic circuits and growing exact rationals can still be expensive. Measurements do not justify silently adding it to defaults. See [benchmarks](../../reference/benchmarks.md) for numbers, release Native settings and missing workload families.

## How to use it directly

[Choose a solver](../../how-to/choose-a-solver.md) explains explicit registration and support checks; [incremental sessions](../../how-to/incremental-sessions.md) explains the shared session API with tested examples. For Kotlin-specific construction and usage boundaries, follow the tested-source links on [the internals page](../pure-kotlin-solver.md).
