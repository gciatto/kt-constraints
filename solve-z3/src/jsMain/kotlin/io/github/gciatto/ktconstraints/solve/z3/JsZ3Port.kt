package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.BitVecOperator
import io.github.gciatto.ktconstraints.core.BitVecRelation
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.CheckStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asDeferred
import kotlinx.coroutines.withContext
import org.gciatto.kt.math.BigInteger
import kotlin.js.Promise

/** Binding of one high-level z3-solver context and solver; terms are opaque JS objects. */
@Suppress("TooManyFunctions") // One override per Z3Port operation; splitting would only scatter the binding.
internal class JsZ3Port(
    private val context: dynamic,
) : Z3Port<Any> {
    private val solver: dynamic = js("Reflect.construct")(context.Solver, emptyArray<Any>())
    private var model: dynamic = null

    override fun boolConst(name: String): Any = context.Bool.const(name)

    override fun intConst(name: String): Any = context.Int.const(name)

    override fun bool(value: Boolean): Any = context.Bool.`val`(value)

    override fun int(value: BigInteger): Any = context.Int.`val`(value.toString())

    override fun realConst(name: String): Any = context.Real.const(name)

    override fun real(value: Rational): Any = context.Real.`val`(value.toString())

    override fun toReal(operand: Any): Any = context.ToReal(operand)

    override fun bitVecConst(
        name: String,
        width: Int,
    ): Any = context.BitVec.const(name, width)

    override fun bitVec(
        value: BigInteger,
        width: Int,
    ): Any = context.BitVec.`val`(value.toString(), width)

    override fun bitVecBinary(
        operator: BitVecOperator,
        left: Any,
        right: Any,
    ): Any {
        val a = left.asDynamic()
        return when (operator) {
            BitVecOperator.ADD -> a.add(right)
            BitVecOperator.SUBTRACT -> a.sub(right)
            BitVecOperator.MULTIPLY -> a.mul(right)
            BitVecOperator.AND -> a.and(right)
            BitVecOperator.OR -> a.or(right)
            BitVecOperator.XOR -> a.xor(right)
            BitVecOperator.SHIFT_LEFT -> a.shl(right)
            BitVecOperator.LOGICAL_SHIFT_RIGHT -> a.lshr(right)
            BitVecOperator.ARITHMETIC_SHIFT_RIGHT -> a.shr(right)
        }.unsafeCast<Any>()
    }

    override fun bitVecNot(operand: Any): Any = operand.asDynamic().not()

    override fun bitVecNegate(operand: Any): Any = operand.asDynamic().neg()

    override fun bitVecCompare(
        relation: BitVecRelation,
        left: Any,
        right: Any,
    ): Any {
        val a = left.asDynamic()
        return when (relation) {
            BitVecRelation.EQUAL -> a.eq(right)
            BitVecRelation.UNSIGNED_LESS_THAN -> a.ult(right)
            BitVecRelation.UNSIGNED_LESS_OR_EQUAL -> a.ule(right)
            BitVecRelation.SIGNED_LESS_THAN -> a.slt(right)
            BitVecRelation.SIGNED_LESS_OR_EQUAL -> a.sle(right)
        }.unsafeCast<Any>()
    }

    override fun bitVecToInt(
        operand: Any,
        signed: Boolean,
    ): Any = context.BV2Int(operand, signed)

    override fun add(operands: List<Any>): Any = operands.reduce { left, right -> left.asDynamic().add(right) }

    override fun subtract(
        left: Any,
        right: Any,
    ): Any = left.asDynamic().sub(right)

    override fun multiply(operands: List<Any>): Any = operands.reduce { left, right -> left.asDynamic().mul(right) }

    override fun negate(operand: Any): Any = operand.asDynamic().neg()

    override fun and(operands: List<Any>): Any = spread(context.And, operands)

    override fun or(operands: List<Any>): Any = spread(context.Or, operands)

    override fun not(operand: Any): Any = context.Not(operand)

    override fun implies(
        left: Any,
        right: Any,
    ): Any = context.Implies(left, right)

    override fun equal(
        left: Any,
        right: Any,
    ): Any = context.Eq(left, right)

    override fun less(
        left: Any,
        right: Any,
    ): Any = left.asDynamic().lt(right)

    override fun lessOrEqual(
        left: Any,
        right: Any,
    ): Any = left.asDynamic().le(right)

    override fun ite(
        condition: Any,
        then: Any,
        otherwise: Any,
    ): Any = context.If(condition, then, otherwise)

    override fun distinct(operands: List<Any>): Any = spread(context.Distinct, operands)

    override fun atMost(
        operands: List<Any>,
        count: Int,
    ): Any = context.AtMost(operands.toTypedArray(), count)

    override fun atLeast(
        operands: List<Any>,
        count: Int,
    ): Any = context.AtLeast(operands.toTypedArray(), count)

    override fun exactly(
        operands: List<Any>,
        count: Int,
    ): Any = context.PbEq(operands.toTypedArray(), Array(operands.size) { 1 }, count)

    override fun assert(constraints: List<Any>) {
        if (constraints.isNotEmpty()) spread(solver.add, constraints, solver)
    }

    override fun push() {
        solver.push()
    }

    override fun pop(levels: Int) {
        solver.pop(levels)
    }

    override suspend fun check(
        assumptions: List<Any>,
        timeoutMillis: Long?,
    ): Z3Outcome {
        // Z3's timeout parameter is an unsigned 32-bit count of milliseconds; its maximum means no limit.
        solver.set("timeout", (timeoutMillis?.coerceAtMost(MAX_TIMEOUT) ?: MAX_TIMEOUT).toDouble())
        model = null
        val promise: Promise<String> = spread(solver.check, assumptions, solver).unsafeCast<Promise<String>>()
        val pending = promise.asDeferred()
        val result =
            try {
                pending.await()
            } catch (cancellation: CancellationException) {
                // The native check keeps running on a worker thread; release the context only once it has stopped.
                context.interrupt()
                withContext(NonCancellable) { runCatching { pending.await() } }
                throw cancellation
            }
        return when (result) {
            "sat" -> Z3Outcome(CheckStatus.SATISFIABLE).also { model = solver.model() }
            "unsat" -> Z3Outcome(CheckStatus.UNSATISFIABLE)
            else -> Z3Outcome(CheckStatus.UNKNOWN, solver.reasonUnknown().unsafeCast<String>())
        }
    }

    override fun boolValue(term: Any): Boolean = context.isTrue(checkNotNull(model).eval(term, true)).unsafeCast<Boolean>()

    override fun intValue(term: Any): BigInteger =
        BigInteger.of(
            checkNotNull(model)
                .eval(term, true)
                .value()
                .toString()
                .unsafeCast<String>(),
        )

    override fun realValue(term: Any): Rational {
        val value: dynamic = checkNotNull(model).eval(term, true)
        return exactRational(if (context.isRealVal(value).unsafeCast<Boolean>()) value.asString().unsafeCast<String>() else null)
    }

    override fun bitVecValue(term: Any): BigInteger =
        BigInteger.of(
            checkNotNull(model)
                .eval(term, true)
                .asString()
                .unsafeCast<String>(),
        )

    override fun unsatCore(assumptions: List<Any>): Set<Int> {
        val core: dynamic = solver.unsatCore()
        val members = (0 until core.length().unsafeCast<Int>()).map { core.get(it) }
        return assumptions.indices.filterTo(linkedSetOf()) { index ->
            members.any { member -> member.eqIdentity(assumptions[index]).unsafeCast<Boolean>() }
        }
    }

    /** Interrupts an active native check from the JS thread. */
    fun interrupt() {
        context.interrupt()
    }

    private fun spread(
        function: dynamic,
        operands: List<Any>,
        receiver: dynamic = context,
    ): dynamic = function.apply(receiver, operands.toTypedArray())

    private companion object {
        const val MAX_TIMEOUT: Long = 4_294_967_295L
    }
}
