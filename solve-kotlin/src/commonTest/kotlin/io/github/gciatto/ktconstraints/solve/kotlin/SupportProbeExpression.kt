package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.Expr

/** Records support traversal without allowing structural hashing or equality of source expressions. */
internal class SupportProbeExpression(
    private val onChildrenRead: () -> Unit,
) : BoolExpr {
    override val children: List<Expr>
        get() {
            onChildrenRead()
            return emptyList()
        }

    override fun equals(other: Any?): Boolean = error("Support inspection must compare expressions by reference")

    override fun hashCode(): Int = error("Support inspection must not hash expressions")
}
