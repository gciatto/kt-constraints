@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js.store
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.js.expression.BoolExpression
import io.github.gciatto.ktconstraints.js.expression.IntExpression
import io.github.gciatto.ktconstraints.js.expression.RealExpression
import io.github.gciatto.ktconstraints.js.global.max
import io.github.gciatto.ktconstraints.js.global.min
import io.github.gciatto.ktconstraints.js.platform.AbortSignal
import io.github.gciatto.ktconstraints.js.platform.cancellable
import io.github.gciatto.ktconstraints.js.platform.toBigInt
import io.github.gciatto.ktconstraints.js.platform.toBigInteger
import io.github.gciatto.ktconstraints.solve.store.ConstraintStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt
import kotlin.js.Promise

/**
 * Constraint store for an external logic engine: declare variables at any time, post constraints one by one,
 * backtrack with [mark]/[undoTo], ask entailment and explanations, and [fork]. One operation at a time.
 */
@JsExport
@Suppress("TooManyFunctions") // One method per store operation, as in the Kotlin and Java APIs.
class Store internal constructor(
    private val store: ConstraintStore,
) {
    private val scope = CoroutineScope(SupervisorJob())
    private val integers = hashMapOf<IntVar, IntExpression>()
    private val posted = hashMapOf<BoolExpr, BoolExpression>()

    /** Engine and execution route, such as `z3/wasm`. */
    val id: String get() = store.descriptor.id

    /** Whether the posted constraints are known to be unsatisfiable. */
    val isFailed: Boolean get() = store.isFailed

    /** Declares an integer variable over [min]..[max]. */
    fun intVar(
        name: String,
        min: JsBigInt,
        max: JsBigInt,
    ): IntExpression {
        val variable = store.intVar(name, IntDomain.of(min.toBigInteger(), max.toBigInteger()))
        return IntExpression(variable).also { integers[variable] = it }
    }

    /** Declares a Boolean variable. */
    fun boolVar(name: String): BoolExpression = BoolExpression(store.boolVar(name))

    /** Declares an exact real variable, where the route supports reals. */
    fun realVar(name: String): RealExpression = RealExpression(store.realVar(name))

    /** Posts [constraint]; [signal] cancels the post where the route allows it. */
    fun constrain(
        constraint: BoolExpression,
        timeoutMillis: Int? = null,
        signal: AbortSignal? = null,
    ): Promise<StorePropagation> =
        scope.cancellable(signal) {
            val result = store.constrain(constraint.expr, timeoutMillis?.toLong())
            posted[constraint.expr] = constraint
            StorePropagation(result.status.name, result.changed.mapNotNull { integers[it] }.toTypedArray())
        }

    /** Records the current state. */
    fun mark(): Mark = Mark(store.mark())

    /** Restores [mark], invalidating it and newer marks; stale or foreign marks are rejected. */
    fun undoTo(mark: Mark) = store.undoTo(mark.mark)

    /** Whether the store implies [constraint], its negation, or neither; nothing is posted. */
    fun entails(
        constraint: BoolExpression,
        timeoutMillis: Int? = null,
        signal: AbortSignal? = null,
    ): Promise<EntailmentAnswer> =
        scope.cancellable(signal) {
            val answer = store.entails(constraint.expr, timeoutMillis?.toLong())
            EntailmentAnswer(answer.value.name, answer.evidence.name)
        }

    /** When failed, posted constraints inconsistent by themselves, as the caller's own expressions; otherwise null. */
    fun explain(
        timeoutMillis: Int? = null,
        signal: AbortSignal? = null,
    ): Promise<Array<BoolExpression>?> =
        scope.cancellable(signal) {
            store.explain(timeoutMillis?.toLong())?.map { posted[it] ?: BoolExpression(it) }?.toTypedArray()
        }

    /** Current domain of [variable] as inclusive `[min, max]` intervals. */
    fun domain(variable: IntExpression): Array<Array<JsBigInt>> {
        val declared = requireNotNull(variable.expr as? IntVar) { "Not a store variable" }
        return store
            .domain(declared)
            .intervals
            .map { arrayOf(it.min.toBigInt(), it.max.toBigInt()) }
            .toTypedArray()
    }

    /** An independent copy of the store, without marks; close it separately. */
    fun fork(): Store =
        Store(store.fork()).also {
            it.integers.putAll(integers)
            it.posted.putAll(posted)
        }

    /** Cancels pending operations and releases the store; idempotent. */
    fun close() {
        scope.cancel()
        store.close()
    }
}
