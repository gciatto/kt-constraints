package io.github.gciatto.ktconstraints.solve.cpsat

import com.google.ortools.sat.CpSolver
import com.google.ortools.sat.CpSolverSolutionCallback
import com.google.ortools.sat.CpSolverStatus
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.GroundEvaluator
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.Assignment
import io.github.gciatto.ktconstraints.solve.SolutionEvent
import io.github.gciatto.ktconstraints.solve.SolutionFound
import io.github.gciatto.ktconstraints.solve.SolveCompleted
import io.github.gciatto.ktconstraints.solve.SolveMetadata
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.TerminationReason
import io.github.gciatto.ktconstraints.solve.project
import io.github.gciatto.ktconstraints.solve.spi.BackendExecutionException
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.runBlocking
import org.gciatto.kt.math.BigInteger
import kotlin.time.TimeSource

/**
 * One CP-SAT search on its own thread. Callbacks hand validated solutions over a rendezvous channel, so the search
 * waits for the consumer; [release] stops the search, unblocks a waiting callback and joins the thread.
 */
internal class CpSatStream(
    private val model: ConstraintModel,
    private val options: SolveOptions,
    descriptor: SolverDescriptor,
    private val workers: Int,
    private val seed: Int?,
    onClosed: () -> Unit,
) : ManagedSolutionStream(descriptor, onClosed) {
    private val translation = CpSatTranslation(model)
    private val started = TimeSource.Monotonic.markNow()
    private val signals = Channel<CpSatSignal>(Channel.RENDEZVOUS)
    private val solver = CpSolver()
    private val lock = Any()
    private var thread: Thread? = null

    @Volatile
    private var released = false
    private var incumbent: SolutionFound? = null
    private var emitted = 0

    override suspend fun readNext(): SolutionEvent =
        when {
            options.solutionLimit?.let { emitted >= it } == true -> complete(TerminationReason.RESOURCE_LIMIT)
            thread == null && !start() -> complete(TerminationReason.TIMEOUT)
            else -> accept(signals.receive())
        }

    private fun accept(signal: CpSatSignal): SolutionEvent =
        when (signal) {
            is CpSatSignal.Found ->
                signal.solution.also {
                    incumbent = it
                    emitted++
                }
            is CpSatSignal.Failed -> throw signal.problem
            is CpSatSignal.Done -> finished(signal.status)
        }

    override fun release() {
        released = true
        signals.close()
        val search = synchronized(lock) { thread }
        // stopSearch is lost if the native search has not started yet, so repeat it until the thread ends.
        while (search != null && search.isAlive) {
            solver.stopSearch()
            do {
                val drained = signals.tryReceive().isSuccess
            } while (drained)
            search.join(JOIN_STEP_MILLIS)
        }
    }

    /** Configures and starts the search, or returns false when the deadline has already passed. */
    private fun start(): Boolean {
        val remaining = options.timeoutMillis?.let { it - started.elapsedNow().inWholeMilliseconds }
        if (remaining != null && remaining <= 0) return false
        solver.parameters.apply {
            if (remaining != null) setMaxTimeInSeconds(remaining / MILLIS_PER_SECOND)
            if (model.objective == null) {
                setNumWorkers(1)
                setEnumerateAllSolutions(true)
            } else if (workers > 0) {
                setNumWorkers(workers)
            }
            seed?.let { setRandomSeed(it) }
        }
        synchronized(lock) {
            if (!released) thread = Thread(::search, THREAD_NAME).apply { isDaemon = true }.also { it.start() }
        }
        return true
    }

    @Suppress("TooGenericExceptionCaught") // Any search failure must reach the consumer instead of killing the thread.
    private fun search() {
        val signal =
            try {
                CpSatSignal.Done(solver.solve(translation.cpModel, Callback()))
            } catch (problem: Exception) {
                CpSatSignal.Failed(problem)
            }
        deliver(signal)
    }

    private fun deliver(signal: CpSatSignal): Boolean =
        try {
            runBlocking { signals.send(signal) }
            true
        } catch (_: ClosedSendChannelException) {
            false
        }

    private fun finished(status: CpSolverStatus): SolveCompleted =
        when (status) {
            CpSolverStatus.OPTIMAL -> complete(TerminationReason.COMPLETED)
            CpSolverStatus.INFEASIBLE -> {
                if (incumbent != null) throw BackendExecutionException("${descriptor.id} reported infeasibility after a solution")
                complete(TerminationReason.COMPLETED)
            }
            CpSolverStatus.MODEL_INVALID -> throw BackendExecutionException(
                "${descriptor.id} rejected the model: ${translation.cpModel.validate()}",
            )
            else ->
                if (options.timeoutMillis != null) {
                    complete(TerminationReason.TIMEOUT)
                } else {
                    complete(TerminationReason.COMPLETED, proven = false, message = "CP-SAT stopped without proof: $status")
                }
        }

    private fun complete(
        reason: TerminationReason,
        proven: Boolean = true,
        message: String? = null,
    ): SolveCompleted {
        val best = incumbent
        val status =
            when {
                best == null -> if (reason == TerminationReason.COMPLETED && proven) SolveStatus.INFEASIBLE else SolveStatus.UNKNOWN
                model.objective != null && reason == TerminationReason.COMPLETED && proven -> SolveStatus.OPTIMAL
                else -> SolveStatus.FEASIBLE
            }
        return SolveCompleted(
            SolveResult(
                status,
                best?.assignment,
                descriptor,
                reason,
                best?.objectiveValue,
                if (status == SolveStatus.OPTIMAL) best?.objectiveValue else null,
                SolveMetadata(listOfNotNull(message)),
            ),
        )
    }

    /** Runs on the search thread; CP-SAT serializes its invocations. */
    private inner class Callback : CpSolverSolutionCallback() {
        private val seen = hashSetOf<List<Any?>>()
        private var best: BigInteger? = null

        @Suppress("TooGenericExceptionCaught") // An exception must not unwind through the native search.
        override fun onSolutionCallback() {
            if (released) return stopSearch()
            val signal =
                try {
                    solution()?.let { CpSatSignal.Found(it) } ?: return
                } catch (problem: Throwable) {
                    CpSatSignal.Failed(problem)
                }
            if (!deliver(signal) || signal is CpSatSignal.Failed) stopSearch()
        }

        private fun solution(): SolutionFound? {
            val full = translation.assignment(this)
            if (!model.constraints.all { GroundEvaluator.evaluate(it, full) }) {
                throw BackendExecutionException("${descriptor.id} returned an assignment violating the model")
            }
            val projected = full.project(model.outputVariables)
            return when (val objective = model.objective) {
                null -> {
                    val key = model.outputVariables.map { if (it is BoolVar) projected.valueOf(it) else projected.valueOf(it as IntVar) }
                    if (seen.add(key)) SolutionFound(projected) else null
                }
                is Objective.Minimize -> improving(GroundEvaluator.evaluate(objective.expression, full), projected, maximize = false)
                is Objective.Maximize -> improving(GroundEvaluator.evaluate(objective.expression, full), projected, maximize = true)
            }
        }

        private fun improving(
            value: BigInteger,
            projected: Assignment,
            maximize: Boolean,
        ): SolutionFound? {
            val previous = best
            if (previous != null && (if (maximize) value <= previous else value >= previous)) return null
            best = value
            return SolutionFound(projected, value)
        }
    }

    private companion object {
        const val THREAD_NAME = "kt-constraints-cp-sat"
        const val JOIN_STEP_MILLIS = 10L
        const val MILLIS_PER_SECOND = 1000.0
    }
}
