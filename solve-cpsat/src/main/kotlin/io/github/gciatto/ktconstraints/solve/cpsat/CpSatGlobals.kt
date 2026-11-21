package io.github.gciatto.ktconstraints.solve.cpsat

import com.google.ortools.sat.CpModel
import com.google.ortools.sat.LinearArgument
import com.google.ortools.sat.LinearExpr
import com.google.ortools.sat.Literal
import io.github.gciatto.ktconstraints.core.Circuit
import io.github.gciatto.ktconstraints.core.Cumulative
import io.github.gciatto.ktconstraints.core.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.Inverse
import io.github.gciatto.ktconstraints.core.NoOverlap2D
import io.github.gciatto.ktconstraints.core.Regular
import io.github.gciatto.ktconstraints.core.SubCircuit
import com.google.ortools.sat.IntVar as CpIntVar

/** CP-SAT's native forms of top-level structural globals. */
internal class CpSatGlobals(
    private val model: CpModel,
    private val integer: (IntExpr) -> LinearArgument,
    private val equality: (LinearArgument, LinearArgument) -> Literal,
) {
    /** Posts the native form of [expression]; false where its decomposition must be posted instead. */
    fun post(expression: DecomposableConstraint): Boolean =
        when (expression) {
            is Inverse -> inverse(expression)
            is Circuit -> circuit(expression.successors, selfLoops = false)
            is SubCircuit -> circuit(expression.successors, selfLoops = true)
            is Cumulative -> cumulative(expression)
            is Regular -> regular(expression)
            is NoOverlap2D -> noOverlap2D(expression)
            else -> false
        }

    private fun variable(operand: IntExpr): CpIntVar =
        integer(operand) as? CpIntVar
            ?: CpSatSupport.bounds(operand).let { bounds ->
                model
                    .newIntVar(
                        bounds.min.toLongExact(),
                        bounds.max.toLongExact(),
                        "operand",
                    ).also { model.addEquality(it, integer(operand)) }
            }

    private fun inverse(expression: Inverse): Boolean {
        if (expression.direct.isNotEmpty()) {
            model.addInverse(expression.direct.map(::variable).toTypedArray(), expression.inverse.map(::variable).toTypedArray())
        }
        return true
    }

    /** One arc literal per successor value; self-loops mark the nodes a subcircuit leaves out. */
    private fun circuit(
        successors: List<IntExpr>,
        selfLoops: Boolean,
    ): Boolean {
        if (successors.size < 2) return false
        val circuit = model.addCircuit()
        successors.forEachIndexed { tail, successor ->
            val next = integer(successor)
            successors.indices.filter { it != tail || selfLoops }.forEach { head ->
                circuit.addArc(tail, head, equality(next, LinearExpr.constant(head.toLong())))
            }
        }
        return true
    }

    private fun cumulative(expression: Cumulative): Boolean {
        if (expression.starts.isEmpty()) return true
        val intervals =
            expression.starts.zip(expression.durations) { start, duration ->
                model.newFixedSizeIntervalVar(integer(start), duration.toLongExact(), "task")
            }
        val demands = expression.demands.map { it.toLongExact() }.toLongArray()
        model.addCumulative(expression.capacity.toLongExact()).addDemands(intervals.toTypedArray(), demands)
        return true
    }

    private fun regular(expression: Regular): Boolean {
        val automaton = expression.automaton
        if (expression.sequence.isEmpty() || automaton.transitions.isEmpty()) return false
        val constraint =
            model.addAutomaton(
                expression.sequence.map(integer),
                automaton.initial.toLong(),
                automaton.accepting.map { it.toLong() }.toLongArray(),
            )
        automaton.transitions.forEach { constraint.addTransition(it.from, it.to, it.symbol.toLongExact()) }
        return true
    }

    private fun noOverlap2D(expression: NoOverlap2D): Boolean {
        if (expression.xs.size < 2) return true
        val rectangles = model.addNoOverlap2D()
        expression.xs.indices.forEach {
            rectangles.addRectangle(
                model.newFixedSizeIntervalVar(integer(expression.xs[it]), expression.widths[it].toLongExact(), "x"),
                model.newFixedSizeIntervalVar(integer(expression.ys[it]), expression.heights[it].toLongExact(), "y"),
            )
        }
        return true
    }
}
