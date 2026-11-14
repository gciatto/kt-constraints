package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveMetadata
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.project
import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gciatto.kt.math.BigInteger

internal class MiniZincDecoder(
    private val compiled: MiniZincModel,
) {
    private val messages = mutableListOf<String>()
    private val statistics = linkedMapOf<String, String>()
    private val seen = hashSetOf<List<Any>>()
    private var status: String? = null
    private var incumbent: SolutionFound? = null

    fun accept(line: String): SolutionFound? =
        try {
            val message = Json.parseToJsonElement(line).jsonObject
            when (message["type"]?.jsonPrimitive?.content) {
                "solution" -> {
                    if (status != null) fail("Solution received after terminal status")
                    solution(message)
                }
                "status" -> {
                    val next = message["status"]?.jsonPrimitive?.content ?: fail("Missing status")
                    if (status != null && status != next) fail("Conflicting terminal statuses")
                    status = next
                    null
                }
                "statistics" -> {
                    message.getValue("statistics").jsonObject.forEach { (key, value) -> statistics[key] = value.toString() }
                    null
                }
                "warning", "comment", "trace" -> {
                    messages += line
                    null
                }
                "time" -> {
                    message["time"]?.let { statistics["time"] = it.toString() }
                    null
                }
                "error" -> fail("MiniZinc error: $line")
                else -> fail("Unsupported MiniZinc message: $line")
            }
        } catch (failure: Exception) {
            if (failure is BackendExecutionException) throw failure
            throw BackendExecutionException("Invalid MiniZinc output: $line", failure)
        }

    fun finish(
        exitCode: Int,
        stderr: String,
    ): SolveResult {
        if (exitCode != 0) fail("MiniZinc exited with $exitCode: $stderr")
        if (stderr.isNotBlank()) messages += "stderr: $stderr"
        val best = incumbent
        val outcome =
            when (status ?: fail("MiniZinc ended without a terminal status")) {
                "ALL_SOLUTIONS" -> {
                    if (compiled.model.objective != null) fail("Optimization ended without an optimality status")
                    if (best == null) SolveStatus.INFEASIBLE else SolveStatus.FEASIBLE
                }
                "OPTIMAL_SOLUTION" -> {
                    if (compiled.model.objective == null ||
                        best?.objectiveValue == null
                    ) {
                        fail("Optimal status without an objective incumbent")
                    }
                    SolveStatus.OPTIMAL
                }
                "UNSATISFIABLE" -> {
                    if (best != null) fail("Infeasibility contradicts a feasible assignment")
                    SolveStatus.INFEASIBLE
                }
                "UNKNOWN" -> if (best == null) SolveStatus.UNKNOWN else SolveStatus.FEASIBLE
                else -> fail("Unexpected status for finite Gecode model: $status")
            }
        return SolveResult(
            outcome,
            best?.assignment,
            gecodeDescriptor,
            objectiveValue = best?.objectiveValue,
            bestBound = if (outcome == SolveStatus.OPTIMAL) best?.objectiveValue else null,
            metadata = SolveMetadata(messages, statistics),
        )
    }

    private fun solution(message: JsonObject): SolutionFound? {
        val raw =
            message
                .getValue("output")
                .jsonObject
                .getValue("default")
                .jsonPrimitive
        if (!raw.isString) fail("Expected a JSON output section string")
        val values = Json.parseToJsonElement(raw.content).jsonObject
        if (values.keys != compiled.sourceVariables.keys) fail("Solution variable mapping does not match the compiled model")
        val booleans = linkedMapOf<BoolVar, Boolean>()
        val integers = linkedMapOf<IntVar, BigInteger>()
        compiled.sourceVariables.forEach { (name, variable) ->
            val value = values.getValue(name).jsonPrimitive
            if (value.isString) fail("Unexpected string value for $name")
            when (variable) {
                is BoolVar -> booleans[variable] = value.booleanOrNull ?: fail("Expected Boolean value for $name")
                is IntVar -> integers[variable] = BigInteger.of(value.content)
                else -> fail("Unsupported output variable")
            }
        }
        val assignment = Assignment.of(booleans, integers)
        if (!compiled.model.constraints.all {
                GroundEvaluator.evaluate(
                    it,
                    assignment,
                )
            }
        ) {
            fail("Returned assignment violates the source model")
        }
        val value =
            when (val objective = compiled.model.objective) {
                is Objective.Minimize -> GroundEvaluator.evaluate(objective.expression, assignment)
                is Objective.Maximize -> GroundEvaluator.evaluate(objective.expression, assignment)
                null -> null
            }
        val projected = assignment.project(compiled.model.outputVariables)
        val event = SolutionFound(projected, value)
        if (compiled.model.objective == null) {
            if (incumbent == null) incumbent = event
            val key: List<Any> =
                compiled.model.outputVariables.map { variable ->
                    when (variable) {
                        is BoolVar -> requireNotNull(projected.valueOf(variable))
                        is IntVar -> requireNotNull(projected.valueOf(variable))
                        else -> fail("Unsupported projection variable")
                    }
                }
            return if (seen.add(key)) event else null
        }
        incumbent?.objectiveValue?.let { previous ->
            if (when (compiled.model.objective) {
                    is Objective.Minimize -> requireNotNull(value) >= previous
                    else -> requireNotNull(value) <= previous
                }
            ) {
                return null
            }
        }
        incumbent = event
        return event
    }

    private fun fail(message: String): Nothing = throw BackendExecutionException(message)
}
