# :interop-java

Java entry points; no Kotlin receivers, extension functions or `suspend` calls are needed.

- **Modelling:** `ConstraintScope` as in Kotlin, with expressions from the static facade
  `io.github.gciatto.ktconstraints.dsl.Expressions` (`plus`, `minus`, `times`, `eq`, `lt`, `and`, `allDifferent`, ...).
- **Solving:** `BlockingSolver.of(backend)` owns one solver and offers `solve` (blocking), `solveAsync`
  (`CompletableFuture`; cancelling it cancels the search) and `solutions` (a closeable `Iterator` of solutions,
  then one `SolveCompleted`). Closing it cancels pending futures and open streams.
- **Integers** are kt-math `org.gciatto.kt.math.BigInteger`s and stay exact. Convert from and to
  `java.math.BigInteger` through the decimal form: `BigInteger.of(j.toString())`, `new java.math.BigInteger(k.toString())`.

```java
ConstraintScope scope = ConstraintScope.empty();
IntVar x = scope.intVar("x", IntDomain.of(BigInteger.of(1), BigInteger.of(10)));
IntVar y = scope.intVar("y", IntDomain.of(BigInteger.of(1), BigInteger.of(10)));
scope.add(Expressions.eq(Expressions.plus(x, y), 10));
scope.add(Expressions.allDifferent(x, y));
try (BlockingSolver solver = BlockingSolver.of(new ChocoBackend())) {
    SolveResult result = solver.solve(scope.build());
    BigInteger vx = result.getAssignment().valueOf(x);
}
```

The executable reference is `src/test/java/.../JavaUsage.java`, compiled by `javac` and run against Choco, CP-SAT,
Z3, SCIP and HiGHS. Incremental sessions have no Java wrapper yet.
