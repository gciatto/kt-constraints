# Choose a programming style

Choose a model-building API for your language, then an execution API for your task.
All builders produce an immutable model that you can pass to a compatible solver.

## Build a model in Kotlin

Use `ConstraintModel.of { }` when the model fits a receiver block. Import the required
operators from `io.github.gciatto.ktconstraints.dsl`; use symbolic `eq` for a constraint.
Kotlin `==` tests object equality.

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/howto/ModelStylesSpec.kt:receiver"
```

Use `ConstraintScope.empty()` and `build()` when you need to retain variable references for
reading assignments, or assemble the model across several functions. You can construct
expression nodes explicitly without operator imports:

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/howto/ModelStylesSpec.kt:factories"
```

Here `IntEquality`, `IntSum` and `IntLiteral` come from the core expression packages; both
styles solve the same mathematical relation. Each scope owns its variables: reusing a name
in another scope does not make it the same variable. A built snapshot is detached from later
builder changes.

## Use a language facade

- **Java:** use `ConstraintScope`, static `Expressions` factories and `BlockingSolver`.
  The [optimization guide](optimize.md) shows Java factories and a `CompletionStage`.
- **TypeScript:** use `ModelBuilder`, `bigint` constants and methods such as
  `x.plus(y).eq(constant(3n))`. Builder methods such as `add` return the builder for chaining.
  See [optimization](optimize.md) for a tested example.
- **C:** use generated handles through `libktconstraints_symbols()`. Build with `ModelBuilder`,
  call the blocking `solve`, and read exact decimal strings. Dispose every owned object handle
  and returned string. Follow the [C tutorial](../tutorials/c-assignment.md) and
  [C API reference](../reference/c-api.md); C does not expose the other solving modes below.

## Choose how the solver runs

| Task | Kotlin | Java | TypeScript |
| --- | --- | --- | --- |
| One satisfaction or optimization result | Suspending `solve`, or `solveAuto` | Blocking `solve`, or `solveAsync` returning `CompletableFuture` (also a `CompletionStage`) | `solve` returning `Promise<Outcome>` |
| Stream solutions | `SolutionStream.next()` suspends | Closeable `BlockingSolutionStream` iterator | `for await` over `solver.solutions(...)` |
| Repeated checks with reversible assertions | `IncrementalSession` | `BlockingSession`, with blocking and future methods | Session with Promise-based `check` |
| Application-owned branching and entailment | `ConstraintStore` | `BlockingStore`, with blocking and future methods | Store with Promise-based posts and entailment |

Select [batch enumeration](enumerate-solutions.md), [incremental sessions](incremental-sessions.md)
or [stores](constraint-stores.md) according to who owns the search and rollback. A batch solver
does not automatically support the other interfaces. See [solving modes](../explanation/solving-modes.md)
for the design trade-offs.

## Manage lifetimes and cancellation

Use Kotlin `use`, Java try-with-resources, and TypeScript `try/finally` to close solvers,
sessions and stores. Close loaded JS backends as well, to release their workers.
Keep each stream, session or store single-consumer; await an operation before starting
the next on that resource. For cancellation, choose the mechanism supported by the calling
language in [set limits and cancel](limits-and-cancellation.md).
