# Solve and enumerate N-Queens

Use one integer variable per column to place N queens on an N-by-N board. This guide assumes you can build and
solve a model; for a guided setup, start with [One model, every platform](../../tutorials/kotlin-multiplatform.md).

## 1. Create rows and columns

Use this common builder. Each variable's domain is `0 until size`; each list index identifies a column.
Distinct rows prevent horizontal attacks, and the representation prevents vertical attacks.

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:imports"

--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:board"
```

The following helpers use these imports:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:imports"
```

## 2. Choose a diagonal encoding

Use two `allDifferent` constraints to keep diagonal constraints as global nodes until the backend lowers them:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:diagonal-globals"
```

Alternatively, add two inequalities for every pair of columns:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:pairwise"
```

Inside `ConstraintScope.empty { }`, call `queensBoard` and pass its returned rows to either helper. The encodings describe the same legal boards;
the pairwise version adds N(N−1) diagonal inequalities. It still uses a global `allDifferent` for rows.
The global version exposes the diagonal structure to the backend. Actual propagation and performance depend
on the route; see [Global constraints](../../explanation/global-constraints.md).

For one solution, use the [tutorial's solving helper](../../tutorials/kotlin-multiplatform.md#4-solve-from-common-code).

## 3. Break a reflection symmetry when useful

For a board of size greater than one, require the first column's queen to have a smaller row than the last
column's queen:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:symmetry"
```

Reflection across the horizontal axis reverses this inequality. Since rows are distinct, exactly one member
of each such reflection pair remains. This removes one reflection symmetry; it does not select one board per
full rotation/reflection class. Leave it out when counting all boards.

## 4. Enumerate and count complete solutions

`AutoBackend` delegates enumeration to the best eligible route supporting `ENUMERATION`. Read its solution
stream with `forEachSolution`, which returns the terminal result. Keep the backend runtime, solver and stream open
until consumption finishes:

```kotlin
--8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/tutorials/NQueens.kt:enumerate"
```

This helper returns all boards for the requested variant. Call `enumerateQueens(4)` for the global encoding,
`enumerateQueens(4, pairwise = true)` for pairwise diagonals, or `enumerateQueens(4, symmetry = true)` to keep
one board from each horizontal reflection pair. Use the returned list's `size` as the count.

The tests assert two boards for size 4 with either diagonal encoding, one with reflection breaking, and no
boards for size 3. They compare boards independently of search order. No output projection is applied, so
each assignment contains every queen variable.

The termination check rejects a timeout, cancellation or solution limit as an exhaustive count. This helper
stores every board in memory; for larger instances, increment a counter in the `forEachSolution` block instead.
See [Enumerate solutions](../enumerate-solutions.md) for projection and bounded searches, and
[Set limits and cancel](../limits-and-cancellation.md) for stopping long runs.
