package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntVar

/** A tiny model replayed into both a store and the [FiniteOracle], with a [probe] for entailment. */
internal class StoreCase(
    val name: String,
    val domains: List<IntDomain>,
    val constraints: (List<IntVar>) -> List<BoolExpr>,
    val probe: (List<IntVar>) -> BoolExpr,
) {
    override fun toString(): String = name

    companion object {
        /** Orders, an infeasible pigeonhole, a disjunction over holes, and a sum that fixes every variable. */
        fun catalogue(): List<StoreCase> =
            listOf(
                StoreCase(
                    "chain-of-orders",
                    List(3) { IntDomain.of(0, 3) },
                    { (a, b, c) -> listOf(IntLessThan(a, b), IntLessThan(b, c)) },
                    { (a, _, c) -> IntLessOrEqual(IntSum(a, IntLiteral(2)), c) },
                ),
                StoreCase(
                    "pigeons-in-too-few-holes",
                    List(3) { IntDomain.of(0, 1) },
                    { (a, b, c) -> listOf(BoolNot(IntEquality(a, b)), BoolNot(IntEquality(b, c)), BoolNot(IntEquality(a, c))) },
                    { (a, _, _) -> IntEquality(a, IntLiteral(0)) },
                ),
                StoreCase(
                    "disjunction-with-holes",
                    listOf(IntDomain.values(1, 3, 5), IntDomain.of(0, 4)),
                    { (a, b) -> listOf(BoolOr(IntEquality(a, b), IntLessThan(IntSum(a, b), IntLiteral(3)))) },
                    { (a, b) -> IntLessOrEqual(b, a) },
                ),
                StoreCase(
                    "sum-fixes-everything",
                    List(2) { IntDomain.of(0, 5) },
                    { (a, b) -> listOf(IntEquality(IntSum(a, b), IntLiteral(10))) },
                    { (a, _) -> IntEquality(a, IntLiteral(5)) },
                ),
            )
    }
}
