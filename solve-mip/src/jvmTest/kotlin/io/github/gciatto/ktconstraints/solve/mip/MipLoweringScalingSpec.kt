package io.github.gciatto.ktconstraints.solve.mip

import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.mip.program.MipLowering
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

/** Wall-clock scaling guards, run on the JVM only: native debug builds are too slow for a meaningful budget. */
class MipLoweringScalingSpec :
    FunSpec({
        test("mip-lowering/large-all-different-lowers-in-quadratic-time").config(timeout = 15.seconds) {
            val model =
                ConstraintModel.of {
                    val xs = (0 until 250).map { intVar("x$it", 0..249) }
                    add(AllDifferent(xs))
                }
            MipLowering.report(model).isSupported shouldBe true
        }
    })
