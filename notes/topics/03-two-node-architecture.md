# Topic 3: The Two-Node Architecture — Source vs. Effect

Backdrop blur has an inherent challenge: the composable that *shows* the blur (your toolbar, bottom sheet, etc.) is structurally separate from the content *being blurred* behind it. They are different nodes in the composition tree with no direct parent-child relationship.

Haze solves this with a two-node architecture connected by shared state.

---

## The Three Parts

```
┌─────────────────────────────────────────────────────┐
│  HazeState (shared mutable state)                   │
│    └─ areas: List<HazeArea>                         │
│         └─ contentLayer: GraphicsLayer?             │
└──────────────┬──────────────────────────────────────┘
               │ shared reference
   ┌───────────┴────────────┐
   │                        │
HazeSourceNode         HazeEffectNode
(captures content)     (draws blurred result)
```

### HazeState
Shared glue. Holds a list of `HazeArea` objects. Each area carries a `GraphicsLayer` that has recorded some background content. Both nodes hold a reference to the same `HazeState` instance.

### HazeSourceNode (Source)
Attached to the background content — the scrolling list, the image, whatever is *behind* the blur. Its job:
- Wrap its content in a `GraphicsLayer`
- Re-record the layer whenever the content changes
- Register that layer in `HazeState`

It does **not** know anything about where the blur overlay is.

### HazeEffectNode (Effect)
Attached to the blur overlay — the toolbar, bottom sheet, etc. Its job:
- Read the `GraphicsLayer`(s) from `HazeState`
- Composite them (scaled down, if needed)
- Apply `RenderEffect` blur
- Draw the blurred result under its own content

It does **not** know anything about what the source content is.

---

## Why This Design?

In a typical layout, your blur overlay sits *above* the background in the Z-order but is a sibling or cousin in the tree — not a descendant. There's no direct way for the overlay to read pixels from behind itself.

The two-node pattern sidesteps this by having the source node proactively capture its content into a shared `GraphicsLayer`. The effect node just reads from that shared state. No pixel readback, no bespoke communication — just shared state.

---

## Data Flow

```
[Background content changes]
        ↓
HazeSourceNode.record()
  → graphicsLayer.record { drawContent() }
  → hazeState.areas[i].contentLayer = graphicsLayer
        ↓
[Next draw frame]
        ↓
HazeEffectNode.draw()
  → reads hazeState.areas
  → composites content layers into a single scaled layer
  → applies RenderEffect blur
  → drawLayer(blurredLayer)
  → drawContent()  ← draws the overlay's own children on top
```

---

## Minimal Implementation Sketch

```kotlin
// Shared state
val hazeState = remember { HazeState() }

// 1. Source — wrap background content
Box(
    Modifier.drawWithContent {
        hazeState.backgroundLayer.record { drawContent() }
        drawLayer(hazeState.backgroundLayer)
    }
) {
    // ... background content (list, image, etc.)
}

// 2. Effect — the blur overlay
Box(
    Modifier.drawWithContent {
        val layer = hazeState.backgroundLayer
        layer.renderEffect = RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
        drawLayer(layer)       // blurred background
        drawContent()          // overlay's own content on top
    }
) {
    // ... toolbar, bottom sheet content, etc.
}
```

The real implementation adds position offset math, scaling, effect chaining, and dirty tracking — but the core data flow is exactly this.

---

## HazeArea

`HazeArea` is the per-source data container inside `HazeState`. It holds:
- The `GraphicsLayer` with recorded content
- The layout bounds/position of the source (for offset math in the effect node)
- Any per-area blur parameters (if you want different blur strengths per source)

This design supports **multiple sources** — e.g., a screen where several different background regions need to be independently captured and composited together before blurring.

---

## Compose Modifier Nodes vs. Modifier.composed

Haze uses `Modifier.Node` (specifically `DrawModifierNode`) rather than the older `Modifier.composed` pattern. Key difference:

- `Modifier.Node` nodes are allocated once and reused across recompositions
- `Modifier.composed` creates a new lambda scope on every recomposition
- For something that runs every draw frame and holds references to `GraphicsLayer` objects, `Modifier.Node` is the correct choice

---

## What to Read Next

- **Topic 4: DrawModifierNode** — how `HazeEffectNode` hooks into the draw pass
- **Topic 5: Position Tracking** — how the effect node knows which portion of the background to show
- **Topic 8: Dirty Tracking** — how the effect node knows when to redraw
