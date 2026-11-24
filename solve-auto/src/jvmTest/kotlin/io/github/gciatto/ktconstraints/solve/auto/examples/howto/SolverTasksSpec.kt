package io.github.gciatto.ktconstraints.solve.auto.examples.howto

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.store.StoreStatus
import io.github.gciatto.ktconstraints.solve.stream.SolutionFound
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Small JVM examples use the bundled Z3 and Choco runtimes. */
class SolverTasksSpec :
    FunSpec({
        test("select a route that supports enumeration") {
            val (availability, support, result) = selectEnumerationRoute()
            availability.available shouldBe true
            support.isSupported shouldBe true
            result.status shouldBe SolveStatus.FEASIBLE
        }
        test("enumerate distinct projected outputs") {
            val (values, completion) = enumerateProjectedOutputs()
            completion.first?.terminationReason shouldBe TerminationReason.COMPLETED
            completion.second shouldBe null
            values.shouldContainExactlyInAnyOrder(BigInteger.ZERO, BigInteger.ONE)
        }
        test("limit enumeration and close it early") {
            val (first, termination) = cancelEnumeration()
            termination shouldBe TerminationReason.CANCELLED
            (first is SolutionFound) shouldBe true
        }
        test("roll back assertions and check temporary assumptions") {
            val (posted, checkedWithAssumption, recovered) = checkTemporaryAssertions()
            posted.status shouldBe CheckStatus.UNSATISFIABLE
            val (checked, impossible) = checkedWithAssumption
            checked.status shouldBe CheckStatus.UNSATISFIABLE
            checked.unsatCore shouldBe listOf(impossible)
            recovered.status shouldBe CheckStatus.SATISFIABLE
        }
        test("propagate domains and restore a store mark") {
            val (domains, entailed) = restoreStoreDomains()
            domains[0] shouldBe IntDomain.of(2..2)
            entailed shouldBe Entailment.ENTAILED
            domains[1] shouldBe IntDomain.of(0..3)
            domains[2] shouldBe IntDomain.of(3..3)
            domains[3] shouldBe IntDomain.of(0..3)
        }
        test("undo a tentative post after an unknown check") {
            val (post, active, restored) = undoUnknownPost()
            post.status shouldBe StoreStatus.UNKNOWN
            active.value shouldBe Entailment.ENTAILED
            restored.value shouldBe Entailment.UNDETERMINED
        }
        test("read an exact rational") {
            val value = readExactRational()
            value shouldBe Rational.of(1, 10)
        }
        test("wrap fixed-width bit-vector arithmetic") {
            val value = wrapBitVector()
            value shouldBe BigInteger.of(255)
        }
        test("probe an explicitly selected MiniZinc route") {
            val (availability, support, result) = probeMiniZinc()
            if (result != null) result.status shouldBe SolveStatus.FEASIBLE
            support.isSupported shouldBe true
            if (!availability.available) requireNotNull(availability.reason).isNotBlank() shouldBe true
        }
    })
