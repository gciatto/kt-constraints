package io.github.gciatto.ktconstraints.core.domain

/** Value space allowed for a variable. */
interface Domain {
    /** Whether this domain is finite. */
    val isFinite: Boolean
}
