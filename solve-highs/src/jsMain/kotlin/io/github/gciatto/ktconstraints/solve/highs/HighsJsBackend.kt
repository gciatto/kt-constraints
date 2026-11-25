package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ConstraintSolver
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.linear.FloatingPointPrograms
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.SolverBackend
import io.github.gciatto.ktconstraints.solve.spi.SolverBackendLoader
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendUnavailableException
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * HiGHS through the highs Wasm module, in Node and browsers. It solves linear and mixed-integer linear programs in
 * floating point: models with reals need `SolveOptions.numericTolerance`. Solves run on the JavaScript thread and
 * stop only at their time limit.
 */
class HighsJsBackend private constructor(
    private val highs: dynamic,
) : SolverBackend {
    override val descriptor: SolverDescriptor = HighsRoutes.WASM

    override fun availability(): BackendAvailability = BackendAvailability(true)

    override fun support(
        model: ConstraintModel,
        options: SolveOptions,
    ): SupportReport = FloatingPointPrograms.report(model, options)

    override fun createSolver(): ConstraintSolver = HighsSolver(descriptor) { JsHighsRun(highs) }

    /** Asynchronous loading. */
    companion object : SolverBackendLoader<HighsJsBackend> {
        /** Initializes a fresh highs module. */
        override suspend fun load(): HighsJsBackend {
            val loader: dynamic = highsModule.default ?: highsModule
            val highs: dynamic =
                runCatching { loader().unsafeCast<Promise<dynamic>>().await() }
                    .getOrElse { throw BackendUnavailableException("highs cannot start: ${it.message}", it) }
            return HighsJsBackend(highs)
        }
    }
}
