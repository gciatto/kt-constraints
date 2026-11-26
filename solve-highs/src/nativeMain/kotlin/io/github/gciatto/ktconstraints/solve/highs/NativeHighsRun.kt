@file:OptIn(ExperimentalForeignApi::class, ExperimentalAtomicApi::class)

package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_create
import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_destroy
import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_getInfinity
import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_getIntInfoValue
import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_getModelStatus
import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_getSolution
import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_passMip
import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_run
import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_setBoolOptionValue
import io.github.gciatto.ktconstraints.solve.highs.capi.Highs_setDoubleOptionValue
import io.github.gciatto.ktconstraints.solve.highs.capi.khs_watch
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.github.gciatto.ktconstraints.solve.linear.LinearRelation
import io.github.gciatto.ktconstraints.solve.linear.approximate
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.free
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.value
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** One blocking HiGHS C API run on an IO thread; its interrupt flag is polled by HiGHS' interrupt callbacks. */
internal class NativeHighsRun : HighsRun {
    private val lock = AtomicBoolean(false)
    private var flag: IntVar? = null
    private var interrupted = false

    override suspend fun run(
        program: LinearProgram,
        timeoutMillis: Long?,
    ): HighsOutcome =
        coroutineScope {
            val task = async(Dispatchers.IO) { solve(program, timeoutMillis) }
            try {
                task.await()
            } catch (cancelled: CancellationException) {
                interrupt()
                throw cancelled
            }
        }

    override fun interrupt() =
        locked {
            interrupted = true
            flag?.value = 1
        }

    private fun solve(
        program: LinearProgram,
        timeoutMillis: Long?,
    ): HighsOutcome {
        val highs = checkNotNull(Highs_create()) { "HiGHS could not create an instance" }
        val watched = nativeHeap.alloc<IntVar>().also { it.value = 0 }
        locked {
            flag = watched
            if (interrupted) watched.value = 1
        }
        try {
            configure(highs, timeoutMillis)
            check(khs_watch(highs, watched.ptr)) { "HiGHS rejected its interrupt callbacks" }
            memScoped { pass(highs, program) }
            Highs_run(highs)
            return outcome(highs, program.columns.size)
        } finally {
            locked { flag = null }
            nativeHeap.free(watched)
            Highs_destroy(highs)
        }
    }

    private fun configure(
        highs: COpaquePointer,
        timeoutMillis: Long?,
    ) {
        Highs_setBoolOptionValue(highs, "output_flag", 0)
        Highs_setDoubleOptionValue(highs, "mip_rel_gap", 0.0)
        Highs_setDoubleOptionValue(highs, "mip_abs_gap", 0.0)
        timeoutMillis?.let { Highs_setDoubleOptionValue(highs, "time_limit", it / MILLIS_PER_SECOND) }
    }

    /** Passes [program] row-wise; strict rows never reach floating-point routes. */
    private fun MemScope.pass(
        highs: COpaquePointer,
        program: LinearProgram,
    ) {
        val infinity = Highs_getInfinity(highs)
        val columns = program.columns.size
        val rows = program.rows.size
        val nonZeros = program.rows.sumOf { it.terms.size }
        val cost = allocArray<DoubleVar>(columns.coerceAtLeast(1))
        val lower = allocArray<DoubleVar>(columns.coerceAtLeast(1))
        val upper = allocArray<DoubleVar>(columns.coerceAtLeast(1))
        val integrality = allocArray<IntVar>(columns.coerceAtLeast(1))
        program.columns.forEachIndexed { i, column ->
            cost[i] = 0.0
            lower[i] = column.lower?.let { approximate(it) } ?: -infinity
            upper[i] = column.upper?.let { approximate(it) } ?: infinity
            integrality[i] = if (column.integer) 1 else 0
        }
        program.objective?.terms?.forEach { (i, c) -> cost[i] = approximate(c) }
        val rowLower = allocArray<DoubleVar>(rows.coerceAtLeast(1))
        val rowUpper = allocArray<DoubleVar>(rows.coerceAtLeast(1))
        val starts = allocArray<IntVar>(rows.coerceAtLeast(1))
        val indices = allocArray<IntVar>(nonZeros.coerceAtLeast(1))
        val values = allocArray<DoubleVar>(nonZeros.coerceAtLeast(1))
        var next = 0
        program.rows.forEachIndexed { r, row ->
            check(row.relation != LinearRelation.LESS) { "Strict rows are rejected during support" }
            val rhs = approximate(row.rhs)
            rowLower[r] = if (row.relation == LinearRelation.EQUAL) rhs else -infinity
            rowUpper[r] = rhs
            starts[r] = next
            row.terms.forEach { (i, c) ->
                indices[next] = i
                values[next] = approximate(c)
                next++
            }
        }
        val goal = program.objective
        val status =
            Highs_passMip(
                highs,
                columns,
                rows,
                nonZeros,
                ROW_WISE,
                if (goal?.maximize == true) MAXIMIZE else MINIMIZE,
                goal?.let { approximate(it.constant) } ?: 0.0,
                cost,
                lower,
                upper,
                rowLower,
                rowUpper,
                starts,
                indices,
                values,
                integrality,
            )
        if (status == STATUS_ERROR) throw BackendExecutionException("HiGHS rejected the program")
    }

    private fun outcome(
        highs: COpaquePointer,
        columns: Int,
    ): HighsOutcome =
        memScoped {
            val model = Highs_getModelStatus(highs)
            val primal = alloc<IntVar>()
            Highs_getIntInfoValue(highs, "primal_solution_status", primal.ptr)
            val values =
                if (primal.value == SOLUTION_FEASIBLE || model == HighsStatus.MODEL_EMPTY) {
                    val buffer = allocArray<DoubleVar>(columns.coerceAtLeast(1))
                    val ignored = allocArray<DoubleVar>(columns.coerceAtLeast(1))
                    Highs_getSolution(highs, buffer, ignored, null, null)
                    List(columns) { buffer[it] }
                } else {
                    null
                }
            HighsOutcome(HighsStatus.of(model), values, "HiGHS model status $model")
        }

    private inline fun <T> locked(block: () -> T): T {
        while (!lock.compareAndSet(false, true)) {
            // Spin: the holder never blocks.
        }
        try {
            return block()
        } finally {
            lock.store(false)
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000.0
        const val ROW_WISE = 2
        const val MINIMIZE = 1
        const val MAXIMIZE = -1
        const val STATUS_ERROR = -1
        const val SOLUTION_FEASIBLE = 2
    }
}
