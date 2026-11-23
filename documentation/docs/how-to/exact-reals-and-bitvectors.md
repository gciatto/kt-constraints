# Use exact reals and bit-vectors

## Preserve decimal input exactly

Use `Rational.parse` for decimal or fraction text, `JavaNumbers.rational(BigDecimal)` from
Java, and `real("...")` from TypeScript. Avoid converting through `Double`, `double` or
JavaScript `number`. Select a route supporting exact real arithmetic, such as Z3.

=== "Kotlin"

    ```kotlin
    --8<-- "solve-portfolio/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/howto/SolverTasksSpec.kt:real"
    ```

=== "Java"

    ```java
    --8<-- "interop-java/src/test/java/io/github/gciatto/ktconstraints/interop/examples/howto/SolverTasks.java:real"
    ```

=== "TypeScript"

    ```typescript
    --8<-- "interop-js/src/test/typescript/examples/howto-real.mts:real"
    ```

The Kotlin assertion uses Kotest. Java's `check` helper throws on failure.
TypeScript imports `ModelBuilder`, `real` and `loadZ3` from the
[installed package](install.md), and runs the snippet inside a solver lifetime with both
solver and backend closed in `finally`. TypeScript real values return as exact rational text.

For mixed integer/real arithmetic, explicitly embed an integer expression using Kotlin
`toReal()`, Java `Expressions.toReal(...)` or TypeScript `toReal(...)`. Multiply real
expressions by rational constants; products of real variables are not offered by the
linear real API. Real variables are unbounded; add inequalities when you need bounds.
Optimization objectives remain integer expressions; see [optimization](optimize.md).

## Model fixed-width wrapping arithmetic

Use a bit-vector when overflow is part of the model. Declare a positive width and use
same-width operands. This JVM example uses explicit core nodes with Z3 to solve
`x + 1 = 0` modulo 256:

```kotlin
--8<-- "solve-portfolio/src/jvmTest/kotlin/io/github/gciatto/ktconstraints/solve/portfolio/examples/howto/SolverTasksSpec.kt:bits"
```

Import `BitVecLiteral` from `core.bitvector` and the operation types from
`core.bitvector.operation` under `io.github.gciatto.ktconstraints`.
The returned value is the unsigned integer 255. Signed and unsigned comparisons are
separate relations; choose one explicitly. `BitVecToInt` can interpret a bit-vector as a
signed or unsigned integer. Literals must fit their width; arithmetic wraps modulo
`2^width` rather than silently changing the integer sort's semantics.

Java can construct these same core nodes through their constructors. TypeScript and C
currently do not expose bit-vector expressions. For JVM/JS/Native Kotlin, use the
appropriate Z3 route; see [platform routes](../reference/platform-availability.md).

## Keep mathematical integers separate

`IntLiteral` accepts exact decimal text and kt-math `BigInteger`; Kotlin integer expressions
use arbitrary-precision values. A backend may impose a finite supported range and reject
larger values. Use `Assignment.valueOf` for the exact result; Java `intValueOf` and
`longValueOf` throw on overflow instead of truncating. See the
[modelling reference](../reference/modelling-language.md) for numeric sorts and operations.
