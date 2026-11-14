package io.github.gciatto.ktconstraints.dsl

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLiteral
import org.gciatto.kt.math.BigInteger

/** Creates a symbolic integer equality. */
infix fun IntExpr.eq(other: IntExpr): BoolExpr = IntEquality(this, other)

/** Creates a symbolic integer equality with an exact literal. */
infix fun IntExpr.eq(value: Int): BoolExpr = eq(IntLiteral(value))

/** Creates a symbolic integer equality with an exact literal. */
infix fun IntExpr.eq(value: Long): BoolExpr = eq(IntLiteral(value))

/** Creates a symbolic integer equality with an exact literal. */
infix fun IntExpr.eq(value: BigInteger): BoolExpr = eq(IntLiteral(value))

/** Creates a symbolic integer inequality. */
infix fun IntExpr.neq(other: IntExpr): BoolExpr = BoolNot(IntEquality(this, other))

/** Creates a symbolic integer inequality with an exact literal. */
infix fun IntExpr.neq(value: Int): BoolExpr = neq(IntLiteral(value))

/** Creates a symbolic integer inequality with an exact literal. */
infix fun IntExpr.neq(value: Long): BoolExpr = neq(IntLiteral(value))

/** Creates a symbolic integer inequality with an exact literal. */
infix fun IntExpr.neq(value: BigInteger): BoolExpr = neq(IntLiteral(value))
