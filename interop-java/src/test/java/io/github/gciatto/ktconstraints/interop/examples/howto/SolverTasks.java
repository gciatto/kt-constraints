package io.github.gciatto.ktconstraints.interop.examples.howto;

import io.github.gciatto.ktconstraints.core.Rational;
import io.github.gciatto.ktconstraints.core.integer.IntVar;
import io.github.gciatto.ktconstraints.core.real.RealVar;
import io.github.gciatto.ktconstraints.interop.JavaNumbers;
import io.github.gciatto.ktconstraints.model.ConstraintScope;
import io.github.gciatto.ktconstraints.solve.NumericTolerance;
import io.github.gciatto.ktconstraints.solve.SolveOptions;
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend;
import io.github.gciatto.ktconstraints.solve.auto.AutoBackends;
import io.github.gciatto.ktconstraints.solve.blocking.BlockingSession;
import io.github.gciatto.ktconstraints.solve.blocking.BlockingSolutionStream;
import io.github.gciatto.ktconstraints.solve.blocking.BlockingSolver;
import io.github.gciatto.ktconstraints.solve.blocking.BlockingStore;
import io.github.gciatto.ktconstraints.solve.choco.ChocoBackend;
import io.github.gciatto.ktconstraints.solve.choco.ChocoStore;
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus;
import io.github.gciatto.ktconstraints.solve.mip.OrToolsMipBackend;
import io.github.gciatto.ktconstraints.solve.incremental.Checkpoint;
import io.github.gciatto.ktconstraints.solve.result.SolveResult;
import io.github.gciatto.ktconstraints.solve.result.SolveStatus;
import io.github.gciatto.ktconstraints.solve.store.Entailment;
import io.github.gciatto.ktconstraints.solve.store.StoreMark;
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent;
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound;
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted;
import io.github.gciatto.ktconstraints.solve.z3.Z3Backend;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Java boundary examples quoted by the how-to guides. */
public final class SolverTasks {
    private SolverTasks() { }

    /** Uses explicit factories and awaits an asynchronous optimization. */
    public static void optimize() throws Exception {
        // --8<-- [start:optimize]
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", 0, 3);
        IntVar y = scope.intVar("y", 0, 3);
        scope.add(x.plus(y).eq(3));
        scope.maximize(x);
        try (Z3Backend backend = new Z3Backend();
             BlockingSolver solver = BlockingSolver.of(backend)) {
            CompletionStage<SolveResult> pending = solver.solveAsync(scope.buildModel(), new SolveOptions(1_000L, null));
            SolveResult result = pending.toCompletableFuture().get(10, TimeUnit.SECONDS);
            check(result.getStatus() == SolveStatus.OPTIMAL, "optimality");
            check(Integer.valueOf(3).equals(result.getAssignment().intValueOf(x)), "x = 3");
        }
        // --8<-- [end:optimize]
    }

    /** Proves the exact optimum of a mixed-integer linear program with Z3. */
    public static void mixedIntegerProgram() {
        // --8<-- [start:milp]
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", 0, 10);
        RealVar y = scope.realVar("y");
        scope.add(y.ge(0));
        scope.add(x.asRealExpr().plus(y).le(Rational.parse("4.5")));
        scope.add(x.asRealExpr().plus(y.times(3)).le(6));
        scope.maximize(x.asRealExpr().times(3).plus(y.times(2)));
        try (Z3Backend backend = new Z3Backend();
             BlockingSolver solver = BlockingSolver.of(backend)) {
            SolveResult result = solver.solve(scope.buildModel());
            check(result.getStatus() == SolveStatus.OPTIMAL, "optimality");
            check(Rational.of(13).equals(result.getRealObjectiveValue()), "objective = 13");
            check(Rational.parse("1/2").equals(result.getAssignment().valueOf(y)), "y = 1/2");
        }
        // --8<-- [end:milp]
    }

    /** Solves the same program in floating point with SCIP, consenting to its tolerances. */
    public static void floatingPointProgram() {
        // --8<-- [start:tolerance]
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", 0, 10);
        RealVar y = scope.realVar("y");
        scope.add(y.ge(0));
        scope.add(x.asRealExpr().plus(y).le(Rational.parse("4.5")));
        scope.add(x.asRealExpr().plus(y.times(3)).le(6));
        scope.maximize(x.asRealExpr().times(3).plus(y.times(2)));
        SolveOptions options = new SolveOptions(null, null, List.of(), new NumericTolerance(1e-6, 1e-6));
        try (OrToolsMipBackend backend = OrToolsMipBackend.scip();
             BlockingSolver solver = BlockingSolver.of(backend)) {
            SolveResult result = solver.solve(scope.buildModel(), options);
            check(result.getStatus() == SolveStatus.OPTIMAL, "optimality within tolerances");
            Rational gap = result.getRealObjectiveValue().minus(Rational.of(13));
            check(gap.compareTo(Rational.parse("-0.000001")) >= 0 && gap.compareTo(Rational.parse("0.000001")) <= 0, "objective near 13");
        }
        // --8<-- [end:tolerance]
    }

    /** Loads every bundled JVM route and lets the automatic backend pick one per model. */
    public static void auto() {
        // --8<-- [start:auto]
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", 0, 9);
        scope.add(x.times(3).eq(12));
        try (AutoBackend backend = AutoBackends.loadAutoBackend();
             BlockingSolver solver = BlockingSolver.of(backend)) {
            SolveResult result = solver.solve(scope.buildModel());
            check(Integer.valueOf(4).equals(result.getAssignment().intValueOf(x)), "x = 4");
        }
        // --8<-- [end:auto]
    }

    /** Consumes solutions and the completion event separately. */
    public static void enumerate() {
        // --8<-- [start:enumerate]
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", 0, 2);
        int count = 0;
        boolean completed = false;
        try (Z3Backend backend = new Z3Backend();
             BlockingSolver solver = BlockingSolver.of(backend);
             BlockingSolutionStream stream = solver.solutions(scope.buildModel(), new SolveOptions(1_000L, null))) {
            while (stream.hasNext()) {
                SolutionEvent event = stream.next();
                if (event instanceof SolutionFound) {
                    SolutionFound found = (SolutionFound) event;
                    check(found.getAssignment().intValueOf(x) != null, "x is present");
                    count++;
                } else {
                    completed = ((SolveCompleted) event).getResult().getTerminationReason()
                        == io.github.gciatto.ktconstraints.solve.result.TerminationReason.COMPLETED;
                }
            }
        }
        check(count == 3 && completed, "three solutions and completed search");
        // --8<-- [end:enumerate]
    }

    /** Restores an assertion checkpoint. */
    public static void session() {
        // --8<-- [start:session]
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", 0, 3);
        scope.add(x.ge(1));
        try (Z3Backend backend = new Z3Backend();
             BlockingSolver solver = BlockingSolver.of(backend);
             BlockingSession session = solver.openSession(scope.buildModel())) {
            Checkpoint before = session.push();
            session.add(x.eq(0));
            check(session.check().getStatus() == CheckStatus.UNSATISFIABLE, "contradiction");
            session.popTo(before);
            check(session.check().getStatus() == CheckStatus.SATISFIABLE, "restored");
        }
        // --8<-- [end:session]
    }

    /** Posts constraints through the blocking store facade. */
    public static void store() {
        // --8<-- [start:store]
        try (BlockingStore store = BlockingStore.of(new ChocoStore())) {
            IntVar x = store.intVar("x", 0, 3);
            StoreMark before = store.mark();
            store.constrain(x.eq(2));
            check(store.entails(x.eq(2)).getValue() == Entailment.ENTAILED, "entailed");
            store.undoTo(before);
            check(store.entails(x.eq(2)).getValue() == Entailment.UNKNOWN, "restored");
        }
        // --8<-- [end:store]
    }

    /** Converts decimal input without passing through double. */
    public static void real() {
        // --8<-- [start:real]
        ConstraintScope scope = ConstraintScope.empty();
        RealVar r = scope.realVar("r");
        scope.add(r.eq(JavaNumbers.rational(new BigDecimal("0.1"))));
        try (Z3Backend backend = new Z3Backend();
             BlockingSolver solver = BlockingSolver.of(backend)) {
            SolveResult result = solver.solve(scope.buildModel());
            check(Rational.of(1, 10).equals(result.getAssignment().valueOf(r)), "exact tenth");
        }
        // --8<-- [end:real]
    }

    /** Cancels an asynchronous search and releases its solver. */
    public static void cancel() {
        // --8<-- [start:cancel]
        ConstraintScope scope = ConstraintScope.empty();
        IntVar[] pigeons = new IntVar[13];
        for (int i = 0; i < pigeons.length; i++) {
            pigeons[i] = scope.intVar("p" + i, 0, 11);
        }
        // Pairwise disequalities, unlike allDifferent, make this refutation slow enough to cancel.
        for (int i = 0; i < pigeons.length; i++) {
            for (int j = i + 1; j < pigeons.length; j++) {
                scope.add(pigeons[i].neq(pigeons[j]));
            }
        }
        try (ChocoBackend backend = new ChocoBackend();
             BlockingSolver solver = BlockingSolver.of(backend)) {
            CompletableFuture<SolveResult> pending = solver.solveAsync(scope.buildModel());
            check(pending.cancel(true) && pending.isCancelled(), "cancelled");
        }
        // --8<-- [end:cancel]
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
