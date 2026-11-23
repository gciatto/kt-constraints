package io.github.gciatto.ktconstraints.solve.spi.exception

import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport

/** An entire model or requested option cannot be represented without changing semantics. */
class UnsupportedModelException(
    /** Immutable explanations for the rejection. */
    val report: SupportReport,
) : IllegalArgumentException(report.unsupported.joinToString(prefix = "Unsupported model: "))
