package io.github.gciatto.ktconstraints.interop;

import io.github.gciatto.ktconstraints.core.Rational;
import io.github.gciatto.ktconstraints.core.bool.BoolExpr;
import io.github.gciatto.ktconstraints.core.domain.IntDomain;
import io.github.gciatto.ktconstraints.core.integer.IntVar;
import io.github.gciatto.ktconstraints.core.real.RealVar;
import io.github.gciatto.ktconstraints.dsl.Expressions;
import io.github.gciatto.ktconstraints.model.ConstraintModel;
import io.github.gciatto.ktconstraints.model.ConstraintScope;
import io.github.gciatto.ktconstraints.solve.SolveOptions;
import io.github.gciatto.ktconstraints.solve.assignment.Assignment;
import io.github.gciatto.ktconstraints.solve.blocking.BlockingSession;
import io.github.gciatto.ktconstraints.solve.blocking.BlockingSolutionStream;
import io.github.gciatto.ktconstraints.solve.blocking.BlockingSolver;
import io.github.gciatto.ktconstraints.solve.blocking.BlockingStore;
import io.github.gciatto.ktconstraints.solve.incremental.CheckResult;
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus;
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint;
import io.github.gciatto.ktconstraints.solve.result.SolveResult;
import io.github.gciatto.ktconstraints.solve.result.SolveStatus;
import io.github.gciatto.ktconstraints.solve.result.TerminationReason;
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend;
import io.github.gciatto.ktconstraints.solve.store.ConstraintStore;
import io.github.gciatto.ktconstraints.solve.store.Entailment;
import io.github.gciatto.ktconstraints.solve.store.Propagation;
import io.github.gciatto.ktconstraints.solve.store.StoreMark;
import io.github.gciatto.ktconstraints.solve.store.StoreStatus;
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent;
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound;
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.gciatto.kt.math.BigInteger;
import org.gciatto.kt.math.JvmUtils;

/** Compiled by javac: exercises the Java modelling and solving boundary without Kotlin receivers or suspension. */
public final class JavaUsage {
    private JavaUsage() { }

    /** Blocking satisfaction: x + y = 10, x > y, all different, over 1..10. */
    public static void satisfy(SolverBackend backend) {
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", IntDomain.of(BigInteger.of(1), BigInteger.of(10)));
        IntVar y = scope.intVar("y", IntDomain.of(BigInteger.of(1), BigInteger.of(10)));
        scope.add(x.plus(y).eq(10));
        scope.add(x.gt(y).and(Expressions.allDifferent(x, y)));
        ConstraintModel model = scope.buildModel();
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
        scope.add(x.plus(y).le(12));
        scope.maximize(x.times(3).minus(y));
        ConstraintModel model = scope.buildModel();
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
        scope.add(x.lt(y));
        List<String> found = new ArrayList<>();
        SolveCompleted completion = null;
        try (BlockingSolver solver = BlockingSolver.of(backend);
             BlockingSolutionStream events = solver.solutions(scope.buildModel(), SolveOptions.DEFAULT)) {
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

    /** Integers beyond 64 bits survive the Java boundary; kt-math converts java.math.BigInteger directly. */
    public static void exactIntegers(SolverBackend backend) {
        java.math.BigInteger huge = java.math.BigInteger.ONE.shiftLeft(70);
        BigInteger value = JvmUtils.toKotlin(huge);
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", IntDomain.of(BigInteger.ZERO, value.times(BigInteger.of(2))));
        scope.add(x.minus(value).eq(1));
        try (BlockingSolver solver = BlockingSolver.of(backend)) {
            BigInteger result = solver.solve(scope.buildModel()).getAssignment().valueOf(x);
            check(JvmUtils.toJava(result).equals(huge.add(java.math.BigInteger.ONE)), "value " + result);
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
                BoolExpr distinct = pigeons.get(i).neq(pigeons.get(j));
                scope.add(distinct);
            }
        }
        long start = System.nanoTime();
        try (BlockingSolver solver = BlockingSolver.of(backend)) {
            CompletableFuture<SolveResult> future = solver.solveAsync(scope.buildModel()).toCompletableFuture();
            Thread.sleep(200);
            check(!future.isDone(), "the refutation finished too early to test cancellation");
            check(future.cancel(true) && future.isCancelled(), "future not cancelled");
        }
        check(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 10, "cancellation did not stop the search");
    }

    /** Primitive bounds and reads, java.math conversions, and exact BigDecimal-to-Rational conversion. */
    public static void numbers() {
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", -3, Long.MAX_VALUE);
        Assignment assignment = Assignment.of(Map.of(), Map.of(x, BigInteger.of(Long.MAX_VALUE)));
        check(Long.valueOf(Long.MAX_VALUE).equals(assignment.longValueOf(x)), "long read");
        try {
            assignment.intValueOf(x);
            throw new AssertionError("an int read must not truncate");
        } catch (ArithmeticException expected) {
            // the value does not fit an int
        }
        java.math.BigInteger huge = java.math.BigInteger.ONE.shiftLeft(70).negate();
        check(JvmUtils.toJava(JvmUtils.toKotlin(huge)).equals(huge), "java.math.BigInteger round trip");
        check(JavaNumbers.rational(new BigDecimal("-2.50")).equals(Rational.of(-5, 2)), "negative decimal");
        check(JavaNumbers.rational(new BigDecimal("0.125")).equals(Rational.of(1, 8)), "fractional decimal");
        check(JavaNumbers.rational(new BigDecimal("1E+3")).equals(Rational.of(1000, 1)), "negative scale");
    }

    /** Exact reals from Java: maximize n subject to 0.1 * (n as real) + r <= 1.25 with r >= 0.5, so n = 7. */
    public static void reals(SolverBackend backend) {
        ConstraintScope scope = ConstraintScope.empty();
        IntVar n = scope.intVar("n", 0, 100);
        RealVar r = scope.realVar("r");
        scope.add(n.toReal().times(Rational.parse("0.1")).plus(r).le(JavaNumbers.rational(new BigDecimal("1.25"))));
        scope.add(r.ge(Rational.parse("0.5")));
        scope.maximize(n);
        try (BlockingSolver solver = BlockingSolver.of(backend)) {
            SolveResult result = solver.solve(scope.buildModel());
            check(result.getStatus() == SolveStatus.OPTIMAL, "real optimization status " + result.getStatus());
            check(Integer.valueOf(7).equals(result.getAssignment().intValueOf(n)), "n " + result.getAssignment().valueOf(n));
            Rational value = result.getAssignment().valueOf(r);
            check(value.compareTo(Rational.parse("0.5")) >= 0 && value.compareTo(Rational.parse("0.55")) <= 0, "r " + value);
        }
    }

    /** Incremental session: frames, rollback, assumptions with an unsat core, and an asynchronous check. */
    public static void sessions(SolverBackend backend) throws Exception {
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", 0, 10);
        scope.add(x.gt(3));
        try (BlockingSolver solver = BlockingSolver.of(backend);
             BlockingSession session = solver.openSession(scope.buildModel())) {
            Checkpoint checkpoint = session.push();
            session.add(x.lt(2));
            check(session.check().getStatus() == CheckStatus.UNSATISFIABLE, "contradictory frame");
            session.popTo(checkpoint);
            CheckResult restored = session.check();
            check(restored.getStatus() == CheckStatus.SATISFIABLE, "restored status " + restored.getStatus());
            check(restored.getAssignment().intValueOf(x) > 3, "restored x " + restored.getAssignment().valueOf(x));
            BoolExpr low = x.lt(1);
            BoolExpr high = x.gt(8);
            CheckResult assumed = session.check(Arrays.asList(low, high));
            check(assumed.getStatus() == CheckStatus.UNSATISFIABLE, "assumption status " + assumed.getStatus());
            check(assumed.getUnsatCore().contains(low), "core " + assumed.getUnsatCore());
            CheckResult async = session.checkAsync(Collections.singletonList(x.eq(7)), 60_000L)
                .get(120, TimeUnit.SECONDS);
            check(Integer.valueOf(7).equals(async.getAssignment().intValueOf(x)), "async x " + async.getAssignment().valueOf(x));
        }
    }

    /** A solver without incremental sessions refuses to open one rather than emulating it. */
    public static void sessionsUnsupported(SolverBackend backend) {
        ConstraintScope scope = ConstraintScope.empty();
        scope.intVar("x", 0, 1);
        try (BlockingSolver solver = BlockingSolver.of(backend)) {
            try {
                solver.openSession(scope.buildModel()).close();
            } catch (UnsupportedOperationException expected) {
                return;
            }
            throw new AssertionError("session opened on " + solver.getDescriptor().getId());
        }
    }

    /** Constraint store from Java: post, mark, entail, undo, fail, explain and fork, blocking and asynchronously. */
    public static void store(ConstraintStore backing) throws Exception {
        try (BlockingStore store = BlockingStore.of(backing)) {
            IntVar x = store.intVar("x", 0, 9);
            IntVar y = store.intVar("y", 0, 9);
            Propagation posted = store.constrain(x.plus(y).lt(3));
            check(posted.getStatus() != StoreStatus.FAILED, "post status " + posted.getStatus());
            StoreMark mark = store.mark();
            store.constrain(x.eq(2));
            check(store.entails(y.eq(0)).getValue() == Entailment.ENTAILED, "y is forced to 0");
            store.undoTo(mark);
            Entailment bound = store.entailsAsync(x.lt(3)).get(60, TimeUnit.SECONDS).getValue();
            check(bound == Entailment.ENTAILED, "x < 3 is entailed, got " + bound);
            BoolExpr impossible = x.gt(5);
            check(store.constrainAsync(impossible).get(60, TimeUnit.SECONDS).getStatus() == StoreStatus.FAILED, "x > 5 fails");
            check(store.isFailed(), "store is failed");
            check(store.explain().contains(impossible), "explanation " + store.explain());
            try (BlockingStore child = store.fork()) {
                check(child.isFailed(), "forks keep the failure");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
