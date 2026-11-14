package io.github.gciatto.ktconstraints.core

/** Value space allowed for a variable. */
interface Domain {
    /** Whether this domain is finite. */
    val isFinite: Boolean
}
