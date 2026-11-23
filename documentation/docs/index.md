# Kt-Constraints

**Constraint programming, SMT and optimization for Kotlin Multiplatform, with Java, TypeScript and C facades.**

Write a model once, with typed variables, arithmetic and global constraints, and solve it with Z3, OR-Tools CP-SAT,
Choco, SCIP, HiGHS, any MiniZinc solver, or a dependency-free pure-Kotlin engine. Models run on the JVM, on Node, in
the browser, and natively. The model never depends on a solver: each backend translates it, and refuses what it
cannot represent faithfully instead of approximating it.

```kotlin
--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:variables"

--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:model"

--8<-- "solve-portfolio/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/tutorials/SendMoreMoney.kt:solve"
```

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
