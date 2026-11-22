package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.SolveMetadata
import io.github.gciatto.ktconstraints.solve.kotlin.arithmetic.LinearFeasibility
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.Circuit
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.IntegerEncoding
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.IntegerLinear
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.KotlinTranslation
import io.github.gciatto.ktconstraints.solve.kotlin.cp.IntegerPresolver
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatSolver
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatStatus
import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException

/** One isolated mutable search. Theory conflicts include every Boolean fact used by the exact real check. */
internal class KotlinEngine(
    private val model: ConstraintModel,
    configuration: KotlinConfiguration,
    budget: SearchBudget,
) {
    private val sat = SatSolver()
    val circuit = Circuit(sat, configuration, budget)
    val translation = KotlinTranslation(circuit)

    /** The compiled objective: an [IntegerEncoding] or a symbolic [IntegerLinear]. */
    var objective: Any? = null
        private set
    private var theoryConflicts = 0L
    private var presolveReductions = 0

    suspend fun initialize(
        budget: SearchBudget,
        includeObjective: Boolean,
    ) {
        circuit.useBudget(budget)
        val presolved = IntegerPresolver().run(model, budget::checkpoint)
        presolveReductions = presolved.reductions.size
        translation.declare(model, presolved.domains, budget)
        if (presolved.conflict) circuit.clause(circuit.falseLiteral)
        for (constraint in model.constraints) {
            budget.checkpoint()
            translation.post(constraint, budget)
        }
        if (includeObjective) {
            val expression =
                when (val target = model.objective) {
                    is Objective.Minimize -> target.expression
                    is Objective.Maximize -> target.expression
                    null -> null
                }
            if (expression != null) {
                val compiled = translation.translate(expression, budget)
                objective = compiled.value
                circuit.clause(compiled.defined)
            }
        }
    }

    suspend fun literal(
        constraint: BoolExpr,
        budget: SearchBudget,
    ): Int {
        circuit.useBudget(budget)
        return translation.assertion(constraint, budget)
    }

    suspend fun check(
        assumptions: IntArray,
        budget: SearchBudget,
    ): Assignment? {
        circuit.useBudget(budget)
        while (true) {
            budget.checkpoint()
            val result = sat.solve(assumptions, budget::checkpoint)
            if (result.status == SatStatus.UNSATISFIABLE) return null
            val values = requireNotNull(result.values)
            val inequalities = translation.realAtoms.map { it.inequality(values) }
            val realResult = LinearFeasibility().solve(translation.realCount, inequalities, budget::checkpoint)
            val reals = realResult.values
            if (reals != null) {
                return translation.assignment(values, reals).also { validate(it, model.constraints) }
            }
            // A coarse, sound theory lemma. Both atom polarity and bit-valued coefficients are dependencies.
            val dependencies = linkedSetOf<Int>()
            for (atom in translation.realAtoms) {
                budget.checkpoint()
                dependencies += atom.literal
                dependencies += atom.form.bitCoefficients.keys
            }
            circuit.clause(*dependencies.map { if (values[it]) -it else it }.toIntArray())
            theoryConflicts++
        }
    }

    fun validate(
        assignment: Assignment,
        constraints: List<BoolExpr>,
    ) {
        if (!constraints.all { GroundEvaluator.evaluate(it, assignment) }) {
            throw BackendExecutionException("kotlin/direct produced a witness violating an original assertion")
        }
    }

    fun metadata(budget: SearchBudget): SolveMetadata =
        SolveMetadata(
            statistics =
                mapOf(
                    "work" to budget.work.toString(),
                    "variables" to circuit.variables.toString(),
                    "compiledClauses" to circuit.clauses.toString(),
                    "decisions" to sat.decisions.toString(),
                    "conflicts" to sat.conflicts.toString(),
                    "propagations" to sat.propagations.toString(),
                    "theoryConflicts" to theoryConflicts.toString(),
                    "presolveReductions" to presolveReductions.toString(),
                ),
        )
}
