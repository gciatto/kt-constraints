package io.github.gciatto.ktconstraints.solve.kotlin.compiler

import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration
import io.github.gciatto.ktconstraints.solve.kotlin.SearchBudget
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatSolver
import io.github.gciatto.ktconstraints.solve.kotlin.sat.SatStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CircuitSpec : FunSpec({
    test("reified gates have exactly their Boolean truth tables") {
        for (a in listOf(false, true)) {
            for (b in listOf(false, true)) {
                val sat = SatSolver()
                val circuit = Circuit(sat, KotlinConfiguration(), SearchBudget(null, null) { false })
                val x = circuit.variable()
                val y = circuit.variable()
                val and = circuit.and(x, y)
                val xor = circuit.xor(x, y)
                val assumptions = intArrayOf(if (a) x else -x, if (b) y else -y)
                val result = sat.solve(assumptions)
                result.status shouldBe SatStatus.SATISFIABLE
                val values = requireNotNull(result.values)
                circuit.value(and, values) shouldBe (a && b)
                circuit.value(xor, values) shouldBe (a != b)
            }
        }
    }

    test("gates are reusable and complement identities do not allocate") {
        val sat = SatSolver()
        val circuit = Circuit(sat, KotlinConfiguration(), SearchBudget(null, null) { false })
        val x = circuit.variable()
        val y = circuit.variable()
        circuit.and(x, y) shouldBe circuit.and(y, x)
        circuit.and(x, -x) shouldBe circuit.falseLiteral
        circuit.xor(x, x) shouldBe circuit.falseLiteral
        circuit.or(x, -x) shouldBe circuit.trueLiteral
    }
})
