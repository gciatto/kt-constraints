# Results and statuses

An **incumbent** is a feasible answer found so far; see [optimization](../how-to/optimize.md).
**Projection** selects which variable values appear in the answer; see [modelling language](modelling-language.md).

Batch `SolveResult` and incremental `CheckResult` separate mathematical status from the reason execution stopped. A timeout or engine failure is never evidence of infeasibility.

An **objective** is the expression to minimize or maximize.

## SolveResult

| Field | Type | Meaning |
|---|---|---|
| `status` | `SolveStatus` | Mathematical search outcome |
| `assignment` | `Assignment?` | Feasible incumbent; present exactly for `FEASIBLE` and `OPTIMAL` |
| `descriptor` | `SolverDescriptor` | Answering engine and route |
| `terminationReason` | `TerminationReason` | Execution completion or interruption reason; default `COMPLETED` |
| `objectiveValue` | `BigInteger?` | Exact incumbent integer objective; requires an assignment |
| `bestBound` | `BigInteger?` | Exact proven objective bound when supplied; may be absent |
| `metadata` | `SolveMetadata` | Immutable messages and backend-defined statistics |

`Assignment.valueOf(variable)` returns the typed value or null when absent from the projection. Integers and bit-vector values use `BigInteger`; a **bit-vector** is a fixed-width sequence of bits.
Real values use `Rational`, an exact fraction of two integers. `intValueOf` and `longValueOf` throw on overflow rather than truncate. Pass the original variable object to select its entry: another variable with the same name has a different identity.

| SolveStatus | Interpretation |
|---|---|
| `FEASIBLE` | A feasible assignment was found; optimization optimality is not established |
| `OPTIMAL` | An integer objective is proved optimal; `objectiveValue` is required |
| `INFEASIBLE` | No feasible assignment exists |
| `UNBOUNDED` | Unboundedness reported with engine evidence |
| `INFEASIBLE_OR_UNBOUNDED` | Engine evidence does not distinguish these outcomes |
| `UNKNOWN` | No decisive answer and no feasible incumbent |

The status enum covers outcomes beyond what every current model or backend can produce. Satisfaction normally returns `FEASIBLE`, not `OPTIMAL`. An optimal result's provided bound must equal its objective. Bounds are lower bounds for minimization and upper bounds for maximization; absence of a bound does not imply any particular gap.

Only `FEASIBLE` and `UNKNOWN` may have a termination other than `COMPLETED`. A limited optimization keeps a found incumbent as `FEASIBLE`; it does not promote it to `OPTIMAL`.

## TerminationReason

| Value | Cause |
|---|---|
| `COMPLETED` | Requested search completed with the reported evidence |
| `TIMEOUT` | Time budget expired |
| `CANCELLED` | Caller cancelled |
| `INTERRUPTED` | Environment interrupted execution |
| `RESOURCE_LIMIT` | Solution, memory, node or other resource limit stopped execution |
| `SOLVER_FAILURE` | Engine or transport failed |

Coroutine cancellation may propagate as cancellation rather than return a result. Java future cancellation and TypeScript `AbortSignal` cancellation also cancel pending operations. Consult [Limits and cancellation](../how-to/limits-and-cancellation.md) for the calling API.

Managed batch satisfaction stops at the first witness and constructs a result without the stream’s
completion diagnostics or statistics. Consume `solutions` through `SolveCompleted` to obtain those.

`SolveMetadata.messages` retains diagnostics. `statistics` maps backend-defined names to strings retaining raw JSON lexemes; it is not a portable typed statistics schema.

## Streams

`SolutionStream.next()` returns `SolutionFound` events, then one `SolveCompleted`, then null. A solution event carries the projected assignment and optional objective. The completion event carries a `SolveResult`; `terminalResult` exposes it once known. Close the stream when stopping early.

Satisfaction enumeration is distinct by output projection. Optimization streams report improving incumbents. Exhaustive satisfaction enumeration requires the route's `ENUMERATION` capability. See [Enumerate solutions](../how-to/enumerate-solutions.md).

## Incremental CheckResult

Advanced reference: a **session** retains assertions between checks; **assumptions** are assertions used only
for one check. Read [solving modes](../explanation/solving-modes.md) and
[use incremental sessions](../how-to/incremental-sessions.md) before using this API.

| Field | Meaning |
|---|---|
| `status` | `SATISFIABLE`, `UNSATISFIABLE` or `UNKNOWN` (`CheckStatus`) |
| `assignment` | Present exactly for `SATISFIABLE`; projects session variables |
| `descriptor` | Answering engine and route |
| `terminationReason` | Same termination enum as batch results |
| `unsatCore` | Optional sufficient subset of the current check's assumptions; only on `UNSATISFIABLE` |
| `metadata` | Messages and statistics |

Satisfiable and unsatisfiable checks require `COMPLETED`. A core need not be minimal. Null means no core was supplied; it differs from an empty core. Assumptions apply only to that check. Session checks are satisfiability queries, even if the original model carries an optimization objective. See [Incremental sessions](../how-to/incremental-sessions.md).

## Store evidence

Advanced reference: a **store** retains reversible constraints; **propagation** removes impossible domain values,
and **entailment** asks whether the constraints force an expression to hold. Read
[solving modes](../explanation/solving-modes.md) and [use constraint stores](../how-to/constraint-stores.md).

Stores return `Propagation` with `StoreStatus` (`SATISFIABLE`, `CONSISTENT`, `FAILED`, `UNKNOWN`) and changed variables. Entailment returns `EntailmentResult` with `Entailment` (`ENTAILED`, `DISENTAILED`, `UNDETERMINED`, `UNKNOWN`) and `Evidence`:

| Evidence | Meaning |
|---|---|
| `SATISFIABILITY` | Complete satisfiability checks establish definite answers |
| `PROPAGATION` | Native propagation establishes sound definite answers; weak propagation may leave `UNKNOWN` |

`SATISFIABLE` records a proof of satisfiability; `CONSISTENT` records a propagation fixpoint—a state where the propagators cannot remove further values—without a satisfiability proof. `UNDETERMINED` proves that some store solutions satisfy the expression and some do not. `UNKNOWN` means the engine could not decide; it does not establish either the expression or its negation. A failed store is known inconsistent. See [Constraint stores](../how-to/constraint-stores.md).


For `Assignment.of`, `empty`, projection and every typed accessor, see [Assignments](solver-api.md#assignments). For `SolutionEvent`, stream cancellation, sessions and store members, see [Kotlin solver API](solver-api.md). Exhaustive declarations are in the [generated API](/kt-constraints/api/index.html).
