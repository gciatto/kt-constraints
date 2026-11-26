package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.store.StoreStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Small JVM examples use the bundled Z3 and Choco runtimes. */
class SolverTasksSpec :
    FunSpec({
        test("select a route that supports enumeration") {
            solveWithEnumerationRoute().status shouldBe SolveStatus.FEASIBLE
        }
        test("enumerate distinct projected outputs") {
            enumerateProjectedOutputs().shouldContainExactlyInAnyOrder(BigInteger.ZERO, BigInteger.ONE)
        }
        test("limit enumeration and close it early") {
            cancelEnumeration() shouldBe TerminationReason.CANCELLED
        }
        test("roll back assertions and check temporary assumptions") {
            reoptimize() shouldBe Reoptimized(BigInteger.of(21), BigInteger.of(19), BigInteger.of(16), BigInteger.of(21))
            checkTemporaryAssertions() shouldBe
                SessionChecks(
                    withTrialAssertion = CheckStatus.UNSATISFIABLE,
                    withAssumption = CheckStatus.UNSATISFIABLE,
                    afterRollback = CheckStatus.SATISFIABLE,
                )
        }
        test("propagate domains and restore a store mark") {
            restoreStoreDomains() shouldBe
                StoreSteps(
                    afterFixingX = IntDomain.of(2..2),
                    entailedThen = Entailment.ENTAILED,
                    afterUndo = IntDomain.of(0..3),
                    inFork = IntDomain.of(3..3),
                    afterFork = IntDomain.of(0..3),
                )
        }
        test("undo a tentative post after an unknown check") {
            undoUnknownPost().status shouldBe StoreStatus.UNKNOWN
        }
        test("read an exact rational") {
            readExactRational() shouldBe Rational.of(1, 10)
        }
        test("wrap fixed-width bit-vector arithmetic") {
            wrapBitVector() shouldBe BigInteger.of(255)
        }
        test("probe an explicitly selected MiniZinc route") {
            probeMiniZinc()?.status?.let { it shouldBe SolveStatus.FEASIBLE }
        }
    })
