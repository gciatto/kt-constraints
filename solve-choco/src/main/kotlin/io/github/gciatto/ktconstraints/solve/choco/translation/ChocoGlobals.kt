package io.github.gciatto.ktconstraints.solve.choco.translation
import io.github.gciatto.ktconstraints.core.global.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.global.extensional.Automaton
import io.github.gciatto.ktconstraints.core.global.extensional.Regular
import io.github.gciatto.ktconstraints.core.global.graph.Circuit
import io.github.gciatto.ktconstraints.core.global.graph.Inverse
import io.github.gciatto.ktconstraints.core.global.graph.SubCircuit
import io.github.gciatto.ktconstraints.core.global.scheduling.BinPacking
import io.github.gciatto.ktconstraints.core.global.scheduling.Cumulative
import io.github.gciatto.ktconstraints.core.global.scheduling.NoOverlap2D
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import org.chocosolver.solver.Model
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.constraints.nary.automata.FA.FiniteAutomaton
import org.chocosolver.solver.variables.Task
import org.gciatto.kt.math.BigInteger
import org.chocosolver.solver.variables.IntVar as ChocoIntVar

/** Choco's native forms of top-level structural globals, given each operand's Choco variable. */
internal class ChocoGlobals(
    private val model: Model,
    private val variable: (IntExpr) -> ChocoIntVar,
) {
    /** The native constraint, or null where the decomposition must be posted instead. */
    fun native(expression: DecomposableConstraint): Constraint? =
        when (expression) {
            is Inverse -> inverse(expression)
            is Circuit -> if (expression.successors.size > 1) model.circuit(variables(expression.successors), 0) else null
            is SubCircuit ->
                expression.successors.takeIf { it.size > 1 }?.let {
                    model.subCircuit(
                        variables(it),
                        0,
                        model.intVar(0, it.size),
                    )
                }
            is Cumulative -> if (expression.starts.isEmpty()) null else cumulative(expression)
            is Regular -> regular(expression)
            is BinPacking -> binPacking(expression)
            is NoOverlap2D -> noOverlap2D(expression)
            else -> null
        }

    private fun variables(operands: List<IntExpr>): Array<ChocoIntVar> = operands.map(variable).toTypedArray()

    private fun constants(values: List<BigInteger>): Array<ChocoIntVar> = values.map { model.intVar(it.toIntExact()) }.toTypedArray()

    private fun inverse(expression: Inverse): Constraint =
        if (expression.direct.isEmpty()) {
            model.trueConstraint()
        } else {
            model.inverseChanneling(variables(expression.direct), variables(expression.inverse), 0, 0)
        }

    private fun cumulative(expression: Cumulative): Constraint =
        model.cumulative(
            expression.starts.zip(expression.durations) { start, duration -> Task(variable(start), duration.toIntExact()) }.toTypedArray(),
            constants(expression.demands),
            model.intVar(expression.capacity.toIntExact()),
        )

    private fun binPacking(expression: BinPacking): Constraint? =
        if (expression.bins.isEmpty() || expression.loads.isEmpty()) {
            null
        } else {
            model.binPacking(
                variables(expression.bins),
                expression.sizes.map { it.toIntExact() }.toIntArray(),
                variables(expression.loads),
                0,
            )
        }

    private fun noOverlap2D(expression: NoOverlap2D): Constraint? =
        if (expression.xs.size < 2) {
            null
        } else {
            model.diffN(
                variables(expression.xs),
                variables(expression.ys),
                constants(expression.widths),
                constants(expression.heights),
                true,
            )
        }

    /** Native only for alphabets Choco automata can read; operands are narrowed into that alphabet. */
    private fun regular(expression: Regular): Constraint? {
        val automaton = expression.automaton
        val readable = automaton.transitions.isNotEmpty() && automaton.transitions.all { it.symbol.signum >= 0 && it.symbol <= LAST_SYMBOL }
        return if (expression.sequence.isEmpty() || !readable) {
            null
        } else {
            model.regular(expression.sequence.map { alphabetic(variable(it)) }.toTypedArray(), finiteAutomaton(automaton))
        }
    }

    /** [symbol] itself, or an equal copy narrowed into the readable alphabet; an operand entirely outside it fails. */
    private fun alphabetic(symbol: ChocoIntVar): ChocoIntVar {
        val low = maxOf(symbol.lb, 0)
        val high = minOf(symbol.ub, LAST_SYMBOL_VALUE)
        return when {
            low == symbol.lb && high == symbol.ub -> symbol
            low > high -> model.intVar(0).also { model.falseConstraint().post() }
            else -> model.intVar(low, high).also { model.arithm(it, "=", symbol).post() }
        }
    }

    private fun finiteAutomaton(automaton: Automaton): FiniteAutomaton =
        FiniteAutomaton().apply {
            repeat(automaton.states) { addState() }
            setInitialState(automaton.initial)
            automaton.accepting.forEach { setFinal(it) }
            automaton.transitions.forEach { addTransition(it.from, it.to, it.symbol.toIntExact()) }
        }

    private companion object {
        /** Choco automata read symbols in `0..65535` only. */
        const val LAST_SYMBOL_VALUE = 65_535
        val LAST_SYMBOL: BigInteger = BigInteger.of(LAST_SYMBOL_VALUE)
    }
}
