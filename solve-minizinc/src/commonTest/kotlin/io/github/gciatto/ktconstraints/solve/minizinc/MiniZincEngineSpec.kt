package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.minizinc.lowering.MiniZincDecoder
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe

/** Engine selection controls lowering and result identity without changing model identities. */
class MiniZincEngineSpec :
    FunSpec({
        withTests(nameFn = { "engine/descriptor-and-decoder/$it" }, MiniZincEngine.entries) { engine ->
            engine.descriptor.id shouldBe "${engine.engineId}/minizinc"
            val compiled = MiniZincCompiler(engine).compile(ConstraintModel.empty())
            val decoder = MiniZincDecoder(compiled)
            decoder.accept("""{"type":"status","status":"UNSATISFIABLE"}""")
            val result = decoder.finish(0, "")
            result.status shouldBe SolveStatus.INFEASIBLE
            result.descriptor shouldBe engine.descriptor
        }
        test("engine/chuffed-rejects-values-outside-its-conservative-range") {
            val model =
                ConstraintModel.of {
                    intVar("x", IntDomain.of(600_000_000, 600_000_001))
                }
            MiniZincCompiler(MiniZincEngine.GECODE).support(model).isSupported shouldBe true
            MiniZincCompiler(MiniZincEngine.CHUFFED).support(model).isSupported shouldBe false
        }
        test("engine/chuffed-rejects-its-reserved-unbounded-sentinels") {
            for (value in listOf(-500_000_000L, 500_000_000L)) {
                val model =
                    ConstraintModel.of {
                        intVar("x", IntDomain.of(value, value))
                    }
                MiniZincCompiler(MiniZincEngine.CHUFFED).support(model).isSupported shouldBe false
            }
        }
        test("engine/decoder-retains-the-explicit-execution-route") {
            val descriptor = SolverDescriptor("gecode", "minizinc-wasm", MiniZincEngine.GECODE.descriptor.capabilities)
            val decoder = MiniZincDecoder(MiniZincCompiler().compile(ConstraintModel.empty()), descriptor)
            decoder.accept("""{"type":"status","status":"UNSATISFIABLE"}""")
            decoder.finish(0, "").descriptor shouldBe descriptor
        }
    })
