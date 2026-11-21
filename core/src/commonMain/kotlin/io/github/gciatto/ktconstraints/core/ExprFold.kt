package io.github.gciatto.ktconstraints.core

/**
 * Folds operands before their parent, preserving operand order and visiting shared subtrees per occurrence.
 * The traversal keeps its own stack, so expression depth is bounded by memory rather than by the call stack.
 */
fun <R> Expr.fold(combine: (Expr, List<R>) -> R): R {
    val frames = ArrayDeque<FoldFrame<R>>()
    frames.addLast(FoldFrame(this))
    while (true) {
        val frame = frames.last()
        if (frame.results.size < frame.children.size) {
            frames.addLast(FoldFrame(frame.children[frame.results.size]))
            continue
        }
        frames.removeLast()
        val result = combine(frame.node, frame.results)
        if (frames.isEmpty()) return result
        frames.last().results += result
    }
}
