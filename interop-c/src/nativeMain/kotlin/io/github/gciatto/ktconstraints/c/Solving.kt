package io.github.gciatto.ktconstraints.c

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.auto.AutoBackend
import io.github.gciatto.ktconstraints.solve.spi.withSolver
import kotlinx.coroutines.runBlocking
import org.gciatto.kt.math.BigInteger
import io.github.gciatto.ktconstraints.dsl.allDifferent as dslAllDifferent
import io.github.gciatto.ktconstraints.dsl.sum as dslSum

/** Exact integer constant. */
fun constant(value: Long): IntExpression = IntExpression(IntLiteral(value))

/** Exact integer constant of any size, from its decimal text; null if [decimal] is not an integer. */
fun constantOf(decimal: String): IntExpression? = runCatching { IntExpression(IntLiteral(BigInteger.of(decimal))) }.getOrNull()

/** Exact rational constant from integer, fraction or decimal text; null if [decimal] is invalid. */
fun realConstant(decimal: String): RealExpression? = runCatching { RealExpression(RealLiteral(Rational.parse(decimal))) }.getOrNull()

/** Requires all [operands] to take pairwise different values. */
fun allDifferent(operands: IntExpressionList): BoolExpression = BoolExpression(dslAllDifferent(*operands.items.toTypedArray()))

/** Exact sum of [operands]. */
fun sum(operands: IntExpressionList): IntExpression = IntExpression(dslSum(*operands.items.toTypedArray()))

/**
 * Solves [model] with the solvers bundled for this platform (Z3, loaded from `KT_CONSTRAINTS_Z3_LIBRARY` or the
 * loader path), blocking the calling thread; a non-positive [timeoutMillis] means no limit. Never throws: failures
 * become an `ERROR` [Outcome].
 */
fun solve(
    model: Model,
    timeoutMillis: Long,
): Outcome =
    runCatching {
        runBlocking {
            AutoBackend.load().use { auto ->
                auto.withSolver { it.solve(model.model, SolveOptions(timeoutMillis = timeoutMillis.takeIf { it > 0 })) }
            }
        }
    }.fold(
        onSuccess = { Outcome(it, null) },
        onFailure = { Outcome(null, it.message ?: it::class.simpleName ?: "Unknown failure") },
    )
