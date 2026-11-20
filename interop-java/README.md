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
  `BigDecimal` to `Rational` exactly. There are deliberately no `double` overloads: binary floating point would either
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
Z3, SCIP and HiGHS. Incremental sessions have no Java wrapper yet.
