# Configuration

Execution preferences are separate from model constraints. A route may reject a model or option combination in its [support report](solvers-and-routes.md).

## SolveOptions

| Property | Type / default | Contract |
|---|---|---|
| `timeoutMillis` | `Long? = null` | Null means no caller time limit; non-negative values only; zero immediately times out |
| `solutionLimit` | `Int? = null` | Null means no count limit; otherwise strictly positive |
| `backendOptions` | `List<BackendOptions> = emptyList()` | Typed route tuning; `backend<T>()` reads the last matching instance |

`SolveOptions.DEFAULT` has these defaults. `options + tuning` replaces earlier entries of the same concrete tuning type. Limits can produce `FEASIBLE` with an incumbent or `UNKNOWN` without one; they do not establish optimality. See [Results and statuses](results-and-statuses.md).

## Backend settings

| API | Settings and defaults |
|---|---|
| `CpSatBackend(workers = 0, seed = null)` | Zero workers selects the engine default for optimization; satisfaction enumeration always uses one worker |
| `CpSatOptions` | `workers: Int? = null` (non-negative), `randomSeed: Int? = null`, `hints: Assignment = Assignment.empty()`; non-null settings override backend defaults; hints need not be consistent |
| `ChocoOptions` | `search: ChocoSearch? = null`; null retains default search |
| `MipOptions` | `threads: Int? = null` (positive when set); `relativeGap: Double? = null` (non-negative when set). A positive gap prevents reporting `OPTIMAL` |
| `OrToolsMipBackend.scip()` | Interruptible OR-Tools SCIP route |
| `OrToolsMipBackend.highs(solveLimitMillis)` | Required positive backend limit; OR-Tools cannot interrupt this engine call |
| `MiniZincOptions` | `randomSeed: Long? = null`, `threads: Int? = null` (positive when set), `freeSearch: Boolean = false`; applied by JVM routes; JS routes do not apply this tuning yet |
| `MiniZincBackend(engine, executable = "minizinc")` | Explicit engine configuration and executable path/name |
| `MiniZincJsBackend.loadNode(executable = "minizinc")` | Installed Gecode CLI |
| `MiniZincJsBackend.loadBrowser(workerURL, wasmURL, dataURL)` | Explicit absolute URLs of MiniZinc browser assets; Chuffed |
| `Z3NativeBackend(libraryPath = ...)` | Library path defaults to the environment/loader rule below |
| `KotlinBackend(configuration = KotlinConfiguration())` | Pure-Kotlin compiler and work limits; source checkout only |

`ChocoSearch` values: `DOM_OVER_WDEG`, `DOM_OVER_WDEG_REF`, `ACTIVITY_BASED`, `CONFLICT_HISTORY`, `FAILURE_RATE`, `FAILURE_LENGTH`, `MIN_DOMAIN_LOWER_BOUND`, `MIN_DOMAIN_UPPER_BOUND`, `INPUT_ORDER_LOWER_BOUND`, `INPUT_ORDER_UPPER_BOUND`.

`KotlinConfiguration` defaults: `maxBitWidth = 16_384`, `maxVariables = 1_000_000`, `maxClauses = 4_000_000`, `workLimit = null`. Size limits must be positive; a work limit may be zero. Work spans compilation and arithmetic checks as well as search. These are not a total-memory guarantee.

`MiniZincOptions.arguments(engine)` returns driver flags: seed `-r` (omitted for CBC), threads `-p`, and free search `-f`, which lets the engine ignore search annotations. Defaults produce no tuning flags.

`BackendOptions` is the common tuning marker. Each route reads its own implementation and ignores unrelated tuning, so one `SolveOptions` can tune several automatic candidates. For resource ownership and solver calls, see [Kotlin solver API](solver-api.md).

## Runtime environment

| Variable | Read by | Meaning |
|---|---|---|
| `KT_CONSTRAINTS_Z3_LIBRARY` | Native Z3 loader | Non-empty path to the Z3 shared library. Otherwise use `libz3.so` on Linux, `libz3.dll` on Windows, `libz3.dylib` on other configured desktop systems, through the platform loader path |
| `MINIZINC_EXECUTABLE` | JVM/Node `AutoBackend.load()` and runtime tests | Explicitly enables default MiniZinc registration and identifies the executable; constructing a backend directly uses its constructor argument |
| `MINIZINC_ENGINES` | JVM integration suites | Comma-separated requested engines: `gecode,chuffed,cp-sat,highs,cbc`; requested engines must run successfully |
| `MINIZINC_BROWSER_TEST` | MiniZinc browser test task | Presence enables real browser tests |
| `Z3_BROWSER_TEST` | Z3 browser test task | Presence enables real browser tests |
| `INTEROP_BROWSER_TEST` | TypeScript browser task | Presence enables packaged facade browser checks |
| `CHROME_BIN` | Browser runners | Chrome executable when outside PATH |

Native Z3 loads one library per process; changing paths after loading is rejected. Browser Z3 requires cross-origin isolation and its runtime assets; see [TypeScript API](typescript-api.md) and [Native Z3](../how-to/native-and-z3.md).

## Benchmark environment

| Variable(s) | Scope |
|---|---|
| `BENCHMARK_SCALE`, `BENCHMARK_CSV` | `:solve-auto` JVM catalogue scale (default 1) and optional CSV output file |
| `BENCHMARK_SCALES`, `BENCHMARK_SEEDS`, `BENCHMARK_FAMILIES` | Pure-Kotlin JVM family runner; defaults are scales `4 6`, 5 seeds, all families |
| `MINIZINC_BENCHMARK_ENGINE` | MiniZinc phase runner, default `gecode` |
| `STORE_BENCHMARK_QUEENS` | Store benchmark size |
| `KT_CONSTRAINTS_PLATFORM_BENCHMARK` | Opt in to the pure-Kotlin cross-platform catalogue |
| `KT_CONSTRAINTS_PLATFORM_BENCHMARK_SCALES`, `KT_CONSTRAINTS_PLATFORM_BENCHMARK_SEEDS` | Pure-Kotlin platform scales (default `4 6`) and seed count (default 3) |
| `KT_CONSTRAINTS_ROUTE_BENCHMARK` | Opt in to the `:solve-auto` cross-platform route catalogue |
| `KT_CONSTRAINTS_ROUTE_BENCHMARK_SCALES` | Scales, default `1 2 3` |
| `KT_CONSTRAINTS_ROUTE_BENCHMARK_SEEDS` | Seed count, default 3 |
| `KT_CONSTRAINTS_ROUTE_BENCHMARK_TIMEOUT` | Milliseconds, default 10000 |

See [Benchmarks](benchmarks.md) for measured phases and observations.

## Gradle properties

These describe this repository's build; they are not solver runtime environment variables. Pass an override as `-Pname=value` or use `ORG_GRADLE_PROJECT_name` in CI.

| Property | Checked-in value / purpose |
|---|---|
| `versionsFromCatalog` | `libs`, dependency/plugin version catalog |
| `ktTargetJvmDisable`, `ktTargetJsDisable` | `false`; target switches supplied by kt-mpp |
| `nodeVersion` | Empty; permits the build's default Node selection |
| `allWarningsAsErrors` | `true` |
| `ktCompilerArgsJvm` | JVM defaults enabled and Java release 17 |
| `ktCompilerArgsJs` | ES Long-as-BigInt compilation |
| `mochaTimeout` | `180s` |
| `kotlin.mpp.enableCInteropCommonization` | `true`, shared Native Z3 shim use |
| `kotlin.native.ignoreDisabledTargets` | `true` |
| `forceVersion` | Explicit version override used by release and dry-run commands |
| `otherMavenRepo`, `otherMavenUsername`, `otherMavenPassword` | GitHub Maven registry and credentials |
| `mavenCentralUsername`, `mavenCentralPassword` | Optional Central publisher credentials |
| `signingKey`, `signingPassword` | Optional signing material; no key skips signing locally |
| `npmRepo`, `npmOrganization`, `npmToken`, `npmDryRun` | Generic publication settings; release scripts use their own environment tokens |

The root `gradle.properties` also contains project, license, SCM and developer POM metadata. Credential values there are placeholders or empty; real credentials belong in environment-backed properties and CI secrets. See [Release](../how-to/contributing/release.md) for the script-specific npm tokens and repository variables.
