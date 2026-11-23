package io.github.gciatto.ktconstraints.solve.choco.runtime
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.solve.store.StoreMark
import org.chocosolver.solver.constraints.Constraint

/**
 * What a [ChocoStore] ([owner]) added since this frame opened, which is also its mark: variables, source constraints, and every Choco
 * constraint created meanwhile, including reifications Choco posts permanently. [world] is the environment index
 * to pop back to, and [failedBefore] the failure state when the mark was taken.
 */
internal class ChocoFrame(
    val owner: Any,
    val world: Int,
    val failedBefore: Boolean,
) : StoreMark {
    val variables = mutableListOf<Variable>()
    val constraints = mutableListOf<BoolExpr>()
    val natives = mutableListOf<Constraint>()
}
