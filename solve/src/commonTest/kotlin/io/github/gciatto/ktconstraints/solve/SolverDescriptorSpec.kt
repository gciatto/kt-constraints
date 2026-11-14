package io.github.gciatto.ktconstraints.solve

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Engine identity is separate from execution route and mutable caller-owned capability sets. */
class SolverDescriptorSpec :
    FunSpec({
        test("descriptor/route-scoped-identity-and-immutable-capabilities") {
            val capabilities = mutableSetOf(SolverCapability.SATISFACTION)
            val direct = SolverDescriptor("cp-sat", "direct", capabilities)
            capabilities.clear()
            runCatching { (direct.capabilities as MutableSet).clear() }
            direct.capabilities shouldBe setOf(SolverCapability.SATISFACTION)
            direct.id shouldBe "cp-sat/direct"
            SolverDescriptor("cp-sat", "minizinc", emptySet()).id shouldBe "cp-sat/minizinc"
            direct shouldBe SolverDescriptor("cp-sat", "direct", setOf(SolverCapability.SATISFACTION))
        }

        test("descriptor/rejects-ambiguous-or-empty-identifiers") {
            shouldThrow<IllegalArgumentException> { SolverDescriptor(" ", "direct", emptySet()) }
            shouldThrow<IllegalArgumentException> { SolverDescriptor("cp-sat", "", emptySet()) }
            shouldThrow<IllegalArgumentException> { SolverDescriptor("cp-sat/direct", "direct", emptySet()) }
            shouldThrow<IllegalArgumentException> { SolverDescriptor("cp-sat", "mini zinc", emptySet()) }
        }
    })
