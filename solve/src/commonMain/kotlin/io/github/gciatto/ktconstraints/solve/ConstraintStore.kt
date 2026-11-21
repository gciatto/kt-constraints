package io.github.gciatto.ktconstraints.solve

import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolVar
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntVar
import io.github.gciatto.ktconstraints.core.RealVar
import io.github.gciatto.ktconstraints.core.Variable

/**
 * Solver-neutral logical store an external logic engine owns: variables are declared at any time, constraints are
 * posted one by one, and [mark]/[undoTo] backtrack in LIFO order. Single consumer. A failed store stays failed,
 * and rejects nothing but answers [StoreStatus.FAILED], until [undoTo] restores a mark taken before the failure.
 */
interface ConstraintStore : AutoCloseable {
    /** Engine and route behind this store. */
    val descriptor: SolverDescriptor

    /** Whether the posted constraints are known to be unsatisfiable. */
    val isFailed: Boolean

    /** Declares a Boolean variable owned by this store. */
    fun boolVar(name: String): BoolVar

    /** Declares an integer variable over [domain] owned by this store. */
    fun intVar(
        name: String,
        domain: IntDomain,
    ): IntVar

    /** Declares an exact real variable; stores without exact reals reject it. */
    fun realVar(name: String): RealVar

    /** Posts [constraint], reporting what the engine could establish within [timeoutMillis]. */
    suspend fun constrain(
        constraint: BoolExpr,
        timeoutMillis: Long? = null,
    ): Propagation

    /** Records the current constraints, variables and domains. */
    fun mark(): StoreMark

    /** Restores [mark], invalidating it and newer marks; stale or foreign marks are rejected. */
    fun undoTo(mark: StoreMark)

    /** Whether the posted constraints imply [constraint], its negation, or neither; nothing is posted. */
    suspend fun entails(
        constraint: BoolExpr,
        timeoutMillis: Long? = null,
    ): EntailmentResult

    /** Current domain of [variable]: the declared domain unless the store propagates. */
    fun domain(variable: IntVar): IntDomain

    /** Constraints the store still holds on [variables]; see [Residual] for its precision. */
    fun project(variables: List<Variable>): Residual

    /**
     * When the store has failed, posted constraints that are inconsistent by themselves (with the declared domains),
     * found with the store's own evidence; not necessarily minimal. Null while the store has not failed.
     */
    suspend fun explain(timeoutMillis: Long? = null): List<BoolExpr>?

    /**
     * An independent store holding the same variables, constraints and failure state, but none of the marks. Later
     * changes to either store do not affect the other. Costs a replay of the current constraints.
     */
    fun fork(): ConstraintStore

    /** Releases engine resources; idempotent. */
    override fun close()
}
