package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.Rational
import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.IntToReal
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.core.real.RealLiteral
import io.github.gciatto.ktconstraints.core.real.relation.RealEquality
import io.github.gciatto.ktconstraints.core.real.relation.RealLessThan
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gciatto.kt.math.BigInteger

/** Theory lemmas must stay sound when Boolean assumptions and user frames change. */
class KotlinTheorySessionSpec :
    FunSpec({
        test("mixed-real conflicts do not leak a popped integer interval") {
            ConstraintScope.empty {
                val x = intVar("x", 0..2)
                val r = realVar("r")
                add(RealEquality(r, IntToReal(x)))
                KotlinBackend().create().use { solver ->
                    solver.openSession(buildModel()).use { session ->
                        val frame = session.push()
                        session.add(RealLessThan(RealLiteral(Rational.ZERO), r))
                        session.add(RealLessThan(r, RealLiteral(Rational.ONE)))
                        session.check().status shouldBe CheckStatus.UNSATISFIABLE
                        session.popTo(frame)
                        for (value in 0..2) {
                            val result = session.check(listOf(IntEquality(x, IntLiteral(value))))
                            result.status shouldBe CheckStatus.SATISFIABLE
                            result.assignment?.valueOf(x) shouldBe BigInteger.of(value)
                            result.assignment?.valueOf(r) shouldBe Rational.of(value.toLong())
                        }
                    }
                }
            }
        }

        test("real assumption cores remain sufficient after a later satisfiable check") {
            ConstraintScope.empty {
                val r = realVar("r")
                val small = RealLessThan(r, RealLiteral(Rational.ZERO))
                val large = RealLessThan(RealLiteral(Rational.ONE), r)
                val assumptions: List<BoolExpr> = listOf(small, large)
                KotlinBackend().create().use { solver ->
                    solver.openSession(buildModel()).use { session ->
                        val conflict = session.check(assumptions)
                        conflict.status shouldBe CheckStatus.UNSATISFIABLE
                        val core = requireNotNull(conflict.unsatCore)
                        assumptions.containsAll(core) shouldBe true
                        session.check(listOf(large)).status shouldBe CheckStatus.SATISFIABLE
                        session.check(core).status shouldBe CheckStatus.UNSATISFIABLE
                        session.check(listOf(small)).status shouldBe CheckStatus.SATISFIABLE
                    }
                }
            }
        }

        test("same-name declarations in simultaneous engines keep separate bindings") {
            with(ConstraintScope.empty()) first@{
                val x = intVar("x", 0..2)
                add(IntEquality(x, IntLiteral(0)))
                ConstraintScope.empty {
                    val y = intVar("x", 0..2)
                    add(IntEquality(y, IntLiteral(2)))
                    KotlinBackend().create().use { solver ->
                        solver.openSession(this@first.buildModel()).use { left ->
                            solver.openSession(buildModel()).use { right ->
                                left.check().assignment?.valueOf(x) shouldBe BigInteger.ZERO
                                right.check().assignment?.valueOf(y) shouldBe BigInteger.TWO
                                left.check().assignment?.valueOf(y) shouldBe null
                                right.check().assignment?.valueOf(x) shouldBe null
                            }
                        }
                    }
                }
            }
        }
    })
