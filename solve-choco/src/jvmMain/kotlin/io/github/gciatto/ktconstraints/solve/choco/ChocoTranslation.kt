package io.github.gciatto.ktconstraints.solve.choco

import io.github.gciatto.ktconstraints.core.AllDifferent
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
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import org.chocosolver.solver.Model
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.constraints.extension.Tuples
import org.gciatto.kt.math.BigInteger
import java.util.OptionalInt
import org.chocosolver.solver.variables.BoolVar as ChocoBoolVar
import org.chocosolver.solver.variables.IntVar as ChocoIntVar

/**
 * Choco encoding of a supported model. Each Boolean expression is one native constraint, reified by Choco when nested,
 * so auxiliary variables are functionally determined by the source variables.
 */
internal class ChocoTranslation(
    model: ConstraintModel,
) {
    /** The Choco model owned by one stream. */
    val chocoModel = Model()
    private val booleans = linkedMapOf<BoolVar, ChocoBoolVar>()
    private val integers = linkedMapOf<IntVar, ChocoIntVar>()

    init {
        val report = ChocoSupport.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
        model.variables.forEachIndexed { index, variable ->
            when (variable) {
                is BoolVar -> booleans[variable] = chocoModel.boolVar("b$index")
                is IntVar -> integers[variable] = integer(variable.domain, "i$index")
                else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported variable: ${variable.name}")))
            }
        }
        model.constraints.forEach { post(it) }
        when (val objective = model.objective) {
            is Objective.Minimize -> chocoModel.setObjective(Model.MINIMIZE, variable(objective.expression))
            is Objective.Maximize -> chocoModel.setObjective(Model.MAXIMIZE, variable(objective.expression))
            null -> Unit
        }
    }

    /** Full source assignment of the solution the solver currently holds. */
    fun assignment(): Assignment =
        Assignment.of(
            booleans = booleans.mapValues { (_, variable) -> variable.value == 1 },
            integers = integers.mapValues { (_, variable) -> BigInteger.of(variable.value) },
        )

    private fun integer(
        domain: IntDomain,
        name: String,
    ): ChocoIntVar {
        if (domain.isEmpty) {
            chocoModel.falseConstraint().post()
            return chocoModel.intVar(name, 0)
        }
        val intervals = domain.intervals
        val variable = chocoModel.intVar(name, intervals.first().min.toIntExact(), intervals.last().max.toIntExact())
        intervals.zipWithNext { below, above ->
            chocoModel.notMember(variable, (below.max + BigInteger.ONE).toIntExact(), (above.min - BigInteger.ONE).toIntExact()).post()
        }
        return variable
    }

    private fun post(expression: BoolExpr) {
        if (expression is BoolAnd) {
            post(expression.left)
            post(expression.right)
        } else {
            constraint(expression).post()
        }
    }

    private fun literal(expression: BoolExpr): ChocoBoolVar =
        when (expression) {
            is BoolVar -> booleans.getValue(expression)
            is BoolLiteral -> chocoModel.boolVar(expression.value)
            is BoolNot -> literal(expression.operand).not()
            else -> constraint(expression).reify()
        }

    private fun constraint(expression: BoolExpr): Constraint =
        when (expression) {
            is BoolVar, is BoolNot -> chocoModel.arithm(literal(expression), "=", 1)
            is BoolLiteral -> if (expression.value) chocoModel.trueConstraint() else chocoModel.falseConstraint()
            is BoolAnd -> chocoModel.and(literal(expression.left), literal(expression.right))
            is BoolOr -> chocoModel.or(literal(expression.left), literal(expression.right))
            is BoolImplies -> chocoModel.or(literal(expression.left).not(), literal(expression.right))
            is BoolEquality -> chocoModel.arithm(literal(expression.left), "=", literal(expression.right))
            is IntEquality -> (linear(expression.left) + linear(expression.right) * MINUS_ONE).relation(chocoModel, "=")
            is IntLessThan -> (linear(expression.left) + linear(expression.right) * MINUS_ONE).relation(chocoModel, "<")
            is IntLessOrEqual -> (linear(expression.left) + linear(expression.right) * MINUS_ONE).relation(chocoModel, "<=")
            is BoolCardinality -> cardinality(expression)
            is AllDifferent, is Table -> global(expression)
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported expression: $expression")))
        }

    private fun cardinality(expression: BoolCardinality): Constraint {
        val count =
            expression.operands.fold(ChocoLinear.of(BigInteger.of(-expression.count.toLong()))) { total, operand ->
                total + ChocoLinear.of(literal(operand))
            }
        val operator =
            when (expression.relation) {
                CardinalityRelation.AT_MOST -> "<="
                CardinalityRelation.AT_LEAST -> ">="
                CardinalityRelation.EXACTLY -> "="
            }
        return count.relation(chocoModel, operator)
    }

    private fun global(expression: BoolExpr): Constraint =
        when {
            expression is AllDifferent && expression.operands.size < 2 -> chocoModel.trueConstraint()
            expression is AllDifferent -> chocoModel.allDifferent(expression.operands.map { variable(it) }.toTypedArray(), "DEFAULT")
            (expression as Table).tuples.isEmpty() -> chocoModel.falseConstraint()
            expression.operands.isEmpty() -> chocoModel.trueConstraint()
            else -> {
                val rows = expression.tuples.map { row -> row.map { it.toIntExact() }.toIntArray() }.toTypedArray()
                chocoModel.table(expression.operands.map { variable(it) }.toTypedArray(), Tuples(rows, true, OptionalInt.empty()))
            }
        }

    private fun linear(expression: IntExpr): ChocoLinear =
        when (expression) {
            is IntVar -> ChocoLinear.of(integers.getValue(expression))
            is IntLiteral -> ChocoLinear.of(expression.value)
            is IntSum -> linear(expression.left) + linear(expression.right)
            is IntDifference -> linear(expression.left) + linear(expression.right) * MINUS_ONE
            is IntNegation -> linear(expression.operand) * MINUS_ONE
            is LinearSum ->
                expression.terms.zip(expression.coefficients).fold(ChocoLinear.of(expression.constant)) { total, (term, coefficient) ->
                    total + linear(term) * coefficient
                }
            is IntProduct -> {
                val left = variable(expression.left)
                val right = variable(expression.right)
                ChocoLinear.of(auxiliary(expression).also { chocoModel.times(left, right, it).post() })
            }
            is Element -> {
                val index = variable(expression.index)
                val values = expression.values.map { variable(it) }.toTypedArray()
                // Posted at top level: the index range is enforced regardless of nesting, as on every other route.
                ChocoLinear.of(auxiliary(expression).also { chocoModel.element(it, values, index, 0).post() })
            }
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported expression: $expression")))
        }

    /** A Choco variable equal to [expression]: the source variable itself, a constant, or a defined auxiliary. */
    private fun variable(expression: IntExpr): ChocoIntVar {
        val form = linear(expression)
        val single = form.terms.entries.singleOrNull()
        return when {
            form.terms.isEmpty() -> chocoModel.intVar(form.constant.toIntExact())
            single != null && single.value == BigInteger.ONE && form.constant.signum == 0 -> single.key
            else -> auxiliary(expression).also { (form + ChocoLinear.of(it) * MINUS_ONE).relation(chocoModel, "=").post() }
        }
    }

    private fun auxiliary(expression: IntExpr): ChocoIntVar {
        val bounds = ChocoSupport.bounds(expression)
        return chocoModel.intVar(bounds.min.toIntExact(), bounds.max.toIntExact())
    }

    private companion object {
        val MINUS_ONE: BigInteger = -BigInteger.ONE
    }
}
