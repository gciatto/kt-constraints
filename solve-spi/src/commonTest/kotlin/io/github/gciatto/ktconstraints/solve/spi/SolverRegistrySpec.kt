package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.NumericTolerance
import io.github.gciatto.ktconstraints.solve.SolveOptions
import io.github.gciatto.ktconstraints.solve.SolverCapability
import io.github.gciatto.ktconstraints.solve.SolverDescriptor
import io.github.gciatto.ktconstraints.solve.spi.exception.NoCompatibleBackendException
import io.github.gciatto.ktconstraints.solve.spi.registry.SolverRegistry
import io.github.gciatto.ktconstraints.solve.spi.support.SupportReport
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Selection uses explicit registration and checks every eligibility dimension. */
class SolverRegistrySpec :
    FunSpec({
        test("registry/selects-first-available-whole-model-compatible-route") {
            val unavailable =
                FakeBackend(
                    SolverDescriptor("example", "missing", setOf(SolverCapability.SATISFACTION)),
                    BackendAvailability(false, "Runtime missing"),
                )
            val unsupported =
                FakeBackend(
                    SolverDescriptor("example", "partial", setOf(SolverCapability.SATISFACTION)),
                    supportReport = SupportReport(unsupported = listOf("Coefficient exceeds native integer range")),
                )
            val direct = FakeBackend(SolverDescriptor("example", "direct", setOf(SolverCapability.SATISFACTION)))
            val viaCompiler = FakeBackend(SolverDescriptor("example", "minizinc", setOf(SolverCapability.SATISFACTION)))
            val registry = SolverRegistry.of(unavailable, unsupported, direct, viaCompiler)
            registry.select(ConstraintModel.empty()) shouldBe direct
            registry.require("example/minizinc") shouldBe viaCompiler
        }

        test("registry/objectives-require-optimization-capability") {
            val satisfaction = FakeBackend(SolverDescriptor("example", "batch", setOf(SolverCapability.SATISFACTION)))
            val optimization =
                FakeBackend(
                    SolverDescriptor(
                        "example",
                        "optimizer",
                        setOf(SolverCapability.SATISFACTION, SolverCapability.OPTIMIZATION, SolverCapability.INTEGER_ARITHMETIC),
                    ),
                )
            val model = ConstraintModel.of { minimize(intVar("x", 0..1)) }
            SolverRegistry.of(satisfaction, optimization).select(model) shouldBe optimization
            shouldThrow<NoCompatibleBackendException> { SolverRegistry.of(satisfaction).select(model) }
        }

        test("registry/floating-point-reals-need-an-explicit-tolerance") {
            val capabilities =
                setOf(
                    SolverCapability.SATISFACTION,
                    SolverCapability.OPTIMIZATION,
                    SolverCapability.REAL_ARITHMETIC,
                    SolverCapability.FLOATING_POINT_REALS,
                )
            val floating = FakeBackend(SolverDescriptor("example", "floating", capabilities))
            val real = ConstraintModel.of { maximize(realVar("r")) }
            val integer = ConstraintModel.of { maximize(intVar("x", 0..1)) }
            val registry = SolverRegistry.of(floating)
            registry.rank(real).rejections.getValue("example/floating") shouldBe
                listOf("Floating-point reals need SolveOptions.numericTolerance")
            registry.select(real, SolveOptions(numericTolerance = NumericTolerance())) shouldBe floating
            shouldThrow<NoCompatibleBackendException> { registry.select(integer) }
        }

        test("registry/rejects-requested-unsupported-operations-with-route-diagnostics") {
            val backend = FakeBackend(SolverDescriptor("example", "direct", setOf(SolverCapability.SATISFACTION)))
            val error =
                shouldThrow<NoCompatibleBackendException> {
                    SolverRegistry.of(backend).select(ConstraintModel.empty(), requiredCapabilities = setOf(SolverCapability.ENUMERATION))
                }
            error.rejections.keys shouldBe setOf("example/direct")
            error.rejections
                .getValue("example/direct")
                .single()
                .contains("ENUMERATION") shouldBe true
        }

        test("registry/empty-means-no-backends-including-test-fakes") {
            val registry = SolverRegistry.empty()
            shouldThrow<NoCompatibleBackendException> { registry.select(ConstraintModel.empty()) }
            shouldThrow<IllegalArgumentException> { registry.require("example/direct") }
        }

        test("registry/rejects-duplicate-route-ids") {
            val first = FakeBackend(SolverDescriptor("example", "direct", emptySet()))
            val second = FakeBackend(SolverDescriptor("example", "direct", setOf(SolverCapability.OPTIMIZATION)))
            shouldThrow<IllegalArgumentException> { SolverRegistry.of(first, second) }
        }

        test("registry/preserves-availability-and-support-diagnostics") {
            val missing =
                FakeBackend(
                    SolverDescriptor("example", "missing", setOf(SolverCapability.SATISFACTION)),
                    BackendAvailability(false, "Executable not installed"),
                )
            val unsupported =
                FakeBackend(
                    SolverDescriptor("example", "direct", setOf(SolverCapability.SATISFACTION)),
                    supportReport = SupportReport(unsupported = listOf("Unsupported table constraint")),
                )
            val error =
                shouldThrow<NoCompatibleBackendException> {
                    SolverRegistry.of(missing, unsupported).select(ConstraintModel.empty())
                }
            error.rejections.getValue("example/missing") shouldBe listOf("Executable not installed")
            error.rejections.getValue("example/direct") shouldBe listOf("Unsupported table constraint")
            runCatching { (error.rejections as MutableMap).clear() }
            error.rejections.size shouldBe 2
        }
    })
