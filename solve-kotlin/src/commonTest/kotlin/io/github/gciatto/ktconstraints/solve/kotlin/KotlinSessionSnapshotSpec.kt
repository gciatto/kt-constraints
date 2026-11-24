package io.github.gciatto.ktconstraints.solve.kotlin

import io.github.gciatto.ktconstraints.core.bool.BoolExpr
import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.relation.IntEquality
import io.github.gciatto.ktconstraints.model.ConstraintScope
import io.github.gciatto.ktconstraints.solve.incremental.CheckStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** An in-flight check owns its input assumptions even when the caller supplied a mutable list. */
class KotlinSessionSnapshotSpec :
    FunSpec({
        test("kotlin-session/assumptions-are-copied-before-the-first-yield") {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                coroutineScope {
                    ConstraintScope.empty {
                        val x = intVar("x", 0..1)
                        val assumptions = mutableListOf<BoolExpr>(IntEquality(x, IntLiteral(0)))
                        KotlinBackend().createSolver().use { solver ->
                            solver.openSession(buildModel()).use { session ->
                                // The single dispatcher lane keeps the yielded continuation queued until await below.
                                val pending = async(start = CoroutineStart.UNDISPATCHED) { session.check(assumptions) }
                                pending.isCompleted shouldBe false
                                assumptions[0] = IntEquality(x, IntLiteral(1))
                                val result = pending.await()
                                result.status shouldBe CheckStatus.SATISFIABLE
                                requireNotNull(result.assignment).intValueOf(x) shouldBe 0
                                session.check(assumptions).assignment?.intValueOf(x) shouldBe 1
                            }
                        }
                    }
                }
            }
        }
    })
