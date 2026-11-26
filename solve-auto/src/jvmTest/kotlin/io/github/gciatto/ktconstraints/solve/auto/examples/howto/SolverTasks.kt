// detekt misses the suspending calls made inside inline `use` blocks (solve, stream.next, session.check).
@file:Suppress("RedundantSuspendModifier")

package io.github.gciatto.ktconstraints.solve.auto.examples.howto

// --8<-- [start:imports]
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bitvector.BitVecLiteral
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecBinary
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecComparison
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecOperator
import io.github.gciatto.ktconstraints.core.bitvector.operation.BitVecRelation
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.model.Objective
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.choco.ChocoStore
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincEngine
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.registry.SolverRegistry
import io.github.gciatto.ktconstraints.solve.spi.store.SatisfiabilityStore
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.store.Propagation
import io.github.gciatto.ktconstraints.solve.store.StoreStatus
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.forEachSolution
import io.github.gciatto.ktconstraints.solve.z3.Z3Backend
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:selection]

/** Selects a route that can enumerate, confirms it can run the model, and solves it. */
internal suspend fun solveWithEnumerationRoute(): SolveResult {
    val model = ConstraintModel.of { intVar("x", 0..2) }
    val registry = SolverRegistry.of(Z3Backend())
    val selection = registry.rank(model, requiredCapabilities = setOf(SolverCapability.ENUMERATION))
    val backend = selection.best.backend
    val availability = backend.availability()
    check(availability.available) { availability.reason.orEmpty() }
    val support = backend.support(model)
    check(support.isSupported) { support.toString() }
    return backend.withSolver { solver -> solver.solve(model) }
}
// --8<-- [end:selection]

// --8<-- [start:enumerate]

/** Lists the values of x in the solutions of x < y, projected onto x. */
internal suspend fun enumerateProjectedOutputs(): List<BigInteger> =
    ConstraintScope.empty {
        val x = intVar("x", 0..2)
        val y = intVar("y", 0..2)
        add(x lt y)
        project(x)
        val model = buildModel()
        val values = mutableListOf<BigInteger>()
        val result =
            AutoBackend.load().use { auto ->
                auto.withSolver { solver ->
                    solver.forEachSolution(model) { found -> values += checkNotNull(found.assignment.valueOf(x)) }
                }
            }
        // A timeout or cancellation would leave the list incomplete.
        check(result.terminationReason == TerminationReason.COMPLETED) { result.toString() }
        println("Enumeration completed: ${result.status}")
        values
    }
// --8<-- [end:enumerate]

// --8<-- [start:cancel]

/** Reads the first solution of a large search, cancels the rest, and returns why the search ended. */
internal suspend fun cancelEnumeration(): TerminationReason? {
    val model = ConstraintModel.of { intVar("x", 0..100) }
    return Z3Backend().withSolver { solver ->
        solver.solutions(model, SolveOptions(timeoutMillis = 1_000, solutionLimit = 2)).use { stream ->
            val first = stream.next()
            check(first is SolutionFound) { "Expected a solution, got $first" }
            stream.cancel()
            stream.terminalResult?.terminationReason
        }
    }
}
// --8<-- [end:cancel]

// --8<-- [start:session]

/** Checks a trial assertion in a reversible frame, then a temporary assumption, then the restored model. */
internal suspend fun checkTemporaryAssertions(): SessionChecks =
    ConstraintScope.empty {
        val x = intVar("x", 0..3)
        add(x ge 1)
        val model = buildModel()
        Z3Backend().createSolver().use { solver ->
            solver.openSession(model).use { session ->
                val checkpoint = session.push()
                session.add(x eq 0)
                val trial = session.check()
                session.popTo(checkpoint)
                val impossible = x lt 1
                val assumed = session.check(assumptions = listOf(impossible))
                check(assumed.unsatCore == listOf(impossible))
                SessionChecks(trial.status, assumed.status, session.check().status)
            }
        }
    }
// --8<-- [end:session]

// --8<-- [start:session-optimization]

/** Re-optimizes as a session changes: inside a frame, under a one-call assumption, then with the frame popped. */
internal suspend fun reoptimize(): Reoptimized =
    ConstraintScope.empty {
        val x = intVar("x", 0..9)
        val y = intVar("y", 0..9)
        add(x + y le 12)
        val goal = Objective.Maximize(x * 2 + y)
        KotlinBackend().createSolver().use { solver ->
            solver.openSession(buildModel()).use { session ->
                val initial = session.optimize(goal)
                val checkpoint = session.push()
                session.add(y ge 5)
                val inFrame = session.optimize(goal)
                session.popTo(checkpoint)
                val assumed = session.optimize(goal, assumptions = listOf(x le 4))
                Reoptimized(initial.objectiveValue, inFrame.objectiveValue, assumed.objectiveValue, session.optimize(goal).objectiveValue)
            }
        }
    }
// --8<-- [end:session-optimization]

// --8<-- [start:store]

/** Propagates in a Choco store, then restores it through a mark and a fork. */
internal suspend fun restoreStoreDomains(): StoreSteps =
    ChocoStore().use { store ->
        val x = store.intVar("x", IntDomain.of(0..3))
        val y = store.intVar("y", IntDomain.of(0..3))
        store.constrain(x + y eq 3)
        val mark = store.mark()
        store.constrain(x eq 1)
        val afterFixingX = store.domain(y)
        val entailed = store.entails(y eq 2).value
        store.undoTo(mark)
        val afterUndo = store.domain(y)
        val inFork =
            store.fork().use { child ->
                child.constrain(x eq 0)
                child.domain(y)
            }
        StoreSteps(afterFixingX, entailed, afterUndo, inFork, store.domain(y))
    }
// --8<-- [end:store]

// --8<-- [start:uncertain-post]

/** Posts a constraint without time to decide it, then rolls it back. */
internal suspend fun undoUnknownPost(): Propagation =
    SatisfiabilityStore.of(Z3Backend().createSolver()).use { store ->
        val x = store.intVar("x", IntDomain.of(0..1))
        val mark = store.mark()
        val post = store.constrain(x eq 1, timeoutMillis = 0)
        check(store.entails(x eq 1).value == Entailment.ENTAILED) { "An undecided post still constrains the store" }
        if (post.status == StoreStatus.UNKNOWN) {
            println("Undecided post remains active; rolling back the tentative branch")
            store.undoTo(mark)
        }
        check(store.entails(x eq 1).value == Entailment.UNDETERMINED)
        post
    }
// --8<-- [end:uncertain-post]

// --8<-- [start:real]

/** Reads back the exact rational value of r = 0.1. */
internal suspend fun readExactRational(): Rational? =
    ConstraintScope.empty {
        val r = realVar("r")
        add(r eq Rational.parse("0.1"))
        val model = buildModel()
        val result = Z3Backend().withSolver { solver -> solver.solve(model) }
        checkNotNull(result.assignment).valueOf(r)
    }
// --8<-- [end:real]

// --8<-- [start:bits]

/** Solves x + 1 = 0 over 8-bit vectors, where addition wraps around. */
internal suspend fun wrapBitVector(): BigInteger? =
    ConstraintScope.empty {
        val x = bitVecVar("x", 8)
        val one = BitVecLiteral(BigInteger.ONE, 8)
        val zero = BitVecLiteral(BigInteger.ZERO, 8)
        val sum = BitVecBinary(BitVecOperator.ADD, x, one)
        add(BitVecComparison(BitVecRelation.EQUAL, sum, zero))
        val model = buildModel()
        val result = Z3Backend().withSolver { solver -> solver.solve(model) }
        checkNotNull(result.assignment).valueOf(x)
    }
// --8<-- [end:bits]

// --8<-- [start:minizinc]

/** Solves with Gecode through MiniZinc, or returns null when the route cannot run here. */
internal suspend fun probeMiniZinc(): SolveResult? {
    val model = ConstraintModel.of { intVar("x", 0..2) }
    val backend = MiniZincBackend(MiniZincEngine.GECODE, System.getenv("MINIZINC_EXECUTABLE") ?: "minizinc")
    val availability = backend.availability()
    val support = backend.support(model)
    check(support.isSupported) { support.toString() }
    if (!availability.available) {
        println("MiniZinc unavailable: ${availability.reason}")
        return null
    }
    val result = backend.withSolver { solver -> solver.solve(model) }
    println(result.status)
    return result
}
// --8<-- [end:minizinc]
