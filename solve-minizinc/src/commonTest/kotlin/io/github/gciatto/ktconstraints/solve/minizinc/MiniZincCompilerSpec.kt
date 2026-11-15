package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.AllDifferent
import io.github.gciatto.ktconstraints.core.BoolAnd
import io.github.gciatto.ktconstraints.core.BoolExpr
import io.github.gciatto.ktconstraints.core.BoolLiteral
import io.github.gciatto.ktconstraints.core.Element
import io.github.gciatto.ktconstraints.core.IntDomain
import io.github.gciatto.ktconstraints.core.IntEquality
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntProduct
import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.RealLessThan
import io.github.gciatto.ktconstraints.core.RealLiteral
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.spi.UnsupportedModelException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Compilation preserves identity and rejects unsupported semantics before runtime startup. */
class MiniZincCompilerSpec :
    FunSpec({
        test("compiler/uses-deterministic-identifiers-and-retains-source-mapping") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x; constraint false;", IntDomain.values(1, 3))
            val flag = scope.boolVar("not a MiniZinc identifier")
            scope.add(IntEquality(x, IntLiteral(3)))
            val compiled = MiniZincCompiler().compile(scope.build())
            compiled.source.contains("x; constraint false;") shouldBe false
            compiled.source.contains("v0") shouldBe true
            compiled.sourceVariables shouldBe mapOf("v0" to x, "v1" to flag)
            compiled.source shouldBe MiniZincCompiler().compile(scope.build()).source
            compiled.close()
        }
        test("compiler/preserves-globals-and-enforces-zero-based-element-definedness") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..2)
            val y = scope.intVar("y", 0..2)
            scope.add(AllDifferent(listOf(x, y)))
            scope.add(IntEquality(Element(x, listOf(IntLiteral(1), y)), IntLiteral(1)))
            val source = MiniZincCompiler().compile(scope.build()).source
            source.contains("all_different(") shouldBe true
            source.contains("array1d(0..1") shouldBe true
            source.contains("constraint (v0 >= 0 /\\ v0 < 2);") shouldBe true
        }
        test("compiler/rejects-native-range-overflow-and-unknown-nodes-in-all-branches") {
            val compiler = MiniZincCompiler()
            val large = ConstraintScope.empty()
            large.intVar("x", IntDomain.of(Long.MIN_VALUE, Long.MAX_VALUE))
            shouldThrow<UnsupportedModelException> { compiler.compile(large.build()) }
            val overflow = ConstraintScope.empty()
            val x = overflow.intVar("x", 1..100_000)
            overflow.minimize(IntProduct(x, x))
            shouldThrow<UnsupportedModelException> { compiler.compile(overflow.build()) }
            val unknown = ConstraintScope.empty()
            unknown.add(BoolAnd(BoolLiteral(false), object : BoolExpr {}))
            compiler.support(unknown.build()).isSupported shouldBe false
        }
        test("compiler/rejects-exact-reals-instead-of-approximating-them-as-floats") {
            val compiler = MiniZincCompiler()
            val declared = ConstraintScope.empty()
            declared.realVar("x")
            compiler.support(declared.build()).isSupported shouldBe false
            shouldThrow<UnsupportedModelException> { compiler.compile(declared.build()) }
            val constrained = ConstraintScope.empty()
            constrained.add(RealLessThan(constrained.realVar("y"), RealLiteral(Rational.of(1, 3))))
            compiler.support(constrained.build()).isSupported shouldBe false
        }

        test("compiler/rejects-bit-vectors") {
            val scope = ConstraintScope.empty()
            scope.bitVecVar("b", 8)
            MiniZincCompiler().support(scope.build()).isSupported shouldBe false
            shouldThrow<UnsupportedModelException> { MiniZincCompiler().compile(scope.build()) }
        }

        test("compiler/empty-domains-remain-explicitly-unsatisfiable") {
            val scope = ConstraintScope.empty()
            scope.intVar("x", IntDomain.empty())
            MiniZincCompiler().compile(scope.build()).source.contains("constraint false;") shouldBe true
        }
    })
