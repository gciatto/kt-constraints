package io.github.gciatto.ktconstraints.solve.kotlin.lp

/** Raised inside a [Simplex] whose stop condition became true, unwinding its search. */
internal class SimplexStopped : RuntimeException("Simplex search stopped")
