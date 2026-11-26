package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRelation
import io.github.gciatto.ktconstraints.solve.linear.approximate
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException

/**
 * One run of the highs Wasm module. The solve is synchronous on the JavaScript thread, so nothing can interrupt it:
 * it ends at the time limit, and [interrupt] only prevents a run that has not started yet.
 */
internal class JsHighsRun(
    private val highs: dynamic,
) : HighsRun {
    private var interrupted = false

    override suspend fun run(
        program: LinearProgram,
        timeoutMillis: Long?,
    ): HighsOutcome {
        if (interrupted) return HighsOutcome(HighsStatus.INTERRUPTED, null)
        val model: dynamic = highs.createModel(data(program))
        try {
            val options: dynamic = js("({output_flag: false, mip_rel_gap: 0, mip_abs_gap: 0})")
            timeoutMillis?.let { options.time_limit = it / MILLIS_PER_SECOND }
            model.options.set(options)
            model.run()
            val status = (model.getModelStatus() as Number).toInt()
            val primal = (model.info.get("primal_solution_status") as Number).toInt()
            val values =
                if (primal == SOLUTION_FEASIBLE || status == HighsStatus.MODEL_EMPTY) {
                    val found: dynamic = model.getSolution().colValue
                    List(program.columns.size) { (found[it] as Number).toDouble() }
                } else {
                    null
                }
            return HighsOutcome(HighsStatus.of(status), values, "HiGHS model status $status")
        } finally {
            model.dispose()
        }
    }

    override fun interrupt() {
        interrupted = true
    }

    /** The program as highs' structured input, row-wise; strict rows never reach floating-point routes. */
    private fun data(program: LinearProgram): dynamic {
        val infinity = (highs.infinity as Number).toDouble()
        val starts = mutableListOf(0)
        val indices = mutableListOf<Int>()
        val values = mutableListOf<Double>()
        val rowLower = mutableListOf<Double>()
        val rowUpper = mutableListOf<Double>()
        for (row in program.rows) {
            if (row.relation == LinearRelation.LESS) throw BackendExecutionException("Strict rows are rejected during support")
            val rhs = approximate(row.rhs)
            rowLower += if (row.relation == LinearRelation.EQUAL) rhs else -infinity
            rowUpper += rhs
            row.terms.forEach { (i, c) ->
                indices += i
                values += approximate(c)
            }
            starts += indices.size
        }
        val cost = DoubleArray(program.columns.size)
        program.objective?.terms?.forEach { (i, c) -> cost[i] = approximate(c) }
        val goal = program.objective
        val data: dynamic = js("({})")
        data.numCols = program.columns.size
        data.numRows = program.rows.size
        data.sense = if (goal?.maximize == true) highs.constants.objectiveSense.maximize else highs.constants.objectiveSense.minimize
        data.offset = goal?.let { approximate(it.constant) } ?: 0.0
        data.colCost = cost.toTypedArray()
        data.colLower = program.columns.map { column -> column.lower?.let { approximate(it) } ?: -infinity }.toTypedArray()
        data.colUpper = program.columns.map { column -> column.upper?.let { approximate(it) } ?: infinity }.toTypedArray()
        data.rowLower = rowLower.toTypedArray()
        data.rowUpper = rowUpper.toTypedArray()
        val matrix: dynamic = js("({format: 'csr'})")
        matrix.numRows = program.rows.size
        matrix.numCols = program.columns.size
        matrix.starts = starts.toTypedArray()
        matrix.indices = indices.toTypedArray()
        matrix.values = values.toTypedArray()
        data.matrix = matrix
        data.integrality = program.columns.map { if (it.integer) 1 else 0 }.toTypedArray()
        return data
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
        const val SOLUTION_FEASIBLE = 2
    }
}
