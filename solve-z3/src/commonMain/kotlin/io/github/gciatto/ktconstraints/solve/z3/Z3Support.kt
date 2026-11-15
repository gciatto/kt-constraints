package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolImplies
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.fold
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.spi.SupportReport

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
                    is BoolVar, is IntVar, is BoolLiteral, is IntLiteral, is IntSum, is IntDifference, is IntProduct,
                    is IntNegation, is IntEquality, is IntLessThan, is IntLessOrEqual, is BoolAnd, is BoolOr,
                    is BoolNot, is BoolImplies, is BoolEquality, is LinearSum,
                    -> Unit
                    else -> unsupported += "Unsupported expression: ${node::class.simpleName ?: node}"
                }
            }
        }
        return SupportReport(unsupported, decompositions)
    }

    private fun roots(model: ConstraintModel): List<Expr> =
        model.variables + model.constraints +
            when (val objective = model.objective) {
                is Objective.Minimize -> listOf(objective.expression)
                is Objective.Maximize -> listOf(objective.expression)
                null -> emptyList()
            }
}
