package io.github.gciatto.ktconstraints.model

import io.github.gciatto.ktconstraints.test.core.modelScopeContract
import io.kotest.core.spec.style.FunSpec

/** Executes shared scope contracts with isolated builders. */
class ModelScopeContractSpec :
    FunSpec({
        include(modelScopeContract { ConstraintScope.empty() })
    })
