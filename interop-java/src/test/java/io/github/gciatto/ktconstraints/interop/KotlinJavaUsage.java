package io.github.gciatto.ktconstraints.interop;

import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend;
import io.github.gciatto.ktconstraints.solve.kotlin.KotlinConfiguration;

/** Compiled by javac: constructs the portable backend through its Java-visible API. */
public final class KotlinJavaUsage {
    private KotlinJavaUsage() { }

    /** Uses the no-argument constructor generated for the default configuration. */
    public static KotlinBackend backend() {
        return new KotlinBackend();
    }

    /** Supplies explicit resource bounds without any native solver runtime. */
    public static KotlinBackend configured() {
        return new KotlinBackend(new KotlinConfiguration(4096, 100_000, 400_000, null));
    }
}
