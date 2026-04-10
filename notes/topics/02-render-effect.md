# Topic 2: RenderEffect — The Blur Primitive

`android.graphics.RenderEffect` (API 31+) is the Android framework API that performs GPU-accelerated blur. It replaces the pre-31 RenderScript path entirely and is the foundation of all blur work on modern Android.

---

## What Is RenderEffect?

`RenderEffect` is an immutable descriptor that tells the GPU how to post-process a rendered layer. It is not a live operation — you construct it, attach it to a `GraphicsLayer`, and the GPU applies it every time that layer is drawn.

Key properties:
- **Immutable** — create a new one when parameters change
- **GPU-native** — runs entirely on the GPU, no CPU readback
- **Composable** — multiple effects can be chained/blended together

---

## Creating a Gaussian Blur

```kotlin
val blurEffect = RenderEffect.createBlurEffect(
    radiusX = 25f,       // horizontal blur radius in pixels
    radiusY = 25f,       // vertical blur radius in pixels
    tileMode = Shader.TileMode.CLAMP  // edge behavior
)
```

Attach it to a `GraphicsLayer`:

```kotlin
layer.renderEffect = blurEffect
```

That's the minimal blur. Everything else is layered on top.

---

## Shader.TileMode — Edge Behavior

Controls what happens when the blur kernel samples pixels outside the layer bounds.

| Mode | Behavior | Use case |
|------|----------|----------|
| `CLAMP` | Extends the edge pixel color outward | Most blur scenarios — avoids dark/transparent borders |
| `REPEAT` | Tiles the content | Repeating textures |
| `MIRROR` | Mirrors and tiles | Symmetric tiling |
| `DECAL` | Transparent outside bounds | When you want hard edges |

For backdrop blur, `CLAMP` is almost always correct.

---

## Chaining RenderEffects

`RenderEffect` objects can be composed. Each wraps the previous, forming a pipeline:

```kotlin
// 1. Start with blur
var effect: RenderEffect = RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)

// 2. Blend a noise texture on top (for frosted glass texture)
effect = RenderEffect.createBlendModeEffect(noiseEffect, effect, BlendMode.SOFT_LIGHT)

// 3. Apply a color tint
effect = RenderEffect.createColorFilterEffect(
    BlendModeColorFilter(Color.argb(80, 255, 255, 255), BlendMode.SRC_OVER),
    effect
)

// 4. Apply an alpha mask (for shaped or gradient blur)
effect = RenderEffect.createBlendModeEffect(maskEffect, effect, BlendMode.DST_IN)
```

Each call takes the previous `effect` as its input, so the pipeline runs in order: blur → noise → tint → mask.

---

## Other RenderEffect Factory Methods

| Factory | What it does |
|---------|-------------|
| `createBlurEffect(rx, ry, tileMode)` | Gaussian blur |
| `createBitmapEffect(bitmap)` | Renders a bitmap as an effect layer |
| `createColorFilterEffect(filter, input)` | Applies a `ColorFilter` (matrix, blend mode color, etc.) |
| `createBlendModeEffect(dst, src, blendMode)` | Composites two effects with a blend mode |
| `createShaderEffect(shader)` | Applies a custom `RuntimeShader` (API 33+) |
| `createOffsetEffect(offsetX, offsetY, input)` | Shifts the effect output |
| `createChainEffect(outer, inner)` | Sequences two effects |

---

## Why Immutability Matters (and Why You Cache)

`RenderEffect` objects are **expensive to create** — constructing one involves GPU state setup. Since blur parameters can change on every animation frame, naively creating a new `RenderEffect` each frame will cause performance problems.

The solution is a cache:

```kotlin
val cache = LruCache<BlurParams, RenderEffect>(50)

fun getOrCreate(params: BlurParams): RenderEffect {
    return cache.get(params) ?: buildRenderEffect(params).also { cache.put(params, it) }
}
```

Key the cache on all parameters that affect the effect: radius, tint color, blend mode, noise factor, etc.

---

## BlendMode Primer (for RenderEffect chaining)

| BlendMode | Formula | Use in blur |
|-----------|---------|-------------|
| `SRC_OVER` | src + dst*(1-src.a) | Standard alpha compositing, tint layers |
| `DST_IN` | dst * src.a | Alpha masking — clips dst to the shape of src |
| `SOFT_LIGHT` | Complex dodge/burn | Noise texture overlay — adds texture without washing out |

---

## Attach to a Layer vs. Attach to a Composable

Two ways to use `RenderEffect` in Compose:

```kotlin
// Option A: Via GraphicsLayer (more control, explicit)
val layer = rememberGraphicsLayer()
layer.renderEffect = myBlurEffect
drawLayer(layer)

// Option B: Via graphicsLayer modifier (simpler, but less control over recording)
Box(
    Modifier.graphicsLayer {
        renderEffect = myBlurEffect
    }
)
```

For backdrop blur you'll use Option A — you need explicit control over when content is recorded and when it's replayed with blur applied.

---

## What to Read Next

- **Topic 7: RenderEffect Composition** — full frosted glass pipeline (blur + noise + tint + mask)
- **Topic 9: Caching** — `LruCache` pattern for `RenderEffect` objects
