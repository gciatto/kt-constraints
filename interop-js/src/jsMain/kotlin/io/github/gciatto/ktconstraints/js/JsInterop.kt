@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.gciatto.ktconstraints.js

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.promise
import org.gciatto.kt.math.BigInteger
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsBigInt
import kotlin.js.Promise

internal fun BigInteger.toBigInt(): JsBigInt = bigInt(toString()).unsafeCast<JsBigInt>()

internal fun JsBigInt.toBigInteger(): BigInteger = BigInteger.of(toString())

private val bigInt: dynamic = js("BigInt")

/** Runs [block] in this scope; aborting [signal] cancels it and rejects the returned promise. */
internal fun <T> CoroutineScope.cancellable(
    signal: AbortSignal?,
    block: suspend () -> T,
): Promise<T> {
    val work = async { block() }
    signal?.let { if (it.aborted) work.cancel() else it.addEventListener("abort") { work.cancel() } }
    return promise { work.await() }
}
