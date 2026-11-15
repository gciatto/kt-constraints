package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.model.ConstraintModel

/** Batch solver that can also open incremental sessions; closing it closes them. */
interface IncrementalConstraintSolver : ConstraintSolver {
    /** Opens a session whose root frame holds [model]'s constraints over its declared variables. */
    fun openSession(model: ConstraintModel): IncrementalSession
}
