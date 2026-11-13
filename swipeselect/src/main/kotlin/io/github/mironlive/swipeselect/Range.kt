package io.github.mironlive.swipeselect

import kotlin.math.max
import kotlin.math.min

/** Inclusive range of item indices. */
public data class IndexRange(val lower: Int, val upper: Int) {
    public operator fun contains(i: Int): Boolean = i in lower..upper

    public companion object {
        public fun of(a: Int, b: Int): IndexRange = IndexRange(min(a, b), max(a, b))
    }
}

/**
 * Moves the selection from [previous] range to [next] range.
 *
 * Items entering the range get the gesture mode, items leaving it get back their
 * state from [initial]. Only the difference between the ranges is touched, so the
 * cost doesn't depend on how long the range is. Both ranges must contain the anchor.
 *
 * @return the new selection, or `null` when nothing changed.
 */
public fun <K> applyRangeTransition(
    initial: Set<K>,
    current: Set<K>,
    previous: IndexRange?,
    next: IndexRange,
    shouldSelect: Boolean,
    keyForIndex: (Int) -> K?,
    isSelectable: ((Int) -> Boolean)? = null,
): Set<K>? {
    var result: MutableSet<K>? = null

    fun set(i: Int, inRange: Boolean) {
        val key = keyForIndex(i) ?: return
        if (isSelectable?.invoke(i) == false) return
        val want = if (inRange) shouldSelect else key in initial
        val has = key in (result ?: current)
        if (want == has) return
        val r = result ?: LinkedHashSet(current).also { result = it }
        if (want) r.add(key) else r.remove(key)
    }

    fun visit(lo: Int, hi: Int) {
        for (i in lo..hi) set(i, i in next)
    }

    if (previous != null) {
        visit(min(previous.lower, next.lower), max(previous.lower, next.lower) - 1)
        visit(min(previous.upper, next.upper) + 1, max(previous.upper, next.upper))
        if (previous.upper < next.lower || next.upper < previous.lower) {
            visit(previous.lower, previous.upper)
            visit(next.lower, next.upper)
        }
    } else {
        visit(next.lower, next.upper)
    }
    return result
}
