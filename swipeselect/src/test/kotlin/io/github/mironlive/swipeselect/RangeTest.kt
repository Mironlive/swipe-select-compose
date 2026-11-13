package io.github.mironlive.swipeselect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeTest {
    private val keys = (0 until 20).map { "k$it" }
    private fun key(i: Int) = keys.getOrNull(i)

    /** Simulates a gesture: starts at [anchor], then the finger passes [path]. */
    private fun drag(initial: List<String>, anchor: Int, path: List<Int>, selectable: ((Int) -> Boolean)? = null): List<String> {
        val start = initial.toSet()
        val shouldSelect = keys[anchor] !in start
        var current = start
        var previous: IndexRange? = null
        for (i in listOf(anchor) + path) {
            val next = IndexRange.of(anchor, i)
            applyRangeTransition(start, current, previous, next, shouldSelect, ::key, selectable)?.let { current = it }
            previous = next
        }
        return current.sortedBy { it.drop(1).toInt() }
    }

    @Test fun down() = assertEquals(listOf("k2", "k3", "k4", "k5"), drag(emptyList(), 2, listOf(3, 4, 5)))
    @Test fun up() = assertEquals(listOf("k3", "k4", "k5"), drag(emptyList(), 5, listOf(4, 3)))
    @Test fun movingBackRestores() {
        assertEquals(listOf("k1", "k2", "k3", "k4"), drag(listOf("k4"), 1, listOf(2, 3, 4, 5, 6, 3)))
        assertEquals(listOf("k1", "k4"), drag(listOf("k4"), 1, listOf(6, 1)))
    }
    @Test fun crossingAnchor() = assertEquals(listOf("k3", "k4", "k5"), drag(emptyList(), 5, listOf(7, 3)))
    @Test fun startingOnSelectedDeselects() = assertEquals(listOf("k1", "k9"), drag(listOf("k1", "k2", "k3", "k9"), 2, listOf(3, 4)))
    @Test fun deselectAndBack() = assertEquals(listOf("k3", "k4"), drag(listOf("k1", "k2", "k3", "k4"), 1, listOf(4, 2)))
    @Test fun jump() = assertEquals(11, drag(emptyList(), 0, listOf(10)).size)
    @Test fun notSelectable() = assertEquals(listOf("k0", "k1", "k2", "k4", "k5"), drag(emptyList(), 0, listOf(5)) { it != 3 })
    @Test fun outsideUntouched() = assertEquals(listOf("k2", "k3", "k4", "k15", "k16"), drag(listOf("k15", "k16"), 2, listOf(4)))
    @Test fun noChangeReturnsNull() = assertNull(applyRangeTransition(setOf("k1"), setOf("k1"), null, IndexRange(1, 1), true, ::key))
    @Test fun bigJumpBackAndForth() = assertEquals(listOf("k10"), drag(emptyList(), 10, listOf(0, 19, 10)))
}
