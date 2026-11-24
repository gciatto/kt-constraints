# Run tests and benchmarks

Run commands from the repository root with the checked-in Gradle wrapper. Use JDK 17 or 21 for JVM work; CI's main verification uses JDK 21 and JavaScript coverage uses Node 22. Native tests require a supported host/toolchain. Run only one Gradle build at a time.

## 1. Format, then check style

Run `./gradlew ktlintFormat` first. Once it finishes, run `./gradlew ktlintCheck detektAll --parallel --continue`. Do not run formatting and style checking concurrently. Warnings are errors; fix their cause rather than suppressing them to pass CI.

## 2. Run the relevant platforms

| Goal | Command |
|---|---|
| JVM and JS quick loop | `./gradlew jvmTest jsTest --parallel --continue` |
| Complete host-supported checks | `./gradlew check --parallel --continue` |
| Portfolio examples on JVM/Node | `./gradlew :solve-auto:jvmTest :solve-auto:jsNodeTest --continue` |
| Shared Native examples on Linux | `./gradlew :solve-auto:linuxX64Test` |
| Java boundary | `./gradlew :interop-java:test` |
| Packaged TypeScript boundary on Node | `./gradlew :interop-js:typescriptUsageTest` |
| C shared library consumer | `./gradlew :interop-c:nativeUsageTest` |

Use `macosArm64Test` or `mingwX64Test` on the corresponding Native host. Z3 Native target tests extract the pinned host library and set its path. Running an application yourself requires `KT_CONSTRAINTS_Z3_LIBRARY` or a library on the loader path.

Tests use Kotest 6 `FunSpec`, `<Subject>Spec` naming and Kotest assertions. Build independent scopes, sessions and solvers inside each leaf. Backend adapters must reuse the [shared conformance suites](add-a-backend.md#4-reuse-the-conformance-suites).

## 3. Request external runtimes explicitly

Install MiniZinc with the requested configurations, then run:

`MINIZINC_EXECUTABLE=/path/to/minizinc MINIZINC_ENGINES=gecode,chuffed,cp-sat,highs,cbc ./gradlew :solve-minizinc:jvmTest :solve-minizinc:jsNodeTest :interop-js:typescriptUsageTest --continue`

CI uses the checksum-pinned MiniZinc 2.10.1 bundle; a requested engine must execute rather than be silently skipped. Without the opt-in environment, portable tests do not establish real MiniZinc runtime coverage.

For browser tests, install Chrome and run the following separately. Set `CHROME_BIN` too if Chrome is outside PATH.

| Runtime | Command |
|---|---|
| MiniZinc Chuffed Wasm | `MINIZINC_BROWSER_TEST=true ./gradlew :solve-minizinc:jsBrowserTest` |
| Z3 Wasm | `Z3_BROWSER_TEST=true ./gradlew :solve-z3:jsBrowserTest` |
| TypeScript on both | `INTEROP_BROWSER_TEST=true ./gradlew :interop-js:typescriptBrowserUsageTest` |

The browser test harness configures runtime assets and cross-origin isolation. Passing Node tests alone does not verify browser deployment. See [Platform availability](../../reference/platform-availability.md) for tested environments.

## 4. Build documentation

Install the documentation dependency with `pip install mkdocs-material`. Run `./gradlew :documentation:assembleSite` to build the site and aggregated API docs.

For a page-only check without Gradle, change to `documentation` and run `mkdocs build --strict -d /tmp/site-$RANDOM`. The API HTML is copied in by Gradle and is not needed for this page/link/snippet check.

## 5. Measure the relevant phases

Benchmark tasks are manual, outside routine `check`. Use the same host, versions, scale and seeds for comparisons; keep raw observations and verify the expected answer. Separate construction, support/compilation, engine setup, solve, projection and checkpoint restoration rather than treating all elapsed time as search.

| Measurement | Command |
|---|---|
| Bundled JVM routes and automatic selection | `./gradlew :solve-auto:jvmBenchmark` |
| Choco store operations | `./gradlew :solve-choco:storeBenchmark` |
| Z3 store operations | `./gradlew :solve-z3:jvmStoreBenchmark` |
| MiniZinc phase breakdown | `MINIZINC_EXECUTABLE=/path/to/minizinc MINIZINC_BENCHMARK_ENGINE=gecode ./gradlew :solve-minizinc:jvmBenchmark` |
| Pure-Kotlin internal phases | `./gradlew :solve-kotlin:jvmBenchmark` |
| Seeded pure-Kotlin families | `./gradlew :solve-kotlin:jvmFamilyBenchmark` |
| Pure-Kotlin cross-platform catalogue | `KT_CONSTRAINTS_PLATFORM_BENCHMARK=1 ./gradlew :solve-kotlin:jsNodeTest -i` |
| Cross-platform route catalogue | `KT_CONSTRAINTS_ROUTE_BENCHMARK=1 ./gradlew :solve-auto:jsNodeTest -i` |

Use `jvmTest` in place of `jsNodeTest` for the JVM catalogue. The JS runner needs `-i` to show measurement rows. `BENCHMARK_SCALE` and `BENCHMARK_CSV` control the JVM portfolio catalogue. The route catalogue accepts `KT_CONSTRAINTS_ROUTE_BENCHMARK_SCALES`, `KT_CONSTRAINTS_ROUTE_BENCHMARK_SEEDS` and `KT_CONSTRAINTS_ROUTE_BENCHMARK_TIMEOUT`.

For Native timing, first run `./gradlew :solve-auto:linkReleaseTestLinuxX64`. Then run `solve-auto/build/bin/linuxX64/releaseTest/test.kexe` with `KT_CONSTRAINTS_ROUTE_BENCHMARK=1` and `KT_CONSTRAINTS_Z3_LIBRARY` pointing to the absolute path of `solve-z3/build/z3-native/linuxX64/libz3.so`. This library must already have been extracted by the Native Z3 setup. Use the corresponding release task/binary in `solve-kotlin` with `KT_CONSTRAINTS_PLATFORM_BENCHMARK=1` for that catalogue. Avoid comparing debug Native timing against release JVM/JS timing.

See [Benchmarks](../../reference/benchmarks.md) for exact recorded numbers, output columns, limits and selection evidence. Those observations are not regression thresholds or universal performance promises.
