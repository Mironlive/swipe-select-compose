package io.github.mironlive.swipeselect

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** When an activation is available. */
public enum class ActivationWhen {
    /** Always. */
    Always,

    /** Only in selection mode (something is selected, or selection mode is forced on). */
    SelectionMode,

    /**
     * Only outside selection mode. Handy to let a long press enter the mode
     * and the edge strip take over afterwards.
     */
    Idle,
}

/** Which edge of the list the selection strip is on. */
public enum class EdgeSide {
    /** Left edge (right in right-to-left layouts). Top edge for horizontal lists. */
    Start,

    /** The other edge. */
    End,
}

/** How a selection gesture starts. Combine several in a list. */
@Immutable
public sealed class SwipeSelectActivation {
    /** When this activation is available. */
    public abstract val `when`: ActivationWhen

    /**
     * A strip along the edge of the list (like mail apps). Dragging inside the strip
     * selects; outside of it the list scrolls as usual. Taps inside the strip still
     * reach your own buttons.
     */
    public data class Edge(
        val side: EdgeSide = EdgeSide.Start,
        val size: Dp = 56.dp,
        override val `when`: ActivationWhen = ActivationWhen.SelectionMode,
    ) : SwipeSelectActivation()

    /**
     * Long press anywhere on an item (like Photos or Gmail). Until the finger is held,
     * the list scrolls as usual.
     */
    public data class LongPress(
        val durationMillis: Long = 300,
        override val `when`: ActivationWhen = ActivationWhen.Always,
    ) : SwipeSelectActivation()

    /**
     * Swipe across the list (like Photos in select mode): moving along the list
     * scrolls, moving across it selects.
     */
    public data class Pan(
        override val `when`: ActivationWhen = ActivationWhen.SelectionMode,
    ) : SwipeSelectActivation()
}

/** Auto-scroll near the edges of the list. */
@Immutable
public data class SwipeSelectAutoScroll(
    val enabled: Boolean = true,
    /** Size of the zone near the edge that triggers auto-scroll. */
    val edgeSize: Dp = 60.dp,
    /** Speed at the inner border of the zone, dp per second. */
    val minSpeed: Dp = 120.dp,
    /** Speed at the very edge, dp per second. */
    val maxSpeed: Dp = 900.dp,
) {
    public companion object {
        /** No auto-scroll. */
        public val Disabled: SwipeSelectAutoScroll = SwipeSelectAutoScroll(enabled = false)
    }
}

/** What the current gesture does. */
public enum class DragMode { Select, Deselect }

/** Where a selection change came from. */
public enum class SelectionChangeReason { Gesture, Toggle, Api }

/** Passed to `onDragStart` / `onItemEnter` / `onDragEnd`. */
public data class SwipeSelectEvent<K>(
    /** Item under the finger. */
    val index: Int,
    val key: K,
    val mode: DragMode,
    /** Selection at the moment of the event. */
    val selected: Set<K>,
)
