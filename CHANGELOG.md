# Changelog

## 0.1.0

First public release.

- `Modifier.swipeSelect(...)` for `LazyColumn` / `LazyRow` (`LazyListState`) and `LazyVerticalGrid` / `LazyHorizontalGrid` (`LazyGridState`)
- `SwipeSelectState` + `rememberSwipeSelectState` — selection, selection mode, `onChange` once per gesture, `sync` for outside state
- Activation: edge strip (start / end), long press, swipe across; combine several with `ActivationWhen` (`Always` / `SelectionMode` / `Idle`)
- Auto-scroll that ramps up towards the edge; `reverseLayout` (chats), horizontal lists, content padding, right-to-left
- Moving back restores what was selected before the gesture
- Non-selectable items, drag callbacks for haptics
