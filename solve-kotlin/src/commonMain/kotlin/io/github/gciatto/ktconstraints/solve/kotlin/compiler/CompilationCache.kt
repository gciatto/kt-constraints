package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.Expr
import kotlin.reflect.KClass

/** Referential cache: source data-class hashCode/equals may recurse on arbitrarily deep expression trees. */
internal class CompilationCache {
    private val nodes = mutableMapOf<KClass<out Expr>, MutableList<Expr>>()
    private val terms = mutableMapOf<KClass<out Expr>, MutableList<CompiledTerm>>()

    operator fun get(node: Expr): CompiledTerm? {
        val bucket = nodes[node::class] ?: return null
        val index = bucket.indexOfFirst { it === node }
        return if (index < 0) null else terms.getValue(node::class)[index]
    }

    fun put(node: Expr, term: CompiledTerm) {
        nodes.getOrPut(node::class) { mutableListOf() }.add(node)
        terms.getOrPut(node::class) { mutableListOf() }.add(term)
    }
}
