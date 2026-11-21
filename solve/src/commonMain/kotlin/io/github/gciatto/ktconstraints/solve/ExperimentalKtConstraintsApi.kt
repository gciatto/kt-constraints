package io.github.gciatto.ktconstraints.solve

/** Marks API whose shape may change before it is backed by real usage, such as benchmark-driven ranking. */
@RequiresOptIn("This API is experimental and may change without a deprecation cycle.", RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class ExperimentalKtConstraintsApi
