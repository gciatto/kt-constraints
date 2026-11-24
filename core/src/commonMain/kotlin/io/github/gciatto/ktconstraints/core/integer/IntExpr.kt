package io.github.gciatto.ktconstraints.core.integer

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Sort
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.core.real.RealExpr
import org.gciatto.kt.math.BigInteger

/** Mathematical integer expression. */
interface IntExpr : Expr {
    override val sort: Sort get() = Sort.Integer

    /** Creates a symbolic integer sum. */
    operator fun plus(other: IntExpr): IntExpr = IntSum(this, other)

    /** Creates a symbolic integer sum with an exact literal. */
    operator fun plus(value: Int): IntExpr = plus(IntLiteral(value))

    /** Creates a symbolic integer sum with an exact literal. */
    operator fun plus(value: Long): IntExpr = plus(IntLiteral(value))

    /** Creates a symbolic integer sum with an exact literal. */
    operator fun plus(value: BigInteger): IntExpr = plus(IntLiteral(value))

    /** Creates a symbolic integer difference. */
    operator fun minus(other: IntExpr): IntExpr = IntDifference(this, other)

    /** Creates a symbolic integer difference with an exact literal. */
    operator fun minus(value: Int): IntExpr = minus(IntLiteral(value))

    /** Creates a symbolic integer difference with an exact literal. */
    operator fun minus(value: Long): IntExpr = minus(IntLiteral(value))

    /** Creates a symbolic integer difference with an exact literal. */
    operator fun minus(value: BigInteger): IntExpr = minus(IntLiteral(value))

    /** Creates a symbolic integer product. */
    operator fun times(other: IntExpr): IntExpr = IntProduct(this, other)

    /** Creates a symbolic integer product with an exact literal. */
    operator fun times(value: Int): IntExpr = times(IntLiteral(value))

    /** Creates a symbolic integer product with an exact literal. */
    operator fun times(value: Long): IntExpr = times(IntLiteral(value))

    /** Creates a symbolic integer product with an exact literal. */
    operator fun times(value: BigInteger): IntExpr = times(IntLiteral(value))

    /** Creates a symbolic integer equality. */
    infix fun eq(other: IntExpr): BoolExpr = IntEquality(this, other)

    /** Creates a symbolic integer equality with an exact literal. */
    infix fun eq(value: Int): BoolExpr = eq(IntLiteral(value))

    /** Creates a symbolic integer equality with an exact literal. */
    infix fun eq(value: Long): BoolExpr = eq(IntLiteral(value))

    /** Creates a symbolic integer equality with an exact literal. */
    infix fun eq(value: BigInteger): BoolExpr = eq(IntLiteral(value))

    /** Creates a symbolic integer inequality. */
    infix fun neq(other: IntExpr): BoolExpr = BoolNot(IntEquality(this, other))

    /** Creates a symbolic integer inequality with an exact literal. */
    infix fun neq(value: Int): BoolExpr = neq(IntLiteral(value))

    /** Creates a symbolic integer inequality with an exact literal. */
    infix fun neq(value: Long): BoolExpr = neq(IntLiteral(value))

    /** Creates a symbolic integer inequality with an exact literal. */
    infix fun neq(value: BigInteger): BoolExpr = neq(IntLiteral(value))

    /** Creates a symbolic integer strict ordering. */
    infix fun lt(other: IntExpr): BoolExpr = IntLessThan(this, other)

    /** Creates a symbolic integer strict ordering with an exact literal. */
    infix fun lt(value: Int): BoolExpr = lt(IntLiteral(value))

    /** Creates a symbolic integer strict ordering with an exact literal. */
    infix fun lt(value: Long): BoolExpr = lt(IntLiteral(value))

    /** Creates a symbolic integer strict ordering with an exact literal. */
    infix fun lt(value: BigInteger): BoolExpr = lt(IntLiteral(value))

    /** Creates a symbolic integer non-strict ordering. */
    infix fun le(other: IntExpr): BoolExpr = IntLessOrEqual(this, other)

    /** Creates a symbolic integer non-strict ordering with an exact literal. */
    infix fun le(value: Int): BoolExpr = le(IntLiteral(value))

    /** Creates a symbolic integer non-strict ordering with an exact literal. */
    infix fun le(value: Long): BoolExpr = le(IntLiteral(value))

    /** Creates a symbolic integer non-strict ordering with an exact literal. */
    infix fun le(value: BigInteger): BoolExpr = le(IntLiteral(value))

    /** Creates a symbolic integer strict reverse ordering. */
    infix fun gt(other: IntExpr): BoolExpr = IntLessThan(other, this)

    /** Creates a symbolic integer strict reverse ordering with an exact literal. */
    infix fun gt(value: Int): BoolExpr = gt(IntLiteral(value))

    /** Creates a symbolic integer strict reverse ordering with an exact literal. */
    infix fun gt(value: Long): BoolExpr = gt(IntLiteral(value))

    /** Creates a symbolic integer strict reverse ordering with an exact literal. */
    infix fun gt(value: BigInteger): BoolExpr = gt(IntLiteral(value))

    /** Creates a symbolic integer non-strict reverse ordering. */
    infix fun ge(other: IntExpr): BoolExpr = IntLessOrEqual(other, this)

    /** Creates a symbolic integer non-strict reverse ordering with an exact literal. */
    infix fun ge(value: Int): BoolExpr = ge(IntLiteral(value))

    /** Creates a symbolic integer non-strict reverse ordering with an exact literal. */
    infix fun ge(value: Long): BoolExpr = ge(IntLiteral(value))

    /** Creates a symbolic integer non-strict reverse ordering with an exact literal. */
    infix fun ge(value: BigInteger): BoolExpr = ge(IntLiteral(value))

    /** Creates an exact symbolic additive inverse. */
    operator fun unaryMinus(): IntExpr = IntNegation(this)

    /** Embeds this integer expression into the reals explicitly. */
    fun toReal(): RealExpr = IntToReal(this)
}
