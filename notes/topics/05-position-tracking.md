# Topic 5: Position Tracking

The blur effect node needs to know *which portion* of the background it's sitting over. Without accurate position data, the effect would always sample the top-left corner of the background layer instead of the region that's actually behind the overlay.

---

## The Problem

The source and effect nodes are in different parts of the composition tree. They may be siblings, cousins, or even in different windows (e.g., a `Dialog`). Neither node has a direct reference to the other's layout position.

```
Root
├── Box (source — background content, position: 0,0)
│     └── LazyColumn
└── Box (effect — blur overlay, position: 0, 600)
      └── BottomSheet content
```

When the effect node draws the blurred background, it needs to know: "I am at y=600, so I should show the portion of the background starting at y=600."

---

## LayoutCoordinates

Compose exposes layout position through `LayoutCoordinates`, available via `onGloballyPositioned` or inside a `LayoutModifierNode`/`GlobalPositionAwareModifierNode`.

```kotlin
Modifier.onGloballyPositioned { coordinates ->
    val positionInRoot: Offset = coordinates.positionInRoot()
    val positionOnScreen: Offset = coordinates.positionOnScreen()
    val size: IntSize = coordinates.size
}
```

Both the source and effect nodes capture their own `LayoutCoordinates`. The effect node subtracts its position from the source's position to get the correct sampling offset.

---

## Three Strategies

Haze supports three coordinate strategies, selected based on where the source and effect live:

### Local (positionInRoot)

```kotlin
val offset = effectCoords.positionInRoot() - sourceCoords.positionInRoot()
```

Works when both nodes are in the **same window**. Most common case — a bottom navigation bar blurring a list in the same activity.

### Screen (positionOnScreen)

```kotlin
val offset = effectCoords.positionOnScreen() - sourceCoords.positionOnScreen()
```

Required when the effect is in a **different window** — e.g., a `Dialog` or `Popup`. `positionInRoot()` gives coordinates relative to each window's root, which aren't comparable across windows.

### Auto (default)

Starts with Local strategy. If the source and effect are detected to be in different windows (via `LocalView.windowId` comparison), it promotes itself to Screen strategy automatically.

---

## Using the Offset

Once you have the offset, you use it when drawing the background layer in the effect node:

```kotlin
override fun ContentDrawScope.draw() {
    // Translate the canvas so we draw the correct portion of the background
    translate(left = -offset.x, top = -offset.y) {
        drawLayer(backgroundLayer)
    }
}
```

The translation shifts the background layer so that the portion visible through the effect composable's bounds matches what's physically behind it on screen.

---

## Scaled Layer Offset Adjustment

When the source layer is scaled down for performance (see Topic 6), the offset must be scaled too:

```kotlin
val scaledOffset = offset * scaleFactor
translate(left = -scaledOffset.x, top = -scaledOffset.y) {
    scale(scaleFactor) {
        drawLayer(backgroundLayer)
    }
}
```

Getting this math wrong produces the most visually obvious bugs — the blurred content will be misaligned with what's actually behind the overlay.

---

## Window Detection

To detect cross-window scenarios, Haze reads `LocalView.current.windowId` at both the source and effect sites. If the IDs differ, the nodes are in different windows.

```kotlin
val windowId = LocalView.current.windowId
// Pass this into your modifier node via a CompositionLocal or parameter
```

This is an Android-specific detail — `windowId` is a property of the `View` that Compose renders into.

---

## Position Updates

Position can change without recomposition — e.g., the effect node is in an animated bottom sheet that slides up, changing its `y` position every frame. You need to update the offset on every frame, not just on recomposition.

This is typically handled by combining:
- `onGloballyPositioned` for initial and post-recomposition updates
- `ViewTreeObserver.addOnPreDrawListener` to catch per-frame position changes (see Topic 8: Dirty Tracking)

---

## What to Read Next

- **Topic 6: Input Scaling** — why offsets need to be scaled along with the layer
- **Topic 8: Dirty Tracking** — how position changes trigger redraws frame-by-frame
