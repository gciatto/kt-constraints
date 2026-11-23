package io.github.gciatto.ktconstraints.solve.cpsat.translation
import com.google.ortools.sat.Constraint
import com.google.ortools.sat.CpModel
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.LinearArgument
import com.google.ortools.sat.LinearExpr
import com.google.ortools.sat.Literal
import com.google.ortools.util.Domain
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
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import org.gciatto.kt.math.BigInteger

/**
 * Lossless CP-SAT encoding of a supported model. Nested Boolean expressions become literals whose both directions
 * are posted, so auxiliary variables are functionally determined by the source variables.
 */
internal class CpSatTranslation(
    model: ConstraintModel,
    hints: Assignment = Assignment.empty(),
) {
    /** Native-independent model description; requires the OR-Tools native library for domains and validation. */
    val cpModel = CpModel()
    private val booleans = linkedMapOf<BoolVar, com.google.ortools.sat.BoolVar>()
    private val integers = linkedMapOf<IntVar, com.google.ortools.sat.IntVar>()
    private val literals = CpSatLiterals(cpModel)
    private var auxiliaries = 0
    private val globals = CpSatGlobals(cpModel, ::integer, ::equality)

    init {
        val report = CpSatSupport.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
        model.variables.forEachIndexed { index, variable ->
            when (variable) {
                is BoolVar -> booleans[variable] = cpModel.newBoolVar("b$index")
                is IntVar -> integers[variable] = integer(variable.domain, "i$index")
                else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported variable: ${variable.name}")))
            }
        }
        booleans.forEach { (variable, native) -> hints.valueOf(variable)?.let { cpModel.addHint(native, it) } }
        integers.forEach { (variable, native) -> hints.valueOf(variable)?.let { cpModel.addHint(native, it.toLongExact()) } }
        model.constraints.forEach { post(it) }
        when (val objective = model.objective) {
            is Objective.Minimize -> cpModel.minimize(integer(objective.expression))
            is Objective.Maximize -> cpModel.maximize(integer(objective.expression))
            null -> Unit
        }
        val problem = cpModel.validate()
        if (problem.isNotEmpty()) throw UnsupportedModelException(SupportReport(listOf("CP-SAT rejected the model: $problem")))
    }

    /** Full source assignment of the solution currently reported by [callback]. */
    fun assignment(callback: CpSolverSolutionCallback): Assignment =
        Assignment.of(
            booleans = booleans.mapValues { (_, variable) -> callback.booleanValue(variable) },
            integers = integers.mapValues { (_, variable) -> BigInteger.of(callback.value(variable)) },
        )

    private fun integer(
        domain: IntDomain,
        name: String,
    ): com.google.ortools.sat.IntVar {
        if (domain.isEmpty) {
            cpModel.addBoolOr(listOf(cpModel.falseLiteral()))
            return cpModel.newIntVar(0, 0, name)
        }
        val flat = domain.intervals.flatMap { listOf(it.min.toLongExact(), it.max.toLongExact()) }
        return cpModel.newIntVarFromDomain(Domain.fromFlatIntervals(flat.toLongArray()), name)
    }

    private fun post(expression: BoolExpr) {
        when (expression) {
            is BoolAnd -> {
                post(expression.left)
                post(expression.right)
            }
            is BoolLiteral -> if (!expression.value) cpModel.addBoolOr(listOf(cpModel.falseLiteral()))
            is BoolOr -> cpModel.addBoolOr(listOf(literal(expression.left), literal(expression.right)))
            is BoolImplies -> cpModel.addImplication(literal(expression.left), literal(expression.right))
            is BoolEquality -> cpModel.addBoolXor(listOf(literal(expression.left), literal(expression.right).not()))
            is IntEquality, is IntLessThan, is IntLessOrEqual, is BoolCardinality -> relation(expression).first()
            is AllDifferent -> cpModel.addAllDifferent(expression.operands.map { integer(it) })
            is Table -> table(expression)
            is DecomposableConstraint ->
                if (!globals.post(expression)) post(expression.decomposition)
            else -> cpModel.addBoolOr(listOf(literal(expression)))
        }
    }

    private fun table(expression: Table) {
        if (expression.tuples.isEmpty()) {
            cpModel.addBoolOr(listOf(cpModel.falseLiteral()))
            return
        }
        cpModel
            .addAllowedAssignments(expression.operands.map { integer(it) })
            .addTuples(expression.tuples.map { row -> row.map { it.toLongExact() }.toLongArray() }.toTypedArray())
    }

    private fun literal(expression: BoolExpr): Literal =
        when (expression) {
            is BoolVar -> booleans.getValue(expression)
            is BoolLiteral -> if (expression.value) cpModel.trueLiteral() else cpModel.falseLiteral()
            is BoolNot -> literal(expression.operand).not()
            is BoolAnd -> literals.conjunction(listOf(literal(expression.left), literal(expression.right)))
            is BoolOr -> literals.disjunction(listOf(literal(expression.left), literal(expression.right)))
            is BoolImplies -> literals.disjunction(listOf(literal(expression.left).not(), literal(expression.right)))
            is BoolEquality -> literals.equivalence(literal(expression.left), literal(expression.right))
            is IntEquality, is IntLessThan, is IntLessOrEqual, is BoolCardinality ->
                relation(expression).let { (positive, negative) -> literals.reified(positive, negative) }
            is AllDifferent, is Table -> nestedGlobal(expression)
            is DecomposableConstraint -> literal(expression.decomposition)
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported expression: $expression")))
        }

    /** Pairwise disequalities for all-different, a disjunction of tuple conjunctions for tables. */
    private fun nestedGlobal(expression: BoolExpr): Literal =
        if (expression is AllDifferent) {
            val operands = expression.operands.map { integer(it) }
            val pairs = operands.indices.flatMap { i -> (i + 1 until operands.size).map { j -> operands[i] to operands[j] } }
            literals.conjunction(pairs.map { (left, right) -> equality(left, right).not() })
        } else {
            val table = expression as Table
            val operands = table.operands.map { integer(it) }
            literals.disjunction(
                table.tuples.map { row ->
                    literals.conjunction(
                        operands.zip(row).map { (operand, value) ->
                            equality(operand, LinearExpr.constant(value.toLongExact()))
                        },
                    )
                },
            )
        }

    private fun equality(
        left: LinearArgument,
        right: LinearArgument,
    ): Literal = literals.reified({ cpModel.addEquality(left, right) }, { cpModel.addDifferent(left, right) })

    /** Builders posting a linear relation or a cardinality, and its negation. */
    private fun relation(expression: BoolExpr): Pair<() -> Constraint, () -> Constraint> {
        val (left, right) =
            when (expression) {
                is IntEquality -> integer(expression.left) to integer(expression.right)
                is IntLessThan -> integer(expression.left) to integer(expression.right)
                is IntLessOrEqual -> integer(expression.left) to integer(expression.right)
                is BoolCardinality ->
                    LinearExpr.sum(expression.operands.map { literal(it) }.toTypedArray()) to LinearExpr.constant(expression.count.toLong())
                else -> error("Not a relation: $expression")
            }
        val relation = if (expression is BoolCardinality) expression.relation else null
        return when {
            expression is IntLessThan -> Pair({ cpModel.addLessThan(left, right) }, { cpModel.addGreaterOrEqual(left, right) })
            expression is IntLessOrEqual || relation == CardinalityRelation.AT_MOST ->
                Pair({ cpModel.addLessOrEqual(left, right) }, { cpModel.addGreaterThan(left, right) })
            relation == CardinalityRelation.AT_LEAST ->
                Pair(
                    { cpModel.addGreaterOrEqual(left, right) },
                    { cpModel.addLessThan(left, right) },
                )
            else -> Pair({ cpModel.addEquality(left, right) }, { cpModel.addDifferent(left, right) })
        }
    }

    private fun integer(expression: IntExpr): LinearArgument =
        when (expression) {
            is IntVar -> integers.getValue(expression)
            is IntLiteral -> LinearExpr.constant(expression.value.toLongExact())
            is IntSum ->
                LinearExpr
                    .newBuilder()
                    .add(integer(expression.left))
                    .add(integer(expression.right))
                    .build()
            is IntDifference ->
                LinearExpr
                    .newBuilder()
                    .add(integer(expression.left))
                    .addTerm(integer(expression.right), -1)
                    .build()
            is IntNegation -> LinearExpr.term(integer(expression.operand), -1)
            is LinearSum ->
                expression.terms
                    .zip(expression.coefficients)
                    .fold(LinearExpr.newBuilder().add(expression.constant.toLongExact())) { builder, (term, coefficient) ->
                        builder.addTerm(integer(term), coefficient.toLongExact())
                    }.build()
            is IntProduct, is Element, is IntMin, is IntMax -> {
                val operands = expression.children.map { integer(it as IntExpr) }
                auxiliary(expression).also { cpModel.defining(expression, it, operands) }
            }
            is BoolToInt -> literal(expression.operand)
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported expression: $expression")))
        }

    private fun auxiliary(expression: IntExpr): com.google.ortools.sat.IntVar {
        val bounds = CpSatSupport.bounds(expression)
        return cpModel.newIntVar(bounds.min.toLongExact(), bounds.max.toLongExact(), "a${auxiliaries++}")
    }
}

/** CP-SAT's native constraint defining [result] as [expression] over its translated [operands]. */
private fun CpModel.defining(
    expression: IntExpr,
    result: LinearArgument,
    operands: List<LinearArgument>,
): Constraint =
    when (expression) {
        is IntProduct -> addMultiplicationEquality(result, operands[0], operands[1])
        is Element -> addElement(operands[0], operands.drop(1), result)
        is IntMin -> addMinEquality(result, operands)
        else -> addMaxEquality(result, operands)
    }
