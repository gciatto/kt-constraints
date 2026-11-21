package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BinPacking
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolImplies
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolToInt
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.Circuit
import io.github.gciatto.ktconstraints.core.Cumulative
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntMin
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.Inverse
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.NoOverlap2D
import io.github.gciatto.ktconstraints.core.Regular
import io.github.gciatto.ktconstraints.core.SubCircuit
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.fold
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException

internal fun requireOracleSupported(model: ConstraintModel) {
    val errors = mutableListOf<String>()
    if (model.variables.size > FiniteOracle.MAX_VARIABLES) errors += "Oracle variable cap exceeded"
    val roots: List<Expr> =
        model.variables + model.constraints +
            when (val objective = model.objective) {
                is Objective.Minimize -> listOf(objective.expression)
                is Objective.Maximize -> listOf(objective.expression)
                null -> emptyList()
            }
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
