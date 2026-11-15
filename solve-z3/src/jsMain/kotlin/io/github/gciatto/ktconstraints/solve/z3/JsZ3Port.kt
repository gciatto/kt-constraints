package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.solve.CheckStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import org.gciatto.kt.math.BigInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
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
        val result =
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { context.interrupt() }
                promise.then({ continuation.resume(it) }, { continuation.resumeWithException(it) })
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
