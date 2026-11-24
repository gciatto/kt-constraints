# Backends

A **backend** translates a model and provides access to a solver. The **engine** is the program that searches for answers; the **route** is how this library reaches it. A route ID is `engine/route`. For example, `cp-sat/direct` builds OR-Tools objects, whereas `cp-sat/minizinc` emits MiniZinc source for the same engine family. Their accepted models and runtime requirements differ. See [engines and routes](../engines-and-routes.md).

**Satisfaction** finds an assignment meeting every constraint. **Optimization** additionally minimizes or maximizes an integer expression. **Enumeration** lists distinct projected satisfaction assignments. An incremental **session** retains assertions between checks; a **store** supports reversible posting, and may also narrow variable domains through propagation. These are separate capabilities; see [solving modes](../solving-modes.md).

## Route overview

Compare the model shapes below, then check whole-model support. Platforms describe implementations,
not test coverage on every operating system; see [platform availability](../../reference/platform-availability.md).
The [route reference](../../reference/solvers-and-routes.md) is the canonical table of capabilities and numeric limits.

| Route | Suitable problems | Platforms |
|---|---|---|
| [Z3 direct, Wasm, Native](z3.md) | Exact linear reals, bit-vectors, large integers, incremental checks | JVM, Node, browser, Native |
| [CP-SAT direct](cp-sat.md) | Bounded integer scheduling and combinatorial models | JVM |
| [Choco direct](choco.md) | Finite-domain models; separate store for domain propagation | JVM |
| [SCIP / HiGHS through OR-Tools](or-tools-mip.md) | Bounded affine integer optimization | JVM |
| [MiniZinc CLI configurations](minizinc.md) | Global constraints with engine-specific flattening | JVM; Gecode on Node |
| [MiniZinc Chuffed Wasm](minizinc.md) | Bounded integer models in a browser worker | Browser |
| [Pure Kotlin](kotlin.md) | Portable solving without an external runtime | JVM, JS (Node and browsers), Native |
| [AutoBackend](auto.md) | Delegate batch solving and enumeration to eligible registered routes | JVM, Node, browser, Native |

SMT (satisfiability modulo theories) combines logical solving with typed arithmetic. CP (constraint
programming) uses finite domains; CP-SAT combines these with Boolean satisfiability search. MIP
(mixed-integer programming) uses linear equalities and inequalities; these routes expose only a
bounded Boolean/integer subset. MiniZinc compiles a model for a configured engine.

Reserved `:solve-flatzinc` and `:solve-highs` modules do not supply operational routes.

## Which one when?

Start with [AutoBackend](auto.md) for batch solving when you want eligible routes tried automatically. Read [selection](selection.md) to understand its preferences and failure handling.

Choose [Z3](z3.md) for exact linear reals, bit-vectors, large integers or incremental sessions. On the JVM, compare [CP-SAT](cp-sat.md) and [Choco](choco.md) for puzzles, scheduling and other finite-domain models; choose `ChocoStore` when domain propagation is the operation you need. Consider [SCIP or HiGHS](or-tools-mip.md) for bounded affine integer optimization. Use [MiniZinc](minizinc.md) for its explicit engine configurations and alternative global decompositions. The [Kotlin route](kotlin.md) is an opt-in experiment when avoiding an external solver runtime matters.

Check availability and whole-model support before direct use. A broad capability such as integer arithmetic does not promise every integer value or expression. The [how-to guide](../../how-to/choose-a-solver.md) shows the checks using tested examples.

## What the measurements establish

The recorded catalogue shows complementary strengths, not one universal winner. Choco often has lower cost on easy JVM models; CP-SAT remains strong on the harder measured scheduling family. Z3 and direct MIP routes can struggle with large all-different decompositions. Explicitly registered Kotlin often beats Z3 on the measured Node and Native instances, but remains absent from defaults. MiniZinc adds process and flattening cost; its MIP global decompositions can nevertheless beat the direct MIP lowering.

Default ranking uses fixed heuristics, not these timings. The catalogue lacks real-arithmetic, nonlinear-integer and pure-Boolean families, and its bit-vector case is small. See [benchmarks](../../reference/benchmarks.md) for numbers, host/version details and qualifications.
