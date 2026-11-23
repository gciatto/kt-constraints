package io.github.gciatto.ktconstraints.test.solve.benchmark
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.store.ConstraintStore
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.store.StoreStatus
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Manual CLP-style benchmark: labels [queens]-queens by depth-first search over [store] marks, timing each store
 * operation, then times rebuilding and batch-solving the model with [batch] at every depth of the found solution.
 * Returns raw per-operation means; these are observations, not regression thresholds.
 */
suspend fun benchmarkStore(
    queens: Int,
    store: () -> ConstraintStore,
    batch: () -> ConstraintSolver,
): String {
    val timings = linkedMapOf<String, MutableList<Duration>>()

    suspend fun <T> timed(
        operation: String,
        block: suspend () -> T,
    ): T {
        val started = TimeSource.Monotonic.markNow()
        return block().also { timings.getOrPut(operation) { mutableListOf() } += started.elapsedNow() }
    }

    val solution =
        store().use { target ->
            val xs = timed("declare") { List(queens) { target.intVar("q$it", IntDomain.of(0, queens.toLong() - 1)) } }
            queensConstraints(xs).forEach { timed("constrain") { target.constrain(it) } }
            // Track the labeling: a store without propagation keeps the declared domains.
            val values = MutableList(queens) { 0 }

            suspend fun label(index: Int): Boolean {
                if (index == queens) return true
                for (value in 0 until queens) {
                    val mark = timed("mark") { target.mark() }
                    val bound = timed("bind") { target.constrain(IntEquality(xs[index], IntLiteral(value))) }
                    values[index] = value
                    if (bound.status != StoreStatus.FAILED && label(index + 1)) return true
                    timed("undo") { target.undoTo(mark) }
                }
                return false
            }
            check(label(0)) { "$queens-queens has no solution" }
            xs.zip(values).forEach { (x, value) ->
                check(timed("entails") { target.entails(IntEquality(x, IntLiteral(value))) }.value == Entailment.ENTAILED)
            }
            timed("project") { target.project(xs.take(2)) }
            values
        }

    for (depth in 0..queens) {
        timed("rebuild-and-solve") {
            val scope = ConstraintScope.empty()
            val xs = List(queens) { scope.intVar("q$it", 0 until queens) }
            queensConstraints(xs).forEach { scope.add(it) }
            xs.zip(solution).take(depth).forEach { (x, value) -> scope.add(IntEquality(x, IntLiteral(value))) }
            batch().use { check(it.solve(scope.build()).status == SolveStatus.FEASIBLE) }
        }
    }
    return timings.entries.joinToString("\n") { (operation, samples) ->
        val mean = samples.fold(Duration.ZERO) { total, sample -> total + sample } / samples.size
        "$operation\tcount=${samples.size}\tmean=$mean"
    }
}

private fun queensConstraints(xs: List<IntExpr>): List<BoolExpr> =
    xs.indices.flatMap { i ->
        (i + 1 until xs.size).flatMap { j ->
            listOf(
                BoolNot(IntEquality(xs[i], xs[j])),
                BoolNot(IntEquality(IntDifference(xs[i], xs[j]), IntLiteral(i - j))),
                BoolNot(IntEquality(IntDifference(xs[i], xs[j]), IntLiteral(j - i))),
            )
        }
    }
