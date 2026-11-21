package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/** Wall-clock scaling guards, run on the JVM only: native debug builds are too slow for a meaningful budget. */
class ConstraintScopeScalingSpec :
    FunSpec({
        test("scope/adding-many-named-constraints-is-linear").config(timeout = 15.seconds) {
            val scope = ConstraintScope.empty()
            val xs = (0 until 40_000).map { scope.intVar("x$it", 0..9) }
            xs.forEachIndexed { i, x -> scope.add(IntEquality(x, IntLiteral(i % 10)), name = "c$i") }
            scope.build().constraints.size shouldBe 40_000
            shouldThrow<IllegalArgumentException> { scope.add(IntEquality(xs[0], IntLiteral(0)), name = "c0") }
        }
    })
