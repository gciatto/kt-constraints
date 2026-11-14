package io.github.gciatto.ktconstraints.core

/** Folds operands before their parent, preserving operand order and visiting shared subtrees per occurrence. */
fun <R> Expr.fold(combine: (Expr, List<R>) -> R): R = combine(this, children.map { it.fold(combine) })
