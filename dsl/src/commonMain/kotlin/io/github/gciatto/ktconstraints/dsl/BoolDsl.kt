package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolExpr

/** Creates a symbolic Boolean conjunction. */
infix fun BoolExpr.and(other: BoolExpr): BoolExpr = BoolAnd(this, other)
