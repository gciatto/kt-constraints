package io.github.gciatto.ktconstraints.solve.kotlin.runtime
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration
import io.github.gciatto.ktconstraints.solve.kotlin.arithmetic.LinearFeasibility
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.KotlinTranslation
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.circuit.Circuit
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.encoding.IntegerEncoding
import io.github.gciatto.ktconstraints.solve.kotlin.compiler.linear.IntegerLinear
import io.github.gciatto.ktconstraints.solve.kotlin.cp.presolve.IntegerPresolver
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatSolver
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatStatus
import io.github.gciatto.ktconstraints.solve.kotlin.search.SearchBudget
import io.github.gciatto.ktconstraints.solve.result.SolveMetadata
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.support.rejectRealObjective
import org.gciatto.kt.math.BigInteger

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
    private var theoryBudget = budget

    /** The real theory; its tableau persists across checks, so each SAT model warm-starts from the last basis. */
    private val reals = LinearFeasibility { theoryBudget.consume() }
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
                    is Objective.MinimizeReal, is Objective.MaximizeReal ->
                        rejectRealObjective()
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

    /** The compiled [objective] reaches score [target]; minimization negates the score. */
    suspend fun objectiveAtLeast(
        objective: Any,
        target: BigInteger,
        maximize: Boolean,
    ): Int =
        if (maximize) {
            translation.linear.lessOrEqual(translation.binary.integer(target), objective)
        } else {
            translation.linear.lessOrEqual(objective, translation.binary.integer(-target))
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
            val atoms = translation.realAtoms
            theoryBudget = budget
            val realResult = reals.solve(translation.realCount, atoms.map { it.inequality(values) })
            realResult.values?.let { witness ->
                return translation.assignment(values, witness).also { validate(it, model.constraints) }
            }
            // The lemma blocks only the atoms in the checked Farkas refutation: their polarity and bit-valued constants.
            val dependencies = linkedSetOf<Int>()
            for (atom in realResult.conflict.map { atoms[it] }) {
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
                    "technique" to "sat",
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
