package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BitVecBinary
import io.github.gciatto.ktconstraints.core.BitVecComparison
import io.github.gciatto.ktconstraints.core.BitVecExpr
import io.github.gciatto.ktconstraints.core.BitVecLiteral
import io.github.gciatto.ktconstraints.core.BitVecNegation
import io.github.gciatto.ktconstraints.core.BitVecNot
import io.github.gciatto.ktconstraints.core.BitVecToInt
import io.github.gciatto.ktconstraints.core.BitVecVar
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolCardinality
import io.github.gciatto.ktconstraints.core.BoolEquality
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolImplies
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.BoolNot
import io.github.gciatto.ktconstraints.core.BoolOr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntToReal
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealDifference
import io.github.gciatto.ktconstraints.core.RealEquality
import io.github.gciatto.ktconstraints.core.RealExpr
import io.github.gciatto.ktconstraints.core.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.RealLessThan
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.core.RealNegation
import io.github.gciatto.ktconstraints.core.RealScale
import io.github.gciatto.ktconstraints.core.RealSum
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.kotlin.SearchBudget
import org.gciatto.kt.math.BigInteger

/** Exact translation into reusable Boolean circuits and linear-real atoms; no search occurs here. */
internal class KotlinTranslation(val circuit: Circuit) {
    val binary = BinaryArithmetic(circuit, circuit::checkpoint)
    val realAtoms = mutableListOf<RealAtom>()
    private val cache = CompilationCache()
    private val booleans = linkedMapOf<BoolVar, Int>()
    private val integers = linkedMapOf<IntVar, IntegerEncoding>()
    private val bitVectors = linkedMapOf<BitVecVar, IntArray>()
    private val reals = linkedMapOf<RealVar, Int>()

    val realCount: Int get() = reals.size

    suspend fun declare(model: ConstraintModel, domains: Map<IntVar, IntDomain>, budget: SearchBudget) {
        for (variable in model.variables) {
            budget.checkpoint()
            when (variable) {
                is BoolVar -> booleans[variable] = circuit.variable()
                is IntVar -> integers[variable] = binary.variable(domains.getValue(variable))
                is BitVecVar -> {
                    circuit.width(variable.width)
                    bitVectors[variable] = IntArray(variable.width) { circuit.variable() }
                }
                is RealVar -> reals[variable] = reals.size
                else -> error("Unsupported declaration")
            }
        }
    }

    suspend fun assertion(expression: BoolExpr, budget: SearchBudget): Int {
        val term = translate(expression, budget)
        return circuit.and(term.value as Int, term.defined)
    }

    suspend fun translate(expression: Expr, budget: SearchBudget): CompiledTerm {
        cache[expression]?.let { return it }
        val stack = ArrayDeque<CompilationFrame>()
        stack.addLast(CompilationFrame(expression))
        while (true) {
            budget.checkpoint()
            val frame = stack.last()
            if (frame.results.size < frame.children.size) {
                val child = frame.children[frame.results.size]
                val known = cache[child]
                if (known == null) stack.addLast(CompilationFrame(child)) else frame.results += known
            } else {
                val term = compile(frame.node, frame.results)
                cache.put(frame.node, term)
                stack.removeLast()
                if (stack.isEmpty()) return term
                stack.last().results += term
            }
        }
    }

    fun assignment(values: BooleanArray, realValues: List<Rational>): Assignment =
        Assignment.of(
            booleans.mapValues { circuit.value(it.value, values) },
            integers.mapValues { binary.decodeInteger(it.value, values) },
            reals.mapValues { realValues[it.value] },
            bitVectors.mapValues { binary.decodeBits(it.value, values) },
        )

    private suspend fun compile(node: Expr, terms: List<CompiledTerm>): CompiledTerm {
        var defined = circuit.all(terms.map { it.defined })
        val children = terms.map { it.value }
        val value = when (node) {
            is BoolExpr -> boolean(node, children)
            is BitVecExpr -> bitVector(node, children)
            is RealExpr -> real(node, children)
            is IntExpr -> integer(node, children)
            else -> error("Unsupported expression")
        }
        if (node is Element) {
            val index = children.first() as IntegerEncoding
            val lower = -binary.less(index, binary.integer(BigInteger.ZERO))
            val upper = binary.less(index, binary.integer(BigInteger.of(node.values.size)))
            defined = circuit.and(defined, circuit.and(lower, upper))
        }
        return CompiledTerm(value, defined)
    }

    private suspend fun integer(node: IntExpr, values: List<Any>): IntegerEncoding = when (node) {
        is IntVar -> integers.getValue(node)
        is IntLiteral -> binary.integer(node.value)
        is IntSum -> binary.add(values[0] as IntegerEncoding, values[1] as IntegerEncoding)
        is IntDifference -> binary.subtract(values[0] as IntegerEncoding, values[1] as IntegerEncoding)
        is IntProduct -> binary.multiply(values[0] as IntegerEncoding, values[1] as IntegerEncoding)
        is IntNegation -> binary.negate(values.single() as IntegerEncoding)
        is BitVecToInt -> binary.toInteger(values.single() as IntArray, node.signed)
        is LinearSum -> values.indices.fold(binary.integer(node.constant)) { sum, index ->
            binary.add(sum, binary.multiply(values[index] as IntegerEncoding, binary.integer(node.coefficients[index])))
        }
        is Element -> {
            val index = values.first() as IntegerEncoding
            var result = values.last() as IntegerEncoding
            for (position in node.values.size - 2 downTo 0) {
                result = binary.choose(
                    binary.equal(index, binary.integer(BigInteger.of(position))),
                    values[position + 1] as IntegerEncoding,
                    result,
                )
            }
            result
        }
        else -> error("Unsupported integer expression")
    }

    private suspend fun bitVector(node: BitVecExpr, values: List<Any>): IntArray = when (node) {
        is BitVecVar -> bitVectors.getValue(node)
        is BitVecLiteral -> binary.bits(node.value, node.width)
        is BitVecBinary -> binary.bitVector(node.operator, values[0] as IntArray, values[1] as IntArray)
        is BitVecNot -> binary.bitNot(values.single() as IntArray)
        is BitVecNegation -> binary.bitNegate(values.single() as IntArray)
        else -> error("Unsupported bit-vector expression")
    }

    private fun real(node: RealExpr, values: List<Any>): LinearForm = when (node) {
        is RealVar -> LinearForm(realCoefficients = mapOf(reals.getValue(node) to Rational.ONE))
        is RealLiteral -> LinearForm(constant = node.value)
        is RealSum -> (values[0] as LinearForm) + (values[1] as LinearForm)
        is RealDifference -> (values[0] as LinearForm) + (values[1] as LinearForm).scale(Rational.of(-1))
        is RealNegation -> (values.single() as LinearForm).scale(Rational.of(-1))
        is RealScale -> (values.single() as LinearForm).scale(node.coefficient)
        is IntToReal -> embed(values.single() as IntegerEncoding)
        else -> error("Unsupported real expression")
    }

    private suspend fun boolean(node: BoolExpr, values: List<Any>): Int = when (node) {
        is BoolVar -> booleans.getValue(node)
        is BoolLiteral -> circuit.constant(node.value)
        is BoolNot -> -(values.single() as Int)
        is BoolAnd -> circuit.and(values[0] as Int, values[1] as Int)
        is BoolOr -> circuit.or(values[0] as Int, values[1] as Int)
        is BoolImplies -> circuit.or(-(values[0] as Int), values[1] as Int)
        is BoolEquality -> -circuit.xor(values[0] as Int, values[1] as Int)
        is IntEquality -> binary.equal(values[0] as IntegerEncoding, values[1] as IntegerEncoding)
        is IntLessThan -> binary.less(values[0] as IntegerEncoding, values[1] as IntegerEncoding)
        is IntLessOrEqual -> -binary.less(values[1] as IntegerEncoding, values[0] as IntegerEncoding)
        is BitVecComparison -> binary.bitCompare(node.relation, values[0] as IntArray, values[1] as IntArray)
        is RealEquality, is RealLessThan, is RealLessOrEqual -> realRelation(node, values)
        else -> global(node, values)
    }

    private suspend fun global(node: BoolExpr, values: List<Any>): Int = when (node) {
        is AllDifferent -> {
            var result = circuit.trueLiteral
            for (left in values.indices) {
                for (right in left + 1 until values.size) {
                    result = circuit.and(result, -binary.equal(values[left] as IntegerEncoding, values[right] as IntegerEncoding))
                }
            }
            result
        }
        is Table -> circuit.any(node.tuples.map { row ->
            circuit.all(values.indices.map { binary.equal(values[it] as IntegerEncoding, binary.integer(row[it])) })
        })
        is BoolCardinality -> {
            val sum = values.fold(binary.integer(BigInteger.ZERO)) { total, literal ->
                binary.add(total, IntegerEncoding(intArrayOf(literal as Int, circuit.falseLiteral), BigInteger.ZERO, BigInteger.ONE))
            }
            val bound = binary.integer(BigInteger.of(node.count))
            when (node.relation) {
                CardinalityRelation.EXACTLY -> binary.equal(sum, bound)
                CardinalityRelation.AT_MOST -> -binary.less(bound, sum)
                CardinalityRelation.AT_LEAST -> -binary.less(sum, bound)
            }
        }
        else -> error("Unsupported Boolean expression")
    }

    private fun realRelation(node: BoolExpr, values: List<Any>): Int {
        val difference = (values[0] as LinearForm) + (values[1] as LinearForm).scale(Rational.of(-1))
        return if (node is RealEquality) {
            circuit.and(atom(difference, false), atom(difference.scale(Rational.of(-1)), false))
        } else {
            atom(difference, node is RealLessThan)
        }
    }

    private fun atom(form: LinearForm, strict: Boolean): Int {
        if (form.realCoefficients.isEmpty() && form.bitCoefficients.isEmpty()) {
            return circuit.constant(if (strict) form.constant < Rational.ZERO else form.constant <= Rational.ZERO)
        }
        return circuit.variable().also { realAtoms += RealAtom(it, form, strict) }
    }

    private fun embed(integer: IntegerEncoding): LinearForm {
        var result = LinearForm()
        for (index in integer.bits.indices) {
            val magnitude = BigInteger.ONE shl index
            val coefficient = Rational.of(if (index == integer.bits.lastIndex) -magnitude else magnitude)
            val literal = integer.bits[index]
            result += when (literal) {
                circuit.trueLiteral -> LinearForm(constant = coefficient)
                circuit.falseLiteral -> LinearForm()
                else -> if (literal > 0) LinearForm(bitCoefficients = mapOf(literal to coefficient)) else {
                    LinearForm(bitCoefficients = mapOf(-literal to -coefficient), constant = coefficient)
                }
            }
        }
        return result
    }
}
