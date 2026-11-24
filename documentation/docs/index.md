# Kt-Constraints

**Constraint programming, SMT and optimization for Kotlin Multiplatform, with Java, TypeScript and C facades.**

Write a model once, with typed variables, arithmetic and global constraints, and solve it with Z3, OR-Tools CP-SAT,
Choco, SCIP, HiGHS, configured MiniZinc engines, or a pure-Kotlin engine that needs no external runtime. Models run on the JVM, on Node, in
the browser, and natively. The model never depends on a solver: each backend translates it, and refuses what it
cannot represent faithfully instead of approximating it.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/SendMoreMoney.kt:imports"

--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/SendMoreMoney.kt:model"

--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/SendMoreMoney.kt:solve"
```

## Suggested reading order

Each step relies only on the steps before it, so you can stop at any point with a consistent picture. Pages in
brackets are optional detours for that step.

**1. Get a first result**

1. [Install](how-to/install.md) the modules for your platform.
2. [Your first model in Kotlin](tutorials/kotlin-first-model.md): scopes, variables, constraints, solving with
   `AutoBackend`, and reading the answer.
3. [Results and statuses](reference/results-and-statuses.md): what `FEASIBLE`, `OPTIMAL`, `INFEASIBLE` and `UNKNOWN`
   prove, before you trust an answer.

**2. Model well**

4. [Choose a programming style](how-to/programming-styles.md): the scope DSL versus explicit factories.
5. [Modelling language](reference/modelling-language.md) and [Global constraints](explanation/global-constraints.md):
   the operators you can use and why `allDifferent` and friends stay whole.
6. [Optimize](how-to/optimize.md), then [Enumerate solutions](how-to/enumerate-solutions.md), then
   [Set limits and cancel](how-to/limits-and-cancellation.md).
7. Classic problems, in nav order from [N-Queens](how-to/problems/n-queens.md) to
   [Nurse rostering](how-to/problems/nurse-rostering.md) (leave Pigeonhole for step 4).

**3. Understand and choose solvers**

8. [Engines and routes](explanation/engines-and-routes.md): engine versus route, and why they differ.
9. [Backends overview](explanation/backends/index.md), then the page of each backend you consider.
10. [Choose a solver](how-to/choose-a-solver.md) and [Automatic selection](explanation/backends/selection.md):
    picking a route yourself or letting `AutoBackend` rank them.
11. [One model, every platform](tutorials/kotlin-multiplatform.md), with [Platform availability](reference/platform-availability.md)
    [and [Run on Kotlin/Native with Z3](how-to/native-and-z3.md), [Use MiniZinc](how-to/use-minizinc.md)].

**4. Go beyond batch solving**

12. [Solving modes](explanation/solving-modes.md): batch solving, incremental sessions and constraint stores.
13. [Use incremental sessions](how-to/incremental-sessions.md), then [Pigeonhole](how-to/problems/pigeonhole.md) (unsat cores).
14. [Use constraint stores](how-to/constraint-stores.md) and [Use exact reals and bit-vectors](how-to/exact-reals-and-bitvectors.md).
15. [Kotlin solver API](reference/solver-api.md): every solving type in one place.

**5. Use it from another language**

Start these tutorials after part 1. For the full modelling sections of the language references, also read
steps 4–5; for optimization, enumeration and limits, read step 6. Session and store sections require
steps 12–14. Use the matching language reference alongside each tutorial.

16. Java: [Knapsack from Java](tutorials/java-knapsack.md) → [Java API](reference/java-api.md).
    TypeScript: [Sudoku from TypeScript](tutorials/typescript-sudoku.md) → [Scheduling in the browser](tutorials/browser-scheduling.md)
    → [TypeScript API](reference/typescript-api.md). C: [Assignment from C](tutorials/c-assignment.md) → [C API](reference/c-api.md).

**6. Look inside and contribute**

17. [Principles](explanation/principles.md), [Architecture](explanation/architecture.md),
    [The pure-Kotlin solver](explanation/pure-kotlin-solver.md).
18. [Backend SPI](reference/spi.md), [Benchmarks](reference/benchmarks.md), then [Add a backend](how-to/contributing/add-a-backend.md) and
    [Run tests and benchmarks](how-to/contributing/run-tests-and-benchmarks.md).

The navigation groups pages by type (tutorials, how-to guides, reference, explanation) and orders each group
roughly by prerequisites. Follow the suggested reading order above as your learning path; footer links follow the navigation.

## Where to start

<div class="grid cards" markdown>

- :material-school: **[Tutorials](tutorials/index.md)**

    Build a working program step by step: SEND+MORE=MONEY in Kotlin, N-Queens on every platform, a knapsack
    from Java, Sudoku from TypeScript, scheduling in the browser, an assignment from C.

- :material-hammer-wrench: **[How-to guides](how-to/index.md)**

    Recipes for specific tasks: installing, choosing a solver, optimizing, enumerating, sessions and stores,
    and a cookbook of ten classic problems.

- :material-book-open-variant: **[Reference](reference/index.md)**

    The modelling language, every solver route and where it runs, results and statuses, the Java, TypeScript and
    C APIs, benchmarks, and the [generated API reference](/kt-constraints/api/index.html).

- :material-lightbulb-on: **[Explanation](explanation/index.md)**

    Why the library is built the way it is: its principles, architecture, solving modes, automatic solver
    selection, and how the pure-Kotlin solver works inside.

</div>
