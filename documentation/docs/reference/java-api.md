# Java API

`:interop-java` is a JVM facade in `io.github.gciatto.ktconstraints.interop`. It wraps the common model and result types rather than exporting a separate Java model. It removes coroutine suspension from the calling surface. Maven coordinates and backend dependencies are listed in [Modules and artifacts](modules-and-artifacts.md).

## Modelling and numbers

| Surface | Members / behavior |
|---|---|
| `ConstraintScope.empty()` | Fresh builder; `boolVar`, `intVar`, `realVar`, `bitVecVar`, `add`, `project`, `minimize`, `maximize`, `satisfy`, `buildModel` |
| `io.github.gciatto.ktconstraints.dsl.Expressions` | Static facade for Kotlin DSL functions: arithmetic, comparisons, Boolean operations, conversions, counting, ordering and globals |
| Primitive inputs | `scope.intVar("x", 1, 10)` and arithmetic/comparison overloads accept machine integers without truncation |
| `org.gciatto.kt.math.BigInteger` | Exact model integers and objective values |
| `JvmUtils.toKotlin(java.math.BigInteger)` | kt-math's lossless conversion into a model integer |
| `JvmUtils.toJava(BigInteger)` | kt-math's lossless conversion back to `java.math.BigInteger` |
| `JavaNumbers.rational(BigDecimal)` | Exact decimal-to-rational conversion |
| `Rational.parse(text)` | Exact integer, fraction or decimal parsing |
| `Assignment.intValueOf` / `longValueOf` | Checked machine integer extraction; throws `ArithmeticException` on overflow |

Static expression calls take the receiver as their first argument: `Expressions.plus(x, 3)`, `Expressions.eq(x, y)`, `Expressions.toReal(x)`. Symbolic equality is `Expressions.eq`; object equality remains `equals`. There are no `double` arithmetic overloads. Full core node constructors remain available where the DSL has no wrapper, such as `Table` and bit-vector nodes. See [Modelling language](modelling-language.md).

## Solvers and streams

| Type | Public surface |
|---|---|
| `BlockingSolver` | Static `of(SolverBackend)` creates a solver with `createSolver()` but does not close the backend, so close a backend that owns a runtime yourself; `of(ConstraintSolver)` takes ownership. `getDescriptor`, `solve(model, options)`, `solveAsync(model, options)`, `solutions(model, options)`, `openSession(model)`, `close` |
| `BlockingSolutionStream` | `Iterator<SolutionEvent>` and `AutoCloseable`; `hasNext`, `next`, `getTerminalResult`, `cancel`, `close` |
| `BlockingSession` | `getDescriptor`, `add`, `push`, `popTo`, `check(assumptions, timeoutMillis)`, `checkAsync(assumptions, timeoutMillis)`, `close` |

Overloads omit optional solve options, assumptions or timeouts. `solve` blocks the calling thread; thread interruption cancels the search. `solveAsync` returns a `CompletableFuture<SolveResult>`; cancelling it cancels the search. Closing a solver cancels pending futures and closes its solver and streams.

A stream yields `SolutionFound` events followed by one `SolveCompleted`; close it when stopping early. The final result is available through the completion event or `getTerminalResult()`. Use try-with-resources for the solver and stream.

`openSession` requires an `IncrementalConstraintSolver`; unsupported routes throw `UnsupportedOperationException`. `push` returns an owner-specific `Checkpoint`; `popTo` invalidates it and newer checkpoints. Session checks return `CheckResult`, with an optional sufficient assumption core. There is no `declare` wrapper on `BlockingSession`.

## Constraint stores

`BlockingStore.of(ConstraintStore)` takes ownership of a store. It can wrap `ChocoStore` or `SatisfiabilityStore.of(incrementalSolver)`.

| Operation | Members |
|---|---|
| Declarations | `boolVar`, `intVar(name, domain)`, `intVar(name, min, max)`, `realVar` where supported |
| Posting | `constrain`, `constrainAsync` with optional timeout |
| Entailment | `entails`, `entailsAsync` with optional timeout |
| State | `mark`, `undoTo`, `domain`, `project`, `fork`, `isFailed`, `getDescriptor` |
| Explanation | Blocking `explain(timeoutMillis)` |
| Lifecycle | `close` cancels pending futures and closes the store |

Stores are single-consumer: do not overlap operations, including pending futures. Forks are independent owners and must be closed separately. Marks are local and become stale after rollback. `project` returns a `Residual`, whose `exact` flag must be inspected rather than assuming variable elimination is exact.

Results use the ordinary Kotlin getter names (`getStatus`, `getAssignment`, `getObjectiveValue`, `getBestBound`, `getTerminationReason`). See [Results and statuses](results-and-statuses.md).

The boundary checks live in `interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/JavaUsage.java` and its Kotest caller. For a guided example, see [Knapsack from Java](../tutorials/java-knapsack.md); for declarations, see the [API reference](/kt-constraints/api/index.html).


`BlockingSolutionStream.hasNext()` can block while the engine searches; it retains a lookahead event for `next()`. `next()` throws `NoSuchElementException` after the terminal event has been consumed. Thread interruption cancels the read. The iterator has one consumer.

The facade reuses Kotlin `SolveOptions`, `Assignment`, `CheckResult`, `Checkpoint`, `StoreMark`, `Propagation`, `EntailmentResult` and `Residual`; their members and ownership rules are listed in [Kotlin solver API](solver-api.md). There is no separate Java status or assignment class. `JavaNumbers` methods are static, and the blocking wrapper factories are the entry points rather than public wrapper constructors.
