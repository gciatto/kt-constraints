package io.github.gciatto.ktconstraints.test.solve

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolNot
import io.github.gciatto.ktconstraints.core.domain.IntDomain
import io.github.gciatto.ktconstraints.core.evaluation.GroundEvaluator
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessThan
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.Evidence
import io.github.gciatto.ktconstraints.solve.assignment.Assignment
import io.github.gciatto.ktconstraints.solve.spi.exception.UnsupportedModelException
import io.github.gciatto.ktconstraints.solve.store.ConstraintStore
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.store.EntailmentResult
import io.github.gciatto.ktconstraints.solve.store.StoreStatus
import io.github.gciatto.ktconstraints.test.solve.oracle.FiniteOracle
import io.github.gciatto.ktconstraints.test.solve.oracle.enumerate
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.funSpec
import io.kotest.datatest.withTests
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.withTimeoutOrNull
import org.gciatto.kt.math.BigInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared [ConstraintStore] contracts. With [propagates] false the store answers by complete satisfiability checks,
 * so posts are proven [StoreStatus.SATISFIABLE] and entailment is never [Entailment.UNKNOWN]; otherwise it answers
 * by propagation, which must shrink domains and stay sound but may be undecided. [reals] enables exact real variables;
 * otherwise declaring one must be rejected. Satisfiability stores must also survive timeouts and cancellation.
 */
fun storeContract(
    propagates: Boolean,
    reals: Boolean = !propagates,
    create: () -> ConstraintStore,
) = funSpec {
    val evidence = if (propagates) Evidence.PROPAGATION else Evidence.SATISFIABILITY

    test("store-contract/failure-persists-until-undone") {
        create().use { store ->
            val x = store.intVar("x", IntDomain.of(0, 3))
            val mark = store.mark()
            store.constrain(IntLessThan(IntLiteral(5), x)).status shouldBe StoreStatus.FAILED
            store.isFailed shouldBe true
            store.constrain(IntEquality(x, IntLiteral(1))).status shouldBe StoreStatus.FAILED
            store.undoTo(mark)
            store.isFailed shouldBe false
            store.constrain(IntEquality(x, IntLiteral(1))).status shouldNotBe StoreStatus.FAILED
        }
    }

    test("store-contract/successful-posts-report-their-evidence") {
        create().use { store ->
            val x = store.intVar("x", IntDomain.of(0, 9))
            val status = store.constrain(IntLessThan(x, IntLiteral(3))).status
            if (propagates) {
                status shouldBeIn listOf(StoreStatus.CONSISTENT, StoreStatus.SATISFIABLE)
            } else {
                status shouldBe
                    StoreStatus.SATISFIABLE
            }
        }
    }

    test("store-contract/nested-marks-restore-earlier-constraints") {
        create().use { store ->
            val x = store.intVar("x", IntDomain.of(0, 9))
            store.constrain(IntLessOrEqual(IntLiteral(2), x))
            val outer = store.mark()
            store.constrain(IntLessOrEqual(x, IntLiteral(4)))
            val inner = store.mark()
            store.constrain(IntEquality(x, IntLiteral(9))).status shouldBe StoreStatus.FAILED
            store.undoTo(inner)
            store.entails(IntLessOrEqual(x, IntLiteral(4))).value shouldBe Entailment.ENTAILED
            store.undoTo(outer)
            store.entails(IntLessOrEqual(x, IntLiteral(4))).value shouldNotBe Entailment.ENTAILED
            store.entails(IntLessOrEqual(IntLiteral(2), x)).value shouldBe Entailment.ENTAILED
        }
    }

    test("store-contract/undoing-past-several-marks-invalidates-them") {
        create().use { store ->
            store.intVar("x", IntDomain.of(0, 9))
            val first = store.mark()
            val second = store.mark()
            val third = store.mark()
            store.undoTo(first)
            shouldThrow<IllegalArgumentException> { store.undoTo(second) }
            shouldThrow<IllegalArgumentException> { store.undoTo(third) }
            shouldThrow<IllegalArgumentException> { store.undoTo(first) }
        }
    }

    test("store-contract/foreign-marks-and-variables-are-rejected") {
        create().use { store ->
            create().use { other ->
                val foreign = other.intVar("x", IntDomain.of(0, 1))
                shouldThrow<IllegalArgumentException> { store.undoTo(other.mark()) }
                shouldThrow<IllegalArgumentException> { store.constrain(IntEquality(foreign, IntLiteral(0))) }
            }
            val scoped = ConstraintScope.empty().intVar("y", 0..1)
            shouldThrow<IllegalArgumentException> { store.entails(IntEquality(scoped, IntLiteral(0))) }
        }
    }

    test("store-contract/variables-declared-after-a-mark-are-dropped-by-undo") {
        create().use { store ->
            val x = store.intVar("x", IntDomain.of(0, 3))
            val mark = store.mark()
            val y = store.intVar("y", IntDomain.of(0, 3))
            store.constrain(IntLessThan(x, y)).status shouldNotBe StoreStatus.FAILED
            store.undoTo(mark)
            shouldThrow<IllegalArgumentException> { store.constrain(IntEquality(y, IntLiteral(0))) }
            shouldThrow<IllegalArgumentException> { store.domain(y) }
            store.constrain(IntEquality(x, IntLiteral(3))).status shouldNotBe StoreStatus.FAILED
        }
    }

    test("store-contract/entailment-is-four-valued-and-sound") {
        create().use { store ->
            val x = store.intVar("x", IntDomain.of(0, 9))
            store.constrain(IntLessOrEqual(IntLiteral(5), x))
            store.entails(IntLessOrEqual(IntLiteral(3), x)) shouldBe EntailmentResult(Entailment.ENTAILED, evidence)
            store.entails(IntLessThan(x, IntLiteral(2))) shouldBe EntailmentResult(Entailment.DISENTAILED, evidence)
            val open = store.entails(IntEquality(x, IntLiteral(7)))
            open.evidence shouldBe evidence
            if (propagates) {
                open.value shouldBeIn listOf(Entailment.UNDETERMINED, Entailment.UNKNOWN)
            } else {
                open.value shouldBe
                    Entailment.UNDETERMINED
            }
            store.constrain(IntEquality(x, IntLiteral(6))).status shouldNotBe StoreStatus.FAILED
        }
    }

    test("store-contract/propagation-shrinks-domains").config(enabled = propagates) {
        create().use { store ->
            val x = store.intVar("x", IntDomain.of(0, 9))
            val y = store.intVar("y", IntDomain.of(0, 9))
            val result = store.constrain(IntLessThan(IntSum(x, y), IntLiteral(3)))
            result.changed shouldContain x
            store.domain(x) shouldBe IntDomain.of(0, 2)
            val mark = store.mark()
            store.constrain(IntEquality(x, IntLiteral(2)))
            store.domain(y) shouldBe IntDomain.of(0, 0)
            store.undoTo(mark)
            store.domain(y) shouldBe IntDomain.of(0, 2)
        }
    }

    test("store-contract/domains-without-propagation-are-declared").config(enabled = !propagates) {
        create().use { store ->
            val x = store.intVar("x", IntDomain.values(1, 4))
            store.constrain(IntLessThan(x, IntLiteral(3))).changed shouldBe emptyList()
            store.domain(x) shouldBe IntDomain.values(1, 4)
        }
    }

    test("store-contract/residual-holds-the-relevant-constraints") {
        create().use { store ->
            val x = store.intVar("x", IntDomain.of(0, 9))
            val y = store.intVar("y", IntDomain.of(0, 9))
            val z = store.intVar("z", IntDomain.of(0, 9))
            val linking = IntLessThan(x, y)
            val unrelated = IntLessThan(z, IntLiteral(4))
            store.constrain(linking)
            store.constrain(unrelated)
            val residual = store.project(listOf(x))
            residual.constraints shouldContain linking
            (unrelated in residual.constraints) shouldBe false
            residual.domains.keys shouldBe setOf(x)
        }
    }

    withTests(nameFn = { "store-contract/agrees-with-the-oracle/${it.name}" }, StoreCase.catalogue()) { case ->
        create().use { store ->
            val xs = case.domains.mapIndexed { index, domain -> store.intVar("x$index", domain) }
            var status = StoreStatus.UNKNOWN
            for (constraint in case.constraints(xs)) status = store.constrain(constraint).status
            with(ConstraintScope.empty()) {
                val ys = case.domains.mapIndexed { index, domain -> intVar("x$index", domain) }
                case.constraints(ys).forEach { add(it) }
                val solutions = FiniteOracle().use { oracle -> enumerate(oracle, build()).first }
                when {
                    solutions.isEmpty() -> if (!propagates) status shouldBe StoreStatus.FAILED
                    else -> status shouldNotBe StoreStatus.FAILED
                }
                // An unsatisfiable store entails everything vacuously, so only satisfiable cases probe entailment.
                if (solutions.isEmpty()) return@withTests
                val probe = case.probe(xs)
                val holds =
                    solutions
                        .map { values ->
                            GroundEvaluator.evaluate(
                                case.probe(ys),
                                Assignment.of(
                                    integers =
                                        ys.zip(values).associate { (y, v) ->
                                            y to
                                                v as BigInteger
                                        },
                                ),
                            )
                        }.toSet()
                val truth =
                    when (holds) {
                        setOf(true) -> Entailment.ENTAILED
                        setOf(false) -> Entailment.DISENTAILED
                        else -> Entailment.UNDETERMINED
                    }
                val answer = store.entails(probe).value
                if (propagates) answer shouldBeIn listOf(truth, Entailment.UNKNOWN) else answer shouldBe truth
            }
        }
    }

    test("store-contract/failures-are-explained-by-posted-constraints") {
        create().use { store ->
            val x = store.intVar("x", IntDomain.of(0, 9))
            val y = store.intVar("y", IntDomain.of(0, 9))
            val z = store.intVar("z", IntDomain.of(0, 9))
            val unrelated = IntLessThan(z, IntLiteral(3))
            val forward = IntLessThan(x, y)
            val backward = IntLessThan(y, x)
            store.constrain(unrelated)
            store.constrain(forward)
            store.explain() shouldBe null
            store.constrain(backward).status shouldBe StoreStatus.FAILED
            val why = requireNotNull(store.explain())
            why shouldContainAll listOf(forward, backward)
            listOf(unrelated, forward, backward) shouldContainAll why
        }
    }

    test("store-contract/forks-are-independent") {
        create().use { parent ->
            val x = parent.intVar("x", IntDomain.of(0, 9))
            parent.constrain(IntLessThan(x, IntLiteral(5)))
            val mark = parent.mark()
            parent.fork().use { child ->
                child.entails(IntLessThan(x, IntLiteral(5))).value shouldBe Entailment.ENTAILED
                child.constrain(IntEquality(x, IntLiteral(7))).status shouldBe StoreStatus.FAILED
                parent.isFailed shouldBe false
                parent.constrain(IntEquality(x, IntLiteral(3))).status shouldNotBe StoreStatus.FAILED
                shouldThrow<IllegalArgumentException> { child.undoTo(mark) }
                val y = child.intVar("y", IntDomain.of(0, 1))
                shouldThrow<IllegalArgumentException> { parent.constrain(IntEquality(y, IntLiteral(0))) }
                child.fork().use { grandchild -> grandchild.isFailed shouldBe true }
            }
            parent.undoTo(mark)
            parent.constrain(IntEquality(x, IntLiteral(4))).status shouldNotBe StoreStatus.FAILED
        }
    }

    test("store-contract/exact-reals").config(enabled = reals) {
        create().use { store ->
            val r = store.realVar("r")
            store.constrain(RealLessThan(RealLiteral(Rational.of(1, 2)), r)).status shouldBe StoreStatus.SATISFIABLE
            store.entails(RealLessThan(RealLiteral(Rational.of(1, 3)), r)).value shouldBe Entailment.ENTAILED
            store.entails(RealLessThan(r, RealLiteral(Rational.of(1, 1)))).value shouldBe Entailment.UNDETERMINED
            store.constrain(RealLessThan(r, RealLiteral(Rational.of(1, 3)))).status shouldBe StoreStatus.FAILED
        }
    }

    test("store-contract/reals-are-rejected").config(enabled = !reals) {
        create().use { store -> shouldThrow<UnsupportedModelException> { store.realVar("r") } }
    }

    test("store-contract/timeouts-and-cancellation-are-not-failures").config(enabled = !propagates) {
        create().use { store ->
            val pigeons = List(PIGEONS) { store.intVar("p$it", IntDomain.of(0, PIGEONS - 2L)) }
            val pairs = pigeons.indices.flatMap { i -> (i + 1 until PIGEONS).map { j -> BoolNot(IntEquality(pigeons[i], pigeons[j])) } }
            pairs.dropLast(1).forEach { store.constrain(it).status shouldBe StoreStatus.SATISFIABLE }
            val mark = store.mark()
            store.constrain(pairs.last(), timeoutMillis = 300).status shouldBe StoreStatus.UNKNOWN
            store.isFailed shouldBe false
            store.undoTo(mark)
            val retry = store.mark()
            withTimeoutOrNull(300.milliseconds) { store.constrain(pairs.last()) } shouldBe null
            store.undoTo(retry)
            store.constrain(IntEquality(pigeons[0], IntLiteral(0))).status shouldBe StoreStatus.SATISFIABLE
        }
    }

    test("store-contract/closed-stores-reject-use") {
        val store = create()
        val x = store.intVar("x", IntDomain.of(0, 1))
        store.close()
        store.close()
        shouldThrow<IllegalStateException> { store.constrain(IntEquality(x, IntLiteral(0))) }
        shouldThrow<IllegalStateException> { store.mark() }
    }
}

/** Thirteen pigeons in twelve holes: refuting it takes CDCL solvers far longer than the test's deadlines. */
private const val PIGEONS = 13
