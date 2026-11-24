# Use constraint stores

## 1. Select the evidence you need

Use a store when your application owns branching and rollback, rather than asking a batch
solver to own the entire search.

- On the JVM, `ChocoStore` propagates finite integer domains. A successful propagation
  step does not by itself prove that the whole store has a solution.
- `SatisfiabilityStore.of(incrementalSolver)` takes ownership of a Kotlin incremental solver
  and checks satisfiability after posts. It answers entailment by checks, while domains stay
  as declared. It closes the solver when its last owning store closes.
- Java wraps either implementation in `BlockingStore.of(...)`.
- TypeScript's Z3 backend exposes `backend.openStore()`, a satisfiability store.

## 2. Post, mark and undo

Declare variables through the store, post constraints with `constrain`, and take a mark
before a branch. Undo to that mark when the branch fails or finishes.
The Kotlin example uses Choco to narrow `y`, and checks that a fork is independent.

The Kotlin examples are complete helper functions. Call a `suspend` helper from a coroutine or another
suspending function. The shown imports also cover later Kotlin helpers on this page.

=== "Kotlin"

    ```kotlin
    --8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:imports"

    --8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:store"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:store"
    ```

=== "TypeScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-store.mts:store"
    ```

Kotlin imports `ChocoStore`, `IntDomain` and the DSL operators.
Java's `check` helper throws on failure. The TypeScript snippet runs inside a backend
loaded with `loadZ3`, closed in the enclosing `finally` block. It uses `constant` from the
[installed package](install.md). The TypeScript example tests entailment and rollback;
its domains do not narrow as Choco's do.

## 3. Handle failure and uncertain answers

Inspect the returned propagation status. `FAILED` records inconsistency; that state persists
until undo restores a mark taken before the failure. `UNKNOWN` means the engine could not
decide within the available resources. Check `isFailed` before continuing a branch.

`entails` does not post its argument. Inspect both `value` and `evidence`:

- `ENTAILED`: implied by the store.
- `DISENTAILED`: its negation is implied.
- `UNDETERMINED`: proven neither entailed nor disentailed; there are solutions on both sides.
- `UNKNOWN`: the query could not be decided, including when propagation is too weak.

Choco returns `UNKNOWN` when propagation cannot refute either side; it does not search
for both witnesses. A satisfiability store can prove `UNDETERMINED`. Do not treat
`UNDETERMINED` as a timeout or as permission to assume the expression.
On failure, `explain()` returns inconsistent posted constraints, not necessarily a minimal
explanation. A failed store vacuously entails every expression; handle failure first.


In `SatisfiabilityStore`, `constrain` posts and records the constraint before checking it. An
`UNKNOWN` result leaves that constraint active; retrying does not undo it. Take a mark before
a tentative post, inspect its status, and use `undoTo(mark)` to remove it if you abandon the branch.

```kotlin
--8<-- "solve-auto/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/SolverTasks.kt:uncertain-post"
```

The zero budget deliberately returns `UNKNOWN`. The example checks that the constraint is
active before undo, then checks the restored state.

## 4. Keep rollback ownership explicit

Marks belong to their store and become stale after undo. A fork preserves variables,
constraints and failure state, but starts with no marks and must be closed separately.
Declare branch-local variables after a mark only if you will discard them on undo.

Kotlin/Java `project` returns a residual that can retain constraints involving other variables;
check its `exact` flag before treating it as exact elimination. Store residual projection is
not exposed in TypeScript. See [solving modes](../explanation/solving-modes.md) and the
[API reference](/kt-constraints/api/index.html) for the store contracts.
