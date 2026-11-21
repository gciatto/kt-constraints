package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BitVecBinary
import io.github.gciatto.ktconstraints.core.BitVecComparison
import io.github.gciatto.ktconstraints.core.BitVecLiteral
import io.github.gciatto.ktconstraints.core.BitVecNegation
import io.github.gciatto.ktconstraints.core.BitVecNot
import io.github.gciatto.ktconstraints.core.BitVecToInt
import io.github.gciatto.ktconstraints.core.BitVecVar
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
import io.github.gciatto.ktconstraints.core.IntToReal
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.RealDifference
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.RealLessThan
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.RealNegation
import io.github.gciatto.ktconstraints.core.RealScale
import io.github.gciatto.ktconstraints.core.RealSum
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import kotlin.reflect.KClass

/** Platform-independent assessment of the IR subset the pure Kotlin backend translates exactly. */
internal object KotlinSupport {
    fun report(model: ConstraintModel): SupportReport = report(roots(model))

    fun report(roots: List<Expr>): SupportReport {
        val unsupported = linkedSetOf<String>()
        val decompositions = linkedSetOf<String>()
        // Source data classes can recursively hash or compare their operands, so membership uses references only.
        val visited = mutableMapOf<KClass<out Expr>, MutableList<Expr>>()
        val pending = ArrayDeque(roots.asReversed())
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            val bucket = visited.getOrPut(node::class) { mutableListOf() }
            if (bucket.any { it === node }) continue
            bucket.add(node)
            when (node) {
                is AllDifferent -> decompositions += "allDifferent -> reified pairwise disequalities"
                is Element -> decompositions += "element -> ite chain with a top-level index definedness constraint"
                is Table -> decompositions += "table -> disjunction of tuple conjunctions"
                is BoolCardinality -> decompositions += "cardinality -> exact binary counting circuit"
                is BoolVar, is IntVar, is BoolLiteral, is IntLiteral, is IntSum, is IntDifference, is IntProduct,
                is IntNegation, is IntEquality, is IntLessThan, is IntLessOrEqual, is BoolAnd, is BoolOr,
                is BoolNot, is BoolImplies, is BoolEquality, is LinearSum, is RealVar, is RealLiteral, is RealSum,
                is RealDifference, is RealNegation, is RealScale, is IntToReal, is RealEquality, is RealLessThan,
                is RealLessOrEqual, is BitVecVar, is BitVecLiteral, is BitVecBinary, is BitVecNot, is BitVecNegation,
                is BitVecComparison, is BitVecToInt,
                -> Unit
                else -> unsupported += "Unsupported expression: ${node::class.simpleName ?: node}"
            }
            pending.addAll(node.children.asReversed())
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
