package io.github.gciatto.ktconstraints.solve.z3.port
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecOperator
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecRelation
import io.github.gciatto.ktconstraints.solve.z3.session.Z3Outcome
import org.gciatto.kt.math.BigInteger

/** Platform binding of one Z3 context and solver; [T] is the platform term type. Use only inside its owner's confinement. */
internal interface Z3Port<T> {
    fun boolConst(name: String): T

    fun intConst(name: String): T

    fun bool(value: Boolean): T

    fun realConst(name: String): T

    fun real(value: Rational): T

    /** Exact embedding of an integer term into the reals. */
    fun toReal(operand: T): T

    fun bitVecConst(
        name: String,
        width: Int,
    ): T

    /** Unsigned [value] below 2^[width]. */
    fun bitVec(
        value: BigInteger,
        width: Int,
    ): T

    fun bitVecBinary(
        operator: BitVecOperator,
        left: T,
        right: T,
    ): T

    fun bitVecNot(operand: T): T

    fun bitVecNegate(operand: T): T

    fun bitVecCompare(
        relation: BitVecRelation,
        left: T,
        right: T,
    ): T

    fun bitVecToInt(
        operand: T,
        signed: Boolean,
    ): T

    fun int(value: BigInteger): T

    fun add(operands: List<T>): T

    fun subtract(
        left: T,
        right: T,
    ): T

    fun multiply(operands: List<T>): T

    fun negate(operand: T): T

    fun and(operands: List<T>): T

    fun or(operands: List<T>): T

    fun not(operand: T): T

    fun implies(
        left: T,
        right: T,
    ): T

    fun equal(
        left: T,
        right: T,
    ): T

    fun less(
        left: T,
        right: T,
    ): T

    fun lessOrEqual(
        left: T,
        right: T,
    ): T

    fun ite(
        condition: T,
        then: T,
        otherwise: T,
    ): T

    /** Requires at least two operands. */
    fun distinct(operands: List<T>): T

    /** Requires at least one operand. */
    fun atMost(
        operands: List<T>,
        count: Int,
    ): T

    /** Requires at least one operand. */
    fun atLeast(
        operands: List<T>,
        count: Int,
    ): T

    /** Requires at least one operand. */
    fun exactly(
        operands: List<T>,
        count: Int,
    ): T

    fun assert(constraints: List<T>)

    fun push()

    fun pop(levels: Int)

    /** Checks under [assumptions] (Boolean constants); a null timeout means no native limit. */
    suspend fun check(
        assumptions: List<T>,
        timeoutMillis: Long?,
    ): Z3Outcome

    /** Value of [term] in the model of the last satisfiable check, with model completion. */
    fun boolValue(term: T): Boolean

    /** Exact value of [term] in the model of the last satisfiable check, with model completion. */
    fun intValue(term: T): BigInteger

    /** Exact rational value of a real [term] in the model of the last satisfiable check, with model completion. */
    fun realValue(term: T): Rational

    /** Unsigned value of a bit-vector [term] in the model of the last satisfiable check, with model completion. */
    fun bitVecValue(term: T): BigInteger

    /** Positions of [assumptions] in the core of the last unsatisfiable check. */
    fun unsatCore(assumptions: List<T>): Set<Int>
}
