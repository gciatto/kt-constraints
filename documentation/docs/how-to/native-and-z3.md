# Run on Kotlin/Native with Z3

## 1. Add the Native dependencies

In your Kotlin Multiplatform project, add `kt-constraints-dsl` and
`kt-constraints-solve-auto` to `commonMain`, or use `kt-constraints-solve-z3` for
explicit backend creation. Gradle selects the Native klibs; do not link against the C
facade to call Kotlin APIs. See [installation](install.md).

The exercised desktop routes are Linux x64, macOS arm64 and Windows x64.
iOS targets compile, but no Z3 runtime is provisioned by this project for iOS;
compilation alone does not establish runtime availability. See
[platform evidence](../reference/platform-availability.md).

## 2. Supply the shared library

Install a compatible Z3 library for the process architecture. Set its absolute path before
starting your program, for example on Linux:

```sh
export KT_CONSTRAINTS_Z3_LIBRARY=/opt/z3/lib/libz3.so
```

Without the variable, the loader tries the platform name `libz3.so`, `libz3.dylib` or
`libz3.dll`. The Windows loader must also find the library's dependent DLLs.
Alternatively, pass the path to `Z3NativeBackend(path)` in `nativeMain`.
Z3 is loaded at run time rather than linked into the executable.

## 3. Run your common model

Use the [tested common optimization example](optimize.md). Its Kotlin code calls
`AutoBackend.load()`, whose Native registry contains `Z3NativeBackend`, route `z3/native`.
Inspect `result.descriptor.id` to identify the answering route. Keep the call in a suspend
context; use your application's coroutine entry point.

For explicit route control, construct `Z3NativeBackend` from
`io.github.gciatto.ktconstraints.solve.z3` in a Native source set. Check `availability()`
and `support(model, options)` before creating the solver, and keep the solver in `use`.
Optional follow-up operations: an incremental session keeps assertions between satisfiability checks;
a store lets your application post constraints and undo branches. The API supports batch solving, enumeration and
[incremental sessions](incremental-sessions.md). Use
`SatisfiabilityStore.of(backend.create())` for satisfiability-based
[stores](constraint-stores.md); these do not propagate narrower domains.

## 4. Diagnose a missing runtime

Read `availability.reason`: it includes library-loading or startup-probe failures.
Check the absolute path, architecture and dependent libraries. A successful load must also
pass the startup check. An unavailable route is not evidence that the model is infeasible;
automatic selection throws `UnsupportedModelException` when no route is eligible. If eligible
attempts produce no answer, it returns `UNKNOWN` with diagnostic messages.

In this repository, run the Native tests with the same environment:

```sh
./gradlew :solve-z3:linuxX64Test :solve-auto:linuxX64Test
```

Use `macosArm64Test` or `mingwX64Test` on the corresponding host. The JVM's `z3-turnkey`
packaging and JS Wasm loader are different routes; configuring them does not configure
Native library loading. C consumers use the same shared-library environment variable;
follow the [C tutorial](../tutorials/c-assignment.md) for generated handles and ownership.
