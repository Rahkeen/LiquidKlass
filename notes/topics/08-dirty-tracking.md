# Topic 8: Dirty Tracking & Invalidation

Recomposition is not enough to keep blur up to date. The most common blur scenario — a toolbar blurring a scrolling list — involves content that changes *every frame* without triggering any recomposition at all. Haze solves this with a frame-level dirty tracking system layered on top of Compose's draw pipeline.

---

## The Problem

In Compose, drawing is triggered by:
1. **Recomposition** — state changes → new composition → redraw
2. **Animation** — animated values advance → redraw
3. **Explicit invalidation** — calling `invalidate()` on a draw node → redraw

A scrolling `LazyColumn` behind your blur overlay redraws its own content every frame via the scroll animation path. But the blur *overlay* is a sibling node — it has no knowledge that the background changed. Unless something explicitly invalidates the effect node, it will continue drawing the same stale blurred snapshot.

---

## The Fix: OnPreDrawListener

Haze registers a `ViewTreeObserver.OnPreDrawListener` on the host `View`. This listener fires **before every frame is drawn** in the Android View system:

```kotlin
val preDrawListener = ViewTreeObserver.OnPreDrawListener {
    // Check if anything relevant changed
    if (isDirty()) {
        invalidateDraw()  // tell Compose to redraw this node
    }
    true  // return true to proceed with drawing
}

view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
```

This hooks into the Android draw loop at the View level, which runs every frame regardless of Compose state changes. It's the bridge between the View rendering pipeline and Compose's draw node.

---

## Invalidate Selectively

You don't want to invalidate every frame unconditionally — that would cause continuous redraws even when nothing changed. The goal is to invalidate *only when something actually changed*.

What can change:
- Background content (scroll position, data changes, animations)
- Effect position (animated sheet sliding up/down)
- Blur parameters (radius, tint color, noise factor)
- Layer bounds/size

Haze uses **bitmask dirty flags** to track exactly what changed:

```kotlin
// Conceptual dirty flag system
private var dirtyFields: Int = 0

private object DirtyField {
    const val BLUR_RADIUS   = 1 shl 0
    const val TINT_COLOR    = 1 shl 1
    const val POSITION      = 1 shl 2
    const val CONTENT       = 1 shl 3
    const val LAYER_BOUNDS  = 1 shl 4
}

fun markDirty(field: Int) { dirtyFields = dirtyFields or field }
fun isDirty(field: Int): Boolean = (dirtyFields and field) != 0
fun clearDirty() { dirtyFields = 0 }
```

Only when `dirtyFields != 0` does the node call `invalidateDraw()`.

---

## API 31 Known Issue

On API 31 specifically, `RenderEffect` doesn't automatically trigger a redraw when the input layer's content changes. Even if the background scrolls (updating the source `GraphicsLayer`), the effect node may not redraw.

Haze works around this by forcing a manual invalidation whenever the source layer is re-recorded:

```kotlin
// In HazeSourceNode, after recording:
backgroundLayer.record { drawContent() }
hazeState.areas[i].contentLayer = backgroundLayer
effectNode.invalidateDraw()  // force the effect to redraw on API 31
```

This is a known framework limitation fixed in API 32+, but worth knowing if you're targeting API 31 specifically.

---

## Listener Lifecycle Management

`OnPreDrawListener` must be registered and unregistered correctly:

```kotlin
// In your Modifier.Node:
override fun onAttach() {
    view.viewTreeObserver.addOnPreDrawListener(preDrawListener)
}

override fun onDetach() {
    view.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
}
```

Forgetting to remove the listener causes:
- Memory leaks (the listener holds a reference to your node)
- Ghost invalidations after the composable is removed from the tree
- Performance degradation from unnecessary draw passes

---

## Coordinating with Compose's Draw Loop

The interaction between `OnPreDrawListener` and Compose's draw system:

```
Android frame clock fires
        ↓
OnPreDrawListener.onPreDraw() fires
  → isDirty() check
  → if dirty: node.invalidateDraw()
        ↓
Compose schedules a redraw for the invalidated node
        ↓
ContentDrawScope.draw() runs
  → reads updated source layer
  → applies blur
  → draws result
        ↓
Frame rendered to screen
```

The listener runs *before* Compose draws, so by the time the draw pass runs, the node has already been marked invalid and Compose will include it in the draw.

---

## What Doesn't Need Dirty Tracking

Some invalidation is handled automatically by Compose and doesn't need `OnPreDrawListener`:

- **Recomposition** — blur parameters passed as state will trigger recomposition → redraw automatically
- **Animated parameters** — if blur radius or position is driven by `Animatable` or `animate*AsState`, Compose redraws automatically

`OnPreDrawListener` is specifically for cases where the *background content* changes without Compose knowing about it — which is the scrolling list scenario.

---

## What to Read Next

- **Topic 5: Position Tracking** — position changes are one of the things dirty tracking detects
- **Topic 3: Two-Node Architecture** — the source and effect nodes involved in dirty tracking
