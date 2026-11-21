package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/** Wall-clock scaling guards, run on the JVM only: native debug builds are too slow for a meaningful budget. */
class MipLoweringScalingSpec :
    FunSpec({
        test("mip-lowering/large-all-different-lowers-in-quadratic-time").config(timeout = 5.seconds) {
            val scope = ConstraintScope.empty()
            val xs = (0 until 250).map { scope.intVar("x$it", 0..249) }
            scope.add(AllDifferent(xs))
            MipLowering.report(scope.build()).isSupported shouldBe true
        }
    })
