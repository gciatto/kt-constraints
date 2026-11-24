# Solve a Sudoku grid

Fill each blank so that every row, column and box contains each digit once. Use this 4 × 4 instance with 2 × 2 boxes; zero marks a blank: `1..4 / .41. / 2..3 / .32.`.

## 1. Build the model

Use `:dsl` for the operators. The examples solve with `solveAuto` from `:solve-auto`, which picks a bundled solver for your platform. See [Install](../install.md) for the dependencies and [Choose a solver](../choose-a-solver.md) to pick one yourself.

Imports used by the example and its test:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Sudoku.kt:imports"
```

Create one variable per cell, constrain the three families of groups with `allDifferent`, and post each nonzero given.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Sudoku.kt:model"
```

Domains `1..4` and four distinct cells force each group to be a permutation of the digits. Keep `allDifferent` as a global node so the selected backend can lower it; pairwise inequalities express the same rule but lose that explicit structure.

## 2. Solve and read the result

This is a satisfaction problem: leave the objective unset. The example checks `FEASIBLE` and prints the grid; it does not enumerate completions or prove uniqueness.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/problems/Sudoku.kt:solve"
```

Expected output:

```text
1 2 3 4
3 4 1 2
2 1 4 3
4 3 2 1
```

The solve call suspends; run it in a coroutine. See [Results and statuses](../../reference/results-and-statuses.md) before treating a result as a proof.

## Variations

- For a 9 × 9 puzzle, use nine rows and columns, domains `1..9`, and nine 3 × 3 boxes.
- For diagonal Sudoku, add `allDifferent` over each long diagonal.
- To check uniqueness, [enumerate solutions](../enumerate-solutions.md) and look for a second complete grid. A single returned grid does not establish uniqueness.

See the [modelling language reference](../../reference/modelling-language.md), [global constraints explanation](../../explanation/global-constraints.md), and [API reference](/kt-constraints/api/index.html) for the operators and their semantics.
