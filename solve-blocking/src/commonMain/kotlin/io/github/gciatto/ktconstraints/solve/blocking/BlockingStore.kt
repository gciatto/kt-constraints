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
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Owner of one [ConstraintStore] whose posting, entailment and explanation block the calling thread; on the JVM they
 * are also available as `CompletableFuture`s. Single consumer: do not overlap calls, including pending futures.
 * Closing it cancels pending futures and closes the store.
 */
@Suppress("TooManyFunctions") // One method per store operation.
expect class BlockingStore : AutoCloseable {
    /** Engine and route behind the store. */
    val descriptor: SolverDescriptor

    /** Whether the posted constraints are known to be unsatisfiable. */
    val isFailed: Boolean

    /** Declares a Boolean variable. */
    fun boolVar(name: String): BoolVar

    /** Declares an integer variable over [domain]. */
    fun intVar(
        name: String,
        domain: IntDomain,
    ): IntVar

    /** Declares an integer variable over the inclusive interval [min]..[max]. */
    fun intVar(
        name: String,
        min: Long,
        max: Long,
    ): IntVar

    /** Declares an exact real variable, where the store supports reals. */
    fun realVar(name: String): RealVar

    /** Posts [constraint] on the calling thread. */
    @JvmOverloads
    fun constrain(
        constraint: BoolExpr,
        timeoutMillis: Long? = null,
    ): Propagation

    /** Records the current state. */
    fun mark(): StoreMark

    /** Restores [mark], invalidating it and newer marks. */
    fun undoTo(mark: StoreMark)

    /** Whether the store implies [constraint], its negation, or neither, on the calling thread. */
    @JvmOverloads
    fun entails(
        constraint: BoolExpr,
        timeoutMillis: Long? = null,
    ): EntailmentResult

    /** Posted constraints inconsistent by themselves when the store has failed; null otherwise. */
    @JvmOverloads
    fun explain(timeoutMillis: Long? = null): List<BoolExpr>?

    /** Current domain of [variable]. */
    fun domain(variable: IntVar): IntDomain

    /** Constraints the store still holds on [variables]. */
    fun project(variables: List<Variable>): Residual

    /** An independent copy of the store, without marks. */
    fun fork(): BlockingStore

    override fun close()

    /** Static factories. */
    companion object {
        /** Takes ownership of [store]. */
        @JvmStatic
        fun of(store: ConstraintStore): BlockingStore
    }
}

/** Wraps this store, which the result owns, in a [BlockingStore]. */
fun ConstraintStore.blocking(): BlockingStore = BlockingStore.of(this)
