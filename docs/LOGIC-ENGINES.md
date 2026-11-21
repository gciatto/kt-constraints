# Mapping a logic engine onto a constraint store

This is for authors of an **external** logic-programming runtime (a Prolog, a miniKanren, a rule engine). Such a
runtime can keep its own terms, unification and choice points while delegating constraints to a `ConstraintStore`.
Nothing here depends on a particular runtime, and no adapter lives in this repository.

## Choosing a store

| Store                               | Platforms             | Answers by                       | Use when                                    |
|-------------------------------------|-----------------------|----------------------------------|---------------------------------------------|
| `ChocoStore()`                      | JVM                   | Propagation (`DOMAIN_PROPAGATION`) | You want CLP(FD)-style domain narrowing and wakeups |
| `SatisfiabilityStore.of(solver)`    | Wherever Z3 runs (JVM, Node, browsers, desktop Native) | Complete satisfiability checks | You want proofs: failure is exact, entailment is decided |

Both implement the same interface and pass the same contract (`storeContract` in `:test-solve`).

## The mapping

- **Constrained logic variables.** When the runtime first attaches a constraint to an unbound logic variable,
  declare a store variable with `intVar`/`boolVar` and keep the pair in the variable's attributes. Store
  variables are store-owned identities, so they never alias another store or a `ConstraintScope`.
- **Choice points.** On creating a choice point, take `store.mark()` and save it with the choice point. When the
  runtime backtracks to that choice point, call `store.undoTo(mark)`. Marks are LIFO: undoing to a mark also
  invalidates every newer one, which matches how choice points are discarded. A cut that removes choice points
  only drops their marks; it does not undo anything.
- **Posting.** A goal such as `X #< Y` becomes `store.constrain(IntLessThan(x, y))`:
  - `FAILED` makes the goal fail. The store stays failed until the runtime backtracks past it.
  - `CONSISTENT` or `SATISFIABLE` makes the goal succeed.
  - `UNKNOWN` (timeout) must not be treated as failure. Either succeed and let labeling decide later, or raise an
    error, whichever is the runtime's policy.
- **Wakeups.** `Propagation.changed` lists the variables whose domains shrank. A Prolog runtime wakes the goals
  suspended on those variables, as attributed variables do. A variable whose `domain(x)` has become a single
  value can be unified with that value.
- **Unification of two constrained variables.** Post `IntEquality(x, y)`.
- **Binding to a constant.** Post `IntEquality(x, IntLiteral(v))`.
- **Reified tests and `when/2`.** `store.entails(c)` answers `ENTAILED`, `DISENTAILED`, `UNDETERMINED` (proven
  neither) or `UNKNOWN` (the engine cannot tell). Only the first two should trigger a suspended goal. With
  `ChocoStore`, `UNKNOWN` is common for constraints that propagation cannot decide yet.
- **Labeling.** Labeling is the runtime's own search: for each value, take a mark, post the binding, recurse,
  and undo on failure. This keeps one set of choice points, the runtime's, instead of duplicating the engine's
  internal search. `benchmarkStore` in `:test-solve` is a worked example.
- **Answers and residual goals.** On success, `store.project(vars)` gives each variable's domain and the posted
  constraints that still mention it, for printing residual goals. The projection is sound but not minimal
  (`Residual.exact` is false).

## Limits

- One consumer per store; the runtime must not call it from concurrent threads.
- `fork()` copies a store by replaying it, which suits or-parallel exploration with one fork per worker but costs
  time proportional to the store. `explain()` on a failed store names conflicting posted constraints, for example
  to report why a goal failed.
- `ChocoStore` handles Booleans and integers within ±10^7 and has no exact reals. `SatisfiabilityStore` over Z3
  also handles reals and bit-vectors.
- Java (`BlockingStore`) and TypeScript (`Backend.openStore()`, Z3 Wasm only) expose the same operations. The
  TypeScript facade has no `project`.
