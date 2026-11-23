package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.global.counting.AllDifferent
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.ExperimentalKtConstraintsApi
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.feature.ModelFeatures
import io.github.gciatto.ktconstraints.solve.spi.feature.ProblemClass
import io.github.gciatto.ktconstraints.solve.spi.ranking.Heuristics
import io.github.gciatto.ktconstraints.solve.spi.ranking.PerformanceEvidence
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingHeuristic
import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import io.github.gciatto.ktconstraints.solve.spi.ranking.Score
import io.github.gciatto.ktconstraints.solve.spi.registry.SolverRegistry
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Feature-, capability- and platform-aware ranking with an extension point for benchmark evidence. */
@OptIn(ExperimentalKtConstraintsApi::class)
class RankingSpec :
    FunSpec({
        val cp =
            setOf(
                SolverCapability.SATISFACTION,
                SolverCapability.OPTIMIZATION,
                SolverCapability.INTEGER_ARITHMETIC,
                SolverCapability.GLOBAL_CONSTRAINTS,
            )
        val smt = cp + setOf(SolverCapability.REAL_ARITHMETIC, SolverCapability.INCREMENTAL_ASSERTIONS)
        val choco = FakeBackend(SolverDescriptor("choco", "direct", cp + SolverCapability.GLOBAL_CONSTRAINTS))
        val cpSat = FakeBackend(SolverDescriptor("cp-sat", "direct", cp + SolverCapability.GLOBAL_CONSTRAINTS))
        val gecode = FakeBackend(SolverDescriptor("gecode", "minizinc", cp + SolverCapability.GLOBAL_CONSTRAINTS))
        val scip = FakeBackend(SolverDescriptor("scip", "or-tools", cp))
        val z3 = FakeBackend(SolverDescriptor("z3", "direct", smt))
        val jvm = Platform(Runtime.JVM, "linux")

        fun ranked(
            model: ConstraintModel,
            vararg backends: SolverBackend,
            policy: RankingPolicy = RankingPolicy.DEFAULT,
        ) = SolverRegistry
            .of(*backends)
            .rank(model, policy = policy, platform = jvm)
            .candidates
            .map { it.backend.descriptor.id }

        val reals = ConstraintModel.of { add(RealLessThan(RealLiteral(Rational.of(1, 2)), realVar("r"))) }
        val linear =
            ConstraintModel.of {
                val x = intVar("x", 0..9)
                val y = intVar("y", 0..9)
                add(IntLessOrEqual(IntSum(x, y), IntLiteral(10)))
                maximize(IntSum(x, y))
            }
        val globals = ConstraintModel.of { add(AllDifferent(List(3) { intVar("x$it", 0..2) })) }

        test("ranking/reals-favour-smt-routes") {
            ranked(reals, choco, scip, z3).first() shouldBe "z3/direct"
        }

        test("ranking/linear-integer-optimization-favours-mip-routes") {
            ranked(linear, choco, z3, scip).first() shouldBe "scip/or-tools"
        }

        test("ranking/globals-favour-cp-routes-and-direct-routes-beat-compiled-ones") {
            ranked(globals, gecode, scip, z3, cpSat).take(2) shouldBe listOf("cp-sat/direct", "gecode/minizinc")
        }

        test("ranking/ties-keep-registration-order") {
            val first = FakeBackend(SolverDescriptor("alpha", "direct", cp))
            val second = FakeBackend(SolverDescriptor("beta", "direct", cp))
            ranked(globals, first, second) shouldBe listOf("alpha/direct", "beta/direct")
            ranked(globals, second, first) shouldBe listOf("beta/direct", "alpha/direct")
        }

        test("ranking/benchmark-evidence-can-override-the-defaults") {
            val evidence =
                PerformanceEvidence { problemClass, route, _ ->
                    if (problemClass == ProblemClass.CP_GLOBALS && route == "scip/or-tools") 1.0 else 10_000.0
                }
            val policy = RankingPolicy.DEFAULT.with(Heuristics.evidence(evidence), weight = 10.0)
            ranked(globals, cpSat, choco, scip, policy = policy).first() shouldBe "scip/or-tools"
        }

        test("ranking/custom-heuristics-extend-the-policy") {
            val preferChoco =
                object : RankingHeuristic {
                    override val name = "prefer-choco"

                    override fun score(
                        features: ModelFeatures,
                        descriptor: SolverDescriptor,
                        platform: Platform,
                    ): Score? = if (descriptor.engine == "choco") Score(1.0, "house preference") else null
                }
            ranked(globals, cpSat, choco, policy = RankingPolicy.DEFAULT.with(preferChoco, weight = 5.0)).first() shouldBe "choco/direct"
        }

        test("ranking/selection-explains-scores-and-rejections") {
            val unavailable = FakeBackend(SolverDescriptor("cbc", "minizinc", cp), BackendAvailability(false, "Executable not installed"))
            val selection = SolverRegistry.of(unavailable, scip, cpSat).rank(linear, platform = jvm)
            selection.best.backend shouldBe scip
            selection.rejections.getValue("cbc/minizinc") shouldContain "Executable not installed"
            val top = selection.candidates.first()
            top.contributions.map { it.heuristic } shouldContain "feature-affinity"
            top.explanation shouldContain "MIP"
            selection.features.problemClass shouldBe ProblemClass.LINEAR_INTEGER
        }

        test("ranking/no-evidence-changes-nothing") {
            val policy = RankingPolicy.DEFAULT.with(Heuristics.evidence(PerformanceEvidence.NONE), weight = 100.0)
            ranked(linear, choco, z3, scip, policy = policy) shouldBe ranked(linear, choco, z3, scip)
        }
    })
