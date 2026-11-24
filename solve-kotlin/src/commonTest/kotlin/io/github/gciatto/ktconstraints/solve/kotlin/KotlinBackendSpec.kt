package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
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
            ConstraintScope.empty {
                val x = intVar("x", IntDomain.of(base, base + 2))
                add(IntEquality(IntProduct(x, x), IntLiteral((base + 1) * (base + 1))))
                KotlinBackend().create().use { solver ->
                    val result = solver.solve(buildModel())
                    result.status shouldBe SolveStatus.FEASIBLE
                    result.assignment?.valueOf(x) shouldBe base + 1
                }
            }
        }

        test("minimizing a negative product is not machine wrapping arithmetic") {
            val model =
                ConstraintModel.of {
                    val x = intVar("x", -7..5)
                    val y = intVar("y", -3..4)
                    minimize(IntProduct(x, y))
                }
            KotlinBackend().create().use { solver ->
                solver.solve(model).objectiveValue shouldBe BigInteger.of(-28)
            }
        }
    })
