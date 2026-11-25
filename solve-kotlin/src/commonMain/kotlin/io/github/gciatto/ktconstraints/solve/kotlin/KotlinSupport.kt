package io.github.gciatto.ktconstraints.solve.kotlin

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
import io.github.gciatto.ktconstraints.model.isReal
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.support.UNSUPPORTED_REAL_OBJECTIVE
import kotlin.reflect.KClass

/** Platform-independent assessment of the IR subset the pure Kotlin backend translates exactly. */
internal object KotlinSupport {
    fun report(model: ConstraintModel): SupportReport {
        val report = report(roots(model))
        // shortcut: real objectives are expressible but not yet optimized here; lift when this route optimizes them.
        return if (model.objective?.isReal ==
            true
        ) {
            SupportReport(report.unsupported + UNSUPPORTED_REAL_OBJECTIVE, report.losslessDecompositions)
        } else {
            report
        }
    }

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
                is DecomposableConstraint -> {
                    decompositions += "${node::class.simpleName} -> variable-free decomposition"
                    pending.addLast(node.decomposition)
                }
                is BoolVar, is IntVar, is BoolLiteral, is IntLiteral, is IntSum, is IntDifference, is IntProduct,
                is IntNegation, is IntEquality, is IntLessThan, is IntLessOrEqual, is BoolAnd, is BoolOr,
                is BoolNot, is BoolImplies, is BoolEquality, is LinearSum, is RealVar, is RealLiteral, is RealSum,
                is RealDifference, is RealNegation, is RealScale, is IntToReal, is RealEquality, is RealLessThan,
                is RealLessOrEqual, is BitVecVar, is BitVecLiteral, is BitVecBinary, is BitVecNot, is BitVecNegation,
                is BitVecComparison, is BitVecToInt, is IntMin, is IntMax, is BoolToInt,
                -> Unit
                else -> unsupported += "Unsupported expression: ${node::class.simpleName ?: node}"
            }
            pending.addAll(node.children.asReversed())
        }
        return SupportReport(unsupported, decompositions)
    }

    private fun roots(model: ConstraintModel): List<Expr> =
        model.variables + model.constraints +
            listOfNotNull(model.objective?.expression)
}
