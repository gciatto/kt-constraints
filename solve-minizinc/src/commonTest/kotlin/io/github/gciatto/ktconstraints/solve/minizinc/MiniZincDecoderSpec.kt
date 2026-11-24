package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.minizinc.lowering.MiniZincDecoder
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** JSON decoding requires valid full assignments and definitive, successful process completion. */
class MiniZincDecoderSpec :
    FunSpec({
        test("decoder/validates-full-solutions-before-projecting-and-deduplicating") {
            with(ConstraintScope.empty()) {
                val x = intVar("x", 0..2)
                val hidden = boolVar("hidden")
                add(IntEquality(x, IntLiteral(1)))
                project(x)
                val decoder = MiniZincDecoder(MiniZincCompiler().compile(build()))
                val first = requireNotNull(decoder.accept("""{"type":"solution","output":{"default":"{\"v0\":1,\"v1\":false}"}}"""))
                first.assignment.valueOf(x) shouldBe BigInteger.ONE
                first.assignment.valueOf(hidden) shouldBe null
                decoder.accept("""{"type":"solution","output":{"default":"{\"v0\":1,\"v1\":true}"}}""") shouldBe null
                decoder.accept("""{"type":"status","status":"ALL_SOLUTIONS"}""")
                decoder.finish(0, "").status shouldBe SolveStatus.FEASIBLE
            }
        }
        test("decoder/rejects-invalid-missing-or-wrongly-typed-values") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", 0..2)
                    add(IntEquality(x, IntLiteral(1)))
                }
            val compiled = MiniZincCompiler().compile(model)
            for (value in listOf("{}", "{\"v0\":0}", "{\"v0\":3}", "{\"v0\":\"1\"}")) {
                val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
                shouldThrow<BackendExecutionException> {
                    MiniZincDecoder(compiled).accept("""{"type":"solution","output":{"default":"$escaped"}}""")
                }
            }
        }
        test("decoder/requires-terminal-status-and-successful-exit") {
            val compiled = MiniZincCompiler().compile(ConstraintScope.empty().build())
            shouldThrow<BackendExecutionException> { MiniZincDecoder(compiled).finish(0, "") }
            val decoder = MiniZincDecoder(compiled)
            decoder.accept("""{"type":"status","status":"UNSATISFIABLE"}""")
            shouldThrow<BackendExecutionException> { decoder.finish(1, "engine crashed") }
            shouldThrow<BackendExecutionException> { MiniZincDecoder(compiled).accept("not JSON") }
            shouldThrow<BackendExecutionException> { MiniZincDecoder(compiled).accept("""{"type":"error","message":"bad model"}""") }
        }
        test("decoder/retains-warnings-and-statistics-after-the-status-message") {
            val decoder = MiniZincDecoder(MiniZincCompiler().compile(ConstraintScope.empty().build()))
            decoder.accept("""{"type":"warning","message":"model inconsistency"}""")
            decoder.accept("""{"type":"status","status":"UNSATISFIABLE"}""")
            decoder.accept("""{"type":"statistics","statistics":{"nodes":12,"solveTime":0.000125}}""")
            val result = decoder.finish(0, "stderr diagnostic")
            result.status shouldBe SolveStatus.INFEASIBLE
            result.metadata.statistics shouldBe mapOf("nodes" to "12", "solveTime" to "0.000125")
            result.metadata.messages.size shouldBe 2
        }
    })
