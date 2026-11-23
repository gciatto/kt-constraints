package io.github.gciatto.ktconstraints.solve.minizinc.runtime
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincCompiler
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincJsBackend
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedConstraintSolver
import io.github.gciatto.ktconstraints.solve.spi.managed.ManagedSolutionStream

internal class MiniZincJsSolver(
    private val backend: MiniZincJsBackend,
) : ManagedConstraintSolver() {
    override val descriptor: SolverDescriptor = backend.descriptor

    override fun openStream(
        model: ConstraintModel,
        options: SolveOptions,
        onClosed: () -> Unit,
    ): ManagedSolutionStream {
        backend.requireOpen()
        lateinit var stream: MiniZincJsStream
        stream =
            MiniZincJsStream(MiniZincCompiler(backend.engine).compile(model, options), descriptor, backend.configurationId, options, {
                backend.withSettledPool(it)
            }) {
                backend.remove(stream)
                onClosed()
            }
        backend.register(stream)
        return stream
    }
}
