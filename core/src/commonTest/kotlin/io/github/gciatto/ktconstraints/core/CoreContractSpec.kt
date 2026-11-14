package io.github.gciatto.ktconstraints.core

import io.github.gciatto.ktconstraints.test.core.exactAlgebraContract
import io.github.gciatto.ktconstraints.test.core.integerDomainContract
import io.kotest.core.spec.style.FunSpec

/** Executes reusable representation contracts without a solver. */
class CoreContractSpec :
    FunSpec({
        include(integerDomainContract { min, max -> IntDomain.of(min, max) })
        include(exactAlgebraContract { GroundEvaluator.evaluate(it) })
    })
