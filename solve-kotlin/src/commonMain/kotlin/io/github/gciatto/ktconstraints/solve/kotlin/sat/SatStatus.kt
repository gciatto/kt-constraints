package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** A completed Boolean search proves either a model or a contradiction. */
internal enum class SatStatus {
    SATISFIABLE,
    UNSATISFIABLE,
}
