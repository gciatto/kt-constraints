package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.model.ConstraintModel

/** Both branches are individually feasible; their conjunction must be infeasible to verify restoration. */
internal class KotlinBenchmarkCase(
    val name: String,
    val model: ConstraintModel,
    val pushed: BoolExpr,
    val restored: BoolExpr,
)
