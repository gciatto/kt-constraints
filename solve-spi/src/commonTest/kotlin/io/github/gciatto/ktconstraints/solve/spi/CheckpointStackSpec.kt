package io.github.gciatto.ktconstraints.solve.spi

import io.github.gciatto.ktconstraints.solve.spi.managed.CheckpointStack
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Checkpoints belong to exactly one stack and become stale once popped. */
class CheckpointStackSpec :
    FunSpec({
        test("checkpoints/pop-reports-the-number-of-frames-to-restore") {
            val stack = CheckpointStack()
            val outer = stack.push()
            stack.push()
            stack.push()
            stack.depth shouldBe 3
            stack.popTo(outer) shouldBe 3
            stack.depth shouldBe 0
        }

        test("checkpoints/popped-and-newer-checkpoints-are-stale") {
            val stack = CheckpointStack()
            val outer = stack.push()
            val inner = stack.push()
            stack.popTo(outer)
            shouldThrow<IllegalArgumentException> { stack.popTo(inner) }
            shouldThrow<IllegalArgumentException> { stack.popTo(outer) }
            stack.depth shouldBe 0
        }

        test("checkpoints/foreign-checkpoints-are-rejected-without-changes") {
            val stack = CheckpointStack()
            stack.push()
            val foreign = CheckpointStack().push()
            shouldThrow<IllegalArgumentException> { stack.popTo(foreign) }
            stack.depth shouldBe 1
        }
    })
