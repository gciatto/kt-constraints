package io.github.gciatto.ktconstraints.core;

import org.gciatto.kt.math.BigInteger;

/** Compiles and exercises the actual method-based Java boundary without a test framework. */
public final class JavaUsage {
    private JavaUsage() { }

    public static void verify() {
        IntLiteral large = new IntLiteral("9223372036854775808");
        IntExpr sum = new IntSum(large, new IntLiteral(1L));
        if (!GroundEvaluator.evaluate(sum).equals(BigInteger.of("9223372036854775809"))) {
            throw new AssertionError("Java integer evaluation lost precision");
        }
        IntDomain domain = IntDomain.of(BigInteger.ZERO, large.getValue());
        if (!domain.contains(large.getValue()) || !domain.getCardinality().equals(BigInteger.of("9223372036854775809"))) {
            throw new AssertionError("Java domain factories lost precision");
        }
    }
}
