package io.github.gciatto.ktconstraints.solve.z3.session
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.incremental.IncrementalSession
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.z3.port.Z3Owner
import io.github.gciatto.ktconstraints.solve.z3.translation.Z3Support
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Platform-independent Z3 solver; every stream and session owns a fresh port from [newOwner]. */
@OptIn(ExperimentalAtomicApi::class)
internal class Z3Solver<T>(
    override val descriptor: SolverDescriptor,
    private val newOwner: () -> Z3Owner<T>,
) : ManagedConstraintSolver(),
    IncrementalConstraintSolver {
    private val sessions = AtomicReference(emptySet<Z3Session<T>>())

    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream {
        requireSupported(model)
        return Z3Stream(model, options, descriptor, newOwner(), onClosed)
    }

    override fun openSession(model: ConstraintModel): IncrementalSession {
        check(!isClosed) { "Solver is closed" }
        requireSupported(model)
        val session = Z3Session(model, descriptor, newOwner()) { closed -> update { it - closed } }
        update { it + session }
        // close() may have drained the sessions between the check above and registration.
        if (isClosed) {
            session.close()
            error("Solver is closed")
        }
        return session
    }

    override fun onClose() = sessions.load().forEach { it.close() }

    private fun update(change: (Set<Z3Session<T>>) -> Set<Z3Session<T>>) {
        while (true) {
            val current = sessions.load()
            if (sessions.compareAndSet(current, change(current))) return
        }
    }

    private fun requireSupported(model: ConstraintModel) {
        val report = Z3Support.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
    }
}
