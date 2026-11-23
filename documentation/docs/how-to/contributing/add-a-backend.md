# Add a backend

Implement an engine adapter without changing solver-independent model semantics. Read the repository's `AGENTS.md`, `docs/ARCHITECTURE.md` and `ROADMAP.md` before introducing public types or build changes. Follow an existing adapter with the same transport: `solve-cpsat` for a JVM API, `solve-z3` for multiplatform bindings, or `solve-minizinc` for a compiler/process route.

## 1. Define the route and supported subset

Choose separate engine and route IDs. `SolverDescriptor(engine, route, capabilities)` derives `engine/route`; neither component may contain whitespace or `/`. A second transport for an existing engine needs a distinct route ID.

List only capabilities you will exercise through the shared contracts. Distinguish batch solving, incremental assertions and domain propagation. An integer backend need not accept arbitrary-precision values: it must check its exact representable subset and reject anything outside it. Preserve global nodes until backend lowering.

Use [Solvers and routes](../../reference/solvers-and-routes.md) and [Results and statuses](../../reference/results-and-statuses.md) to compare existing contracts.

## 2. Implement the SPI

Implement `SolverBackend` in `:solve-spi`:

| Member | Required behavior |
|---|---|
| `descriptor` | Stable engine/route identity and tested capabilities |
| `availability()` | Probe actual runtime availability; return `BackendAvailability(false, reason)` with a non-blank explanation when unavailable |
| `support(model, options)` | Assess all declared variables, assertions, objectives and requested options; return a `SupportReport` |
| `create()` | Create a fresh owned solver or throw `BackendUnavailableException` |

`isAvailable()` delegates to `availability()`. Capabilities, runtime availability and model support are separate checks. Support reports list both unsupported features and lossless decompositions; a non-empty `unsupported` list rejects the whole model. Do not silently skip unknown custom nodes or approximate values.

For an explicit compilation stage, implement `ConstraintCompiler<C : CompiledModel>` with `support` and `compile`. Compilation must fail with `UnsupportedModelException` before engine execution when semantics cannot be represented. Existing `BoundedIntegerSupport` can assess the Boolean/integer subset, exact ranges and accumulated magnitudes for a bounded adapter.

Keep processes, native libraries, JNI, C types and JS modules in platform source sets. Common code may contain immutable lowered programs, support analysis and portable decoding.

## 3. Implement execution and ownership

Implement `ConstraintSolver` with `solve`, `solutions` and idempotent `close`. Reuse `ManagedConstraintSolver` and `ManagedSolutionStream` when their ownership and cancellation model fits. A stream must emit solutions or improving incumbents, one completion event, then exhaustion. Close must release owned workers, processes, temporary workspaces and engine resources.

Decode exact values, validate complete assignments against the original model, then apply output projection. Test holes in domains, empty domains, empty projection, nested globals and partially defined expressions such as `Element`. A solution limit or timeout may preserve an incumbent but must never invent optimality or infeasibility. Supply a bound only with backend evidence.

If you advertise incremental assertions, return an `IncrementalConstraintSolver` and implement genuine `IncrementalSession` behavior: owned checkpoints, rollback, declarations, temporary assumptions and repeatable checks. Add a separate `ConstraintStore` only when its domain, entailment, undo, fork and evidence contracts are implemented. Re-solving does not constitute native propagation.

## 4. Reuse the conformance suites

Add a test dependency on `:test-solve` and register its specs from a Kotest 6 `FunSpec` named `<Subject>Spec`.

| Suite | Use |
|---|---|
| `solverContract(enumeration, create)` | SAT/UNSAT, independent assignment validation, objective proofs, projection, streams, limits, rejection and lifecycle |
| `differentialContract(prefix, enumeration, cases, create)` | Compare supported cases with the independent finite oracle; default `DifferentialCase.catalogue()` |
| `incrementalContract(unsatCores, create)` | Sessions, rollback, assumptions, scoped definedness, lifecycle and optional assumption cores |
| `storeContract(propagates, reals, create)` | Store posting, entailment, failure, rollback, domains, forks and evidence; default `reals = !propagates` |

Disable only explicitly unsupported capability cases, such as exhaustive enumeration on a MIP route. Add adapter-specific tests for numeric limits, runtime startup, error mapping and cancellation. Build fresh identity-bearing fixtures inside each leaf. Use Kotest assertions only; do not use `kotlin.test`, JUnit test APIs or nested `runTest`.

Make explicitly requested runtime tests fail if their engine is missing. Verify JVM, Node and available Native targets as applicable, plus Java/TypeScript/C consumers when their boundary changes. Follow [Run tests and benchmarks](run-tests-and-benchmarks.md).

## 5. Register deliberately and document availability

Registration is explicit: use `SolverRegistry.of(backends)` or add a proven route to the appropriate platform's `DefaultBackends.load()`. There is no classpath discovery. Duplicate route IDs are rejected. Registry ranking filters capabilities, availability and whole-model support without creating a solver; ties preserve registration order.

Keep any loaded JS/native runtime owner alive for the selected solver lifetime. Do not register test fakes or claim a route is usable solely because its module compiles. Add real-engine CI coverage and update availability documentation to match it. A backend implementation, a registry entry and a publication are distinct changes.

Only add a module to root `publishedModules` once its runtime works and shared contracts pass. Verify Kotlin, Java and TypeScript usage before reserving stable public APIs. See [Modules and artifacts](../../reference/modules-and-artifacts.md) and [Release](release.md).
