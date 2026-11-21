package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BitVecComparison
import io.github.gciatto.ktconstraints.core.BitVecLiteral
import io.github.gciatto.ktconstraints.core.BitVecRelation
import io.github.gciatto.ktconstraints.core.BitVecVar
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.solve.Assignment

/** Blocks exactly a projected tuple, leaving hidden variables existentially quantified. */
internal object ProjectionBlocker {
    fun block(variables: List<Variable>, assignment: Assignment): BoolExpr {
        var result: BoolExpr = BoolLiteral(false)
        for (variable in variables) {
            val equal = when (variable) {
                is BoolVar -> BoolEquality(variable, BoolLiteral(requireNotNull(assignment.valueOf(variable))))
                is IntVar -> IntEquality(variable, IntLiteral(requireNotNull(assignment.valueOf(variable))))
                is RealVar -> RealEquality(variable, RealLiteral(requireNotNull(assignment.valueOf(variable))))
                is BitVecVar -> BitVecComparison(
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
