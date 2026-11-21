package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class KotlinSupportSpec :
    FunSpec({
        test("kotlin-support/shared-subtrees-and-roots-are-inspected-once") {
            var childReads = 0
            var expression: BoolExpr = SupportProbeExpression { childReads++ }
            repeat(12) { expression = BoolAnd(expression, expression) }

            val report = KotlinSupport.report(listOf(expression, expression))

            report.isSupported shouldBe false
            report.unsupported shouldBe listOf("Unsupported expression: SupportProbeExpression")
            childReads shouldBe 1
        }

        test("kotlin-support/distinct-extension-nodes-are-each-inspected") {
            var firstReads = 0
            var secondReads = 0
            val first = SupportProbeExpression { firstReads++ }
            val second = SupportProbeExpression { secondReads++ }

            KotlinSupport.report(listOf(BoolAnd(first, second))).isSupported shouldBe false

            firstReads shouldBe 1
            secondReads shouldBe 1
        }

        test("kotlin-support/unsupported-nodes-in-inactive-branches-are-still-rejected") {
            var childReads = 0
            val hidden = SupportProbeExpression { childReads++ }

            KotlinSupport.report(listOf(BoolAnd(BoolLiteral(false), hidden))).isSupported shouldBe false

            childReads shouldBe 1
        }
    })
