package io.github.gciatto.ktconstraints.interop

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.solve.ConstraintStore
import io.github.gciatto.ktconstraints.solve.EntailmentResult
import io.github.gciatto.ktconstraints.solve.Propagation
import io.github.gciatto.ktconstraints.solve.Residual
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.StoreMark
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CompletableFuture

/**
 * Java owner of one [ConstraintStore]: posting, entailment and explanation are blocking or [CompletableFuture]-based,
 * so Java callers never handle Kotlin suspension. Single consumer: do not overlap calls, including pending futures.
 * Closing it cancels pending futures and closes the store.
 */
@Suppress("TooManyFunctions") // One method per store operation, plus blocking and asynchronous variants.
class BlockingStore private constructor(
    private val store: ConstraintStore,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** Engine and route behind the store. */
    val descriptor: SolverDescriptor get() = store.descriptor

    /** Whether the posted constraints are known to be unsatisfiable. */
    val isFailed: Boolean get() = store.isFailed

    /** Declares a Boolean variable. */
    fun boolVar(name: String): BoolVar = store.boolVar(name)

    /** Declares an integer variable over [domain]. */
    fun intVar(
        name: String,
        domain: IntDomain,
    ): IntVar = store.intVar(name, domain)

    /** Declares an integer variable over the inclusive interval [min]..[max]. */
    fun intVar(
        name: String,
        min: Long,
        max: Long,
    ): IntVar = store.intVar(name, IntDomain.of(min, max))

    /** Declares an exact real variable, where the store supports reals. */
    fun realVar(name: String): RealVar = store.realVar(name)

    /** Posts [constraint] on the calling thread. */
    @JvmOverloads
    fun constrain(
        constraint: BoolExpr,
        timeoutMillis: Long? = null,
    ): Propagation = runBlocking { store.constrain(constraint, timeoutMillis) }

    /** Posts [constraint] in the background; cancelling the future cancels the post where the store allows it. */
    @JvmOverloads
    fun constrainAsync(
        constraint: BoolExpr,
        timeoutMillis: Long? = null,
    ): CompletableFuture<Propagation> = scope.future { store.constrain(constraint, timeoutMillis) }

    /** Records the current state. */
    fun mark(): StoreMark = store.mark()

    /** Restores [mark], invalidating it and newer marks. */
    fun undoTo(mark: StoreMark) = store.undoTo(mark)

    /** Whether the store implies [constraint], its negation, or neither, on the calling thread. */
    @JvmOverloads
    fun entails(
        constraint: BoolExpr,
        timeoutMillis: Long? = null,
    ): EntailmentResult = runBlocking { store.entails(constraint, timeoutMillis) }

    /** Entailment in the background. */
    @JvmOverloads
    fun entailsAsync(
        constraint: BoolExpr,
        timeoutMillis: Long? = null,
    ): CompletableFuture<EntailmentResult> = scope.future { store.entails(constraint, timeoutMillis) }

    /** Posted constraints inconsistent by themselves when the store has failed; null otherwise. */
    @JvmOverloads
    fun explain(timeoutMillis: Long? = null): List<BoolExpr>? = runBlocking { store.explain(timeoutMillis) }

    /** Current domain of [variable]. */
    fun domain(variable: IntVar): IntDomain = store.domain(variable)

    /** Constraints the store still holds on [variables]. */
    fun project(variables: List<Variable>): Residual = store.project(variables)

    /** An independent copy of the store, without marks. */
    fun fork(): BlockingStore = BlockingStore(store.fork())

    override fun close() {
        scope.cancel()
        store.close()
    }

    /** Static factories. */
    companion object {
        /** Takes ownership of [store]. */
        @JvmStatic
        fun of(store: ConstraintStore): BlockingStore = BlockingStore(store)
    }
}
