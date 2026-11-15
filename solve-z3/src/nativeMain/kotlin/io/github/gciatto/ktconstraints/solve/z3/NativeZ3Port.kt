@file:OptIn(ExperimentalForeignApi::class)

package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.core.BitVecOperator
import io.github.gciatto.ktconstraints.core.BitVecRelation
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.solve.CheckStatus
import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_ast
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_astVar
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_ast_vector_dec_ref
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_ast_vector_get
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_ast_vector_inc_ref
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_ast_vector_size
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_context
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_get_bool_value
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_get_error_code
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_get_error_msg
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_get_numeral_string
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_is_eq_ast
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_is_numeral_ast
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_add
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_and
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_atleast
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_atmost
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bool_sort
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bv2int
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bv_sort
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvadd
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvand
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvashr
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvlshr
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvmul
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvneg
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvnot
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvor
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvshl
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvsle
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvslt
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvsub
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvule
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvult
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_bvxor
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_const
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_distinct
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_eq
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_false
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_implies
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_int2real
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_int_sort
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_ite
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_le
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_lt
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_mul
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_not
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_numeral
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_or
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_params
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_pbeq
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_real_sort
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_solver
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_string_symbol
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_sub
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_true
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_mk_unary_minus
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_model
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_model_dec_ref
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_model_eval
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_model_inc_ref
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_params_dec_ref
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_params_inc_ref
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_params_set_uint
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_assert
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_check_assumptions
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_dec_ref
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_get_model
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_get_reason_unknown
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_get_unsat_core
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_inc_ref
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_pop
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_push
import io.github.gciatto.ktconstraints.solve.z3.capi.Z3_solver_set_params
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import org.gciatto.kt.math.BigInteger

/** Z3 C API binding of one context and solver; confined to its owner's thread, every call checked for Z3 errors. */
@Suppress("TooManyFunctions") // One override per Z3Port operation; splitting would only scatter the binding.
internal class NativeZ3Port(
    private val context: Z3_context,
) : Z3Port<Z3_ast> {
    private val boolSort = checked(Z3_mk_bool_sort(context))
    private val intSort = checked(Z3_mk_int_sort(context))
    private val realSort = checked(Z3_mk_real_sort(context))
    private val solver = checkNotNull(checked(Z3_mk_solver(context))).also { Z3_solver_inc_ref(context, it) }
    private var model: Z3_model? = null

    override fun boolConst(name: String): Z3_ast = ast(Z3_mk_const(context, Z3_mk_string_symbol(context, name), boolSort))

    override fun intConst(name: String): Z3_ast = ast(Z3_mk_const(context, Z3_mk_string_symbol(context, name), intSort))

    override fun bool(value: Boolean): Z3_ast = ast(if (value) Z3_mk_true(context) else Z3_mk_false(context))

    override fun int(value: BigInteger): Z3_ast = ast(Z3_mk_numeral(context, value.toString(), intSort))

    override fun realConst(name: String): Z3_ast = ast(Z3_mk_const(context, Z3_mk_string_symbol(context, name), realSort))

    override fun real(value: Rational): Z3_ast = ast(Z3_mk_numeral(context, value.toString(), realSort))

    override fun toReal(operand: Z3_ast): Z3_ast = ast(Z3_mk_int2real(context, operand))

    override fun bitVecConst(
        name: String,
        width: Int,
    ): Z3_ast = ast(Z3_mk_const(context, Z3_mk_string_symbol(context, name), Z3_mk_bv_sort(context, width.toUInt())))

    override fun bitVec(
        value: BigInteger,
        width: Int,
    ): Z3_ast = ast(Z3_mk_numeral(context, value.toString(), Z3_mk_bv_sort(context, width.toUInt())))

    override fun bitVecBinary(
        operator: BitVecOperator,
        left: Z3_ast,
        right: Z3_ast,
    ): Z3_ast =
        ast(
            when (operator) {
                BitVecOperator.ADD -> Z3_mk_bvadd(context, left, right)
                BitVecOperator.SUBTRACT -> Z3_mk_bvsub(context, left, right)
                BitVecOperator.MULTIPLY -> Z3_mk_bvmul(context, left, right)
                BitVecOperator.AND -> Z3_mk_bvand(context, left, right)
                BitVecOperator.OR -> Z3_mk_bvor(context, left, right)
                BitVecOperator.XOR -> Z3_mk_bvxor(context, left, right)
                BitVecOperator.SHIFT_LEFT -> Z3_mk_bvshl(context, left, right)
                BitVecOperator.LOGICAL_SHIFT_RIGHT -> Z3_mk_bvlshr(context, left, right)
                BitVecOperator.ARITHMETIC_SHIFT_RIGHT -> Z3_mk_bvashr(context, left, right)
            },
        )

    override fun bitVecNot(operand: Z3_ast): Z3_ast = ast(Z3_mk_bvnot(context, operand))

    override fun bitVecNegate(operand: Z3_ast): Z3_ast = ast(Z3_mk_bvneg(context, operand))

    override fun bitVecCompare(
        relation: BitVecRelation,
        left: Z3_ast,
        right: Z3_ast,
    ): Z3_ast =
        ast(
            when (relation) {
                BitVecRelation.EQUAL -> Z3_mk_eq(context, left, right)
                BitVecRelation.UNSIGNED_LESS_THAN -> Z3_mk_bvult(context, left, right)
                BitVecRelation.UNSIGNED_LESS_OR_EQUAL -> Z3_mk_bvule(context, left, right)
                BitVecRelation.SIGNED_LESS_THAN -> Z3_mk_bvslt(context, left, right)
                BitVecRelation.SIGNED_LESS_OR_EQUAL -> Z3_mk_bvsle(context, left, right)
            },
        )

    override fun bitVecToInt(
        operand: Z3_ast,
        signed: Boolean,
    ): Z3_ast = ast(Z3_mk_bv2int(context, operand, signed))

    override fun add(operands: List<Z3_ast>): Z3_ast = ast(Z3_mk_add(context, operands.size.toUInt(), operands.toCValues()))

    override fun subtract(
        left: Z3_ast,
        right: Z3_ast,
    ): Z3_ast = ast(Z3_mk_sub(context, 2u, listOf(left, right).toCValues()))

    override fun multiply(operands: List<Z3_ast>): Z3_ast = ast(Z3_mk_mul(context, operands.size.toUInt(), operands.toCValues()))

    override fun negate(operand: Z3_ast): Z3_ast = ast(Z3_mk_unary_minus(context, operand))

    override fun and(operands: List<Z3_ast>): Z3_ast = ast(Z3_mk_and(context, operands.size.toUInt(), operands.toCValues()))

    override fun or(operands: List<Z3_ast>): Z3_ast = ast(Z3_mk_or(context, operands.size.toUInt(), operands.toCValues()))

    override fun not(operand: Z3_ast): Z3_ast = ast(Z3_mk_not(context, operand))

    override fun implies(
        left: Z3_ast,
        right: Z3_ast,
    ): Z3_ast = ast(Z3_mk_implies(context, left, right))

    override fun equal(
        left: Z3_ast,
        right: Z3_ast,
    ): Z3_ast = ast(Z3_mk_eq(context, left, right))

    override fun less(
        left: Z3_ast,
        right: Z3_ast,
    ): Z3_ast = ast(Z3_mk_lt(context, left, right))

    override fun lessOrEqual(
        left: Z3_ast,
        right: Z3_ast,
    ): Z3_ast = ast(Z3_mk_le(context, left, right))

    override fun ite(
        condition: Z3_ast,
        then: Z3_ast,
        otherwise: Z3_ast,
    ): Z3_ast = ast(Z3_mk_ite(context, condition, then, otherwise))

    override fun distinct(operands: List<Z3_ast>): Z3_ast = ast(Z3_mk_distinct(context, operands.size.toUInt(), operands.toCValues()))

    override fun atMost(
        operands: List<Z3_ast>,
        count: Int,
    ): Z3_ast = ast(Z3_mk_atmost(context, operands.size.toUInt(), operands.toCValues(), count.toUInt()))

    override fun atLeast(
        operands: List<Z3_ast>,
        count: Int,
    ): Z3_ast = ast(Z3_mk_atleast(context, operands.size.toUInt(), operands.toCValues(), count.toUInt()))

    override fun exactly(
        operands: List<Z3_ast>,
        count: Int,
    ): Z3_ast = ast(Z3_mk_pbeq(context, operands.size.toUInt(), operands.toCValues(), IntArray(operands.size) { 1 }.toCValues(), count))

    override fun assert(constraints: List<Z3_ast>) = constraints.forEach { checked(Z3_solver_assert(context, solver, it)) }

    override fun push() = checked(Z3_solver_push(context, solver))

    override fun pop(levels: Int) = checked(Z3_solver_pop(context, solver, levels.toUInt()))

    override suspend fun check(
        assumptions: List<Z3_ast>,
        timeoutMillis: Long?,
    ): Z3Outcome {
        setTimeout(timeoutMillis?.coerceIn(0L, UInt.MAX_VALUE.toLong())?.toUInt() ?: UInt.MAX_VALUE)
        releaseModel()
        val status = checked(Z3_solver_check_assumptions(context, solver, assumptions.size.toUInt(), assumptions.toCValues()))
        return when (status) {
            LBOOL_TRUE -> {
                model = checked(Z3_solver_get_model(context, solver))?.also { Z3_model_inc_ref(context, it) }
                Z3Outcome(CheckStatus.SATISFIABLE)
            }
            LBOOL_FALSE -> Z3Outcome(CheckStatus.UNSATISFIABLE)
            else -> Z3Outcome(CheckStatus.UNKNOWN, checked(Z3_solver_get_reason_unknown(context, solver))?.toKString() ?: "unknown")
        }
    }

    override fun boolValue(term: Z3_ast): Boolean = Z3_get_bool_value(context, evaluate(term)) == LBOOL_TRUE

    override fun intValue(term: Z3_ast): BigInteger =
        BigInteger.of(checkNotNull(checked(Z3_get_numeral_string(context, evaluate(term)))).toKString())

    override fun realValue(term: Z3_ast): Rational {
        val value = evaluate(term)
        return exactRational(if (Z3_is_numeral_ast(context, value)) checked(Z3_get_numeral_string(context, value))?.toKString() else null)
    }

    override fun bitVecValue(term: Z3_ast): BigInteger =
        BigInteger.of(checkNotNull(checked(Z3_get_numeral_string(context, evaluate(term)))).toKString())

    override fun unsatCore(assumptions: List<Z3_ast>): Set<Int> {
        val core = checkNotNull(checked(Z3_solver_get_unsat_core(context, solver)))
        Z3_ast_vector_inc_ref(context, core)
        try {
            val members = List(Z3_ast_vector_size(context, core).toInt()) { Z3_ast_vector_get(context, core, it.toUInt()) }
            return assumptions.indices.filterTo(linkedSetOf()) { index ->
                members.any { it != null && Z3_is_eq_ast(context, it, assumptions[index]) }
            }
        } finally {
            Z3_ast_vector_dec_ref(context, core)
        }
    }

    /** Releases the solver and the last model; the owner deletes the context afterwards. */
    fun release() {
        releaseModel()
        Z3_solver_dec_ref(context, solver)
    }

    private fun setTimeout(millis: UInt) {
        val parameters = checkNotNull(checked(Z3_mk_params(context)))
        Z3_params_inc_ref(context, parameters)
        try {
            checked(Z3_params_set_uint(context, parameters, Z3_mk_string_symbol(context, "timeout"), millis))
            checked(Z3_solver_set_params(context, solver, parameters))
        } finally {
            Z3_params_dec_ref(context, parameters)
        }
    }

    private fun releaseModel() {
        model?.let { Z3_model_dec_ref(context, it) }
        model = null
    }

    private fun evaluate(term: Z3_ast): Z3_ast =
        memScoped {
            val value = alloc<Z3_astVar>()
            check(checked(Z3_model_eval(context, checkNotNull(model), term, true, value.ptr))) { "Z3 could not evaluate a term" }
            checkNotNull(value.value)
        }

    private fun ast(result: Z3_ast?): Z3_ast = checkNotNull(checked(result)) { "Z3 returned no term" }

    /** Z3's error handler is disabled, so every call must be followed by an explicit error check. */
    private fun <R> checked(result: R): R {
        val code = Z3_get_error_code(context)
        if (code != 0) throw BackendExecutionException("Z3 error $code: ${Z3_get_error_msg(context, code)?.toKString()}")
        return result
    }

    private companion object {
        const val LBOOL_TRUE = 1
        const val LBOOL_FALSE = -1
    }
}
