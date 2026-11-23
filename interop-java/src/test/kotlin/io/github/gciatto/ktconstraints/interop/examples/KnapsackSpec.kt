package io.github.gciatto.ktconstraints.interop.examples

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Runs the Java tutorial through the compiled Java facade. */
class KnapsackSpec :
    FunSpec({
        test("blocking knapsack proves value 16") { Knapsack.run(false) shouldBe 16L }
        test("CompletionStage knapsack proves value 16") { Knapsack.run(true) shouldBe 16L }
    })
