# Solve linear and mixed-integer programs

A linear program (LP) optimizes a linear objective over real variables subject to linear constraints. A
mixed-integer linear program (MILP) also has integer variables. Both are ordinary models here: declare real
variables with `realVar`, embed integers with `asRealExpr()`, and pass a real expression to `minimize` or
`maximize`.

## 1. Write the program

This example maximizes `3x + 2y` over an integer `x` in `0..10` and a real `y >= 0`, subject to
`x + y <= 4.5` and `x + 3y <= 6`. Its optimum is `13`, at `x = 4` and `y = 1/2`.

=== "Kotlin"

    ```kotlin
    --8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/LinearPrograms.kt:imports"

    --8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/LinearPrograms.kt:milp"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:milp"
    ```

=== "TypeScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-milp.mts:milp"
    ```

Write decimal and fractional constants exactly, as `Rational.parse("4.5")` or `real("4.5")`: `4.5` is the
rational `9/2`, not a binary floating-point approximation. TypeScript has no overloads, so its builder names
the real objectives `minimizeReal` and `maximizeReal`.

## 2. Read the exact result

A real objective reports its value in `realObjectiveValue` (TypeScript: `realObjective`) and any proven bound in
`realBestBound`, as exact rationals; the integer `objectiveValue` and `bestBound` stay empty.

| Status | Meaning |
|---|---|
| `OPTIMAL` | The value is the exact optimum. |
| `FEASIBLE` | An incumbent without an optimality proof, such as after a limit. A `realBestBound` different from the value can also be an optimum that no solution attains: maximizing `x` subject to `x < 1` has supremum `1` but no maximum. |
| `UNBOUNDED` | The objective improves without limit; there is no assignment. |
| `INFEASIBLE` | No assignment satisfies the constraints. |

## 3. Choose a route

Real objectives are optimized exactly, without floating-point tolerances, by Z3 on the JVM, in JavaScript and on
Kotlin/Native. `AutoBackend.load()` selects such a route; routes that cannot optimize real objectives reject the
model with `Unsupported real objective`. See [choose a solver](choose-a-solver.md) and
[exact reals](exact-reals-and-bitvectors.md).
