package io.github.mironlive.swipeselect

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Adds swipe-to-select to a `LazyColumn` / `LazyRow`. Put it on the list's `modifier`
 * and pass the same [listState].
 *
 * ```
 * val listState = rememberLazyListState()
 * val selection = rememberSwipeSelectState<Long>()
 * LazyColumn(
 *     state = listState,
 *     modifier = Modifier.swipeSelect(selection, listState, keyForIndex = { mails.getOrNull(it)?.id }),
 * ) { items(mails, key = { it.id }) { … } }
 * ```
 *
 * @param keyForIndex stable key of the item at a list index, or `null` for items that
 * can't be selected (headers). The selection holds keys, so it survives sorting and pagination.
 * @param activation how the gesture starts. Default: the edge strip in selection mode.
 */
public fun <K> Modifier.swipeSelect(
    state: SwipeSelectState<K>,
    listState: LazyListState,
    keyForIndex: (Int) -> K?,
    activation: List<SwipeSelectActivation> = listOf(SwipeSelectActivation.Edge()),
    autoScroll: SwipeSelectAutoScroll = SwipeSelectAutoScroll(),
    enabled: Boolean = true,
    isSelectable: ((Int) -> Boolean)? = null,
    onDragStart: ((SwipeSelectEvent<K>) -> Unit)? = null,
    onItemEnter: ((SwipeSelectEvent<K>) -> Unit)? = null,
    onDragEnd: ((SwipeSelectEvent<K>) -> Unit)? = null,
): Modifier = swipeSelectImpl(
    LazyListLayout(listState), state, keyForIndex, activation, autoScroll, enabled,
    isSelectable, onDragStart, onItemEnter, onDragEnd,
)

/**
 * Adds swipe-to-select to a `LazyVerticalGrid` / `LazyHorizontalGrid`. Selection is
 * linear by index, like in Photos.
 */
public fun <K> Modifier.swipeSelect(
    state: SwipeSelectState<K>,
    gridState: LazyGridState,
    keyForIndex: (Int) -> K?,
    activation: List<SwipeSelectActivation> = listOf(SwipeSelectActivation.Edge()),
    autoScroll: SwipeSelectAutoScroll = SwipeSelectAutoScroll(),
    enabled: Boolean = true,
    isSelectable: ((Int) -> Boolean)? = null,
    onDragStart: ((SwipeSelectEvent<K>) -> Unit)? = null,
    onItemEnter: ((SwipeSelectEvent<K>) -> Unit)? = null,
    onDragEnd: ((SwipeSelectEvent<K>) -> Unit)? = null,
): Modifier = swipeSelectImpl(
    LazyGridLayout(gridState), state, keyForIndex, activation, autoScroll, enabled,
    isSelectable, onDragStart, onItemEnter, onDragEnd,
)

// ---------------------------------------------------------------------------------------------

private class Config<K>(
    val state: SwipeSelectState<K>,
    val keyForIndex: (Int) -> K?,
    val activation: List<SwipeSelectActivation>,
    val autoScroll: SwipeSelectAutoScroll,
    val enabled: Boolean,
    val isSelectable: ((Int) -> Boolean)?,
    val onDragStart: ((SwipeSelectEvent<K>) -> Unit)?,
    val onItemEnter: ((SwipeSelectEvent<K>) -> Unit)?,
    val onDragEnd: ((SwipeSelectEvent<K>) -> Unit)?,
    val rtl: Boolean,
    val density: Float,
)

private enum class Source { Edge, LongPress, Pan }

private fun <K> Modifier.swipeSelectImpl(
    layout: LazyLayout,
    state: SwipeSelectState<K>,
    keyForIndex: (Int) -> K?,
    activation: List<SwipeSelectActivation>,
    autoScroll: SwipeSelectAutoScroll,
    enabled: Boolean,
    isSelectable: ((Int) -> Boolean)?,
    onDragStart: ((SwipeSelectEvent<K>) -> Unit)?,
    onItemEnter: ((SwipeSelectEvent<K>) -> Unit)?,
    onDragEnd: ((SwipeSelectEvent<K>) -> Unit)?,
): Modifier = composed {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current.density
    val config = rememberUpdatedState(
        Config(state, keyForIndex, activation, autoScroll, enabled, isSelectable, onDragStart, onItemEnter, onDragEnd, rtl, density),
    )
    val scope = rememberCoroutineScope()
    val drag = remember(layout) { DragSession(layout, config, scope) }
    pointerInput(drag) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture { with(drag) { gesture(slop) } }
    }
}

private class DragSession<K>(
    val layout: LazyLayout,
    val config: State<Config<K>>,
    val scope: CoroutineScope,
) {
    private var anchor = -1
    private var initial: Set<K> = emptySet()
    private var shouldSelect = true
    private var range: IndexRange? = null
    private var lastIndex = -1
    private var finger = Offset.Zero
    private var ticker: Job? = null

    private fun available(a: SwipeSelectActivation): Boolean {
        val s = config.value.state
        return when (a.`when`) {
            ActivationWhen.Always -> true
            ActivationWhen.SelectionMode -> s.isSelectionMode
            ActivationWhen.Idle -> !s.isSelectionMode
        }
    }

    private fun inStrip(p: Offset, e: SwipeSelectActivation.Edge): Boolean {
        val c = config.value
        val size = e.size.value * c.density
        val box = layout.size
        if (!layout.vertical) {
            return if (e.side == EdgeSide.Start) p.y <= size else p.y >= box.height - size
        }
        val start = (e.side == EdgeSide.Start) != c.rtl
        return if (start) p.x <= size else p.x >= box.width - size
    }

    suspend fun AwaitPointerEventScope.gesture(touchSlop: Float) {
        val c = config.value
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!c.enabled || layout.totalCount == 0) return
        val p = down.position
        val edge = c.activation.firstOrNull { it is SwipeSelectActivation.Edge && available(it) && inStrip(p, it) }
        val onItem = layout.itemAt(p, exact = true) != null
        val longPress = c.activation.firstOrNull { it is SwipeSelectActivation.LongPress && available(it) }
            as SwipeSelectActivation.LongPress?
        val pan = c.activation.firstOrNull { it is SwipeSelectActivation.Pan && available(it) }

        val source: Source = when {
            edge != null -> {
                // Claim after a few pixels — earlier than the list's touch slop.
                if (!awaitMove(down.id, 4f * c.density, PointerEventPass.Initial)) return
                Source.Edge
            }
            onItem && longPress != null -> {
                val held = withTimeoutOrNull(longPress.durationMillis) {
                    // Movement before the timeout means the user is scrolling.
                    awaitMove(down.id, touchSlop, PointerEventPass.Main)
                    false
                }
                if (held == null) {
                    if (currentEvent.changes.none { it.id == down.id && it.pressed }) return
                    Source.LongPress
                } else {
                    if (pan == null) return
                    // Fall through to pan only if the move was across the list.
                    val change = currentEvent.changes.firstOrNull { it.id == down.id } ?: return
                    if (!change.pressed || !isAcross(change.position - p)) return
                    Source.Pan
                }
            }
            onItem && pan != null -> {
                if (!awaitMove(down.id, touchSlop * 0.75f, PointerEventPass.Initial)) return
                val change = currentEvent.changes.firstOrNull { it.id == down.id } ?: return
                if (!isAcross(change.position - p)) return
                Source.Pan
            }
            else -> return
        }

        if (!begin(p, currentEvent.changes.firstOrNull { it.id == down.id }?.position ?: p)) return
        currentEvent.changes.forEach { it.consume() }
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                event.changes.forEach { it.consume() }
                if (!change.pressed) break
                finger = change.position
                process()
            }
        } finally {
            end()
        }
    }

    private fun isAcross(d: Offset): Boolean =
        if (layout.vertical) abs(d.x) > abs(d.y) else abs(d.y) > abs(d.x)

    /** Waits until the pointer moves [distance]; false if it was released first. */
    private suspend fun AwaitPointerEventScope.awaitMove(id: PointerId, distance: Float, pass: PointerEventPass): Boolean {
        val start = currentEvent.changes.firstOrNull { it.id == id }?.position ?: return false
        while (true) {
            val event = awaitPointerEvent(pass)
            val change = event.changes.firstOrNull { it.id == id } ?: return false
            if (!change.pressed) return false
            if ((change.position - start).getDistance() >= distance) return true
        }
    }

    private fun begin(start: Offset, current: Offset): Boolean {
        val c = config.value
        val index = layout.itemAt(start, exact = false) ?: return false
        val key = c.keyForIndex(index) ?: return false
        if (c.isSelectable?.invoke(index) == false) return false
        initial = c.state.selected
        shouldSelect = key !in initial
        anchor = index
        range = null
        lastIndex = -1
        finger = current
        c.state.gestureBegan()
        enter(index)
        c.onDragStart?.invoke(event(index, key))
        process()
        startTicker()
        return true
    }

    private fun event(index: Int, key: K) = SwipeSelectEvent(
        index, key, if (shouldSelect) DragMode.Select else DragMode.Deselect, config.value.state.selected,
    )

    private fun enter(index: Int) {
        val c = config.value
        val clamped = index.coerceIn(0, max(0, layout.totalCount - 1))
        if (clamped == lastIndex) return
        lastIndex = clamped
        val next = IndexRange.of(anchor, clamped)
        applyRangeTransition(initial, c.state.selected, range, next, shouldSelect, c.keyForIndex, c.isSelectable)
            ?.let { c.state.gestureUpdate(it) }
        range = next
    }

    private fun process() {
        if (anchor < 0) return
        val c = config.value
        val index = layout.itemAt(finger, exact = false) ?: return
        val before = lastIndex
        enter(index)
        if (lastIndex != before) c.keyForIndex(lastIndex)?.let { c.onItemEnter?.invoke(event(lastIndex, it)) }
    }

    private fun end() {
        ticker?.cancel()
        ticker = null
        if (anchor < 0) return
        val c = config.value
        val i = if (lastIndex >= 0) lastIndex else anchor
        anchor = -1
        range = null
        c.state.gestureEnded(initial)
        c.keyForIndex(i)?.let { c.onDragEnd?.invoke(event(i, it)) }
        initial = emptySet()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var last = 0L
            while (isActive) {
                val now = withFrameNanos { it }
                val dt = if (last == 0L) 1f / 60 else min((now - last) / 1e9f, 1f / 30)
                last = now
                tick(dt)
            }
        }
    }

    private suspend fun tick(dt: Float) {
        val c = config.value
        val a = c.autoScroll
        if (!a.enabled || anchor < 0) return
        val d = c.density
        val pos = if (layout.vertical) finger.y else finger.x
        val size = if (layout.vertical) layout.size.height else layout.size.width
        val zone = min(a.edgeSize.value * d, size / 3f)
        val minSpeed = a.minSpeed.value * d
        val maxSpeed = a.maxSpeed.value * d
        val visual = when {
            pos < zone -> -(minSpeed + (maxSpeed - minSpeed) * min(1f, (zone - pos) / zone))
            pos > size - zone -> minSpeed + (maxSpeed - minSpeed) * min(1f, (pos - (size - zone)) / zone)
            else -> 0f
        }
        if (visual == 0f) {
            c.state.updateAutoScrollDirection(0)
            return
        }
        val delta = visual * dt * (if (layout.reverse) -1 else 1)
        val consumed = layout.scrollBy(delta)
        c.state.updateAutoScrollDirection(if (abs(consumed) < 0.01f) 0 else if (visual > 0) 1 else -1)
        process()
    }
}

// ---------------------------------------------------------------------------------------------

/** Read-only view of a lazy list / grid: where items are, how to scroll. */
private interface LazyLayout {
    val totalCount: Int
    val vertical: Boolean
    val reverse: Boolean
    val size: androidx.compose.ui.unit.IntSize
    fun itemAt(p: Offset, exact: Boolean): Int?
    suspend fun scrollBy(px: Float): Float
}

/** Nearest item to [p]; exact hits win. */
private inline fun nearest(p: Offset, exact: Boolean, items: ((Int, Rect) -> Unit) -> Unit): Int? {
    var best: Int? = null
    var bestDistance = Float.MAX_VALUE
    var hit: Int? = null
    items { index, r ->
        if (hit != null) return@items
        if (r.contains(p)) {
            hit = index
            return@items
        }
        if (exact) return@items
        val dx = max(max(r.left - p.x, 0f), p.x - r.right)
        val dy = max(max(r.top - p.y, 0f), p.y - r.bottom)
        val dist = dx * dx + dy * dy
        if (dist < bestDistance) {
            bestDistance = dist
            best = index
        }
    }
    return hit ?: best
}

/** Main-axis start of an item in the layout's own coordinates. */
private fun mainStart(offset: Int, size: Int, viewportStartOffset: Int, viewport: Int, reverse: Boolean): Float {
    val fromStart = (offset - viewportStartOffset).toFloat()
    return if (reverse) viewport - fromStart - size else fromStart
}

private class LazyListLayout(private val s: LazyListState) : LazyLayout {
    override val totalCount get() = s.layoutInfo.totalItemsCount
    override val vertical get() = s.layoutInfo.orientation == Orientation.Vertical
    override val reverse get() = s.layoutInfo.reverseLayout
    override val size get() = s.layoutInfo.viewportSize

    override fun itemAt(p: Offset, exact: Boolean): Int? {
        val info = s.layoutInfo
        val v = info.orientation == Orientation.Vertical
        val viewport = if (v) info.viewportSize.height else info.viewportSize.width
        val cross = if (v) info.viewportSize.width.toFloat() else info.viewportSize.height.toFloat()
        return nearest(p, exact) { visit ->
            for (item in info.visibleItemsInfo) {
                val start = mainStart(item.offset, item.size, info.viewportStartOffset, viewport, info.reverseLayout)
                val r = if (v) Rect(0f, start, cross, start + item.size) else Rect(start, 0f, start + item.size, cross)
                visit(item.index, r)
            }
        }
    }

    override suspend fun scrollBy(px: Float) = s.scrollBy(px)
}

private class LazyGridLayout(private val s: LazyGridState) : LazyLayout {
    override val totalCount get() = s.layoutInfo.totalItemsCount
    override val vertical get() = s.layoutInfo.orientation == Orientation.Vertical
    override val reverse get() = s.layoutInfo.reverseLayout
    override val size get() = s.layoutInfo.viewportSize

    override fun itemAt(p: Offset, exact: Boolean): Int? {
        val info = s.layoutInfo
        val v = info.orientation == Orientation.Vertical
        val viewport = if (v) info.viewportSize.height else info.viewportSize.width
        return nearest(p, exact) { visit ->
            for (item in info.visibleItemsInfo) {
                val r = if (v) {
                    val top = mainStart(item.offset.y, item.size.height, info.viewportStartOffset, viewport, info.reverseLayout)
                    Rect(item.offset.x.toFloat(), top, (item.offset.x + item.size.width).toFloat(), top + item.size.height)
                } else {
                    val left = mainStart(item.offset.x, item.size.width, info.viewportStartOffset, viewport, info.reverseLayout)
                    Rect(left, item.offset.y.toFloat(), left + item.size.width, (item.offset.y + item.size.height).toFloat())
                }
                visit(item.index, r)
            }
        }
    }

    override suspend fun scrollBy(px: Float) = s.scrollBy(px)
}
