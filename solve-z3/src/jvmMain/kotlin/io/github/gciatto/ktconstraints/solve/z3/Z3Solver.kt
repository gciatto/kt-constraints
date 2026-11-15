package io.github.gciatto.ktconstraints.solve.z3

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.IncrementalConstraintSolver
import io.github.gciatto.ktconstraints.solve.IncrementalSession
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.ManagedSolutionStream
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import java.util.concurrent.ConcurrentHashMap

internal class Z3Solver(
    override val descriptor: SolverDescriptor,
) : ManagedConstraintSolver(),
    IncrementalConstraintSolver {
    private val sessions = ConcurrentHashMap.newKeySet<Z3Session>()

    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream {
        requireSupported(model)
        return Z3Stream(model, options, descriptor, onClosed)
    }

    override fun openSession(model: ConstraintModel): IncrementalSession {
        check(!isClosed) { "Solver is closed" }
        requireSupported(model)
        val session = Z3Session(model, descriptor) { sessions.remove(it) }
        sessions += session
        // close() may have drained the sessions between the check above and registration.
        if (isClosed) {
            session.close()
            error("Solver is closed")
        }
        return session
    }

    override fun onClose() = sessions.toList().forEach { it.close() }

    private fun requireSupported(model: ConstraintModel) {
        val report = Z3Support.report(model)
        if (!report.isSupported) throw UnsupportedModelException(report)
    }
}
