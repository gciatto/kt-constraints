package io.github.gciatto.ktconstraints.solve.kotlin.search
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bitvector.BitVecLiteral
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecRelation
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.solve.assignment.Assignment

/** Blocks exactly a projected tuple, leaving hidden variables existentially quantified. */
internal object ProjectionBlocker {
    fun block(
        variables: List<Variable>,
        assignment: Assignment,
    ): BoolExpr {
        var result: BoolExpr = BoolLiteral(false)
        for (variable in variables) {
            val equal =
                when (variable) {
                    is BoolVar -> BoolEquality(variable, BoolLiteral(requireNotNull(assignment.valueOf(variable))))
                    is IntVar -> IntEquality(variable, IntLiteral(requireNotNull(assignment.valueOf(variable))))
                    is RealVar -> RealEquality(variable, RealLiteral(requireNotNull(assignment.valueOf(variable))))
                    is BitVecVar ->
                        BitVecComparison(
                            BitVecRelation.EQUAL,
                            variable,
                            BitVecLiteral(requireNotNull(assignment.valueOf(variable)), variable.width),
                        )
                    else -> error("Unsupported projected variable")
                }
            result = BoolOr(result, BoolNot(equal))
        }
        return result
    }
}
