package com.edgeflow.core

/** Pure screen-brightness step calculation, independent of Android services. */
object BrightnessSteps {

    /**
     * Returns the next perceptual brightness level, or `null` at the requested bound.
     * The quadratic scale matches the reference action's 16-step brightness curve.
     */
    fun nextValue(
        current: Int,
        minimum: Int,
        maximum: Int,
        increase: Boolean,
        steps: Int = DEFAULT_STEPS,
    ): Int? {
        if (minimum >= maximum || steps <= 0) return null
        val levels = levels(minimum, maximum, steps)
        val index = levels.indexOfFirst { it >= current }.let { found ->
            if (found < 0) levels.lastIndex else found
        }

        return if (increase) {
            levels.getOrNull(index + 1)?.takeIf { it > current }
        } else {
            val currentLevel = levels[index]
            val target = if (currentLevel >= current) levels.getOrNull(index - 1) else currentLevel
            target?.takeIf { it < current }
        }
    }

    private fun levels(minimum: Int, maximum: Int, steps: Int): List<Int> {
        val span = maximum - minimum
        val denominator = steps.toLong() * steps
        val result = ArrayList<Int>(steps + 1)
        repeat(steps + 1) { index ->
            val quadraticOffset = span.toLong() * index * index / denominator
            val value = (minimum + quadraticOffset).toInt()
            result += if (result.isNotEmpty() && value <= result.last()) result.last() + 1 else value
        }
        return result
    }

    private const val DEFAULT_STEPS = 16
}
