@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.promise
import org.gciatto.kt.math.BigInteger
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt
import kotlin.js.Promise

/** The standard JavaScript cancellation signal, from the DOM or Node globals. */
external interface AbortSignal {
    /** Whether abortion was already requested. */
    val aborted: Boolean

    /** Registers [listener] for the `abort` event. */
    fun addEventListener(
        type: String,
        listener: () -> Unit,
    )
}

/** The standard JavaScript async iterable, consumed with `for await`. */
external interface AsyncIterable<out T>

internal fun BigInteger.toBigInt(): JsBigInt = bigInt(toString()).unsafeCast<JsBigInt>()

internal fun JsBigInt.toBigInteger(): BigInteger = BigInteger.of(toString())

private val bigInt: dynamic = js("BigInt")

private val iterable: dynamic =
    js(
        """(function (next, close) {
            var iterator = {
                next: function () {
                    return next().then(function (value) {
                        return value == null ? { done: true, value: undefined } : { done: false, value: value };
                    });
                },
                "return": function () {
                    close();
                    return Promise.resolve({ done: true, value: undefined });
                }
            };
            var result = {};
            result[Symbol.asyncIterator] = function () { return iterator; };
            return result;
        })""",
    )

/** Adapts a pull function returning null when exhausted, closing on early exit from `for await`. */
internal fun <T : Any> asyncIterable(
    next: () -> Promise<T?>,
    close: () -> Unit,
): AsyncIterable<T> = iterable(next, close).unsafeCast<AsyncIterable<T>>()

/** Runs [block] in this scope; aborting [signal] cancels it and rejects the returned promise. */
internal fun <T> CoroutineScope.cancellable(
    signal: AbortSignal?,
    block: suspend () -> T,
): Promise<T> {
    val work = async { block() }
    signal?.let { if (it.aborted) work.cancel() else it.addEventListener("abort") { work.cancel() } }
    return promise { work.await() }
}
