package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolAnd
import io.github.gciatto.ktconstraints.core.bool.BoolEquality
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.bool.BoolLiteral
import io.github.gciatto.ktconstraints.core.bool.BoolToInt
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.global.extensional.Automaton
import io.github.gciatto.ktconstraints.core.global.extensional.Element
import io.github.gciatto.ktconstraints.core.global.extensional.Regular
import io.github.gciatto.ktconstraints.core.global.extensional.Transition
import io.github.gciatto.ktconstraints.core.global.graph.Circuit
import io.github.gciatto.ktconstraints.core.global.graph.Inverse
import io.github.gciatto.ktconstraints.core.global.graph.SubCircuit
import io.github.gciatto.ktconstraints.core.global.scheduling.BinPacking
import io.github.gciatto.ktconstraints.core.global.scheduling.Cumulative
import io.github.gciatto.ktconstraints.core.global.scheduling.NoOverlap2D
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMax
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntMin
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntProduct
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

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
        test("compiler/lowers-min-and-max-to-builtins") {
            val scope = ConstraintScope.empty()
            val x = scope.intVar("x", 0..2)
            val y = scope.intVar("y", -1..4)
            scope.add(IntLessOrEqual(IntMax(listOf(x, y)), IntMin(listOf(y, IntLiteral(3)))))
            val source = MiniZincCompiler().compile(scope.build()).source
            source.contains("max([v0, v1])") shouldBe true
            source.contains("min([v1, 3])") shouldBe true
        }
        test("compiler/structural-globals-are-native-at-top-level-and-decomposed-when-nested") {
            val scope = ConstraintScope.empty()
            val xs = List(3) { scope.intVar("x$it", 0..2) }
            val b = scope.boolVar("b")
            scope.add(BoolAnd(Circuit(xs), Inverse(xs, xs)))
            scope.add(Cumulative(xs, List(3) { BigInteger.ONE }, List(3) { BigInteger.ONE }, BigInteger.TWO))
            scope.add(BoolEquality(b, Circuit(xs.reversed())))
            scope.add(IntLessOrEqual(BoolToInt(b), xs[0]))
            val source = MiniZincCompiler().compile(scope.build()).source
            source.contains("circuit(array1d(0..2, [v0, v1, v2]))") shouldBe true
            source.contains("inverse(array1d(0..2, [v0, v1, v2]), array1d(0..2, [v0, v1, v2]))") shouldBe true
            source.contains("cumulative([v0, v1, v2], [1, 1, 1], [1, 1, 1], 2)") shouldBe true
            source.contains("circuit(array1d(0..2, [v2") shouldBe false
            source.contains("bool2int(v3)") shouldBe true
        }
        test("compiler/routing-and-packing-globals-use-library-predicates") {
            val scope = ConstraintScope.empty()
            val xs = List(2) { scope.intVar("x$it", 0..1) }
            val one = listOf(BigInteger.ONE, BigInteger.ONE)
            scope.add(Regular(xs, Automaton(0, setOf(1), listOf(Transition(0, BigInteger.ZERO, 1), Transition(1, BigInteger.ONE, 1)))))
            scope.add(SubCircuit(xs))
            scope.add(BinPacking(xs, one, xs))
            scope.add(NoOverlap2D(xs, xs, one, one))
            val source = MiniZincCompiler().compile(scope.build()).source
            source.contains("regular([(v0 + 1), (v1 + 1)], 2, 2, array2d(1..2, 1..2, [2, 0, 0, 2]), 1, {2})") shouldBe true
            source.contains("(subcircuit(array1d(0..1, [v0, v1])) /\\ (v0 >= 0 /\\ v0 < 2) /\\ (v1 >= 0 /\\ v1 < 2))") shouldBe true
            source.contains("bin_packing_load(array1d(0..1, [v0, v1]), [v0, v1], [1, 1])") shouldBe true
            source.contains("diffn([v0, v1], [v0, v1], [1, 1], [1, 1])") shouldBe true
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
