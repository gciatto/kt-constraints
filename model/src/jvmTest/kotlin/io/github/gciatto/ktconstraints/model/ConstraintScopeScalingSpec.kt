package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/** Wall-clock scaling guards, run on the JVM only: native debug builds are too slow for a meaningful budget. */
class ConstraintScopeScalingSpec :
    FunSpec({
        test("scope/adding-many-named-constraints-is-linear").config(timeout = 15.seconds) {
            ConstraintScope.empty {
                val xs = (0 until 40_000).map { intVar("x$it", 0..9) }
                xs.forEachIndexed { i, x -> add(IntEquality(x, IntLiteral(i % 10)), name = "c$i") }
                buildModel().constraints.size shouldBe 40_000
                shouldThrow<IllegalArgumentException> { add(IntEquality(xs[0], IntLiteral(0)), name = "c0") }
            }
        }
    })
