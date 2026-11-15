package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe

/** Engine selection controls lowering and result identity without changing model identities. */
class MiniZincEngineSpec : FunSpec({
    withTests(nameFn = { "engine/descriptor-and-decoder/$it" }, MiniZincEngine.entries) { engine ->
        engine.descriptor.id shouldBe "${engine.engineId}/minizinc"
        val compiled = MiniZincCompiler(engine).compile(ConstraintScope.empty().build())
        val decoder = MiniZincDecoder(compiled)
        decoder.accept("""{"type":"status","status":"UNSATISFIABLE"}""")
        val result = decoder.finish(0, "")
        result.status shouldBe SolveStatus.INFEASIBLE
        result.descriptor shouldBe engine.descriptor
    }
    test("engine/chuffed-rejects-values-outside-its-conservative-range") {
        val scope = ConstraintScope.empty()
        scope.intVar("x", IntDomain.of(600_000_000, 600_000_001))
        MiniZincCompiler(MiniZincEngine.GECODE).support(scope.build()).isSupported shouldBe true
        MiniZincCompiler(MiniZincEngine.CHUFFED).support(scope.build()).isSupported shouldBe false
    }
})
