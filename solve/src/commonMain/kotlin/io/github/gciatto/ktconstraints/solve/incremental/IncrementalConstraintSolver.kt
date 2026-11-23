package io.github.gciatto.ktconstraints.solve.incremental
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver

/** Batch solver that can also open incremental sessions; closing it closes them. */
interface IncrementalConstraintSolver : ConstraintSolver {
    /** Opens a session whose root frame holds [model]'s constraints over its declared variables. */
    fun openSession(model: ConstraintModel): IncrementalSession
}
