package io.github.gciatto.ktconstraints.interop.examples.howto;

import io.github.gciatto.ktconstraints.core.Rational;
import io.github.gciatto.ktconstraints.core.integer.IntVar;
import io.github.gciatto.ktconstraints.core.real.RealVar;
import io.github.gciatto.ktconstraints.interop.BlockingSession;
import io.github.gciatto.ktconstraints.interop.BlockingSolutionStream;
import io.github.gciatto.ktconstraints.interop.BlockingSolver;
import io.github.gciatto.ktconstraints.interop.BlockingStore;
import io.github.gciatto.ktconstraints.interop.JavaNumbers;
import io.github.gciatto.ktconstraints.model.ConstraintScope;
import io.github.gciatto.ktconstraints.solve.SolveOptions;
import io.github.gciatto.ktconstraints.solve.choco.ChocoBackend;
import io.github.gciatto.ktconstraints.solve.choco.ChocoStore;
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus;
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
        try (BlockingSolver solver = BlockingSolver.of(new Z3Backend())) {
            CompletionStage<SolveResult> pending = solver.solveAsync(scope.buildModel(), new SolveOptions(1_000L, null));
            SolveResult result = pending.toCompletableFuture().get(10, TimeUnit.SECONDS);
            check(result.getStatus() == SolveStatus.OPTIMAL, "optimality");
            check(Integer.valueOf(3).equals(result.getAssignment().intValueOf(x)), "x = 3");
        }
        // --8<-- [end:optimize]
    }

    /** Consumes solutions and the completion event separately. */
    public static void enumerate() {
        // --8<-- [start:enumerate]
        ConstraintScope scope = ConstraintScope.empty();
        IntVar x = scope.intVar("x", 0, 2);
        int count = 0;
        boolean completed = false;
        try (BlockingSolver solver = BlockingSolver.of(new Z3Backend());
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
        try (BlockingSolver solver = BlockingSolver.of(new Z3Backend());
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
        try (BlockingSolver solver = BlockingSolver.of(new Z3Backend())) {
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
        try (BlockingSolver solver = BlockingSolver.of(new ChocoBackend())) {
            CompletableFuture<SolveResult> pending = solver.solveAsync(scope.buildModel());
            check(pending.cancel(true) && pending.isCancelled(), "cancelled");
        }
        // --8<-- [end:cancel]
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
