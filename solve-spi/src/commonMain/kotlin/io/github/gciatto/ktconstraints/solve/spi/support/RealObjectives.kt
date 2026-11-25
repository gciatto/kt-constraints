package io.github.gciatto.ktconstraints.solve.spi.support

import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException

/** The support-report entry of routes that cannot optimize real objectives. */
const val UNSUPPORTED_REAL_OBJECTIVE: String = "Unsupported real objective"

/** Rejects a real objective that the calling route cannot optimize. */
fun rejectRealObjective(): Nothing = throw UnsupportedModelException(SupportReport(listOf(UNSUPPORTED_REAL_OBJECTIVE)))
