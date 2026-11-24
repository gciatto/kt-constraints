package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolVar
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.core.real.RealVar
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.store.ConstraintStore
import io.github.gciatto.ktconstraints.solve.store.EntailmentResult
import io.github.gciatto.ktconstraints.solve.store.Propagation
import io.github.gciatto.ktconstraints.solve.store.Residual
import io.github.gciatto.ktconstraints.solve.store.StoreMark
import kotlinx.coroutines.runBlocking

/**
 * Owner of one [ConstraintStore]: posting, entailment and explanation block the calling thread, so callers never
 * handle Kotlin suspension. Single consumer: do not overlap calls. Closing it closes the store.
 */
@Suppress("TooManyFunctions") // One method per store operation.
actual class BlockingStore private constructor(
    private val store: ConstraintStore,
) : AutoCloseable {
    actual val descriptor: SolverDescriptor get() = store.descriptor

    actual val isFailed: Boolean get() = store.isFailed

    actual fun boolVar(name: String): BoolVar = store.boolVar(name)

    actual fun intVar(
        name: String,
        domain: IntDomain,
    ): IntVar = store.intVar(name, domain)

    actual fun intVar(
        name: String,
        min: Long,
        max: Long,
    ): IntVar = store.intVar(name, IntDomain.of(min, max))

    actual fun realVar(name: String): RealVar = store.realVar(name)

    actual fun constrain(
        constraint: BoolExpr,
        timeoutMillis: Long?,
    ): Propagation = runBlocking { store.constrain(constraint, timeoutMillis) }

    actual fun mark(): StoreMark = store.mark()

    actual fun undoTo(mark: StoreMark) = store.undoTo(mark)

    actual fun entails(
        constraint: BoolExpr,
        timeoutMillis: Long?,
    ): EntailmentResult = runBlocking { store.entails(constraint, timeoutMillis) }

    actual fun explain(timeoutMillis: Long?): List<BoolExpr>? = runBlocking { store.explain(timeoutMillis) }

    actual fun domain(variable: IntVar): IntDomain = store.domain(variable)

    actual fun project(variables: List<Variable>): Residual = store.project(variables)

    actual fun fork(): BlockingStore = BlockingStore(store.fork())

    actual override fun close() = store.close()

    /** Static factories. */
    actual companion object {
        /** Takes ownership of [store]. */
        actual fun of(store: ConstraintStore): BlockingStore = BlockingStore(store)
    }
}
