package io.github.gciatto.ktconstraints.solve.highs

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.NumericTolerance
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.linear.FloatingPointPrograms
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.result.TerminationReason
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import io.github.gciatto.ktconstraints.test.solve.linearProgrammingContract
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

/** The HiGHS C API route: real-model consent always, and the shared contracts when `KT_CONSTRAINTS_HIGHS_LIBRARY` is set. */
@OptIn(ExperimentalForeignApi::class)
class HighsNativeSpec :
    FunSpec({
        test("highs-native/real-models-need-a-tolerance") {
            val model = ConstraintModel.of { maximize(realVar("r")) }
            val backend = HighsNativeBackend("/nonexistent/kt-constraints/libhighs.so")
            backend.support(model, SolveOptions()).unsupported shouldBe listOf(FloatingPointPrograms.TOLERANCE_NEEDED)
            backend.support(model, SolveOptions(numericTolerance = NumericTolerance())).isSupported shouldBe true
        }

        if (getenv(NativeHighsLibrary.PATH_VARIABLE)?.toKString().isNullOrEmpty()) {
            test("highs-native/requires-KT_CONSTRAINTS_HIGHS_LIBRARY").config(enabled = false) {}
        } else {
            include(solverContract(enumeration = false, globals = false) { HighsNativeBackend().createSolver() })
            include(linearProgrammingContract(NumericTolerance()) { HighsNativeBackend().createSolver() })

            test("highs-native/a-zero-budget-is-a-timeout") {
                val model = ConstraintModel.of { maximize(intVar("x", 0..3)) }
                val result = HighsNativeBackend().withSolver { it.solve(model, SolveOptions(timeoutMillis = 0)) }
                result.status shouldBe SolveStatus.UNKNOWN
                result.terminationReason shouldBe TerminationReason.TIMEOUT
            }
        }
    })
