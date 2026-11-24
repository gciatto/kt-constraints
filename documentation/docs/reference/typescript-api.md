# TypeScript API

`:interop-js` exports an ES module and generated `kt-constraints-interop-js.d.mts` declarations. The released GitHub Packages name is `@gciatto/kt-constraints-interop-js`; repository consumer tests import the local package as `kt-constraints-interop-js`. The facade uses JavaScript `bigint`, promises and async iteration.

## Models and expressions

| Surface | Members |
|---|---|
| `new ModelBuilder()` | `intVar(name, min, max)`, `boolVar(name)`, `realVar(name)`, `add`, `minimize`, `maximize`, `minimizeViolations`, `satisfy`, `build` |
| `IntExpression` | `plus`, `minus`, `times`, `negate`, `eq`, `neq`, `lt`, `le`, `gt`, `ge` |
| `BoolExpression` | `and`, `or`, `implies`, `iff`, `not` |
| `RealExpression` | `plus`, `minus`, `scale(factorText)`, `negate`, `eq`, `neq`, `lt`, `le`, `gt`, `ge` |
| Constant/conversion functions | `constant(bigint)`, `real(text)`, `toReal(integerExpression)`, `toInt(booleanExpression)` |
| `Model` | Immutable snapshot returned by `build()` |

Integer bounds, constants and weights are `bigint`; integer results do not round through `number`. Real constants and scale factors use exact integer, fraction or decimal strings. `minimizeViolations(constraints, weights)` takes parallel arrays with non-negative `bigint` weights, replaces the objective, and returns the integer penalty expression.

Builder names must be unique, and expressions may only reference that builder's variables. Objectives are integers. There is no exported bit-vector facade, arbitrary projection setter, constraint metadata builder or allowed-tuple `Table` wrapper.

| Function group | Exported names and argument shape |
|---|---|
| Arithmetic helpers | `sum(terms)`, `min(operands)`, `max(operands)`, `abs(operand)` |
| Distinctness and selection | `allDifferent(operands)`, `element(index, values)` |
| Boolean cardinality | `atMost(count, operands)`, `atLeast(count, operands)`, `exactly(count, operands)`; count is a machine number |
| Integer counting | `count(value, operands)`, `among(values, operands)`, `globalCardinality(operands, values, counts)`, `nValue(operands)` |
| Ordering | `increasing(operands)`, `decreasing(operands)`, `lexLess(left, right)`, `lexLessOrEqual(left, right)` |
| Permutations and cycles | `inverse(direct, inverse)`, `circuit(successors)`, `subCircuit(successors)` |
| Automata | `new Automaton(initial, accepting).transition(from, symbol, to)`; `regular(sequence, automaton)` |
| Scheduling | `cumulative(starts, durations, demands, capacity)`, `disjunctive(starts, durations)` |
| Packing | `binPacking(bins, sizes, loads)`, `noOverlap2D(xs, ys, widths, heights)` |

Collections are arrays; integer sizes, symbols and capacities use `bigint`. State IDs are machine numbers. See [Modelling language](modelling-language.md) for shared semantics; note the TypeScript `regular` argument order differs from Kotlin's DSL.

## Backend loading and ownership

| Loader | Returned promise resolves to |
|---|---|
| `loadZ3()` | `Backend` for `z3/wasm` in Node or browsers |
| `loadMiniZincNode(executable?)` | `Backend` for installed Gecode, `gecode/minizinc-js-cli`; default executable `minizinc` |
| `loadMiniZincBrowser(workerURL, wasmURL, dataURL)` | `Backend` for Chuffed, `chuffed/minizinc-wasm`; asset URLs must be absolute |

`Backend.id` is the route ID; `Backend.capabilities` is an array of capability enum names. `createSolver()` returns a `Solver`; `createStore()` returns a Z3 satisfiability `Store` and rejects other routes. `close()` releases the loaded runtime. Close child solvers/stores and then their backend. MiniZinc permits one owning JS backend at a time.

Browser Z3 requires cross-origin isolation: `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp`. Load `z3-built.js` as a classic script before the application bundle, with `z3-built.wasm` beside it. Bundlers must map Node's `global` to `globalThis` (for esbuild, `--define:global=globalThis`). This is Kotlin/JS invoking solver Wasm, not a Kotlin/Wasm target.

## Solving and results

| Solver member | Contract |
|---|---|
| `id` | Engine/route ID |
| `solve(model, timeoutMillis?, solutionLimit?, signal?)` | `Promise<Outcome>`; first satisfaction witness or final optimization result |
| `solutions(model, timeoutMillis?, solutionLimit?)` | `AsyncIterable<SolutionEvent>`; solution events followed by one completion |
| `openSession(model)` | Incremental `Session`, or throws on unsupported routes |
| `close()` | Cancels pending operations and releases the solver; idempotent |

Timeouts and solution counts use machine numbers; null/omitted values use defaults. Aborting an `AbortSignal` cancels the search and rejects the promise. Leaving `for await` early closes the search. A `SolutionEvent` has either `solution` or terminal `outcome`.

| Outcome/Solution member | Value |
|---|---|
| `Outcome.status` | `FEASIBLE`, `OPTIMAL`, `INFEASIBLE`, `UNBOUNDED`, `INFEASIBLE_OR_UNBOUNDED` or `UNKNOWN` |
| `Outcome.termination` | Termination enum name |
| `Outcome.route` | Answering engine/route |
| `Outcome.solution` | Nullable validated `Solution` |
| `Outcome.objective`, `Outcome.bestBound` | Nullable exact `bigint` values |
| `Solution.objective` | Nullable exact incumbent objective |
| `Solution.intValueOf(variable)` | Nullable integer `bigint` |
| `Solution.boolValueOf(variable)` | Nullable Boolean |
| `Solution.realValueOf(variable)` | Nullable exact `p` or `p/q` text |

Value accessors require variables, not compound expressions. Outcome wrappers do not expose Kotlin `SolveMetadata`. See [Results and statuses](results-and-statuses.md).

## Sessions and stores

`Session` offers `add`, `push`, `popTo`, `check(assumptions?, timeoutMillis?, signal?)` and `close`. Checks are single-consumer and return `Promise<CheckOutcome>`. Its `status` is `SATISFIABLE`, `UNSATISFIABLE` or `UNKNOWN`; it also carries `termination`, nullable `solution` and nullable `unsatCore`. Core entries are the caller's own assumption objects and need not form a minimal core. Checkpoints reject stale or foreign ownership. There is no exported session `declare` method.

`Store` offers `id`, `isFailed`, `intVar`, `boolVar`, `realVar`, `constrain`, `mark`, `undoTo`, `entails`, `explain`, `domain`, `fork` and `close`. Posting, entailment and explanation return promises and accept timeout/signal arguments. `StorePropagation` has `status` and changed integer expressions; `EntailmentAnswer` has `value` and `evidence` strings. `domain(variable)` returns inclusive `[min, max]` intervals as arrays of `bigint` pairs. Z3 stores use satisfiability checks, so declared domains are not narrowed by propagation. Forks close separately. Store residual projection is not exported.

The actual package boundary is checked by `tsc --strict`, Node consumers in `interop-js/src/test/typescript/node.mts`, and Chromium consumers in `browser.mts`. See [Sudoku from TypeScript](../tutorials/typescript-sudoku.md) and [Scheduling in the browser](../tutorials/browser-scheduling.md).


## Opaque tokens and value wrappers

| Export | How to use it |
|---|---|
| `Checkpoint` | Token returned by `Session.push()` and accepted by that session's `popTo`; it has no public constructor or inspectable state |
| `Mark` | Token returned by `Store.mark()` and accepted by that store's `undoTo`; rolling back invalidates it and newer marks. It has no public constructor or inspectable state |
| `StorePropagation.status`, `changed` | Store status string and array of integer variable wrappers whose domains shrank; Z3 stores do not propagate, so the array is empty |
| `EntailmentAnswer.value` | `ENTAILED`, `DISENTAILED`, `UNDETERMINED` or `UNKNOWN`; `UNDETERMINED` is a proved mixture of satisfying and non-satisfying solutions |
| `EntailmentAnswer.evidence` | `SATISFIABILITY` or `PROPAGATION`; the exported Z3 store uses `SATISFIABILITY` |
| `SolutionEvent.solution`, `outcome` | Nullable fields distinguishing solution events from the single terminal outcome |

Construct models through `ModelBuilder.build()` and obtain `Backend`, `Solver`, `Session`, `Store`, `Outcome`, `CheckOutcome` and `Solution` from their loaders or operations; their wrapper constructors are internal. `Model` itself exposes no public inspection members. Model builders remain mutable, while each built snapshot is independent of later edits.

The facade's `AbortSignal` type describes the standard DOM/Node cancellation object: `aborted` and `addEventListener` for the `abort` event. Supply an `AbortController.signal` to supported promise operations. This type is a bridge to the host signal, not a separate cancellation service. Reference declarations are available in the generated package `.d.mts` file; Kotlin declarations are in the [generated API](/kt-constraints/api/index.html).
