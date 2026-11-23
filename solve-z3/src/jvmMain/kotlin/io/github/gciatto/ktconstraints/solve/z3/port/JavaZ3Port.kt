// Z3's Java API accepts operand lists only as varargs, so building them requires a spread copy.
@file:Suppress("SpreadOperator", "UNCHECKED_CAST")

package io.github.gciatto.ktconstraints.solve.z3.port
import com.microsoft.z3.ArithExpr
import com.microsoft.z3.BitVecNum
import com.microsoft.z3.BitVecSort
import com.microsoft.z3.BoolExpr
import com.microsoft.z3.Context
import com.microsoft.z3.Expr
import com.microsoft.z3.IntNum
import com.microsoft.z3.IntSort
import com.microsoft.z3.Model
import com.microsoft.z3.Status
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecOperator
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecRelation
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.z3.session.Z3Outcome
import io.github.gciatto.ktconstraints.solve.z3.translation.exactRational
import org.gciatto.kt.math.BigInteger

/** Z3 Java API binding of one context and solver; confined to its owner's thread. */
@Suppress("TooManyFunctions") // One override per Z3Port operation; splitting would only scatter the binding.
internal class JavaZ3Port(
    private val context: Context,
) : Z3Port<Expr<*>> {
    private val solver = context.mkSolver()
    private var model: Model? = null

    override fun boolConst(name: String): Expr<*> = context.mkBoolConst(name)

    override fun intConst(name: String): Expr<*> = context.mkIntConst(name)

    override fun bool(value: Boolean): Expr<*> = context.mkBool(value)

    override fun int(value: BigInteger): Expr<*> = context.mkInt(value.toString())

    override fun realConst(name: String): Expr<*> = context.mkRealConst(name)

    override fun real(value: Rational): Expr<*> = context.mkReal(value.toString())

    override fun toReal(operand: Expr<*>): Expr<*> = context.mkInt2Real(operand as Expr<IntSort>)

    override fun bitVecConst(
        name: String,
        width: Int,
    ): Expr<*> = context.mkBVConst(name, width)

    override fun bitVec(
        value: BigInteger,
        width: Int,
    ): Expr<*> = context.mkBV(value.toString(), width)

    override fun bitVecBinary(
        operator: BitVecOperator,
        left: Expr<*>,
        right: Expr<*>,
    ): Expr<*> {
        val a = left as Expr<BitVecSort>
        val b = right as Expr<BitVecSort>
        return when (operator) {
            BitVecOperator.ADD -> context.mkBVAdd(a, b)
            BitVecOperator.SUBTRACT -> context.mkBVSub(a, b)
            BitVecOperator.MULTIPLY -> context.mkBVMul(a, b)
            BitVecOperator.AND -> context.mkBVAND(a, b)
            BitVecOperator.OR -> context.mkBVOR(a, b)
            BitVecOperator.XOR -> context.mkBVXOR(a, b)
            BitVecOperator.SHIFT_LEFT -> context.mkBVSHL(a, b)
            BitVecOperator.LOGICAL_SHIFT_RIGHT -> context.mkBVLSHR(a, b)
            BitVecOperator.ARITHMETIC_SHIFT_RIGHT -> context.mkBVASHR(a, b)
        }
    }

    override fun bitVecNot(operand: Expr<*>): Expr<*> = context.mkBVNot(operand as Expr<BitVecSort>)

    override fun bitVecNegate(operand: Expr<*>): Expr<*> = context.mkBVNeg(operand as Expr<BitVecSort>)

    override fun bitVecCompare(
        relation: BitVecRelation,
        left: Expr<*>,
        right: Expr<*>,
    ): Expr<*> {
        val a = left as Expr<BitVecSort>
        val b = right as Expr<BitVecSort>
        return when (relation) {
            BitVecRelation.EQUAL -> context.mkEq(a, b)
            BitVecRelation.UNSIGNED_LESS_THAN -> context.mkBVULT(a, b)
            BitVecRelation.UNSIGNED_LESS_OR_EQUAL -> context.mkBVULE(a, b)
            BitVecRelation.SIGNED_LESS_THAN -> context.mkBVSLT(a, b)
            BitVecRelation.SIGNED_LESS_OR_EQUAL -> context.mkBVSLE(a, b)
        }
    }

    override fun bitVecToInt(
        operand: Expr<*>,
        signed: Boolean,
    ): Expr<*> = context.mkBV2Int(operand as Expr<BitVecSort>, signed)

    override fun add(operands: List<Expr<*>>): Expr<*> = context.mkAdd(*ints(operands))

    override fun subtract(
        left: Expr<*>,
        right: Expr<*>,
    ): Expr<*> = context.mkSub(int(left), int(right))

    override fun multiply(operands: List<Expr<*>>): Expr<*> = context.mkMul(*ints(operands))

    override fun negate(operand: Expr<*>): Expr<*> = context.mkUnaryMinus(int(operand))

    override fun and(operands: List<Expr<*>>): Expr<*> = context.mkAnd(*bools(operands))

    override fun or(operands: List<Expr<*>>): Expr<*> = context.mkOr(*bools(operands))

    override fun not(operand: Expr<*>): Expr<*> = context.mkNot(operand as BoolExpr)

    override fun implies(
        left: Expr<*>,
        right: Expr<*>,
    ): Expr<*> = context.mkImplies(left as BoolExpr, right as BoolExpr)

    override fun equal(
        left: Expr<*>,
        right: Expr<*>,
    ): Expr<*> = context.mkEq(left, right)

    override fun less(
        left: Expr<*>,
        right: Expr<*>,
    ): Expr<*> = context.mkLt(int(left), int(right))

    override fun lessOrEqual(
        left: Expr<*>,
        right: Expr<*>,
    ): Expr<*> = context.mkLe(int(left), int(right))

    override fun ite(
        condition: Expr<*>,
        then: Expr<*>,
        otherwise: Expr<*>,
    ): Expr<*> = context.mkITE(condition as BoolExpr, then, otherwise)

    override fun distinct(operands: List<Expr<*>>): Expr<*> = context.mkDistinct(*ints(operands))

    override fun atMost(
        operands: List<Expr<*>>,
        count: Int,
    ): Expr<*> = context.mkAtMost(bools(operands), count)

    override fun atLeast(
        operands: List<Expr<*>>,
        count: Int,
    ): Expr<*> = context.mkAtLeast(bools(operands), count)

    override fun exactly(
        operands: List<Expr<*>>,
        count: Int,
    ): Expr<*> = context.mkPBEq(IntArray(operands.size) { 1 }, bools(operands), count)

    override fun assert(constraints: List<Expr<*>>) = solver.add(*bools(constraints))

    override fun push() = solver.push()

    override fun pop(levels: Int) = solver.pop(levels)

    override suspend fun check(
        assumptions: List<Expr<*>>,
        timeoutMillis: Long?,
    ): Z3Outcome {
        val timeout = timeoutMillis?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: Int.MAX_VALUE
        solver.setParameters(context.mkParams().apply { add("timeout", timeout) })
        model = null
        return when (solver.check(*bools(assumptions))) {
            Status.SATISFIABLE -> Z3Outcome(CheckStatus.SATISFIABLE).also { model = solver.model }
            Status.UNSATISFIABLE -> Z3Outcome(CheckStatus.UNSATISFIABLE)
            else -> Z3Outcome(CheckStatus.UNKNOWN, solver.reasonUnknown)
        }
    }

    override fun boolValue(term: Expr<*>): Boolean = checkNotNull(model).eval(term, true).isTrue

    override fun intValue(term: Expr<*>): BigInteger = BigInteger.of((checkNotNull(model).eval(term, true) as IntNum).toString())

    override fun realValue(term: Expr<*>): Rational =
        exactRational(
            checkNotNull(model).eval(term, true).let {
                if (it.isRatNum) it.toString() else null
            },
        )

    override fun bitVecValue(term: Expr<*>): BigInteger = BigInteger.of((checkNotNull(model).eval(term, true) as BitVecNum).toString())

    override fun unsatCore(assumptions: List<Expr<*>>): Set<Int> {
        val core = solver.unsatCore.toSet()
        return assumptions.indices.filterTo(linkedSetOf()) { assumptions[it] in core }
    }

    private fun int(term: Expr<*>): ArithExpr<IntSort> = term as ArithExpr<IntSort>

    private fun ints(terms: List<Expr<*>>): Array<ArithExpr<IntSort>> = terms.map { int(it) }.toTypedArray()

    private fun bools(terms: List<Expr<*>>): Array<BoolExpr> = terms.map { it as BoolExpr }.toTypedArray()
}
