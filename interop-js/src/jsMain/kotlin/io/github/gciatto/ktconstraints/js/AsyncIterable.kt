package io.github.gciatto.ktconstraints.js

import kotlin.js.Promise

/** The standard JavaScript async iterable, consumed with `for await`. */
external interface AsyncIterable<out T>

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
