package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.core.Expr
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bitvector.BitVecExpr
import io.github.gciatto.ktconstraints.core.bitvector.BitVecLiteral
import io.github.gciatto.ktconstraints.core.bitvector.BitVecToInt
import io.github.gciatto.ktconstraints.core.bitvector.BitVecVar
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecBinary
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNegation
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecNot
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolImplies
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.bool.BoolOr
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.counting.BoolCardinality
import io.github.gciatto.ktconstraints.core.global.counting.CardinalityRelation
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Table
import io.github.gciatto.ktconstraints.core.integer.IntExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntDifference
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMax
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMin
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntNegation
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.arithmetic.LinearSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.core.real.RealExpr
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealDifference
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealNegation
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealScale
import io.github.gciatto.ktconstraints.core.real.arithmetic.RealSum
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessOrEqual
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.BinaryArithmetic
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.Circuit
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.encoding.DirectEncoding
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.encoding.IntegerEncoding
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.encoding.OrderEncoding
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear.IntegerLinear
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear.LinearCompiler
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear.LinearForm
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear.RealAtom
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchBudget
import org.gciatto.kt.math.BigInteger
import kotlin.reflect.KClass

/** Exact translation into reusable Boolean circuits and linear-real atoms; no search occurs here. */
internal class KotlinTranslation(
    val circuit: Circuit,
) {
    val binary = BinaryArithmetic(circuit, circuit::checkpoint)
    private val direct = DirectEncoding(circuit)
    private val order = OrderEncoding(circuit, binary)
    val linear = LinearCompiler(circuit, binary, direct)
    val realAtoms = mutableListOf<RealAtom>()
    private val cache = CompilationCache()
    private val booleans = linkedMapOf<BoolVar, Int>()
    private val integers = linkedMapOf<IntVar, IntegerEncoding>()
    private val bitVectors = linkedMapOf<BitVecVar, IntArray>()
    private val reals = linkedMapOf<RealVar, Int>()

    val realCount: Int get() = reals.size

    suspend fun declare(
        model: ConstraintModel,
        domains: Map<IntVar, IntDomain>,
        budget: SearchBudget,
    ) {
        for (variable in model.variables) {
            budget.checkpoint()
            declare(variable, domains)
        }
    }

    /** Encodes [variable] once, narrowing an integer to its presolved domain when [domains] has one. */
    suspend fun declare(
        variable: Variable,
        domains: Map<IntVar, IntDomain> = emptyMap(),
    ) {
        when (variable) {
            is BoolVar -> booleans.getOrPut(variable) { circuit.variable() }
            is IntVar ->
                if (variable !in integers) {
                    val domain = domains[variable] ?: variable.domain
                    integers[variable] =
                        binary.variable(domain).also {
                            direct.register(it, domain)
                            order.register(it, domain)
                        }
                }
            is BitVecVar ->
                if (variable !in bitVectors) {
                    circuit.width(variable.width)
                    bitVectors[variable] = IntArray(variable.width) { circuit.variable() }
                }
            is RealVar -> reals.getOrPut(variable) { reals.size }
            else -> error("Unsupported declaration")
        }
    }

    /**
     * Posts a permanent root constraint: conjunctions are split, and an all-different over small-domain variables
     * shifted by constants becomes native per-value constraints; anything else is asserted through its literal.
     */
    suspend fun post(
        constraint: BoolExpr,
        budget: SearchBudget,
    ) {
        val pending = ArrayDeque(listOf(constraint))
        while (pending.isNotEmpty()) {
            budget.checkpoint()
            when (val node = pending.removeLast()) {
                is BoolAnd -> {
                    pending.addLast(node.right)
                    pending.addLast(node.left)
                }
                is BoolNot -> {
                    val sides = (node.operand as? IntEquality)?.let { disequality(it) }
                    if (sides != null) direct.notEqual(sides.first, sides.second) else circuit.clause(assertion(node, budget))
                }
                else -> {
                    val operands = (node as? AllDifferent)?.operands
                    val shifts = operands?.mapNotNull(::shifted)
                    if (shifts != null &&
                        shifts.size == operands.size
                    ) {
                        direct.allDifferent(shifts)
                    } else {
                        circuit.clause(assertion(node, budget))
                    }
                }
            }
        }
    }

    /** Both sides of [equality] as shifted small-domain terms, reading `x - y = c` as `x = y + c`; null otherwise. */
    private fun disequality(equality: IntEquality): Pair<Pair<IntegerEncoding?, BigInteger>, Pair<IntegerEncoding?, BigInteger>>? {
        val left = equality.left
        val right = equality.right
        if (left is IntDifference && right is IntLiteral) {
            val minuend = shifted(left.left)?.takeIf { it.first != null } ?: return null
            val subtrahend = shifted(left.right)?.takeIf { it.first != null } ?: return null
            return minuend to (subtrahend.first to subtrahend.second + right.value)
        }
        return (shifted(left) ?: return null) to (shifted(right) ?: return null)
    }

    /** `left <= right` (or `<` when [strict]) through order literals when both sides are shifted ordered variables. */
    private suspend fun ordered(
        left: IntExpr,
        right: IntExpr,
        strict: Boolean,
    ): Int? {
        val lower = shifted(left, order::isOrdered) ?: return null
        val upper = shifted(right, order::isOrdered) ?: return null
        if (lower.first == null && upper.first == null) return null
        return order.lessOrEqual(if (strict) lower.first to lower.second + BigInteger.ONE else lower, upper)
    }

    /** `(encoding, shift)` for a small-domain variable plus a constant, `(null, value)` for a constant, else null. */
    private fun shifted(
        operand: IntExpr,
        eligible: (IntegerEncoding) -> Boolean = direct::isDirect,
    ): Pair<IntegerEncoding?, BigInteger>? {
        fun variable(expression: IntExpr): IntegerEncoding? = (expression as? IntVar)?.let { integers[it] }?.takeIf(eligible)
        return when {
            operand is IntLiteral -> null to operand.value
            operand is IntSum && operand.right is IntLiteral -> variable(operand.left)?.let { it to (operand.right as IntLiteral).value }
            operand is IntSum && operand.left is IntLiteral -> variable(operand.right)?.let { it to (operand.left as IntLiteral).value }
            operand is IntDifference && operand.right is IntLiteral ->
                variable(operand.left)?.let {
                    it to
                        -(operand.right as IntLiteral).value
                }
            else -> variable(operand)?.let { it to BigInteger.ZERO }
        }
    }

    suspend fun assertion(
        expression: BoolExpr,
        budget: SearchBudget,
    ): Int {
        val term = translate(expression, budget)
        return circuit.and(term.value as Int, term.defined)
    }

    suspend fun translate(
        expression: Expr,
        budget: SearchBudget,
    ): CompiledTerm {
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
                val term = compile(frame.node, frame.results, budget)
                cache.put(frame.node, term)
                stack.removeLast()
                if (stack.isEmpty()) return term
                stack.last().results += term
            }
        }
    }

    fun assignment(
        values: BooleanArray,
        realValues: List<Rational>,
    ): Assignment =
        Assignment.of(
            booleans.mapValues { circuit.value(it.value, values) },
            integers.mapValues { binary.decodeInteger(it.value, values) },
            reals.mapValues { realValues[it.value] },
            bitVectors.mapValues { binary.decodeBits(it.value, values) },
        )

    private suspend fun compile(
        node: Expr,
        terms: List<CompiledTerm>,
        budget: SearchBudget,
    ): CompiledTerm {
        var defined = circuit.all(terms.map { it.defined })
        if (node is DecomposableConstraint) {
            // shortcut: the variable-free decomposition is exact everywhere but large; native circuits would be smaller.
            val decomposed = translate(node.decomposition, budget)
            return CompiledTerm(decomposed.value, circuit.and(defined, decomposed.defined))
        }
        val children = if (node::class in LINEAR_AWARE) terms.map { it.value } else terms.map { materialize(it.value) }
        val value =
            when (node) {
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

    private suspend fun materialize(value: Any): Any = if (value is IntegerLinear) linear.encoding(value) else value

    private fun symbolic(value: Any): IntegerLinear = value as? IntegerLinear ?: IntegerLinear.of(value as IntegerEncoding)

    private suspend fun integer(
        node: IntExpr,
        values: List<Any>,
    ): Any =
        when {
            node is LinearSum ->
                values.indices.fold(IntegerLinear(emptyList(), node.constant)) { sum, index ->
                    sum + symbolic(values[index]).scale(node.coefficients[index])
                }
            values.any { it is IntegerLinear } ->
                when (node) {
                    is IntSum -> symbolic(values[0]) + symbolic(values[1])
                    is IntDifference -> symbolic(values[0]) + symbolic(values[1]).scale(-BigInteger.ONE)
                    else -> symbolic(values.single()).scale(-BigInteger.ONE)
                }
            else -> encoded(node, values)
        }

    private suspend fun encoded(
        node: IntExpr,
        values: List<Any>,
    ): IntegerEncoding =
        when (node) {
            is IntVar -> integers.getValue(node)
            is IntLiteral -> binary.integer(node.value)
            is IntSum -> binary.add(values[0] as IntegerEncoding, values[1] as IntegerEncoding)
            is IntDifference -> binary.subtract(values[0] as IntegerEncoding, values[1] as IntegerEncoding)
            is IntProduct -> binary.multiply(values[0] as IntegerEncoding, values[1] as IntegerEncoding)
            is IntNegation -> binary.negate(values.single() as IntegerEncoding)
            is BitVecToInt -> binary.toInteger(values.single() as IntArray, node.signed)
            is BoolToInt -> IntegerEncoding(intArrayOf(values.single() as Int, circuit.falseLiteral), BigInteger.ZERO, BigInteger.ONE)
            is IntMin -> values.map { it as IntegerEncoding }.reduce { least, next -> binary.choose(binary.less(next, least), next, least) }
            is IntMax -> values.map { it as IntegerEncoding }.reduce { most, next -> binary.choose(binary.less(most, next), next, most) }
            is Element -> {
                val index = values.first() as IntegerEncoding
                var result = values.last() as IntegerEncoding
                for (position in node.values.size - 2 downTo 0) {
                    result =
                        binary.choose(
                            linear.equal(index, binary.integer(BigInteger.of(position))),
                            values[position + 1] as IntegerEncoding,
                            result,
                        )
                }
                result
            }
            else -> error("Unsupported integer expression")
        }

    private suspend fun bitVector(
        node: BitVecExpr,
        values: List<Any>,
    ): IntArray =
        when (node) {
            is BitVecVar -> bitVectors.getValue(node)
            is BitVecLiteral -> binary.bits(node.value, node.width)
            is BitVecBinary -> binary.bitVector(node.operator, values[0] as IntArray, values[1] as IntArray)
            is BitVecNot -> binary.bitNot(values.single() as IntArray)
            is BitVecNegation -> binary.bitNegate(values.single() as IntArray)
            else -> error("Unsupported bit-vector expression")
        }

    private suspend fun real(
        node: RealExpr,
        values: List<Any>,
    ): LinearForm =
        when (node) {
            is RealVar -> LinearForm(realCoefficients = mapOf(reals.getValue(node) to Rational.ONE))
            is RealLiteral -> LinearForm(constant = node.value)
            is RealSum -> (values[0] as LinearForm) + (values[1] as LinearForm)
            is RealDifference -> (values[0] as LinearForm) + (values[1] as LinearForm).scale(Rational.of(-1))
            is RealNegation -> (values.single() as LinearForm).scale(Rational.of(-1))
            is RealScale -> (values.single() as LinearForm).scale(node.coefficient)
            is IntToReal -> embed(values.single() as IntegerEncoding)
            else -> error("Unsupported real expression")
        }

    private suspend fun boolean(
        node: BoolExpr,
        values: List<Any>,
    ): Int =
        when (node) {
            is BoolVar -> booleans.getValue(node)
            is BoolLiteral -> circuit.constant(node.value)
            is BoolNot -> -(values.single() as Int)
            is BoolAnd -> circuit.and(values[0] as Int, values[1] as Int)
            is BoolOr -> circuit.or(values[0] as Int, values[1] as Int)
            is BoolImplies -> circuit.or(-(values[0] as Int), values[1] as Int)
            is BoolEquality -> -circuit.xor(values[0] as Int, values[1] as Int)
            is IntEquality -> linear.equal(values[0], values[1])
            is IntLessThan -> ordered(node.left, node.right, strict = true) ?: linear.less(values[0], values[1])
            is IntLessOrEqual -> ordered(node.left, node.right, strict = false) ?: linear.lessOrEqual(values[0], values[1])
            is BitVecComparison -> binary.bitCompare(node.relation, values[0] as IntArray, values[1] as IntArray)
            is RealEquality, is RealLessThan, is RealLessOrEqual -> realRelation(node, values)
            else -> global(node, values)
        }

    private suspend fun global(
        node: BoolExpr,
        values: List<Any>,
    ): Int =
        when (node) {
            is AllDifferent -> {
                var result = circuit.trueLiteral
                for (left in values.indices) {
                    for (right in left + 1 until values.size) {
                        result = circuit.and(result, -binary.equal(values[left] as IntegerEncoding, values[right] as IntegerEncoding))
                    }
                }
                for (value in permutationValues(node)) {
                    result = circuit.and(result, circuit.any(values.map { linear.equal(it as IntegerEncoding, binary.integer(value)) }))
                }
                result
            }
            is Table ->
                circuit.any(
                    node.tuples.map { row ->
                        circuit.all(values.indices.map { linear.equal(values[it] as IntegerEncoding, binary.integer(row[it])) })
                    },
                )
            is BoolCardinality -> {
                val sum =
                    values.fold(binary.integer(BigInteger.ZERO)) { total, literal ->
                        binary.add(
                            total,
                            IntegerEncoding(intArrayOf(literal as Int, circuit.falseLiteral), BigInteger.ZERO, BigInteger.ONE),
                        )
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

    /**
     * Values of an all-different over variables whose domains jointly hold exactly as many values as operands.
     * Each value is then taken: an implied, exact strengthening that lets the SAT search count.
     */
    private fun permutationValues(node: AllDifferent): List<BigInteger> {
        val variables = node.operands.map { it as? IntVar ?: return emptyList() }
        val union = variables.map { it.domain }.reduce(IntDomain::union)
        val values = mutableListOf<BigInteger>()
        for (interval in union.intervals) {
            var value = interval.min
            while (value <= interval.max) {
                if (values.size == variables.size) return emptyList()
                values += value
                value += BigInteger.ONE
            }
        }
        return if (values.size == variables.size) values else emptyList()
    }

    private suspend fun realRelation(
        node: BoolExpr,
        values: List<Any>,
    ): Int {
        val difference = (values[0] as LinearForm) + (values[1] as LinearForm).scale(Rational.of(-1))
        return if (node is RealEquality) {
            circuit.and(atom(difference, false), atom(difference.scale(Rational.of(-1)), false))
        } else {
            atom(difference, node is RealLessThan)
        }
    }

    private suspend fun atom(
        form: LinearForm,
        strict: Boolean,
    ): Int {
        if (form.realCoefficients.isEmpty() && form.bitCoefficients.isEmpty()) {
            return circuit.constant(if (strict) form.constant < Rational.ZERO else form.constant <= Rational.ZERO)
        }
        if (form.realCoefficients.isEmpty()) return discreteAtom(form, strict)
        return circuit.variable().also { realAtoms += RealAtom(it, form, strict) }
    }

    private suspend fun discreteAtom(
        form: LinearForm,
        strict: Boolean,
    ): Int {
        // All denominators are positive, so multiplying by their LCM preserves strict and non-strict ordering.
        var denominator = form.constant.denominator
        for (coefficient in form.bitCoefficients.values) {
            circuit.checkpoint()
            denominator = (denominator / denominator.gcd(coefficient.denominator)) * coefficient.denominator
        }
        val zero = binary.integer(BigInteger.ZERO)
        var sum = binary.integer(form.constant.numerator * (denominator / form.constant.denominator))
        for ((variable, coefficient) in form.bitCoefficients) {
            circuit.checkpoint()
            val scaled = coefficient.numerator * (denominator / coefficient.denominator)
            // Selecting the coefficient or zero implements coefficient * bit without a multiplier circuit.
            val term = binary.choose(variable, binary.integer(scaled), zero)
            sum = binary.add(sum, term)
        }
        return if (strict) binary.less(sum, zero) else -binary.less(zero, sum)
    }

    private suspend fun embed(integer: IntegerEncoding): LinearForm {
        val coefficients = mutableMapOf<Int, Rational>()
        var constant = Rational.ZERO
        for (index in integer.bits.indices) {
            if ((index and 127) == 0) circuit.checkpoint()
            val magnitude = BigInteger.ONE shl index
            val coefficient = Rational.of(if (index == integer.bits.lastIndex) -magnitude else magnitude)
            val literal = integer.bits[index]
            when (literal) {
                circuit.trueLiteral -> constant += coefficient
                circuit.falseLiteral -> Unit
                else -> {
                    val variable = if (literal > 0) literal else -literal
                    val contribution = if (literal > 0) coefficient else -coefficient
                    if (literal < 0) constant += coefficient
                    val combined = (coefficients[variable] ?: Rational.ZERO) + contribution
                    if (combined == Rational.ZERO) coefficients.remove(variable) else coefficients[variable] = combined
                }
            }
        }
        return LinearForm(bitCoefficients = coefficients, constant = constant)
    }

    private companion object {
        /** Nodes that accept symbolic sums as children; every other consumer receives materialised bits. */
        val LINEAR_AWARE: Set<KClass<out Expr>> =
            setOf(
                LinearSum::class,
                IntSum::class,
                IntDifference::class,
                IntNegation::class,
                IntEquality::class,
                IntLessThan::class,
                IntLessOrEqual::class,
            )
    }
}
