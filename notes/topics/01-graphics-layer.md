# Topic 1: GraphicsLayer — The Foundation

Everything in backdrop blur on Android depends on `GraphicsLayer`. It is the entry point for capturing content and manipulating it off-screen before it appears on screen.

---

## What Is a GraphicsLayer?

A `GraphicsLayer` is Compose's off-screen GPU render target. It lets you:

1. **Record** drawing commands into a buffer without immediately rendering them to screen
2. **Replay** those commands later (or repeatedly) — potentially with effects applied
3. **Attach GPU effects** (like blur) to the entire recorded content as a post-process step

It is the Compose equivalent of calling `View.setLayerType(LAYER_TYPE_HARDWARE)` on a traditional Android View. Under the hood, it is backed by Android's `RenderNode`.

---

## Key Operations

### Recording content

```kotlin
val layer = rememberGraphicsLayer()

Modifier.drawWithContent {
    layer.record {
        // Everything drawn inside here is captured, not rendered to screen yet
        this@drawWithContent.drawContent()
    }
    // Now draw it to the actual canvas too (so it's visible)
    drawLayer(layer)
}
```

`layer.record { }` captures draw commands. The content is held in GPU memory and can be replayed or transformed later.

### Replaying a layer

```kotlin
drawLayer(layer) // renders the recorded content into the current draw scope
```

### Applying a RenderEffect

```kotlin
layer.renderEffect = RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
```

Any effect set on the layer is applied every time the layer is drawn. This is how blur is attached — record once, blur on every draw.

---

## CompositingStrategy.Offscreen

When you need correct alpha compositing (e.g., punching a shape out of a blurred layer using `BlendMode.DstIn`), set:

```kotlin
graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}
```

This forces the layer to render into an isolated off-screen buffer before compositing onto the screen. Without it, blend modes that depend on the layer's own alpha can produce incorrect results.

---

## Lifecycle Concern (Android-specific)

After an Activity goes through `stop → start`, a `GraphicsLayer` can lose its repaint tracking — it may stop re-recording when the underlying content changes. The fix is to listen for `Lifecycle.State.CREATED` and release/re-record the layer when the Activity restarts.

This is a practical concern if you're using `GraphicsLayer` in a long-lived node that survives Activity transitions.

---

## Mental Model

Think of `GraphicsLayer` as a **snapshot buffer**:

```
drawWithContent {
    layer.record { drawContent() }  ← takes a snapshot of what's behind
    drawLayer(layer)                ← plays it back to screen (optional)
}

// Meanwhile, somewhere else in the tree:
layer.renderEffect = blurEffect    ← snapshot is blurred when replayed
drawLayer(layer)                   ← draw the blurred snapshot here
```

The power is that the snapshot can be read by a completely different part of the composition tree — which is exactly how backdrop blur works.

---

## Key APIs

| API | Purpose |
|-----|---------|
| `rememberGraphicsLayer()` | Creates and remembers a `GraphicsLayer` tied to composition |
| `layer.record { }` | Captures draw commands into the layer |
| `drawLayer(layer)` | Renders a recorded layer into a `DrawScope` |
| `layer.renderEffect` | Attaches a `RenderEffect` (blur, etc.) to the layer |
| `CompositingStrategy.Offscreen` | Forces isolated off-screen compositing for correct blend modes |

---

## What to Read Next

- **Topic 2: RenderEffect** — what you attach to a `GraphicsLayer` to blur it
- **Topic 4: DrawModifierNode** — the draw lifecycle where `GraphicsLayer` is used
