package io.github.gciatto.ktconstraints.solve.kotlin.runtime

import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.encoding.IntegerEncoding
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear.IntegerLinear
import io.github.gciatto.ktconstraints.solve.kotlin.search.OptimizationState
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchBudget
import io.github.gciatto.ktconstraints.solve.result.SolveMetadata
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.support.rejectRealObjective
import org.gciatto.kt.math.BigInteger

/**
 * One integer-objective optimization over a [KotlinSession]'s engine: the bisection of batch streams, except that the
 * objective's definedness and every bound are assumptions rather than clauses, so nothing outlives the call but
 * definitions and learned clauses, which stay valid.
 */
internal class SessionOptimization(
    objective: Objective,
    private val descriptor: SolverDescriptor,
    private val outputs: List<Variable>,
) {
    private val expression: IntExpr =
        when (objective) {
            is Objective.Minimize -> objective.expression
            is Objective.Maximize -> objective.expression
            is Objective.MinimizeReal, is Objective.MaximizeReal -> rejectRealObjective()
        }
    private val maximize = objective.maximize
    private var state: OptimizationState? = null
    private var best: Assignment? = null
    private var value: BigInteger? = null

    suspend fun run(
        session: KotlinSession,
        assumptions: List<BoolExpr>,
        budget: SearchBudget,
    ): SolveResult {
        val (engine, literals) = session.active(assumptions, budget)
        val compiled = engine.translation.translate(expression, budget)
        val (min, max) =
            when (val term = compiled.value) {
                is IntegerLinear -> term.min to term.max
                else -> (term as IntegerEncoding).let { it.min to it.max }
            }
        val search = OptimizationState(maximize, if (maximize) max else -min).also { state = it }
        val base = literals + compiled.defined
        while (!search.proven) {
            budget.checkpoint()
            val target = search.target()
            val bound = target?.let { atLeast(engine, compiled.value, it) }
            val full = engine.check(if (bound == null) base else base + bound, budget)
            if (full == null) {
                if (target == null) return result(SolveStatus.INFEASIBLE, TerminationReason.COMPLETED)
                search.refuted(target)
            } else {
                session.validate(engine, full, assumptions)
                val found = GroundEvaluator.evaluate(expression, full)
                search.improved(found)
                best = full.project(outputs)
                value = found
            }
        }
        return result(SolveStatus.OPTIMAL, TerminationReason.COMPLETED)
    }

    /** The answer when a limit or cancellation stopped [run]: the incumbent, if any, with the bound proven so far. */
    fun stopped(reason: TerminationReason): SolveResult = result(if (best == null) SolveStatus.UNKNOWN else SolveStatus.FEASIBLE, reason)

    /** The objective is at least the score [target], in maximization terms: minimization negates the score. */
    private suspend fun atLeast(
        engine: KotlinEngine,
        objective: Any,
        target: BigInteger,
    ): Int {
        val linear = engine.translation.linear
        val binary = engine.translation.binary
        return if (maximize) {
            linear.lessOrEqual(
                binary.integer(target),
                objective,
            )
        } else {
            linear.lessOrEqual(objective, binary.integer(-target))
        }
    }

    private fun result(
        status: SolveStatus,
        reason: TerminationReason,
    ): SolveResult =
        SolveResult(
            status,
            best,
            descriptor,
            reason,
            value,
            if (status == SolveStatus.OPTIMAL) value else state?.takeIf { best != null }?.bestBound,
            SolveMetadata(statistics = mapOf("technique" to "sat")),
        )
}
