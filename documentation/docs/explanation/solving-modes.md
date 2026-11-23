# Solving modes

Batch search, incremental checks and constraint stores retain different kinds of state and answer different questions. Choosing the right interface matters even when all three can ultimately find a satisfying assignment.

| Mode | State retained | Main question |
|---|---|---|
| Batch solver | One search over a frozen model | Find a solution, enumerate outputs or optimize an integer objective |
| Incremental session | Declarations and an assertion stack | Are these active assertions satisfiable under these temporary assumptions? |
| Constraint store | Posted constraints, declarations, marks and possibly narrowed domains | What follows after posting a constraint, and what must be restored on backtracking? |

## Batch search owns the search

`ConstraintSolver.solve` consumes an immutable model. For satisfaction it returns the first validated solution as `FEASIBLE` and closes the search. It need not enumerate the remaining assignments. For optimization it consumes the search to completion, preserving an incumbent when a limit prevents an optimality proof.

`solutions` exposes a single-pass asynchronous stream. Satisfaction streams enumerate distinct projected assignments on routes supporting enumeration. Optimization streams report improving incumbents. A completion event is separate from each solution event, because observing an assignment does not establish that search is exhausted or the objective is optimal.

Output projection changes which values are reported and which solutions count as duplicates; it does not remove constraints or existentially required variables from validation. A model with an empty projection can still have a satisfying witness.

Batch is appropriate when the application can describe the whole problem before search. Engine-specific search heuristics and native propagators can operate internally without exposing their state to the caller. See [enumeration](../how-to/enumerate-solutions.md), [optimization](../how-to/optimize.md) and [results and statuses](../reference/results-and-statuses.md).

## Incremental sessions retain assertions

`IncrementalConstraintSolver.openSession` opens an `IncrementalSession` whose root contains the model. New assertions and declarations belong to the current frame. `push` creates a checkpoint; `popTo` restores the state at that checkpoint and invalidates it and all newer checkpoints. Foreign or stale checkpoints are rejected.

A `check` answers satisfiability, optionally under assumptions that apply only to that call. An unsat core, when returned, is a sufficient subset of those assumptions, not a promise of a minimal explanation of all root assertions. Session checks are satisfiability queries; a model objective does not turn `check` into an optimization operation.

Z3 exposes incremental sessions on its JVM, JavaScript and Native routes. The experimental pure-Kotlin route also implements the incremental interface. Neither interface promises domain-change reports. Retaining an assertion stack and narrowing variable domains are different capabilities.

Incrementality suits repeated related questions: a configuration stays fixed while tentative choices change, or a verifier checks several assumptions against a shared base. See [use incremental sessions](../how-to/incremental-sessions.md).

## Constraint stores expose reversible posting

`ConstraintStore` supports variables declared during execution, `constrain`, domain inspection, entailment and reversible marks. A mark records constraints, declarations and domains. Undoing restores that state, invalidates the mark and newer marks, and removes declarations made after it. A store that proves failure remains failed until an undo restores a state before the failure.

The two implementations provide different evidence:

- `ChocoStore` on the JVM runs native finite-domain propagation. `Propagation.changed` reports variables whose domains shrank. Reaching a fixpoint gives `CONSISTENT`, which does not prove that a complete solution exists.
- `SatisfiabilityStore` in `:solve-spi` wraps an incremental solver. Each post runs a satisfiability check. A successful complete check gives `SATISFIABLE`; domains remain as declared and the changed list is empty. With Z3 this is available across its execution platforms.

Both can return `FAILED` after establishing inconsistency, or `UNKNOWN` when they cannot decide. `UNKNOWN` must not be treated as failure. An `EntailmentResult` also records whether its evidence came from satisfiability checking or propagation.

Entailment deliberately has four values. `ENTAILED` means every solution satisfies the queried constraint; `DISENTAILED` means none does. `UNDETERMINED` proves that both possibilities exist. `UNKNOWN` means the engine could not decide. Weak propagation and a timeout can produce the last answer without establishing the third.

Residual projection exposes current integer domains and relevant posted constraints. It is sound but not an exact elimination of hidden variables: current implementations return `Residual.exact = false`, and constraints may still mention other variables. Forking replays state into an independent store, retaining the variables but no marks. Explanations name inconsistent posted constraints and are not guaranteed minimal. See [use constraint stores](../how-to/constraint-stores.md).

## How a logic engine can own a store

A logic runtime can keep terms, unification, suspended goals and choice points while delegating constraints to a store. A constrained logic variable is associated with a store-owned variable. Binding it posts an equality; unifying two constrained variables posts equality between them.

Each logic choice point saves a store mark. Backtracking calls `undoTo`; pruning alternatives without backtracking discards the corresponding saved marks without undoing the current constraints. The runtime turns `Propagation.changed` into wakeups for goals suspended on those variables and can bind variables whose domains become singletons.

Labeling then belongs to the logic runtime: take a mark, post a candidate binding, recurse and undo when exploring another branch. This preserves one owner of the choice points. Running an independent batch search at every branch would lose the benefit of the reversible store.

A runtime must choose a policy for undecided posts and entailment queries. In particular, a timeout cannot justify failure, and a propagation fixpoint cannot justify success as a complete answer. Final labeling or satisfiability checking may still be needed.

Stores are single-consumer objects. Independent exploration can use forks, at the cost of replaying the live state. Kt-Constraints supplies the store contract, not a Prolog or miniKanren runtime. There is no dependency on 2p-Kt and no logic-engine adapter in this repository.
