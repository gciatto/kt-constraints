# Kotlin solver API

A model describes the values and relationships you want. A **solver** searches for values satisfying those relationships. A **backend** creates solvers for one engine and execution route. Solver and result contracts live in `:solve`; backend, availability, support, registry and ranking contracts live in `:solve-spi` (the service provider interface for adapters).

Use this page to choose an operation and manage its lifetime. See [Modelling language](modelling-language.md) for model construction, [Results and statuses](results-and-statuses.md) for evidence, and the [generated API](/kt-constraints/api/index.html) for exhaustive signatures and overloads.

## Batch solving and streams

| API | When to use it and what it does |
|---|---|
| `SolverBackend.create()` | Create a fresh `ConstraintSolver`. The caller should first check availability and support for the intended model and options; `create()` takes no model |
| `ConstraintSolver.descriptor` | Identify the solver's engine, route and advertised capabilities |
| `solve(model, options)` | Suspending call returning `SolveResult`. Satisfaction returns the first witness; optimization returns the final result |
| `solutions(model, options)` | Open a `SolutionStream` for multiple solutions or improving optimization candidates |
| `ConstraintSolver.close()` | Release the solver's resources; close owned searches when finished |
| `SolutionStream.next()` | Suspending, single-consumer read of a `SolutionEvent`; concurrent reads are rejected |
| `SolutionFound.assignment`, `objectiveValue` | A projected solution and its optional exact integer objective. An optimization candidate is an **incumbent**: the best feasible answer found so far |
| `SolveCompleted.result` | The stream's single definitive completion result; subsequent reads return null |
| `SolutionStream.terminalResult` | Null until completion or cancellation, then the definitive result, even before its event is consumed |
| `SolutionStream.cancel()` / `close()` | Stop unfinished work and release resources; repeated calls are safe and preserve a definitive result |

Closing an automatic solver closes its open enumeration streams. An automatic batch `solve` in
progress is stopped by cancelling its coroutine, using structured concurrency (child work belongs
to the calling coroutine), rather than by `close()`.

Managed batch satisfaction returns a new first-witness result without the stream’s completion
diagnostics or statistics. Consume `solutions` through `SolveCompleted` when you need that metadata.

A stream is single-pass. Close it if you stop reading early. Satisfaction enumeration distinguishes assignments by the model's output projection, the ordered variables included in public answers. Exhaustive enumeration needs `SolverCapability.ENUMERATION`; a stream alone does not promise it. See [Enumerate solutions](../how-to/enumerate-solutions.md).

`SolveOptions` supplies `timeoutMillis`, `solutionLimit` and `backendOptions`. `DEFAULT` supplies no caller limits or tuning; `plus` replaces tuning of the same concrete type, and `backend<T>()` retrieves the last matching type. A `BackendOptions` implementation tunes its own route; other routes ignore it. Consult [Configuration](configuration.md) for every field, default and route-specific setting.

`SolveResult` carries `status`, nullable `assignment`, `descriptor`, `terminationReason`, nullable `objectiveValue` and `bestBound`, and `metadata`. `SolveMetadata` copies diagnostic `messages` and backend-defined string `statistics`. Read the [status and termination tables](results-and-statuses.md) together: reaching a limit does not prove infeasibility or optimality.

## Assignments

`Assignment` is a read-only `Valuation`, meaning typed bindings from variables to values. Access uses variable identity, so an identically named variable from another scope does not retrieve a value.

| Member | Meaning |
|---|---|
| `valueOf(BoolVar)` | Boolean or null when unassigned |
| `valueOf(IntVar)` | Exact kt-math `BigInteger` or null |
| `valueOf(RealVar)` | Exact `Rational` or null |
| `valueOf(BitVecVar)` | Unsigned `BigInteger` within the variable's width, or null |
| `intValueOf`, `longValueOf` | Nullable machine integer extraction; throw `ArithmeticException` on overflow |
| `Assignment.of(booleans, integers, reals, bitVectors)` | Copy typed maps; reject integer values outside domains and bit-vector values outside widths |
| `Assignment.empty()` | Assignment with no bindings, useful for empty projections or absent hints |
| `Assignment.project(variables)` | Extension copying only the requested variables; rejects any missing value |

A projected assignment may omit variables needed to evaluate a compound expression. Use `GroundEvaluator.evaluate(expression, valuation)` only when the valuation contains every required binding; see [Evaluation](modelling-language.md#evaluation-and-traversal).

## Incremental sessions

An **incremental session** keeps assertions between checks. A **frame** groups changes that can be undone together. This is repeated satisfiability checking, separate from batch optimization and from domain propagation (removing impossible values).

`IncrementalConstraintSolver` extends `ConstraintSolver`. Its `openSession(model)` puts the model's constraints and variables in the root frame; closing the solver closes its sessions. Session checks ask for satisfiability even when the source model has an objective.

| `IncrementalSession` member | Contract |
|---|---|
| `descriptor` | Engine and route executing the session |
| `declare(variable)` | Add a variable and its domain to the current frame and output projection; reject already declared variables and unsupported sorts |
| `add(constraint)` | Assert in the current frame; foreign variables and unsupported nodes leave the session unchanged |
| `push()` | Open a frame and return an opaque `Checkpoint` for the state before that frame's changes |
| `popTo(checkpoint)` | Remove that frame and all newer frames, including their variables; invalidate those checkpoints |
| `check(assumptions, timeoutMillis)` | Suspending satisfiability query; assumptions hold for this call only; defaults are an empty list and no timeout |
| `close()` | Idempotent resource release |

A checkpoint belongs to one session; stale or foreign checkpoints are errors. Use one consumer per session. `CheckResult` reports `CheckStatus`, assignment, descriptor, termination, metadata and optional assumption `unsatCore`. The core is a sufficient subset, not necessarily the smallest one; see [Results and statuses](results-and-statuses.md#incremental-checkresult) and [Use incremental sessions](../how-to/incremental-sessions.md).

## Constraint stores

A `ConstraintStore` is a mutable logical state for an external logic engine. It owns its variables, posted constraints and reversible marks. A **domain** is the set of allowed values for a variable; **entailment** asks whether all current solutions satisfy an expression.

| Member | Contract |
|---|---|
| `descriptor`, `isFailed` | Route and whether inconsistency has been established |
| `boolVar(name)`, `intVar(name, domain)`, `realVar(name)` | Declare owned variables; exact real declarations require route support |
| `constrain(constraint, timeoutMillis)` | Post an assertion and return `Propagation` with `status` and `changed` variables whose domains shrank; failed posts report no changes |
| `mark()` | Save constraints, declarations and domains in an opaque `StoreMark` |
| `undoTo(mark)` | Restore the mark and invalidate it and newer marks; reject stale or foreign marks |
| `entails(constraint, timeoutMillis)` | Return `EntailmentResult(value, evidence)` without posting the query |
| `domain(IntVar)` | Current integer domain; remains the declared domain when the store does not propagate |
| `project(variables)` | Return a sound `Residual` describing requested variables |
| `explain(timeoutMillis)` | Null before failure; after failure, a sufficient inconsistent set of posted constraints with declared domains, not necessarily minimal |
| `fork()` | Independent store with the same variables, assertions and failure state, without the parent's marks; requires replaying assertions |
| `close()` | Idempotent resource release; close forks separately |

A failed store remains failed until rollback restores a state before the failure. Stores have one consumer. `Residual.domains` maps requested integer variables to current domains; `constraints` retains relevant posted expressions. Check `exact`: when false, the residual can mention other variables and can be weaker than the exact projection. It is not a guarantee of variable elimination.

`StoreStatus` distinguishes proven `SATISFIABLE`, propagation-only `CONSISTENT`, proven `FAILED` and `UNKNOWN`. `Entailment` distinguishes `ENTAILED`, `DISENTAILED`, proven `UNDETERMINED` and `UNKNOWN`; `Evidence` distinguishes `SATISFIABILITY` checks from `PROPAGATION`. See [Results and statuses](results-and-statuses.md#store-evidence).

`ChocoStore` narrows finite integer domains using propagation. Its propagation runs to a fixpoint (no further reduction is available from its propagators), and its timeout arguments are not applied. `SatisfiabilityStore.of(incrementalSolver)` takes ownership of an incremental solver and checks satisfiability after posting; its domains stay as declared. It shares solver ownership with its forks, closing the solver when the last store releases it. See [Use constraint stores](../how-to/constraint-stores.md).


In `SatisfiabilityStore`, `constrain` posts and records the constraint before checking it. An
`UNKNOWN` result leaves that constraint active; retrying does not undo it. Take a mark before
a tentative post, inspect its status, and use `undoTo(mark)` to remove it if you abandon the branch.

See [tentative posts and rollback](../how-to/constraint-stores.md#3-handle-failure-and-uncertain-answers) for a tested example.

## Backends, capabilities and availability

A route can be installed yet reject a particular model. Conversely, declaring a capability does not establish that its runtime is installed.

| Type/member | Meaning |
|---|---|
| `SolverDescriptor.engine`, `route`, `id` | Stable identifiers; `id` joins engine and route with `/`. Components must be non-blank, contain no whitespace and contain no slash |
| `SolverDescriptor.capabilities` | Detached capability set in enum declaration order |
| `SolverBackend.descriptor` | Adapter metadata |
| `availability()` | `BackendAvailability(available, reason)` runtime probe; an unavailable result requires a non-blank reason |
| `isAvailable()` | Boolean shorthand for the probe |
| `support(model, options)` | Whole-model `SupportReport`, including options and numeric range checks |
| `SupportReport.unsupported`, `isSupported` | Any rejection makes `isSupported` false |
| `SupportReport.losslessDecompositions` | Required translations into equivalent constraints; these do not reject the model |
| `create()` | Fresh solver, failing explicitly if the runtime cannot start |

| `SolverCapability` | Operation or construct advertised |
|---|---|
| `SATISFACTION`, `OPTIMIZATION` | Find a witness; optimize an integer objective |
| `ENUMERATION` | Enumerate distinct projected satisfaction assignments; finite projections can be exhausted, while real outputs may yield an endless stream of distinct rational witnesses |
| `INCREMENTAL_ASSERTIONS`, `BACKTRACKING` | Keep assertions between calls; restore earlier state |
| `DOMAIN_PROPAGATION`, `ENTAILMENT` | Narrow domains; answer logical implication queries |
| `ASSUMPTIONS`, `UNSAT_CORES` | Temporary check assertions; sufficient unsatisfiable assumption subsets |
| `INTEGER_ARITHMETIC`, `REAL_ARITHMETIC`, `BITVECTORS` | Numeric expression families; whole-model support still applies |
| `GLOBAL_CONSTRAINTS` | Structured constraints such as all-different; native handling or exact lowering depends on the route |

See [Solvers and routes](solvers-and-routes.md) for actual descriptors and numeric subsets and [Platform availability](platform-availability.md) for supported execution environments.

## Registry and automatic solving

`SolverRegistry.of(vararg backends)` or `of(list)` creates an immutable, explicitly registered set; duplicate route IDs are rejected. `empty()` registers nothing. `require(id)` retrieves a registered backend or fails for an unknown ID.

| Operation/result | Meaning |
|---|---|
| `rank(model, options, requiredCapabilities, policy, platform)` | Filter by model-required plus caller-required capabilities, then availability, then model support; rank survivors without creating solvers |
| `select(...)` | Return the best backend from the same selection process |
| `Selection.features` | Extracted `ModelFeatures` used for ranking |
| `Selection.candidates` | Eligible `Candidate`s, highest weighted score first; ties preserve registration order |
| `Selection.rejections` | Reasons keyed by rejected engine/route ID |
| `Selection.best` | First candidate; throws `NoCompatibleBackendException` when none survives |
| `Candidate.backend`, `contributions`, `total`, `explanation` | Selected route, per-heuristic scores, weighted sum and readable diagnostic explanation |

`Platform(runtime, os)` describes the selection environment; `Platform.current()` detects it. `Runtime` values are `JVM` (including Android), `NODE`, `BROWSER` and `NATIVE`; `os` identifies an operating-system family such as `linux` or `browser`. A platform value used for ranking does not install a runtime.

`AutoBackend(backends, strategy, policy, platform)` wraps explicit routes. `AutoBackend.load(strategy, policy)` loads platform defaults; its `registry` exposes the registered routes, `strategy` describes execution, and `ENGINE` is `auto`. Close the backend to release closeable loaded routes as well as closing created solvers.

| `AutoStrategy` | Batch behavior |
|---|---|
| `Fallback` (default) | Try ranked routes sequentially under one deadline; proceed after `UNKNOWN` or failure |
| `Race(parallelism = 2)` | Run at most this positive number of routes at once under one deadline; the first proof wins and cancels the rest; otherwise retain the best incumbent without claiming optimality |

The strategy's `route` is `fallback` or `race`. The backend advertises the union of its routes' batch capabilities. Successful answers identify the answering route; an empty eligible set throws `UnsupportedModelException`. If eligible attempts produce no answer, the result is `UNKNOWN` under `auto/fallback` or `auto/race`. Metadata lists skipped and failed routes. `solutions` uses the best eligible enumerating route. Sessions and stores require concrete routes.

For tuning ranking, extracting features and implementing backends, see [Backend SPI](spi.md). `ExperimentalKtConstraintsApi` is an error-level opt-in annotation for APIs whose shape may change without a deprecation cycle, including benchmark-driven ranking; callers must opt in deliberately.
