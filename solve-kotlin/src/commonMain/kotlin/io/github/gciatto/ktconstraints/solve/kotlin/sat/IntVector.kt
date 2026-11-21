package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** Growable primitive storage for trails, clause watches and conflict analysis. */
internal class IntVector(
    initialCapacity: Int = 8,
) {
    private var values = IntArray(initialCapacity.coerceAtLeast(1))

    var size: Int = 0
        private set

    operator fun get(index: Int): Int {
        require(index in 0 until size)
        return values[index]
    }

    operator fun set(
        index: Int,
        value: Int,
    ) {
        require(index in 0 until size)
        values[index] = value
    }

    fun add(value: Int) {
        if (size == values.size) values = values.copyOf(values.size * 2)
        values[size++] = value
    }

    fun removeLast(): Int {
        check(size > 0)
        return values[--size]
    }

    fun removeAtSwap(index: Int) {
        require(index in 0 until size)
        values[index] = values[--size]
    }

    fun truncate(length: Int) {
        require(length in 0..size)
        size = length
    }

    fun clear() {
        size = 0
    }

    fun toIntArray(): IntArray = values.copyOf(size)
}
