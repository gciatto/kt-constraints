package io.github.gciatto.ktconstraints.solve.choco.runtime
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.domain.IntInterval
import org.chocosolver.solver.Model
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.exception.ContradictionException
import org.chocosolver.util.ESat
import org.gciatto.kt.math.BigInteger
import org.chocosolver.solver.variables.IntVar as ChocoIntVar

/** Reversible-world, propagation and domain-reading mechanics of one Choco [model], as a [ChocoStore] uses them. */
internal class ChocoWorlds(
    private val model: Model,
) {
    /** Current environment world. */
    val world: Int get() = model.environment.worldIndex

    /** Opens a world. */
    fun push() = model.environment.worldPush()

    /** Restores [target] and unposts [natives], which a world pop leaves in place. */
    fun popTo(
        target: Int,
        natives: List<Constraint>,
    ) {
        model.solver.engine.flush()
        model.environment.worldPopUntil(target)
        natives.filter { it.status == Constraint.Status.POSTED }.forEach { model.unpost(it) }
    }

    /** Propagates to a fixpoint; false on contradiction, after which the engine is ready again. */
    fun propagate(): Boolean =
        try {
            model.solver.propagate()
            true
        } catch (_: ContradictionException) {
            model.solver.engine.flush()
            false
        }

    /** Runs [block] and returns the Choco constraints it created, including permanently posted reifications. */
    fun recording(block: () -> Unit): List<Constraint> {
        // shortcut: diffs the whole constraint array per post, O(constraints); track Model.post calls if stores grow large.
        val before = model.cstrs.toHashSet()
        block()
        return model.cstrs.filterNot { it in before }
    }

    /** Whether posting with [post] in a throwaway world makes propagation fail; leaves the model unchanged. */
    fun refutes(post: () -> Unit): Boolean {
        val target = world
        push()
        var natives = emptyList<Constraint>()
        try {
            natives = recording(post)
            return !propagate()
        } finally {
            popTo(target, natives)
        }
    }

    /** Whether every one of [natives] holds for all values left in the domains. */
    fun entailed(natives: List<Constraint>): Boolean = natives.all { it.isSatisfied == ESat.TRUE }

    /** Bounds and size of [variable], enough to tell whether its domain shrank. */
    fun footprint(variable: ChocoIntVar): Triple<Int, Int, Int> = Triple(variable.lb, variable.ub, variable.domainSize)

    /** Current domain of [variable]. */
    fun domain(variable: ChocoIntVar): IntDomain {
        val intervals = mutableListOf<IntInterval>()
        val ranges = variable.getRangeIterator(true)
        while (ranges.hasNext()) {
            intervals += IntInterval(BigInteger.of(ranges.min().toLong()), BigInteger.of(ranges.max().toLong()))
            ranges.next()
        }
        return IntDomain.ofIntervals(intervals)
    }
}
