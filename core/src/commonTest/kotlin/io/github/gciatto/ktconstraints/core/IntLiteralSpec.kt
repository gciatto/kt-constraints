package io.github.gciatto.ktconstraints.core

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Integer literals preserve exact values beyond machine integer limits. */
class IntLiteralSpec :
    FunSpec({
        withTests(
            nameFn = { "literal/exact/$it" },
            listOf("0", "-1", "9223372036854775808", "-9223372036854775809", "9007199254740993"),
        ) { decimal ->
            val literal = IntLiteral(decimal)
            literal.value.toString() shouldBe decimal
            literal.sort shouldBe Sort.Integer
            literal.variables shouldBe emptySet()
            literal shouldBe IntLiteral(BigInteger.of(decimal))
            literal.hashCode() shouldBe IntLiteral(BigInteger.of(decimal)).hashCode()
        }

        test("literal/long-convenience-preserves-extremes") {
            IntLiteral(Long.MIN_VALUE).value shouldBe BigInteger.of(Long.MIN_VALUE)
            IntLiteral(Long.MAX_VALUE).value shouldBe BigInteger.of(Long.MAX_VALUE)
            IntLiteral(2) shouldBe IntLiteral(2L)
        }

        test("literal/checked-narrowing-rejects-overflow") {
            shouldThrow<ArithmeticException> { IntLiteral("9223372036854775808").value.toLongExact() }
        }

        test("literal/malformed-decimal-is-rejected") {
            shouldThrow<NumberFormatException> { IntLiteral("1.5") }
        }
    })
