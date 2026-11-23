# How-to guides

Choose the task you need to complete. For a first guided example, start with the
[tutorials](../tutorials/index.md).

## Set up and model

- [Install the library](install.md): Gradle, Maven, npm, C archives and source builds.
- [Choose a programming style](programming-styles.md): Kotlin builders and expression factories, language facades and execution APIs.
- [Choose a solver](choose-a-solver.md): check availability, model support and required capabilities.
- [Use exact reals and bit-vectors](exact-reals-and-bitvectors.md): preserve numeric semantics.

## Run a search

- [Set limits and cancel](limits-and-cancellation.md).
- [Enumerate solutions](enumerate-solutions.md), including projected outputs.
- [Optimize an objective or weighted preferences](optimize.md).
- [Use incremental sessions](incremental-sessions.md) for repeated assertion checks.
- [Use constraint stores](constraint-stores.md) for application-owned branching and rollback.
- [Use MiniZinc](use-minizinc.md) on the JVM, Node or in the browser.
- [Run on Kotlin/Native with Z3](native-and-z3.md).

## Adapt a classic model

The [problems cookbook](problems/index.md) contains [N-Queens](problems/n-queens.md),
[Sudoku](problems/sudoku.md), [graph coloring](problems/graph-coloring.md),
[knapsack](problems/knapsack.md), [assignment](problems/assignment.md),
[bin packing](problems/bin-packing.md), [job-shop scheduling](problems/job-shop.md),
[traveling salesman](problems/traveling-salesman.md),
[nurse rostering](problems/nurse-rostering.md) and [pigeonhole](problems/pigeonhole.md).

## Contribute

- [Add a backend](contributing/add-a-backend.md).
- [Run tests and benchmarks](contributing/run-tests-and-benchmarks.md).
- [Release](contributing/release.md).
