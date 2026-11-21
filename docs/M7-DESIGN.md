# M7 design: CLP-ready constraint store

M7 lets an external logic engine own a constraint store. It posts constraints as resolution proceeds, asks what
they imply, and backtracks. Nothing here depends on a Prolog implementation. Status: **accepted** (decisions below
settled on 2026-11-21).

## What exists

- `IncrementalSession` (Z3 on JVM, Wasm and Native): `add`, `push`/`popTo` with checked `Checkpoint`s, and
  `check(assumptions, timeout)` with unsat cores. Its variables are fixed when it opens: the model's
  declarations. Repeated checks are satisfiability queries, not propagation.
- Choco exposes what propagation needs: `Solver.propagate()`, live domains on its variables,
  `getEnvironment().worldPush()/worldPop()` for reversible state, and `Model.postTemp`, which is undone on
  backtrack.

## Layers

1. **`ConstraintStore`**, in `:solve` (common, interface only). A solver-neutral logical view:
   - `intVar`, `boolVar`, `realVar`: declare variables at any time;
   - `constrain(c)`: post a constraint and get a `Propagation` back;
   - `mark()`/`undoTo(mark)`: LIFO backtracking;
   - `entails(c)`;
   - `domain(x)`;
   - `project(vars)`;
   - `close()`.
2. **Two implementations behind one interface**, chosen by capability:
   - `SatisfiabilityStore` over any `IncrementalConstraintSolver` (Z3 today). Entailment comes from proofs
     (below). There is no propagation, so `domain(x)` is the declared domain and `constrain` reports only
     `FAILED` or `UNCHANGED`.
   - `PropagatingStore` (Choco, JVM). `constrain` runs native propagation and reports the domain reductions.
     Its entailment is native and may be `UNKNOWN`.
3. **Facades:** Java `BlockingStore` and TS `Store` (`Backend.openStore`), with the same shape. They were added
   once the Kotlin API settled.

## Semantics

- **Variables are declared on the store, not on a model.** Logic programs create variables during resolution, so
  a fixed model cannot hold them. The store hands out variables from its own `VariableFactory`, so identities
  never alias another scope. A variable declared after a mark is dropped by `undoTo` that mark; using it later
  is rejected as foreign. This needs one SPI addition: sessions accept new declarations
  (`IncrementalSession.declare`), which the Z3 translation already supports lazily.
- **Backtracking is LIFO.** `mark()` returns a `StoreMark`. `undoTo` restores the constraints, variables and
  domains present when the mark was taken, and invalidates that mark and every newer one. It reuses
  `CheckpointStack`'s ownership and staleness rules. This maps to Z3 `push`/`pop` and Choco
  `worldPush`/`worldPop` with no replay and no copying of native contexts.
- **`fork()` replays.** An independent copy replays the live variables and constraints into a fresh store, so
  it costs time proportional to the store and carries none of the marks. Deferred at first, then added after M7's
  core. Z3 forks open a second session on the same solver, which the last store using it closes.
- **Explanations.** On a failed store, `explain()` returns posted constraints that are inconsistent by
  themselves:
  - Z3: the unsat core of the posted constraints, checked as assumptions in a throwaway session.
  - Choco: a deletion filter that keeps a constraint only when propagation can no longer refute the rest without
    it.
  Neither result is guaranteed minimal.
- **Entailment** returns `ENTAILED`, `DISENTAILED`, `UNDETERMINED` or `UNKNOWN`:
  - `UNDETERMINED`: proven to be neither entailed nor disentailed.
  - `UNKNOWN`: the engine could not decide (timeout, or propagation that is too weak).
  - Over Z3, `ENTAILED` means the store plus `not c` is UNSAT, and `DISENTAILED` means the store plus `c` is
    UNSAT, both checked as assumptions. Both SAT gives `UNDETERMINED`.
  - Choco answers from its propagators' `isEntailed`, which often returns `UNDEFINED`. That maps to `UNKNOWN`,
    never to `UNDETERMINED`.
  - Each answer carries its evidence: proof by satisfiability, or propagation.
  - *Decided:* four values rather than the roadmap's three. A Prolog `when/2`-style wakeup needs to tell
    "not yet" from "cannot tell".
- **Propagation reports are pulled, not pushed.** `constrain` returns either
  - `Propagation.Failed`, or
  - `Propagation.Changed` with the list of variables whose domains shrank, in engine order.
  The caller polls `domain(x)`, so the store makes no callbacks into the logic engine. A Prolog adapter turns
  the changed variables into attributed-variable wakeups.
- **Residual projection is conservative.** `project(vars)` returns each variable's current domain plus the posted
  constraints that mention a requested variable and are not entailed. It is sound but not minimal, and the
  result says so. Exact projection (Z3 quantifier elimination) is optional future work.
- **Failure never lies.** A failed store stays failed until `undoTo`; `constrain` on a failed store returns
  `Failed` without engine work. A timeout is `UNKNOWN`, never a failure.

## Tests and benchmarks

- **Shared `storeContract` in `:test-solve`:**
  - nested mark and undo;
  - stale and foreign marks;
  - variables declared after a mark;
  - repeated constrain/entails cycles;
  - a cut-like pattern: undo past several marks at once;
  - failure, then recovery;
  - exhaustive agreement with the finite oracle on tiny models;
  - close during work.
- **Benchmarks:** for each store, time one constrain, propagate, bind, mark, undo and project, against rebuilding
  and re-solving the model from scratch.

## Order of work

1. SPI: `IncrementalSession.declare`, with Z3 tests.
2. `ConstraintStore` types and `storeContract`.
3. `SatisfiabilityStore` over Z3.
4. Spike on Choco `postTemp`, `propagate` and world push/pop with variables added mid-search, then
   `PropagatingStore`.
5. Benchmarks.
6. A doc on mapping a logic engine onto the store.

**Choco spike result (step 4):**
- Confirmed:
  - `worldPush`, then post, then `propagate`, then `worldPop` restores domains.
  - `worldPopUntil` undoes several marks at once.
  - Recovery after a `ContradictionException` works: `engine.flush()`, then pop.
  - Variables created inside a world are harmless after popping it.
  - `Constraint.isSatisfied()` gives TRUE, FALSE or UNDEFINED.
- The catch: `reify()` posts the reification permanently, so a world pop does not remove it. The store
  therefore records every Choco constraint created in a frame and `unpost`s them on undo, instead of relying on
  `postTemp`.
- The Choco store is `ChocoStore`, in `:solve-choco`.

The exit criterion is met by Z3 (incremental) plus Choco (genuine propagation). If the Choco spike fails,
propagation is documented as unavailable rather than emulated.
