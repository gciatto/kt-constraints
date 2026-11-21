package io.github.gciatto.ktconstraints.solve.kotlin.sat

/** Binary max-heap of variables by activity, so a decision costs a logarithmic number of comparisons. */
internal class SatOrder(
    private val activity: () -> DoubleArray,
) {
    private var heap = IntArray(16)
    private var positions = IntArray(16) { -1 }
    private var size = 0

    /** Restores every variable in `1..variables` to the heap. */
    fun reset(variables: Int) {
        if (variables >= positions.size) {
            positions = IntArray(variables + 1)
            heap = IntArray(variables + 1)
        }
        positions.fill(-1)
        size = 0
        for (variable in 1..variables) {
            heap[size] = variable
            positions[variable] = size++
        }
        for (index in size / 2 - 1 downTo 0) down(index)
    }

    fun insert(variable: Int) {
        if (positions[variable] >= 0) return
        heap[size] = variable
        positions[variable] = size
        up(size++)
    }

    /** Re-establishes order after [variable]'s activity increased. */
    fun increased(variable: Int) {
        val position = positions[variable]
        if (position >= 0) up(position)
    }

    /** Removes and returns the most active variable, or 0 when empty. */
    fun removeMax(): Int {
        if (size == 0) return 0
        val top = heap[0]
        positions[top] = -1
        val last = heap[--size]
        if (size > 0) {
            heap[0] = last
            positions[last] = 0
            down(0)
        }
        return top
    }

    private fun up(start: Int) {
        val scores = activity()
        val variable = heap[start]
        var index = start
        while (index > 0) {
            val parent = (index - 1) / 2
            if (scores[heap[parent]] >= scores[variable]) break
            heap[index] = heap[parent]
            positions[heap[index]] = index
            index = parent
        }
        heap[index] = variable
        positions[variable] = index
    }

    private fun down(start: Int) {
        val scores = activity()
        val variable = heap[start]
        var index = start
        while (true) {
            var child = 2 * index + 1
            if (child >= size) break
            if (child + 1 < size && scores[heap[child + 1]] > scores[heap[child]]) child++
            if (scores[heap[child]] <= scores[variable]) break
            heap[index] = heap[child]
            positions[heap[index]] = index
            index = child
        }
        heap[index] = variable
        positions[variable] = index
    }
}
