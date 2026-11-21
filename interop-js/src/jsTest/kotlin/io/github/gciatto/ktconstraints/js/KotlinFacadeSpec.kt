@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js

import io.github.gciatto.ktconstraints.solve.kotlin.KotlinBackend
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt
import kotlin.js.Promise
import kotlin.js.jsTypeOf

/** Exercises exported JavaScript method names and values with a test-only backend dependency. */
class KotlinFacadeSpec : FunSpec({
    test("portable facade resolves a JavaScript Promise with an exact bigint") {
        val solver = Solver(KotlinBackend().create()).asDynamic()
        try {
            val builder = ModelBuilder().asDynamic()
            val x = builder.intVar("x", jsInteger("1180591620717411303424"), jsInteger("1180591620717411303426"))
            builder.add(x.eq(constant(jsInteger("1180591620717411303425"))))
            val pending = solver.solve(builder.build(), null, null, null)
            jsTypeOf(pending.then) shouldBe "function"
            val outcome = awaited(pending)
            outcome.status as String shouldBe "FEASIBLE"
            outcome.route as String shouldBe "kotlin/direct"
            val value = outcome.solution.valueOf(x)
            jsTypeOf(value) shouldBe "bigint"
            value.toString() as String shouldBe "1180591620717411303425"
        } finally {
            solver.close()
        }
    }

    test("portable facade returns exact rational strings") {
        val solver = Solver(KotlinBackend().create()).asDynamic()
        try {
            val builder = ModelBuilder().asDynamic()
            val r = builder.realVar("r")
            builder.add(r.eq(real("1/3")))
            val outcome = awaited(solver.solve(builder.build(), null, null, null))
            outcome.status as String shouldBe "FEASIBLE"
            outcome.solution.realValueOf(r) as String shouldBe "1/3"
        } finally {
            solver.close()
        }
    }

    test("portable facade implements the JavaScript async iterator protocol") {
        val solver = Solver(KotlinBackend().create()).asDynamic()
        try {
            val builder = ModelBuilder().asDynamic()
            val x = builder.intVar("x", jsInteger("0"), jsInteger("2"))
            val iterable = solver.solutions(builder.build(), null, null)
            val iterator = iterable[js("Symbol.asyncIterator")]()
            val answers = mutableListOf<String>()
            var completions = 0
            repeat(4) {
                val item = awaited(iterator.next())
                item.done as Boolean shouldBe false
                val event = item.value
                if (event.solution != null) {
                    answers += event.solution.valueOf(x).toString() as String
                } else {
                    event.outcome.status as String shouldBe "FEASIBLE"
                    event.outcome.termination as String shouldBe "COMPLETED"
                    completions++
                }
            }
            answers.sorted() shouldBe listOf("0", "1", "2")
            completions shouldBe 1
            awaited(iterator.next()).done as Boolean shouldBe true
        } finally {
            solver.close()
        }
    }

    test("returning from a JavaScript async iterator releases only that search") {
        val solver = Solver(KotlinBackend().create()).asDynamic()
        try {
            val builder = ModelBuilder().asDynamic()
            builder.intVar("x", jsInteger("0"), jsInteger("5"))
            val model = builder.build()
            val iterable = solver.solutions(model, null, null)
            val iterator = iterable[js("Symbol.asyncIterator")]()
            awaited(iterator.next()).done as Boolean shouldBe false
            awaited(iterator["return"]()).done as Boolean shouldBe true
            awaited(solver.solve(model, null, null, null)).status as String shouldBe "FEASIBLE"
        } finally {
            solver.close()
        }
    }

    test("portable facade preserves session frames assumption identity and close") {
        val solver = Solver(KotlinBackend().create()).asDynamic()
        try {
            val builder = ModelBuilder().asDynamic()
            val x = builder.intVar("x", jsInteger("0"), jsInteger("2"))
            val session = solver.openSession(builder.build())
            try {
                session.add(x.ge(constant(jsInteger("1"))))
                val frame = session.push()
                session.add(x.lt(constant(jsInteger("1"))))
                awaited(session.check(emptyArray<BoolExpression>(), null, null)).status as String shouldBe "UNSATISFIABLE"
                session.popTo(frame)
                shouldThrow<IllegalArgumentException> { session.popTo(frame) }

                val one = x.eq(constant(jsInteger("1"))).unsafeCast<BoolExpression>()
                val two = x.eq(constant(jsInteger("2"))).unsafeCast<BoolExpression>()
                val conflict = awaited(session.check(arrayOf(one, two), null, null))
                conflict.status as String shouldBe "UNSATISFIABLE"
                conflict.unsatCore.length as Int shouldBe 2
                (conflict.unsatCore[0] === one) shouldBe true
                (conflict.unsatCore[1] === two) shouldBe true

                val restored = awaited(session.check(arrayOf(two), null, null))
                restored.status as String shouldBe "SATISFIABLE"
                restored.solution.valueOf(x).toString() as String shouldBe "2"
                awaited(session.check(emptyArray<BoolExpression>(), null, null)).status as String shouldBe "SATISFIABLE"
                solver.close()
                shouldThrow<IllegalStateException> { session.push() }
            } finally {
                session.close()
                session.close()
            }
        } finally {
            solver.close()
        }
    }

    test("an already aborted JavaScript signal rejects the Promise without timers") {
        val solver = Solver(KotlinBackend().create()).asDynamic()
        try {
            val controller = js("new AbortController()")
            controller.abort()
            val model = ModelBuilder().build()
            rejected(solver.solve(model, null, null, controller.signal)) shouldBe true
            awaited(solver.solve(model, null, null, null)).status as String shouldBe "FEASIBLE"
        } finally {
            solver.close()
        }
    }
})

private fun jsInteger(value: String): JsBigInt = js("BigInt")(value).unsafeCast<JsBigInt>()

private suspend fun awaited(value: Promise<dynamic>): dynamic = value.await()

private suspend fun rejected(value: dynamic): Boolean =
    awaited(value.then(js("() => false"), js("() => true"))) as Boolean
