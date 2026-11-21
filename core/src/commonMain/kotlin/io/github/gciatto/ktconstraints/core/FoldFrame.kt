package io.github.gciatto.ktconstraints.core

/** One pending node of [fold] with the results of its operands folded so far. */
internal class FoldFrame<R>(
    val node: Expr,
) {
    val children: List<Expr> = node.children
    val results: MutableList<R> = ArrayList(children.size)
}
