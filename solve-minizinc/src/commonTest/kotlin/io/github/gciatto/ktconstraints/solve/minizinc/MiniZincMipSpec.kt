package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.minizinc.lowering.MiniZincDecoder
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.github.gciatto.ktconstraints.solve.spi.exception.BackendExecutionException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe

/** MIP routes expose a bounded linear subset and never claim complete enumeration. */
class MiniZincMipSpec :
    FunSpec({
        withTests(nameFn = { "mip/linear-support-and-capabilities/$it" }, listOf(MiniZincEngine.HIGHS, MiniZincEngine.CBC)) { engine ->
            (SolverCapability.ENUMERATION in engine.descriptor.capabilities) shouldBe false
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -3..3)
            scope.add(IntEquality(IntProduct(IntLiteral(2), x), IntLiteral(2)))
            val compiler = MiniZincCompiler(engine)
            compiler.support(scope.build()).isSupported shouldBe true
            scope.maximize(IntProduct(x, x))
            compiler.support(scope.build()).isSupported shouldBe false
        }
        test("mip/satisfaction-needs-a-validated-witness-but-not-an-exhaustion-status") {
            val scope = ConstraintScope.empty()
            scope.intVar("x", 0..1)
            val decoder = MiniZincDecoder(MiniZincCompiler(MiniZincEngine.HIGHS).compile(scope.build()))
            decoder.accept("""{"type":"solution","output":{"default":"{\"v0\":1}"}}""")
            decoder.finish(0, "").status shouldBe SolveStatus.FEASIBLE
            shouldThrow<BackendExecutionException> {
                MiniZincDecoder(MiniZincCompiler(MiniZincEngine.HIGHS).compile(scope.build())).finish(0, "")
            }
        }
        test("mip/optimization-still-requires-a-terminal-status") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..1)
            scope.maximize(x)
            val decoder = MiniZincDecoder(MiniZincCompiler(MiniZincEngine.HIGHS).compile(scope.build()))
            decoder.accept("""{"type":"solution","output":{"default":"{\"v0\":1}"}}""")
            shouldThrow<BackendExecutionException> { decoder.finish(0, "") }
        }
        test("mip/does-not-treat-a-wire-satisfied-status-as-an-exhaustion-proof") {
            val compiled = MiniZincCompiler().compile(ConstraintScope.empty().build())
            val decoder = MiniZincDecoder(compiled)
            decoder.accept("""{"type":"solution","output":{"default":"{}"}}""")
            decoder.accept("""{"type":"status","status":"SATISFIED"}""")
            shouldThrow<BackendExecutionException> { decoder.finish(0, "") }
        }
    })
