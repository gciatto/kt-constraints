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
import io.github.gciatto.ktconstraints.core.BoolToInt
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.CardinalityRelation
import io.github.gciatto.ktconstraints.core.DecomposableConstraint
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLessThan
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntMax
import io.github.gciatto.ktconstraints.core.IntMin
import io.github.gciatto.ktconstraints.core.IntNegation
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.LinearSum
import io.github.gciatto.ktconstraints.core.Table
import io.github.gciatto.ktconstraints.core.Variable
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
    private var declared = 0
    private val globals = ChocoGlobals(chocoModel) { variable(it) }

    /** The Choco variable of each declared source variable; Choco Booleans are 0/1 integer variables. */
    val natives = hashMapOf<Variable, ChocoIntVar>()

    init {
        val report = ChocoSupport.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
        model.variables.forEach { declare(it) }
        model.constraints.forEach { post(it) }
        when (val objective = model.objective) {
            is Objective.Minimize -> chocoModel.setObjective(Model.MINIMIZE, variable(objective.expression))
            is Objective.Maximize -> chocoModel.setObjective(Model.MAXIMIZE, variable(objective.expression))
            null -> Unit
        }
    }

    /** Creates the Choco variable of [variable], posting its domain holes. */
    fun declare(variable: Variable) {
        fun integer(
            domain: IntDomain,
            name: String,
        ): ChocoIntVar {
            if (domain.isEmpty) {
                chocoModel.falseConstraint().post()
                return chocoModel.intVar(name, 0)
            }
            val intervals = domain.intervals
            val created = chocoModel.intVar(name, intervals.first().min.toIntExact(), intervals.last().max.toIntExact())
            intervals.zipWithNext { below, above ->
                chocoModel.notMember(created, (below.max + BigInteger.ONE).toIntExact(), (above.min - BigInteger.ONE).toIntExact()).post()
            }
            return created
        }
        val index = declared++
        when (variable) {
            is BoolVar -> booleans[variable] = chocoModel.boolVar("b$index").also { natives[variable] = it }
            is IntVar -> integers[variable] = integer(variable.domain, "i$index").also { natives[variable] = it }
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported variable: ${variable.name}")))
        }
    }

    /** Full source assignment of the solution the solver currently holds. */
    fun assignment(): Assignment =
        Assignment.of(
            booleans = booleans.mapValues { (_, variable) -> variable.value == 1 },
            integers = integers.mapValues { (_, variable) -> BigInteger.of(variable.value) },
        )

    /** Posts [expression] at top level; conjunctions become separate constraints. */
    fun post(expression: BoolExpr) {
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
            is DecomposableConstraint -> literal(expression.decomposition)
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
            is AllDifferent, is Table, is DecomposableConstraint -> global(expression)
            else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported expression: $expression")))
        }

    private fun cardinality(expression: BoolCardinality): Constraint {
        val count =
            ChocoLinear.sum(
                expression.operands.map { ChocoLinear.of(literal(it)) } + ChocoLinear.of(BigInteger.of(-expression.count.toLong())),
            )
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
            expression is DecomposableConstraint ->
                globals.native(expression) ?: constraint(expression.decomposition)
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
                ChocoLinear.sum(
                    expression.terms.zip(expression.coefficients).map { (term, c) -> linear(term) * c } +
                        ChocoLinear.of(expression.constant),
                )
            is IntProduct, is Element, is IntMin, is IntMax -> {
                val operands = expression.children.map { variable(it as IntExpr) }
                // Posted at top level: element's index range is enforced regardless of nesting, as on every other route.
                ChocoLinear.of(auxiliary(expression).also { chocoModel.defining(expression, it, operands).post() })
            }
            is BoolToInt -> ChocoLinear.of(literal(expression.operand))
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

/** Choco's native constraint defining [result] as [expression] over its translated [operands]. */
private fun Model.defining(
    expression: IntExpr,
    result: ChocoIntVar,
    operands: List<ChocoIntVar>,
): Constraint =
    when (expression) {
        is IntProduct -> times(operands[0], operands[1], result)
        is Element -> element(result, operands.drop(1).toTypedArray(), operands[0], 0)
        is IntMin -> min(result, operands.toTypedArray())
        else -> max(result, operands.toTypedArray())
    }
