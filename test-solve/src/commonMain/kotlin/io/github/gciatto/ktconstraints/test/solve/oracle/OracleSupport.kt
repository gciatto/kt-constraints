package io.github.gciatto.ktconstraints.test.solve.oracle
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolImplies
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.evaluation.fold
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Regular
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.global.graph.Circuit
import io.github.gciatto.ktconstraints.core.global.graph.Inverse
import io.github.gciatto.ktconstraints.core.global.graph.SubCircuit
import io.github.gciatto.ktconstraints.core.global.scheduling.BinPacking
import io.github.gciatto.ktconstraints.core.global.scheduling.Cumulative
import io.github.gciatto.ktconstraints.core.global.scheduling.NoOverlap2D
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
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
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.isReal
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.support.UNSUPPORTED_REAL_OBJECTIVE

internal fun requireOracleSupported(model: ConstraintModel) {
    val errors = mutableListOf<String>()
    if (model.variables.size > FiniteOracle.MAX_VARIABLES) errors += "Oracle variable cap exceeded"
    if (model.objective?.isReal == true) errors += UNSUPPORTED_REAL_OBJECTIVE
    val roots: List<Expr> =
        model.variables + model.constraints +
            listOfNotNull(model.objective?.expression)
    for (root in roots) {
        root.fold { node, _: List<Unit> ->
            if (!knownOracleNode(node)) errors += "Unsupported expression: ${node::class.simpleName}"
        }
    }
    if (errors.isNotEmpty()) throw UnsupportedModelException(SupportReport(errors.distinct()))
}

private fun knownOracleNode(node: Expr): Boolean =
    when (node) {
        is BoolVar, is IntVar, is BoolLiteral, is IntLiteral, is IntSum, is IntDifference, is IntProduct,
        is IntNegation, is IntEquality, is IntLessThan, is IntLessOrEqual, is BoolAnd, is BoolOr,
        is BoolNot, is BoolImplies, is BoolEquality, is AllDifferent, is Element, is Table,
        is LinearSum, is BoolCardinality, is IntMin, is IntMax, is BoolToInt, is Inverse, is Circuit, is Cumulative,
        is Regular, is SubCircuit, is BinPacking, is NoOverlap2D,
        -> true
        else -> false
    }
