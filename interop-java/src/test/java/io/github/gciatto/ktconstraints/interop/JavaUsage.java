package io.github.gciatto.ktconstraints.interop;

import io.github.gciatto.ktconstraints.core.BoolExpr;
import io.github.gciatto.ktconstraints.core.IntDomain;
import io.github.gciatto.ktconstraints.core.IntVar;
import io.github.gciatto.ktconstraints.dsl.Expressions;
import io.github.gciatto.ktconstraints.model.ConstraintModel;
import io.github.gciatto.ktconstraints.model.ConstraintScope;
import io.github.gciatto.ktconstraints.solve.SolutionEvent;
import io.github.gciatto.ktconstraints.solve.SolutionFound;
import io.github.gciatto.ktconstraints.solve.SolveCompleted;
import io.github.gciatto.ktconstraints.solve.SolveOptions;
import io.github.gciatto.ktconstraints.solve.SolveResult;
import io.github.gciatto.ktconstraints.solve.SolveStatus;
import io.github.gciatto.ktconstraints.solve.TerminationReason;
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.gciatto.kt.math.BigInteger;

/** Compiled by javac: exercises the Java modelling and solving boundary without Kotlin receivers or suspension. */
public final class JavaUsage {
    private JavaUsage() { }

    /** Blocking satisfaction: x + y = 10, x > y, all different, over 1..10. */
    public static void satisfy(SolverBackend backend) {
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", IntDomain.of(BigInteger.of(1), BigInteger.of(10)));
        IntVar y = scope.intVar("y", IntDomain.of(BigInteger.of(1), BigInteger.of(10)));
        scope.add(Expressions.eq(Expressions.plus(x, y), 10));
        scope.add(Expressions.and(Expressions.gt(x, y), Expressions.allDifferent(x, y)));
        ConstraintModel model = scope.build();
        try (BlockingSolver solver = BlockingSolver.of(backend)) {
            SolveResult result = solver.solve(model);
            check(result.getStatus() == SolveStatus.FEASIBLE, "satisfaction status " + result.getStatus());
            BigInteger vx = result.getAssignment().valueOf(x);
            BigInteger vy = result.getAssignment().valueOf(y);
            check(vx.plus(vy).equals(BigInteger.of(10)) && vx.compareTo(vy) > 0, "invalid solution " + vx + ", " + vy);
        }
    }

    /** Asynchronous optimization: maximize 3x - y subject to x + y <= 12 over 0..10, optimum 30. */
    public static void optimizeAsync(SolverBackend backend) throws Exception {
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", IntDomain.of(BigInteger.of(0), BigInteger.of(10)));
        IntVar y = scope.intVar("y", IntDomain.of(BigInteger.of(0), BigInteger.of(10)));
        scope.add(Expressions.le(Expressions.plus(x, y), 12));
        scope.maximize(Expressions.minus(Expressions.times(x, 3), y));
        ConstraintModel model = scope.build();
        try (BlockingSolver solver = BlockingSolver.of(backend)) {
            CompletionStage<SolveResult> stage = solver.solveAsync(model, new SolveOptions(60_000L, null));
            SolveResult result = stage.toCompletableFuture().get(120, TimeUnit.SECONDS);
            check(result.getStatus() == SolveStatus.OPTIMAL, "optimization status " + result.getStatus());
            check(BigInteger.of(30).equals(result.getObjectiveValue()), "objective " + result.getObjectiveValue());
        }
    }

    /** Iterates every solution of x < y over 0..2, then reads the single completion event. */
    public static void enumerate(SolverBackend backend) {
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", IntDomain.of(BigInteger.of(0), BigInteger.of(2)));
        IntVar y = scope.intVar("y", IntDomain.of(BigInteger.of(0), BigInteger.of(2)));
        scope.add(Expressions.lt(x, y));
        List<String> found = new ArrayList<>();
        SolveCompleted completion = null;
        try (BlockingSolver solver = BlockingSolver.of(backend);
             BlockingSolutionStream events = solver.solutions(scope.build(), SolveOptions.DEFAULT)) {
            while (events.hasNext()) {
                SolutionEvent event = events.next();
                if (event instanceof SolutionFound) {
                    SolutionFound solution = (SolutionFound) event;
                    found.add(solution.getAssignment().valueOf(x) + "<" + solution.getAssignment().valueOf(y));
                } else {
                    completion = (SolveCompleted) event;
                }
            }
        }
        check(found.size() == 3 && found.containsAll(List.of("0<1", "0<2", "1<2")), "solutions " + found);
        check(completion != null && completion.getResult().getTerminationReason() == TerminationReason.COMPLETED,
            "completion " + completion);
    }

    /** Integers beyond 64 bits survive the Java boundary; java.math.BigInteger converts through its decimal form. */
    public static void exactIntegers(SolverBackend backend) {
        java.math.BigInteger huge = java.math.BigInteger.ONE.shiftLeft(70);
        BigInteger value = BigInteger.of(huge.toString());
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", IntDomain.of(BigInteger.ZERO, value.times(BigInteger.of(2))));
        scope.add(Expressions.eq(Expressions.minus(x, value), 1));
        try (BlockingSolver solver = BlockingSolver.of(backend)) {
            BigInteger result = solver.solve(scope.build()).getAssignment().valueOf(x);
            check(new java.math.BigInteger(result.toString()).equals(huge.add(java.math.BigInteger.ONE)), "value " + result);
        }
    }

    /** Cancelling the future stops a hard refutation (13 pigeons, 12 holes); closing the solver then returns. */
    public static void cancelAsync(SolverBackend backend) throws Exception {
        ConstraintScope scope = ConstraintScope.empty();
        List<IntVar> pigeons = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            pigeons.add(scope.intVar("p" + i, IntDomain.of(BigInteger.ZERO, BigInteger.of(11))));
        }
        for (int i = 0; i < pigeons.size(); i++) {
            for (int j = i + 1; j < pigeons.size(); j++) {
                BoolExpr distinct = Expressions.neq(pigeons.get(i), pigeons.get(j));
                scope.add(distinct);
            }
        }
        long start = System.nanoTime();
        try (BlockingSolver solver = BlockingSolver.of(backend)) {
            CompletableFuture<SolveResult> future = solver.solveAsync(scope.build()).toCompletableFuture();
            Thread.sleep(200);
            check(!future.isDone(), "the refutation finished too early to test cancellation");
            check(future.cancel(true) && future.isCancelled(), "future not cancelled");
        }
        check(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10, "cancellation did not stop the search");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
