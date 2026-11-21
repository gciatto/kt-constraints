package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.IncrementalSession
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Owns isolated streams and incremental sessions; immutable model objects never own search state. */
@OptIn(ExperimentalAtomicApi::class)
internal class KotlinSolver(
    override val descriptor: SolverDescriptor,
    private val configuration: KotlinConfiguration,
) : ManagedConstraintSolver(),
    IncrementalConstraintSolver {
    private val sessions = AtomicReference(emptySet<KotlinSession>())

    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream {
        requireSupported(model)
        return KotlinStream(model, options, descriptor, configuration, onClosed)
    }

    override fun openSession(model: ConstraintModel): IncrementalSession {
        check(!isClosed) { "Solver is closed" }
        requireSupported(model)
        val session = KotlinSession(model, descriptor, configuration) { closed -> update { it - closed } }
        update { it + session }
        if (isClosed) {
            session.close()
            error("Solver is closed")
        }
        return session
    }

    override fun onClose() = sessions.load().forEach { it.close() }

    private fun update(change: (Set<KotlinSession>) -> Set<KotlinSession>) {
        while (true) {
            val current = sessions.load()
            if (sessions.compareAndSet(current, change(current))) return
        }
    }

    private fun requireSupported(model: ConstraintModel) {
        val report = KotlinSupport.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
    }
}
