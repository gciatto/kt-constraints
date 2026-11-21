package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration
import io.github.gciatto.ktconstraints.solve.kotlin.SearchBudget
import io.github.gciatto.ktconstraints.solve.kotlin.SearchStopped
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatSolver

/** Reusable, fully reified Boolean gates. Literals are signed one-based SAT variable IDs. */
internal class Circuit(
    val solver: SatSolver,
    private val configuration: KotlinConfiguration,
    private var budget: SearchBudget,
) {
    private val gates = mutableMapOf<GateKey, Int>()

    var variables: Int = 0
        private set

    var clauses: Int = 0
        private set

    val trueLiteral: Int = variable().also { clause(it) }
    val falseLiteral: Int get() = -trueLiteral

    fun useBudget(budget: SearchBudget) {
        this.budget = budget
    }

    suspend fun checkpoint() = budget.checkpoint()

    fun variable(): Int {
        budget.consume()
        if (variables >= configuration.maxVariables) throw SearchStopped(TerminationReason.RESOURCE_LIMIT)
        variables++
        return solver.newVariable()
    }

    fun clause(vararg literals: Int) {
        budget.consume()
        if (clauses >= configuration.maxClauses) throw SearchStopped(TerminationReason.RESOURCE_LIMIT)
        clauses++
        solver.addClause(literals)
    }

    fun width(width: Int) {
        if (width <= 0 || width > configuration.maxBitWidth) throw SearchStopped(TerminationReason.RESOURCE_LIMIT)
    }

    fun constant(value: Boolean): Int = if (value) trueLiteral else falseLiteral

    fun and(
        left: Int,
        right: Int,
    ): Int {
        budget.consume()
        if (left == falseLiteral || right == falseLiteral || left == -right) return falseLiteral
        if (left == trueLiteral || left == right) return right
        if (right == trueLiteral) return left
        return gates.getOrPut(GateKey(0, minOf(left, right), maxOf(left, right))) {
            variable().also { output ->
                clause(-output, left)
                clause(-output, right)
                clause(output, -left, -right)
            }
        }
    }

    fun or(
        left: Int,
        right: Int,
    ): Int = -and(-left, -right)

    fun xor(
        left: Int,
        right: Int,
    ): Int {
        budget.consume()
        if (left == right) return falseLiteral
        if (left == -right) return trueLiteral
        if (left == falseLiteral) return right
        if (right == falseLiteral) return left
        if (left == trueLiteral) return -right
        if (right == trueLiteral) return -left
        return gates.getOrPut(GateKey(1, minOf(left, right), maxOf(left, right))) {
            variable().also { output ->
                clause(-left, -right, -output)
                clause(left, right, -output)
                clause(left, -right, output)
                clause(-left, right, output)
            }
        }
    }

    fun choose(
        condition: Int,
        whenTrue: Int,
        whenFalse: Int,
    ): Int = if (whenTrue == whenFalse) whenTrue else or(and(condition, whenTrue), and(-condition, whenFalse))

    fun all(literals: Iterable<Int>): Int = literals.fold(trueLiteral, ::and)

    fun any(literals: Iterable<Int>): Int = literals.fold(falseLiteral, ::or)

    fun value(
        literal: Int,
        values: BooleanArray,
    ): Boolean = if (literal > 0) values[literal] else !values[-literal]
}
