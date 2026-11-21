package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.solve.StoreMark

/** Variables and constraints added since [mark] (the root frame has none), and whether the store had failed then. */
internal class StoreFrame(
    val mark: StoreMark?,
    val failedBefore: Boolean,
) {
    val variables = mutableListOf<Variable>()
    val constraints = mutableListOf<BoolExpr>()
}
