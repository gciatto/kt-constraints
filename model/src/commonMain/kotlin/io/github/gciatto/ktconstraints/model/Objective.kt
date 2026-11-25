package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.real.RealExpr

/**
 * Optimization objective, separate from satisfiability constraints. Integer objectives ([Minimize], [Maximize]) report
 * integer values; real objectives ([MinimizeReal], [MaximizeReal]) report exact rational values.
 */
sealed interface Objective {
    /** The optimized expression. */
    val expression: Expr

    /** Whether larger values are better. */
    val maximize: Boolean

    /** Minimize an integer expression. */
    data class Minimize(
        override val expression: IntExpr,
    ) : Objective {
        override val maximize: Boolean get() = false
    }

    /** Maximize an integer expression. */
    data class Maximize(
        override val expression: IntExpr,
    ) : Objective {
        override val maximize: Boolean get() = true
    }

    /** Minimize an exact real expression. */
    data class MinimizeReal(
        override val expression: RealExpr,
    ) : Objective {
        override val maximize: Boolean get() = false
    }

    /** Maximize an exact real expression. */
    data class MaximizeReal(
        override val expression: RealExpr,
    ) : Objective {
        override val maximize: Boolean get() = true
    }
}

/** Whether this objective optimizes a real expression, whose values are exact rationals. */
val Objective.isReal: Boolean get() = this is Objective.MinimizeReal || this is Objective.MaximizeReal
