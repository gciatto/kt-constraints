package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.Expr

/** Explicit traversal frame, avoiding call-stack limits for deep source expressions. */
internal class CompilationFrame(val node: Expr) {
    val children: List<Expr> = node.children
    val results: MutableList<CompiledTerm> = mutableListOf()
}
