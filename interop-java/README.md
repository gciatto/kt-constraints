# :interop-java

Java entry points; no Kotlin receivers, extension functions or `suspend` calls are needed.

- **Modelling:** `ConstraintScope` as in Kotlin, with expressions from the static facade
  `io.github.gciatto.ktconstraints.dsl.Expressions` (`plus`, `minus`, `times`, `eq`, `lt`, `and`, `allDifferent`, ...).
- **Solving:** `BlockingSolver.of(backend)` owns one solver and offers `solve` (blocking), `solveAsync`
  (`CompletableFuture`; cancelling it cancels the search) and `solutions` (a closeable `Iterator` of solutions,
  then one `SolveCompleted`). Closing it cancels pending futures and open streams.
- **Numbers** stay exact. Model integers are kt-math `org.gciatto.kt.math.BigInteger`s, but primitives work where
  they fit: `scope.intVar("x", 1, 10)`, `Expressions.plus(x, 3)`, and `assignment.longValueOf(x)` / `intValueOf(x)`,
  which throw `ArithmeticException` rather than truncate. `JavaNumbers` converts `java.math.BigInteger` both ways and
  `BigDecimal` to `Rational` exactly; `Rational.parse("0.1")` also reads decimals exactly. Real expressions use the
  same `Expressions` methods (`plus`, `times(x, rational)`, `le`, ...), with `Expressions.toReal(n)` to embed integers. There are deliberately no `double` overloads: binary floating point would either
  turn `0.1` into a surprising fraction or need rounding.

```java
ConstraintScope scope = ConstraintScope.empty();
IntVar x = scope.intVar("x", 1, 10);
IntVar y = scope.intVar("y", 1, 10);
scope.add(Expressions.eq(Expressions.plus(x, y), 10));
scope.add(Expressions.allDifferent(x, y));
try (BlockingSolver solver = BlockingSolver.of(new ChocoBackend())) {
    SolveResult result = solver.solve(scope.build());
    long vx = result.getAssignment().longValueOf(x);
}
```

The executable reference is `src/test/java/.../JavaUsage.java`, compiled by `javac` and run against Choco, CP-SAT,
Z3, SCIP and HiGHS.

Incremental sessions (`BlockingSolver.openSession`) return a `BlockingSession` with `add`, `push`, `popTo`, and
blocking `check` or future-based `checkAsync`, both taking optional assumptions and a timeout. Only routes with
incremental sessions (Z3) open one; others throw `UnsupportedOperationException` instead of re-solving.

Constraint stores (`BlockingStore.of(new ChocoStore())`, or `BlockingStore.of(SatisfiabilityStore.of(solver))` over
Z3) post, mark, undo, fork, and answer entailment and explanations, blocking or through `CompletableFuture`s. See
[solving modes](https://gciatto.github.io/kt-constraints/explanation/solving-modes/).
