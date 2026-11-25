package io.github.gciatto.ktconstraints.solve.z3.translation
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bitvector.BitVecLiteral
import io.github.gciatto.ktconstraints.core.bitvector.BitVecToInt
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecBinary
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNegation
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNot
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolImplies
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.evaluation.fold
import io.github.gciatto.ktconstraints.core.global.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMax
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMin
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealDifference
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealNegation
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealScale
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealSum
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/** Platform-independent assessment of the IR subset every Z3 route translates exactly. */
internal object Z3Support {
    fun report(model: ConstraintModel): SupportReport = report(roots(model))

    fun report(roots: List<Expr>): SupportReport {
        val unsupported = linkedSetOf<String>()
        val decompositions = linkedSetOf<String>()
        for (root in roots) {
            root.fold { node, _: List<Unit> ->
                when (node) {
                    is AllDifferent -> decompositions += "allDifferent -> distinct"
                    is Element -> decompositions += "element -> ite chain with a top-level index definedness constraint"
                    is Table -> decompositions += "table -> disjunction of tuple conjunctions"
                    is BoolCardinality -> decompositions += "cardinality -> pseudo-Boolean constraint"
                    is IntMin, is IntMax -> decompositions += "min/max -> ite chain"
                    is BoolToInt -> decompositions += "0/1 conversion -> ite"
                    is DecomposableConstraint -> decompositions += "${node::class.simpleName} -> its exact decomposition"
                    is BoolVar, is IntVar, is BoolLiteral, is IntLiteral, is IntSum, is IntDifference, is IntProduct,
                    is IntNegation, is IntEquality, is IntLessThan, is IntLessOrEqual, is BoolAnd, is BoolOr,
                    is BoolNot, is BoolImplies, is BoolEquality, is LinearSum, is RealVar, is RealLiteral, is RealSum,
                    is RealDifference, is RealNegation, is RealScale, is IntToReal, is RealEquality, is RealLessThan,
                    is RealLessOrEqual, is BitVecVar, is BitVecLiteral, is BitVecBinary, is BitVecNot, is BitVecNegation,
                    is BitVecComparison, is BitVecToInt,
                    -> Unit
                    else -> unsupported += "Unsupported expression: ${node::class.simpleName ?: node}"
                }
            }
        }
        return SupportReport(unsupported, decompositions)
    }

    private fun roots(model: ConstraintModel): List<Expr> =
        model.variables + model.constraints +
            listOfNotNull(model.objective?.expression)
}
