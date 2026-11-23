package io.github.gciatto.ktconstraints.solve.choco.translation
import io.github.gciatto.ktconstraints.solve.spi.support.BoundedIntegerSupport
import org.gciatto.kt.math.BigInteger

private const val LIMIT = 10_000_000L

/**
 * The Boolean/integer subset Choco represents exactly within its 32-bit variable bounds. The bound keeps `lhs - rhs`
 * of any relation within `IntVar.MAX_INT_BOUND`; nested globals are reified natively, so they are not decompositions.
 */
internal object ChocoSupport : BoundedIntegerSupport(BigInteger.of(LIMIT), "the Choco margin of 10^7", nestedGlobalsDecompose = false)
