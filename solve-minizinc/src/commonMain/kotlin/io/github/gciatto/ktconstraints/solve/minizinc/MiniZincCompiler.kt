package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.spi.ConstraintCompiler
import io.github.gciatto.ktconstraints.solve.spi.SupportReport
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException

/** Pure compiler for the exact finite Gecode subset, using MiniZinc for flattening and global lowering. */
class MiniZincCompiler : ConstraintCompiler<MiniZincModel> {
    override fun support(model: ConstraintModel, options: SolveOptions): SupportReport = try {
        compile(model, options).close()
        SupportReport()
    } catch (unsupported: UnsupportedModelException) {
        unsupported.report
    }

    override fun compile(model: ConstraintModel, options: SolveOptions): MiniZincModel {
        val names = model.variables.mapIndexed { index, variable -> variable.id to "v$index" }.toMap()
        val lowering = MiniZincLowering(names)
        val declarations = model.variables.map { variable ->
            lowering.lower(variable)
            val name = names.getValue(variable.id)
            when (variable) {
                is BoolVar -> "var bool: $name;"
                is IntVar -> {
                    if (variable.domain.isEmpty) {
                        lowering.definedness += "false"
                        "var 0..0: $name;"
                    } else {
                        val domain = variable.domain.intervals.joinToString(" union ") { "${it.min}..${it.max}" }
                        "var ($domain): $name;"
                    }
                }
                else -> throw UnsupportedModelException(SupportReport(listOf("Unsupported variable: ${variable.name}")))
            }
        }
        model.outputVariables.forEach { lowering.lower(it) }
        val constraints = model.constraints.map { lowering.lower(it).text }
        val solve = when (val objective = model.objective) {
            is Objective.Minimize -> "solve minimize ${lowering.lower(objective.expression).text};"
            is Objective.Maximize -> "solve maximize ${lowering.lower(objective.expression).text};"
            null -> "solve satisfy;"
        }
        val output = mutableListOf(quote("{"))
        model.variables.indices.forEach { index ->
            output += quote("${if (index == 0) "" else ","}\"v$index\":")
            output += "show(v$index)"
        }
        output += quote("}")
        val source = buildString {
            lowering.includes.forEach { appendLine("include ${quote(it)};") }
            declarations.forEach { appendLine(it) }
            lowering.definedness.forEach { appendLine("constraint $it;") }
            constraints.forEach { appendLine("constraint $it;") }
            appendLine(solve)
            appendLine("output [${output.joinToString(", ")}];")
        }
        return MiniZincModel(model, source)
    }

    private fun quote(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
}
