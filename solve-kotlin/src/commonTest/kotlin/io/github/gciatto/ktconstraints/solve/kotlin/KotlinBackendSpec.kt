package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.test.solve.differentialContract
import io.github.gciatto.ktconstraints.test.solve.incrementalContract
import io.github.gciatto.ktconstraints.test.solve.solverContract
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

class KotlinBackendSpec :
    FunSpec({
        include(solverContract { KotlinBackend().create() })
        include(differentialContract("kotlin") { KotlinBackend().create() })
        include(incrementalContract { KotlinBackend().create() })

        test("route is available without an external runtime") {
            val backend = KotlinBackend()
            backend.descriptor.id shouldBe "kotlin/direct"
            backend.isAvailable() shouldBe true
        }

        test("finite integers and intermediate products remain exact above Long") {
            val base = BigInteger.ONE shl 80
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", IntDomain.of(base, base + 2))
            scope.add(IntEquality(IntProduct(x, x), IntLiteral((base + 1) * (base + 1))))
            KotlinBackend().create().use { solver ->
                val result = solver.solve(scope.build())
                result.status shouldBe SolveStatus.FEASIBLE
                result.assignment?.valueOf(x) shouldBe base + 1
            }
        }

        test("minimizing a negative product is not machine wrapping arithmetic") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", -7..5)
            val y = scope.intVar("y", -3..4)
            scope.minimize(IntProduct(x, y))
            KotlinBackend().create().use { solver ->
                solver.solve(scope.build()).objectiveValue shouldBe BigInteger.of(-28)
            }
        }
    })
