# Topic 4: DrawModifierNode and the Draw Pipeline

`DrawModifierNode` is the Compose API that lets a modifier participate in the draw pass. It's how `HazeEffectNode` intercepts drawing and inserts blurred content beneath the composable's own children.

---

## What Is DrawModifierNode?

A `Modifier.Node` subclass that implements `DrawModifierNode` gets a `draw()` callback that runs during the layout's draw phase. Inside that callback, you have full control over what gets drawn and in what order.

```kotlin
class MyDrawNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        // Draw whatever you want here
        // Call drawContent() to draw the composable's own children
    }
}
```

---

## ContentDrawScope

`ContentDrawScope` extends `DrawScope` with one additional method:

```kotlin
fun drawContent()
```

This draws the composable's own child content (everything in the `content` lambda of the composable this modifier is attached to) at the current point in your draw sequence.

This is what gives you control over Z-ordering — you decide when (and whether) the composable's children appear relative to your custom drawing.

---

## Draw Order Control

The order of calls inside `ContentDrawScope.draw()` determines visual layering:

```kotlin
override fun ContentDrawScope.draw() {
    // 1. Draw blurred background first (bottommost)
    drawLayer(blurredBackgroundLayer)

    // 2. Draw a translucent tint over the blur
    drawRect(Color.White.copy(alpha = 0.2f))

    // 3. Draw the composable's own children on top
    drawContent()
}
```

Result (bottom to top): blurred background → tint → composable children.

If you call `drawContent()` first, the composable's children appear *behind* your custom drawing — useful for overlays that sit on top of content.

---

## The Full HazeEffectNode Draw Sequence

What `HazeEffectNode` does inside its `draw()`:

```
1. drawContent()
   → draw the overlay composable's children normally (so layout is correct)

2. For each HazeArea in HazeState:
   → composite the source GraphicsLayer into a single scaled-down content layer

3. Apply RenderEffect blur to the content layer

4. drawLayer(blurredContentLayer)
   → draw the blurred background on top of the children from step 1

5. drawRect(tintColor)
   → draw color tint over the blur

6. (The children from step 1 now appear below the blur — which looks wrong)
```

Wait — that's not quite right. The actual approach is:

```
1. drawLayer(blurredBackgroundLayer)  ← blurred background first
2. drawRect(tintColor)               ← tint over blur
3. drawContent()                     ← overlay's own children on top of blur
```

The key insight: `drawContent()` at the end means the blur sits behind the overlay's children, which is exactly what you want for a frosted glass effect.

---

## DrawScope Capabilities

Inside `ContentDrawScope` you have access to all standard `DrawScope` operations:

```kotlin
drawRect(color, topLeft, size, alpha, blendMode)
drawLayer(graphicsLayer)
drawImage(image, srcOffset, dstOffset)
drawPath(path, paint)
drawCircle(color, radius, center)
// etc.
```

You also have `size` (the composable's measured size), `density`, and `layoutDirection` available.

---

## Modifier.Node vs. Modifier.composed

For blur specifically, `Modifier.Node` matters:

| | `Modifier.Node` | `Modifier.composed` |
|---|---|---|
| Allocation | Once, reused across recompositions | New instance every recomposition |
| State | Survives recomposition | Reset on recomposition |
| Draw performance | Skips recomposition entirely for draw-only changes | May trigger unnecessary recomposition |
| `GraphicsLayer` ownership | Safe to hold long-lived references | References may become stale |

Blur involves `GraphicsLayer` objects that need to persist between frames, so `Modifier.Node` is the right tool.

---

## Minimal DrawModifierNode Example

```kotlin
class BlurEffectNode(
    private val backgroundLayer: GraphicsLayer,
    private val blurRadius: Float
) : Modifier.Node(), DrawModifierNode {

    override fun ContentDrawScope.draw() {
        // Apply blur to the background layer
        backgroundLayer.renderEffect = RenderEffect.createBlurEffect(
            blurRadius, blurRadius, Shader.TileMode.CLAMP
        )

        // Draw blurred background
        drawLayer(backgroundLayer)

        // Draw tint
        drawRect(Color.White.copy(alpha = 0.3f))

        // Draw this composable's own content on top
        drawContent()
    }
}
```

---

## What to Read Next

- **Topic 1: GraphicsLayer** — the layer being drawn inside `drawLayer()`
- **Topic 5: Position Tracking** — computing the correct offset when drawing background layers
- **Topic 8: Dirty Tracking** — triggering redraws when background content changes without recomposition
