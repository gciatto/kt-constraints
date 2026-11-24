package io.github.gciatto.ktconstraints.solve.blocking

import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.github.gciatto.ktconstraints.solve.spi.createIncrementalSolver
import io.github.gciatto.ktconstraints.solve.spi.store.SatisfiabilityStore
import io.github.gciatto.ktconstraints.solve.store.Entailment
import io.github.gciatto.ktconstraints.solve.store.StoreStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/** Blocking store operations over the portable backend. */
class BlockingStoreSpec :
    FunSpec({
        test("posts, entails, undoes and explains on the calling thread") {
            SatisfiabilityStore.of(KotlinBackend().createIncrementalSolver()).blocking().use { store ->
                val x = store.intVar("x", 0, 9)
                val mark = store.mark()
                store.constrain(x eq 2)
                store.entails(x eq 2).value shouldBe Entailment.ENTAILED
                store.undoTo(mark)
                store.entails(x eq 2).value shouldBe Entailment.UNDETERMINED
                val impossible = x gt 12
                store.constrain(impossible).status shouldBe StoreStatus.FAILED
                store.isFailed shouldBe true
                store.explain().orEmpty() shouldContain impossible
                store.fork().use { it.isFailed shouldBe true }
            }
        }
    })
