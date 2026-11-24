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
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntVar
import io.github.gciatto.ktconstraints.dsl.eq
import io.github.gciatto.ktconstraints.dsl.ge
import io.github.gciatto.ktconstraints.dsl.lt
import io.github.gciatto.ktconstraints.dsl.plus
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.choco.ChocoStore
import io.github.gciatto.ktconstraints.solve.incremental.CheckResult
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincBackend
import io.github.gciatto.ktconstraints.solve.minizinc.MiniZincEngine
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.BackendAvailability
import io.github.gciatto.ktconstraints.solve.spi.registry.SolverRegistry
import io.github.gciatto.ktconstraints.solve.spi.store.SatisfiabilityStore
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.store.EntailmentResult
import io.github.gciatto.ktconstraints.solve.store.Propagation
import io.github.gciatto.ktconstraints.solve.store.StoreStatus
import io.github.gciatto.ktconstraints.solve.stream.SolutionEvent
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.github.gciatto.ktconstraints.solve.stream.SolutionStream
import io.github.gciatto.ktconstraints.solve.stream.SolveCompleted
import io.github.gciatto.ktconstraints.solve.z3.Z3Backend
import org.gciatto.kt.math.BigInteger
// --8<-- [end:imports]

// --8<-- [start:selection]
internal suspend fun selectEnumerationRoute(): Triple<BackendAvailability, SupportReport, SolveResult> {
    val model = ConstraintModel.of { intVar("x", 0..2) }
    val registry = SolverRegistry.of(Z3Backend())
    val selection = registry.rank(model, requiredCapabilities = setOf(SolverCapability.ENUMERATION))
    val backend = selection.best.backend
    val availability = backend.availability()
    val support = backend.support(model)
    val result = backend.createSolver().use { it.solve(model) }
    return Triple(availability, support, result)
}
// --8<-- [end:selection]

// --8<-- [start:enumerate]
internal suspend fun enumerateProjectedOutputs(): Pair<List<BigInteger>, Pair<SolveResult?, SolutionEvent?>> =
    ConstraintScope.empty {
        val x = intVar("x", 0..2)
        val y = intVar("y", 0..2)
        add(x lt y)
        project(x)
        enumerateValues(buildModel(), x)
    }

/** Lists every value [x] takes in the solutions of [model], plus the stream's final state. */
internal suspend fun enumerateValues(
    model: ConstraintModel,
    x: IntVar,
): Pair<List<BigInteger>, Pair<SolveResult?, SolutionEvent?>> =
    AutoBackend.load().use { auto ->
        auto.createSolver().use { solver -> solver.solutions(model).use { it.valuesOf(x) } }
    }

/** Reads the stream to completion, collecting the values of [x]. */
internal suspend fun SolutionStream.valuesOf(x: IntVar): Pair<List<BigInteger>, Pair<SolveResult?, SolutionEvent?>> {
    val values = mutableListOf<BigInteger>()
    var event = next()
    while (event is SolutionFound) {
        values += requireNotNull(event.assignment.valueOf(x))
        event = next()
    }
    val completed = event as? SolveCompleted ?: error("Missing completion event")
    check(completed.result.terminationReason == TerminationReason.COMPLETED)
    println("Enumeration completed: ${completed.result.status}")
    return values to (terminalResult to next())
}
// --8<-- [end:enumerate]

// --8<-- [start:cancel]
internal suspend fun cancelEnumeration(): Pair<SolutionEvent?, TerminationReason?> {
    val model = ConstraintModel.of { intVar("x", 0..100) }
    val (first, termination) =
        Z3Backend().createSolver().use { solver ->
            solver.solutions(model, SolveOptions(timeoutMillis = 1_000, solutionLimit = 2)).use { stream ->
                val first = stream.next()
                stream.cancel()
                first to stream.terminalResult?.terminationReason
            }
        }
    return first to termination
}
// --8<-- [end:cancel]

// --8<-- [start:session]
internal suspend fun checkTemporaryAssertions(): Triple<CheckResult, Pair<CheckResult, BoolExpr>, CheckResult> {
    val (posted, checkedWithAssumption, recovered) =
        ConstraintScope.empty {
            val x = intVar("x", 0..3)
            add(x ge 1)
            Z3Backend().createSolver().use { solver ->
                solver.openSession(buildModel()).use { session ->
                    val checkpoint = session.push()
                    session.add(x eq 0)
                    val posted = session.check()
                    session.popTo(checkpoint)
                    val impossible = x lt 1
                    val checked = session.check(assumptions = listOf(impossible))
                    check(checked.unsatCore == listOf(impossible))
                    Triple(posted, checked to impossible, session.check())
                }
            }
        }
    return Triple(posted, checkedWithAssumption, recovered)
}
// --8<-- [end:session]

// --8<-- [start:store]
internal suspend fun restoreStoreDomains(): Pair<List<IntDomain>, Entailment> {
    val (domains, entailed) =
        ChocoStore().use { store ->
            val domains = mutableListOf<IntDomain>()
            val x = store.intVar("x", IntDomain.of(0..3))
            val y = store.intVar("y", IntDomain.of(0..3))
            store.constrain(x + y eq 3)
            val mark = store.mark()
            store.constrain(x eq 1)
            domains += store.domain(y)
            val entailed = store.entails(y eq 2).value
            store.undoTo(mark)
            domains += store.domain(y)
            store.fork().use { child ->
                child.constrain(x eq 0)
                domains += child.domain(y)
            }
            domains += store.domain(y)
            domains to entailed
        }
    return domains to entailed
}
// --8<-- [end:store]

// --8<-- [start:uncertain-post]
internal suspend fun undoUnknownPost(): Triple<Propagation, EntailmentResult, EntailmentResult> {
    val (post, active, restored) =
        SatisfiabilityStore.of(Z3Backend().createSolver()).use { store ->
            val x = store.intVar("x", IntDomain.of(0..1))
            val mark = store.mark()
            val post = store.constrain(x eq 1, timeoutMillis = 0)
            val active = store.entails(x eq 1)
            if (post.status == StoreStatus.UNKNOWN) {
                println("Undecided post remains active; rolling back the tentative branch")
                store.undoTo(mark)
            }
            Triple(post, active, store.entails(x eq 1))
        }
    return Triple(post, active, restored)
}
// --8<-- [end:uncertain-post]

// --8<-- [start:real]
internal suspend fun readExactRational(): Rational? {
    val value =
        ConstraintScope.empty {
            val r = realVar("r")
            add(r eq Rational.parse("0.1"))
            val result = Z3Backend().createSolver().use { it.solve(buildModel()) }
            requireNotNull(result.assignment).valueOf(r)
        }
    return value
}
// --8<-- [end:real]

// --8<-- [start:bits]
internal suspend fun wrapBitVector(): BigInteger? {
    val value =
        ConstraintScope.empty {
            val x = bitVecVar("x", 8)
            val one = BitVecLiteral(BigInteger.ONE, 8)
            val zero = BitVecLiteral(BigInteger.ZERO, 8)
            val sum = BitVecBinary(BitVecOperator.ADD, x, one)
            add(BitVecComparison(BitVecRelation.EQUAL, sum, zero))
            val result = Z3Backend().createSolver().use { it.solve(buildModel()) }
            requireNotNull(result.assignment).valueOf(x)
        }
    return value
}
// --8<-- [end:bits]

// --8<-- [start:minizinc]
internal suspend fun probeMiniZinc(): Triple<BackendAvailability, SupportReport, SolveResult?> {
    val model = ConstraintModel.of { intVar("x", 0..2) }
    val backend = MiniZincBackend(MiniZincEngine.GECODE, System.getenv("MINIZINC_EXECUTABLE") ?: "minizinc")
    val availability = backend.availability()
    val support = backend.support(model)
    val result =
        if (availability.available && support.isSupported) {
            backend.createSolver().use { solver -> solver.solve(model) }
        } else {
            null
        }
    println(availability.reason ?: result?.status)
    return Triple(availability, support, result)
}
// --8<-- [end:minizinc]
