# Topic 6: Input Scaling

Before passing the captured background layer to the blur pipeline, Haze scales it down. This is a deliberate performance optimization that exploits a perceptual property of blur: it's a low-frequency effect that doesn't need full-resolution input.

---

## Why Scale Down?

Gaussian blur is computationally expensive — it samples many neighboring pixels for every output pixel. The cost scales with both the resolution of the input and the blur radius.

Critically, blur destroys high-frequency detail by design. Running it on a full-resolution image wastes GPU work on detail that the blur will immediately discard. A 1/3-resolution input produces a visually indistinguishable result at a fraction of the cost.

---

## Haze's Scale Factors

| Condition | Scale factor |
|-----------|-------------|
| `blurRadius < 7.dp` | 1.0 (no scaling) |
| Progressive/gradient blur | 0.5 |
| Default | 0.333 (1/3 resolution) |

The small-radius exception exists because at very small blur radii, the detail being blurred is coarse enough that scaling artifacts become visible. At large radii, those artifacts are themselves blurred away.

---

## How Scaling Is Applied

The background `GraphicsLayer` is drawn into a new, smaller layer before blur is applied:

```kotlin
// Create a content layer at reduced size
val scaledSize = IntSize(
    (originalSize.width * scaleFactor).roundToInt(),
    (originalSize.height * scaleFactor).roundToInt()
)

val contentLayer = graphicsLayerPool.acquire(scaledSize)

contentLayer.record {
    scale(scaleFactor) {
        drawLayer(backgroundLayer)
    }
}

// Now apply blur to the small layer
contentLayer.renderEffect = RenderEffect.createBlurEffect(
    blurRadiusPx * scaleFactor,
    blurRadiusPx * scaleFactor,
    Shader.TileMode.CLAMP
)
```

The blur radius is also scaled down proportionally, since the layer is now smaller.

---

## Blur Radius Scaling

This is easy to get wrong. If you scale the input by 0.333 but keep the blur radius in original pixel space, the effective blur will be 3x too strong.

```kotlin
// Scale the radius along with the input
val scaledRadius = blurRadiusPx * scaleFactor

val blurEffect = RenderEffect.createBlurEffect(scaledRadius, scaledRadius, TileMode.CLAMP)
```

The GPU applies the radius in the coordinate space of the layer it's operating on. Scale the layer → scale the radius.

---

## Drawing the Scaled Layer Back

When drawing the scaled layer into the effect composable's draw scope, you scale it back up:

```kotlin
// The content layer is 1/3 size — scale it back up when drawing
scale(1f / scaleFactor, pivot = Offset.Zero) {
    translate(-scaledOffset.x, -scaledOffset.y) {
        drawLayer(contentLayer)
    }
}
```

The GPU stretches the blurred small image back to full size. The blur smooths over any upscaling artifacts.

---

## GraphicsLayer Pooling

Creating a new `GraphicsLayer` on every draw frame is expensive. In practice you want to:

- Allocate the scaled content layer once
- Reuse it across frames
- Only reallocate if the size changes (e.g., window resize, orientation change)

Haze uses an internal pool/cache for this. At minimum, `rememberGraphicsLayer()` in Compose handles this for you if you're recording in a composable context.

---

## Visual Impact at Different Scale Factors

| Scale | Quality | Performance |
|-------|---------|-------------|
| 1.0 | Reference | Baseline (most expensive) |
| 0.5 | Indistinguishable at radii > ~10dp | ~4x cheaper (1/4 pixel count) |
| 0.333 | Indistinguishable at radii > ~7dp | ~9x cheaper (1/9 pixel count) |
| 0.25 | May show softness artifacts | ~16x cheaper |

The 1/3 default is a well-tuned balance. For your own implementation, it's worth testing scale factors against your specific content and blur radii — the right value may differ.

---

## Summary

The scaling pipeline looks like:

```
Full-res background layer
        ↓ draw at scaleFactor (e.g. 0.333)
Scaled-down content layer (1/3 resolution)
        ↓ apply RenderEffect with scaled radius
Blurred scaled layer
        ↓ draw at 1/scaleFactor (upscale back to full size)
Blurred full-size result
```

---

## What to Read Next

- **Topic 5: Position Tracking** — offsets must be scaled alongside the layer
- **Topic 7: RenderEffect Composition** — what gets applied to the scaled layer
