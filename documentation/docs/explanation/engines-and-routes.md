# Engines and routes

An engine performs search or satisfiability reasoning. A route is the integration path that translates a model, starts that engine and interprets its answers. Kt-Constraints identifies both because the path changes what you can rely on.

For example, `cp-sat/direct` builds OR-Tools CP-SAT objects on the JVM. `cp-sat/minizinc` emits MiniZinc source, invokes its compiler and runs the configured CP-SAT solver. The engine name is shared, but these are distinct backends with different runtime setup, lowering, bounds and costs.

## What the route changes

| Concern | Why engine identity alone is insufficient |
|---|---|
| Availability | An embedded JVM binding, installed executable and browser Wasm runtime have different prerequisites |
| Supported models | A compiler can accept a smaller subset than the engine itself supports |
| Numeric range | A route checks the bounds required by its representation and intermediates |
| Globals | One route can post a native global while another decomposes or flattens it |
| Execution modes | Batch integration does not automatically expose an engine's incremental or propagation features |
| Lifecycle | Processes, native contexts and shared JavaScript pools need different ownership and cancellation mechanisms |
| Performance | Startup, translation and flattening can dominate search time |

The `SolverDescriptor` records the engine, route and capabilities, with an `engine/route` ID. Availability answers whether the runtime can be used. The support report answers whether that route can represent the entire model and requested options. Both checks matter even when the engine is familiar.

## The current integration choices

Z3 uses `z3/direct` on the JVM, `z3/wasm` in JavaScript and `z3/native` on Kotlin/Native. They share common translation and execution logic behind platform-specific ports. Their integer representation maps to mathematical integers, and they support exact linear reals and fixed-width bit-vectors. Browser Wasm execution has runtime requirements described in [platform availability](../reference/platform-availability.md). JavaScript loading a Wasm solver is distinct from compiling the library to Kotlin/Wasm.

Choco and CP-SAT have direct JVM adapters. Choco also provides `ChocoStore` for native propagation (removing impossible domain values) and reversible posting (adding constraints that can later be undone); see [solving modes](solving-modes.md). The CP-SAT route exposes batch solving and streams; native globals inside its batch model do not imply a public propagating store.

SCIP and HiGHS run through OR-Tools in `:solve-mip`, as `scip/or-tools` and `highs/or-tools`. They share a bounded integer-program lowering. That is separate from the reserved direct HiGHS C API integration.

MiniZinc is a compiler and runtime route to several engines. The JVM integration supports Gecode, Chuffed, CP-SAT, HiGHS and CBC configurations. JavaScript has a Gecode Node CLI route, `gecode/minizinc-js-cli`, and a Chuffed browser route, `chuffed/minizinc-wasm`. MiniZinc owns flattening and its solver-specific decompositions. Native command execution is not implemented.

The `kotlin/direct` route is implemented in common Kotlin, so it runs on every platform from the published `:solve-kotlin` module. It is not bundled by automatic selection. See [the pure-Kotlin solver](pure-kotlin-solver.md).

## Exact models and numerical engines

The model representation does not silently adopt the numeric semantics of whichever engine is selected. Bounded routes check their representable subset before execution, and routes lacking exact reals reject real models rather than substituting MiniZinc floats or MIP continuous variables.

MIP lowering uses exact integer expressions and bounded coefficients, but the external MIP solve is numerical. Returned integer witnesses are rounded and independently checked exactly against the source model. This establishes a valid witness; it does not provide an exact certificate for a numerical proof of optimality or infeasibility. Those statuses rely on the engine's reported evidence. The **relative MIP gap** measures the difference between the incumbent objective and the proven bound, relative to the objective’s magnitude. A configured positive relative MIP gap yields feasible results rather than claims of optimality.

These distinctions affect portability. A model accepted by Z3 may exceed a CP adapter's bounds, and a model accepted by one MiniZinc configuration may fail another's support checks. Shared model semantics enable trying another route; they do not promise identical support or performance.

For exact capabilities and bounds, use [solvers and routes](../reference/solvers-and-routes.md). For selection, see [automatic solver selection](backends/selection.md); for setup, see [choose a solver](../how-to/choose-a-solver.md) and [use MiniZinc](../how-to/use-minizinc.md).
