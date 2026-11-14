package io.github.gciatto.ktconstraints.test.core

import io.github.gciatto.ktconstraints.core.IntDifference
import io.github.gciatto.ktconstraints.core.IntExpr
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.IntSum
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.funSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import org.gciatto.kt.math.BigInteger

/** Reusable exact algebra laws, including inputs whose sums/products overflow machine integers. */
@OptIn(ExperimentalKotest::class)
fun exactAlgebraContract(evaluate: (IntExpr) -> BigInteger) =
    funSpec {
        test("algebra-contract/distributivity-and-cancellation") {
            checkAll(PropTestConfig(iterations = 100, seed = 130363L), Arb.long(), Arb.long(), Arb.long()) { a, b, c ->
                val x = IntLiteral(a)
                val y = IntLiteral(b)
                val z = IntLiteral(c)
                evaluate(IntProduct(x, IntSum(y, z))) shouldBe evaluate(IntSum(IntProduct(x, y), IntProduct(x, z)))
                evaluate(IntDifference(IntSum(x, y), y)) shouldBe BigInteger.of(a)
                evaluate(IntSum(x, y)) shouldBe BigInteger.of(a) + BigInteger.of(b)
            }
        }
    }
