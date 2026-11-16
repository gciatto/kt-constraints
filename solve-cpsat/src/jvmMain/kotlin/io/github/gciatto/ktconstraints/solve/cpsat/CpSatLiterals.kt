package io.github.gciatto.ktconstraints.solve.cpsat

import com.google.ortools.sat.Constraint
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.Literal

/** Reification primitives; every fresh literal is posted in both directions, so it is determined by its operands. */
internal class CpSatLiterals(
    private val cpModel: CpModel,
) {
    private var count = 0

    /** Literal equivalent to the constraint built by [positive], whose negation [negative] builds. */
    fun reified(
        positive: () -> Constraint,
        negative: () -> Constraint,
    ): Literal =
        fresh().also {
            positive().onlyEnforceIf(it)
            negative().onlyEnforceIf(it.not())
        }

    /** Literal equivalent to all [literals]; the empty conjunction is true. */
    fun conjunction(literals: List<Literal>): Literal =
        when (literals.size) {
            0 -> cpModel.trueLiteral()
            1 -> literals.single()
            else -> reified({ cpModel.addBoolAnd(literals) }, { cpModel.addBoolOr(literals.map { it.not() }) })
        }

    /** Literal equivalent to any of [literals]; the empty disjunction is false. */
    fun disjunction(literals: List<Literal>): Literal = conjunction(literals.map { it.not() }).not()

    /** Literal equivalent to `left == right`, as `left xor right xor result`. */
    fun equivalence(
        left: Literal,
        right: Literal,
    ): Literal = fresh().also { cpModel.addBoolXor(listOf(left, right, it)) }

    private fun fresh(): Literal = cpModel.newBoolVar("r${count++}")
}
