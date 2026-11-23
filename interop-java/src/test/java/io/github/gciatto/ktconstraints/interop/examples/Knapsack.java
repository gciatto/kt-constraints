// --8<-- [start:program]
package io.github.gciatto.ktconstraints.interop.examples;

import io.github.gciatto.ktconstraints.core.integer.IntExpr;
import io.github.gciatto.ktconstraints.core.integer.IntVar;
import io.github.gciatto.ktconstraints.dsl.Expressions;
import io.github.gciatto.ktconstraints.interop.BlockingSolver;
import io.github.gciatto.ktconstraints.model.ConstraintModel;
import io.github.gciatto.ktconstraints.model.ConstraintScope;
import io.github.gciatto.ktconstraints.solve.choco.ChocoBackend;
import io.github.gciatto.ktconstraints.solve.result.SolveResult;
import io.github.gciatto.ktconstraints.solve.result.SolveStatus;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** A small 0/1 knapsack, run through both Java solving entry points. */
public final class Knapsack {
    private Knapsack() { }

    /** Returns the checked optimum; each call creates its own model and solver. */
    public static long run(boolean asynchronous) throws Exception {
        // --8<-- [start:model]
        long[] weights = {2, 3, 4};
        long[] values = {6, 10, 12};
        ConstraintScope scope = ConstraintScope.empty();
        IntVar[] take = new IntVar[weights.length];
        IntExpr[] weightTerms = new IntExpr[weights.length];
        IntExpr[] valueTerms = new IntExpr[weights.length];
        for (int i = 0; i < take.length; i++) {
            take[i] = scope.intVar("take" + i, 0, 1);
            weightTerms[i] = Expressions.times(take[i], weights[i]);
            valueTerms[i] = Expressions.times(take[i], values[i]);
        }
        scope.add(Expressions.le(Expressions.sum(weightTerms), 5));
        scope.maximize(Expressions.sum(valueTerms));
        ConstraintModel model = scope.build();
        // --8<-- [end:model]
        // --8<-- [start:solve]
        try (BlockingSolver solver = BlockingSolver.of(new ChocoBackend())) {
            SolveResult result;
            if (asynchronous) {
                CompletionStage<SolveResult> stage = solver.solveAsync(model);
                result = stage.toCompletableFuture().get(10, TimeUnit.SECONDS);
            } else {
                result = solver.solve(model);
            }
            if (result.getStatus() != SolveStatus.OPTIMAL || result.getAssignment() == null) {
                throw new IllegalStateException("Expected an optimum, got " + result.getStatus());
            }
            long totalWeight = 0;
            long totalValue = 0;
            for (int i = 0; i < take.length; i++) {
                long selected = result.getAssignment().longValueOf(take[i]);
                if (selected != 0 && selected != 1) {
                    throw new AssertionError("Not a 0/1 choice");
                }
                totalWeight += selected * weights[i];
                totalValue += selected * values[i];
                System.out.println("take" + i + " = " + selected);
            }
            if (totalWeight > 5 || totalValue != 16 || !"16".equals(result.getObjectiveValue().toString())) {
                throw new AssertionError("Wrong knapsack solution");
            }
            System.out.println(result.getStatus() + ": value = " + totalValue);
            return totalValue;
        }
        // --8<-- [end:solve]
    }

    /** Runs the blocking version, then the CompletionStage version. */
    public static void main(String[] args) throws Exception {
        run(false);
        run(true);
    }
}
// --8<-- [end:program]
