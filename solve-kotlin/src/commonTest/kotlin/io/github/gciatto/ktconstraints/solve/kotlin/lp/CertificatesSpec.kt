package io.github.gciatto.ktconstraints.solve.kotlin.lp

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.kotlin.arithmetic.LinearInequality
import io.github.gciatto.ktconstraints.solve.linear.LinearProgram
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** The checker accepts the simplex's evidence and rejects evidence that does not prove the claim. */
class CertificatesSpec :
    FunSpec({
        fun program() =
            LinearProgram.of(
                ConstraintModel.of {
                    val x = realVar("x")
                    val y = realVar("y")
                    add((x + y) le 4)
                    add((x - y) le 2)
                    maximize(x)
                },
            )

        test("certificates/simplex-duals-prove-the-optimum") {
            val program = program()
            val simplex = Simplex(program)
            simplex.check() shouldBe true
            val objective = mapOf(0 to Rational.ONE)
            simplex.maximize(objective) shouldBe true
            simplex.objectiveValue(objective) shouldBe DeltaRational.of(Rational.of(3))
            val facts = simplex.duals.map { Certificates.fact(program, it) }
            Certificates.bounds(facts, objective, Rational.of(3)) shouldBe true
            Certificates.bounds(facts, objective, Rational.of(4)) shouldBe false
        }

        test("certificates/simplex-rays-prove-unboundedness") {
            val program = program()
            val simplex = Simplex(program)
            simplex.check() shouldBe true
            val objective = mapOf(0 to Rational.of(-1))
            simplex.maximize(objective) shouldBe false
            Certificates.isRay(program, simplex.ray, objective) shouldBe true
            Certificates.isRay(program, mapOf(0 to Rational.of(-1)), objective) shouldBe true
            Certificates.isRay(program, mapOf(0 to Rational.ONE), objective) shouldBe false
            Certificates.isRay(program, mapOf(0 to Rational.of(-1), 1 to Rational.of(2)), objective) shouldBe false
        }

        test("certificates/refutations-need-a-contradictory-combination") {
            val upper = LinearInequality(mapOf(0 to Rational.ONE), Rational.ONE)
            val lower = LinearInequality(mapOf(0 to -Rational.ONE), -Rational.ONE, strict = true)
            Certificates.refutes(listOf(upper to Rational.ONE, lower to Rational.ONE)) shouldBe true
            Certificates.refutes(listOf(upper to Rational.ONE, lower to Rational.of(2))) shouldBe false
            Certificates.refutes(listOf(upper to Rational.ONE)) shouldBe false
        }
    })
