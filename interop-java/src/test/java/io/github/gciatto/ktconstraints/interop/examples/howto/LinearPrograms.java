package io.github.gciatto.ktconstraints.interop.examples.howto;

import io.github.gciatto.ktconstraints.core.Rational;
import io.github.gciatto.ktconstraints.core.integer.IntVar;
import io.github.gciatto.ktconstraints.core.real.RealVar;
import io.github.gciatto.ktconstraints.model.ConstraintScope;
import io.github.gciatto.ktconstraints.solve.blocking.BlockingSolver;
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend;
import io.github.gciatto.ktconstraints.solve.result.SolveResult;
import io.github.gciatto.ktconstraints.solve.result.SolveStatus;

/** Exact LP and MILP examples compiled by javac and run without an external solver runtime. */
public final class LinearPrograms {
    private LinearPrograms() { }

    /** Proves the exact optimum of a continuous linear program with the portable Kotlin route. */
    public static void linearProgram() {
        // --8<-- [start:lp]
        ConstraintScope scope = ConstraintScope.empty();
        RealVar x = scope.realVar("x");
        scope.add(x.ge(0));
        RealVar y = scope.realVar("y");
        scope.add(y.ge(0));
        scope.add(x.plus(y).le(Rational.parse("4.5")));
        scope.add(x.plus(y.times(3)).le(6));
        scope.maximize(x.times(3).plus(y.times(2)));
        try (KotlinBackend backend = new KotlinBackend();
             BlockingSolver solver = BlockingSolver.of(backend)) {
            SolveResult result = solver.solve(scope.buildModel());
            check(result.getStatus() == SolveStatus.OPTIMAL, "optimality");
            check(Rational.of(27, 2).equals(result.getRealObjectiveValue()), "objective = 27/2");
            check(Rational.of(0).equals(result.getAssignment().valueOf(y)), "y = 0");
            check(Rational.of(9, 2).equals(result.getAssignment().valueOf(x)), "x = 9/2");
            check(result.getRealObjectiveValue().equals(result.getRealBestBound()), "exact bound");
        }
        // --8<-- [end:lp]
    }

    /** Proves the exact optimum of a mixed-integer linear program with the portable Kotlin route. */
    public static void mixedIntegerProgram() {
        // --8<-- [start:milp]
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", 0, 10);
        RealVar y = scope.realVar("y");
        scope.add(y.ge(0));
        scope.add(x.asRealExpr().plus(y).le(Rational.parse("4.5")));
        scope.add(x.asRealExpr().plus(y.times(3)).le(6));
        scope.maximize(x.asRealExpr().times(3).plus(y.times(2)));
        try (KotlinBackend backend = new KotlinBackend();
             BlockingSolver solver = BlockingSolver.of(backend)) {
            SolveResult result = solver.solve(scope.buildModel());
            check(result.getStatus() == SolveStatus.OPTIMAL, "optimality");
            check(Rational.of(13).equals(result.getRealObjectiveValue()), "objective = 13");
            check(Rational.parse("1/2").equals(result.getAssignment().valueOf(y)), "y = 1/2");
            check(Integer.valueOf(4).equals(result.getAssignment().intValueOf(x)), "x = 4");
            check(result.getRealObjectiveValue().equals(result.getRealBestBound()), "exact bound");
            scope.maximize(x);
            SolveResult integerGoal = solver.solve(scope.buildModel());
            check(integerGoal.getStatus() == SolveStatus.OPTIMAL, "integer optimality");
            check("4".equals(integerGoal.getObjectiveValue().toString()), "integer objective = 4");
            check(integerGoal.getRealObjectiveValue() == null, "integer objective field only");
        }
        // --8<-- [end:milp]
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
