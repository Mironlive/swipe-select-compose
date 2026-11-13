package io.github.mironlive.swipeselect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Holds the selection. Read it from your composables.
 *
 * During a gesture every change is published (so rows update live), but [onChange]
 * fires only once — when the gesture ends. Use [onChange] to sync with your
 * ViewModel without dozens of updates per second.
 */
@Stable
public class SwipeSelectState<K>(
    initialSelection: Set<K> = emptySet(),
    selectionMode: Boolean? = null,
    /**
     * Called once per gesture (when it ends) and on every [toggle] / [select] /
     * [deselect] / [setSelection] / [clear]. Not called by [sync].
     */
    public var onChange: ((Set<K>, SelectionChangeReason) -> Unit)? = null,
) {
    /** Current selection. */
    public var selected: Set<K> by mutableStateOf(initialSelection.toSet())
        private set

    /**
     * Force selection mode on / off, or `null` to derive it from the selection.
     * Use it when your app has a "Select" button.
     */
    public var selectionMode: Boolean? by mutableStateOf(selectionMode)

    /** A finger is dragging a selection right now. */
    public var isDragging: Boolean by mutableStateOf(false)
        private set

    /** On-screen auto-scroll direction: -1 towards the top / start, 0, 1 towards the bottom / end. */
    public var autoScrollDirection: Int by mutableIntStateOf(0)
        private set

    /** Selection mode: forced by [selectionMode], otherwise on while something is selected. */
    public val isSelectionMode: Boolean get() = selectionMode ?: selected.isNotEmpty()

    public val count: Int get() = selected.size

    public fun isSelected(key: K): Boolean = key in selected

    public fun toggle(key: K) {
        commit(if (key in selected) selected - key else selected + key, SelectionChangeReason.Toggle)
    }

    public fun select(keys: Iterable<K>) { commit(selected + keys, SelectionChangeReason.Api) }

    public fun deselect(keys: Iterable<K>) { commit(selected - keys.toSet(), SelectionChangeReason.Api) }

    public fun setSelection(keys: Iterable<K>) { commit(keys.toSet(), SelectionChangeReason.Api) }

    public fun clear() { commit(emptySet(), SelectionChangeReason.Api) }

    /** Replaces the selection without calling [onChange] — for state that came from the outside. */
    public fun sync(keys: Iterable<K>) {
        val next = keys.toSet()
        if (next != selected) selected = next
    }

    private fun commit(next: Set<K>, reason: SelectionChangeReason) {
        if (next == selected) return
        selected = next
        onChange?.invoke(next, reason)
    }

    // ---- used by the gesture ----

    internal fun gestureUpdate(next: Set<K>) { selected = next }

    internal fun gestureBegan() { isDragging = true }

    internal fun gestureEnded(initial: Set<K>) {
        isDragging = false
        autoScrollDirection = 0
        if (initial != selected) onChange?.invoke(selected, SelectionChangeReason.Gesture)
    }

    internal fun updateAutoScrollDirection(value: Int) {
        if (autoScrollDirection != value) autoScrollDirection = value
    }
}

/** Creates and remembers a [SwipeSelectState]. */
@Composable
public fun <K> rememberSwipeSelectState(
    initialSelection: Set<K> = emptySet(),
    selectionMode: Boolean? = null,
    onChange: ((Set<K>, SelectionChangeReason) -> Unit)? = null,
): SwipeSelectState<K> {
    val state = remember { SwipeSelectState(initialSelection, selectionMode, onChange) }
    state.onChange = onChange
    return state
}
