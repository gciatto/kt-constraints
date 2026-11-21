package io.github.gciatto.ktconstraints.core

/** Immutable symbolic expression, independent of any backend. */
interface Expr {
    /** Sort of this expression. */
    val sort: Sort

    /** Immediate operands, in semantic order; extension nodes must expose their operands here. */
    val children: List<Expr> get() = emptyList()

    /**
     * Free symbolic variables occurring in this expression, in first-occurrence order. Only leaves are asked for their
     * own variables; a compound node contributes those of its [children]. The walk keeps its own stack.
     */
    val variables: Set<Variable>
        get() {
            val found = linkedSetOf<Variable>()
            val pending = ArrayDeque(children.asReversed())
            while (pending.isNotEmpty()) {
                val node = pending.removeLast()
                val operands = node.children
                if (operands.isEmpty()) found += node.variables else pending += operands.asReversed()
            }
            return found
        }
}
