# Solve LP and MILP models

A linear program (LP) optimizes a linear objective over continuous real variables subject to linear constraints.
A mixed-integer linear program (MILP) also has integer variables. Both use ordinary models: declare real
variables with `realVar`, embed integers with `asRealExpr()`, and set one integer or real objective.

The examples use the pure-Kotlin exact solver. Kotlin uses the same common API on JVM, JS and Native;
Java uses `BlockingSolver`, and TypeScript/JavaScript uses the exported ES module. No external solver runtime
is needed for these examples. Add `:solve-kotlin` (artifact `kt-constraints-solve-kotlin`) for direct Kotlin use;
see [installation](install.md) and [modules](../reference/modules-and-artifacts.md).

## 1. Solve a continuous LP

Maximize `3x + 2y` with `x, y >= 0`, `x + y <= 4.5` and `x + 3y <= 6`.
The exact optimum is `27/2`, at `x = 9/2`, `y = 0`.

=== "Kotlin (JVM / JS / Native)"

    ```kotlin
    --8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/LinearPrograms.kt:imports"

    --8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/LinearPrograms.kt:lp"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/LinearPrograms.java:lp"
    ```

=== "TypeScript / JavaScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-lp.mts:imports"

    --8<-- "interop-js/src/test/typescript/examples/howto-lp.mts:lp"
    ```

Call Kotlin's suspending helper from a coroutine. Java imports `ConstraintScope` from `model`,
`Rational` from `core`, `RealVar` from `core.real`, `KotlinBackend` from `solve.kotlin`, `BlockingSolver`
from `solve.blocking`, and result types from `solve.result`, all under `io.github.gciatto.ktconstraints`.
Its `check` helper throws `AssertionError` on failure. The TypeScript example includes imports and runtime cleanup;
use it in an async function or at the top level of an ES module. It is also valid JavaScript. Consumer tests use the local package name; released package names
are in [installation](install.md).

## 2. Make it mixed-integer

Require `x` to be an integer in `0..10`, keeping `y` real. The optimum becomes `13`, at `x = 4`, `y = 1/2`.

=== "Kotlin (JVM / JS / Native)"

    ```kotlin
    --8<-- "solve-auto/src/commonTest/kotlin/io/github/gciatto/ktconstraints/solve/auto/examples/howto/LinearPrograms.kt:milp"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/LinearPrograms.java:milp"
    ```

=== "TypeScript / JavaScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-milp.mts:imports"

    --8<-- "interop-js/src/test/typescript/examples/howto-milp.mts:milp"
    ```

Java additionally imports `IntVar` from `core.integer`. The Kotlin imports from section 1 cover both examples.
Write fractions and decimals as exact text (`Rational.parse("4.5")` or `real("4.5")`); the value is `9/2`.
TypeScript uses distinct names `minimizeReal` and `maximizeReal` for real objectives.

The MILP examples then replace the objective with integer `x`, keeping the real constraints. Its optimum is 4,
reported in `objectiveValue` (TypeScript: `objective`). The [integer optimization guide](optimize.md) covers
integer objectives in all three languages. Real objectives use `realObjectiveValue` and `realBestBound`
(TypeScript: `realObjective` and `realBestBound`), with the integer fields empty.

## C

The C facade uses exact rational strings for constants, real objectives and real variable values. The standalone
`interop-c/src/nativeUsageTest/c/linear-program.c`
program solves both examples through Native automatic defaults. These defaults may load external runtimes;
see [Assignment from C](../tutorials/c-assignment.md) for compilation and runtime setup.

The shared helper builds the constraints and objective, disposing intermediate handles after the model retains them:

```c
--8<-- "interop-c/src/nativeUsageTest/c/linear-program.c:model"
```

=== "LP"

    ```c
    --8<-- "interop-c/src/nativeUsageTest/c/linear-program.c:lp"
    ```

=== "MILP"

    ```c
    --8<-- "interop-c/src/nativeUsageTest/c/linear-program.c:milp"
    ```

`expect` asserts the exact returned text and releases it with `DisposeString`; `post` asserts successful addition
and disposes the constraint handle. Release the remaining handles after inspecting the result:

```c
--8<-- "interop-c/src/nativeUsageTest/c/linear-program.c:cleanup"
```

The MILP also disposes `integerX`. Run `./gradlew :interop-c:nativeUsageTest` to compile and execute every C example.

## 3. Interpret the result

| Status | Meaning |
|---|---|
| `OPTIMAL` | An attained optimum according to the answering route's evidence: exact on Kotlin/Z3; a numerical claim on tolerance routes. |
| `FEASIBLE` | An incumbent without an attained-optimum proof, possibly after a limit. On exact routes, a different `realBestBound` may be an unattained supremum/infimum: maximizing `x < 1` has supremum 1 and no maximum. |
| `UNBOUNDED` | The objective improves without limit according to the route's evidence; no assignment is returned. |
| `INFEASIBLE` | No assignment satisfies the constraints, according to the route's evidence. |
| `INFEASIBLE_OR_UNBOUNDED` | Engine evidence does not distinguish infeasibility from unboundedness. |
| `UNKNOWN` | No decisive answer and no feasible incumbent. Inspect termination and diagnostics. |

A missing bound means none was supplied. It is not the incumbent value. See [results and statuses](../reference/results-and-statuses.md).

## 4. Choose a route and inspect diagnostics

Kotlin's simplex and branch and bound optimize supported LP/MILP exactly on all Kotlin platforms. Z3 supports
exact real optimization on JVM, JS and Native too. `AutoBackend.load()` selects among its platform defaults;
without numeric tolerance consent it skips floating-point routes for real models. Loading the automatic bundle
may initialize external runtimes, even when Kotlin eventually answers. Use `KotlinBackend` directly when you
want only the portable engine.

Route choice and the Kotlin engine's internal technique are separate decisions. Kotlin uses branch and bound
for every supported real objective and for integer objectives over translatable linear models. Satisfaction,
its enumeration, and integer optimization with logic, globals or variable products use SAT plus its real theory.
A real objective with logic or globals is rejected by linear lowering. For optimization completion results,
`metadata.statistics["technique"]` reports `branch-and-bound` or `sat`.

Inspect `SolverRegistry.rank`'s `features`, `rejections`, `diagnosis` and `unmetCapabilities` to understand eligibility.
No bundled route supports nonlinear real objectives; diagnostics name `NONLINEAR_REAL_OBJECTIVES` when needed.
The built-in real expression language itself has no product of real variables. See [selection](../explanation/backends/selection.md)
and [techniques and internal certificates](../explanation/pure-kotlin-solver.md#choosing-the-technique).
Kotlin checks exact primal/dual evidence, Farkas refutations and unbounded rays internally; `SolveResult` does not
export certificates. MILP proof also depends on branch-and-bound search. Z3 confirms attained optima and
unattained bounds with plain solver checks; unboundedness uses its optimizer's claim.

## 5. Opt into floating-point LP/MILP

SCIP and HiGHS through OR-Tools on JVM, and [direct HiGHS](../explanation/backends/highs.md) through the Native C API
or JS Wasm, use numerical engines. Models with reals require explicit `SolveOptions.numericTolerance`; without
it support rejects them. Setting it permits these routes but does not force automatic selection to prefer them.
MiniZinc MIP routes still accept only their Boolean/integer subset.

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:tolerance"
    ```

The Java example imports `SolveOptions` and `NumericTolerance` from `solve` and `OrToolsMipBackend` from `solve.mip`.
Kotlin uses the same options on JVM, JS and Native. The TypeScript facade exports neither tolerance options nor
a HiGHS loader; use Kotlin/JS for those routes.

Returned doubles are converted to rationals; integer columns round only within integrality tolerance. Exact
arithmetic then checks each bound and row against feasibility tolerance, and evaluates the objective from the
accepted values. Diagnostics record the largest excess. An accepted assignment may therefore exceed an exact
constraint within the consented tolerance. `OPTIMAL` remains a numerical engine claim. Strict real inequalities
are rejected. See [configuration](../reference/configuration.md), [route limits](../reference/solvers-and-routes.md)
and [exact reals](exact-reals-and-bitvectors.md).

To re-optimize after reversible changes, use [incremental sessions](incremental-sessions.md#4-re-optimize-as-the-session-changes).
The same operation is available through Kotlin blocking calls, Java blocking/future calls and TypeScript promises.
