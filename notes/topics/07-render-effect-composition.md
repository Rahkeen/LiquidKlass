# Topic 7: RenderEffect Composition — Building Frosted Glass

A plain Gaussian blur looks clinical and flat. The "frosted glass" aesthetic — the look you see in iOS sheets, macOS menus, and modern Android UIs — comes from layering several effects on top of the blur. On API 31+, all of this is done by chaining `RenderEffect` objects into a single GPU pipeline.

---

## The Full Pipeline

```
Background content (scaled down)
        ↓
1. Gaussian blur          createBlurEffect(radiusX, radiusY, CLAMP)
        ↓
2. Noise overlay          createBlendModeEffect(noiseEffect, blurEffect, SOFT_LIGHT)
        ↓
3. Color tint             createColorFilterEffect(BlendModeColorFilter(...), noiseEffect)
        ↓
4. Alpha mask (optional)  createBlendModeEffect(maskEffect, tintEffect, DST_IN)
        ↓
Final RenderEffect → set on GraphicsLayer
```

Each step wraps the previous one. The GPU executes them in sequence.

---

## Step 1: Gaussian Blur

```kotlin
val blurEffect = RenderEffect.createBlurEffect(
    radiusX = blurRadiusPx,
    radiusY = blurRadiusPx,
    tileMode = Shader.TileMode.CLAMP
)
```

This is the foundation. Everything else is applied on top of the blurred result.

---

## Step 2: Noise Texture Overlay

Blur alone produces smooth, banded gradients. A noise texture breaks up those gradients and adds the micro-texture that makes frosted glass feel physical.

```kotlin
// 1. Load the noise bitmap (once, cached globally)
val noiseBitmap = BitmapFactory.decodeResource(resources, R.drawable.haze_noise)

// 2. Wrap it in a repeating shader
val noiseShader = BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

// 3. Turn the shader into a RenderEffect
val noiseEffect = RenderEffect.createShaderEffect(noiseShader)

// 4. Blend noise on top of blur using SOFT_LIGHT
val withNoise = RenderEffect.createBlendModeEffect(
    dst = noiseEffect,   // noise is applied on top
    src = blurEffect,    // blur is underneath
    blendMode = BlendMode.SOFT_LIGHT
)
```

`SOFT_LIGHT` is a photographic blend mode: it dodges highlights and burns shadows based on the noise pattern, producing a subtle texture without washing out the colors underneath.

The `noiseFactor` (0–1) controls opacity/intensity — set it via a `ColorMatrixColorFilter` on the noise layer or by scaling the noise shader's alpha.

---

## Step 3: Color Tint

A translucent color layer is composited over the blur+noise to shift the hue toward white, black, or a brand color:

```kotlin
val tintColor = Color.argb(80, 255, 255, 255)  // 30% white

val withTint = RenderEffect.createColorFilterEffect(
    BlendModeColorFilter(tintColor, BlendMode.SRC_OVER),
    withNoise  // apply over the noise+blur result
)
```

`SRC_OVER` is standard alpha compositing — the tint is painted over the blurred background with its own alpha transparency.

For a `TintBrush` (gradient tint), the approach is similar but uses a `ShaderEffect` with a gradient shader instead of a flat `ColorFilter`.

---

## Step 4: Alpha Mask (Shape / Gradient Clipping)

To clip the blur to a non-rectangular shape or fade it out at the edges, an alpha mask is applied last:

```kotlin
// maskShader defines the shape — pixels with alpha=0 are removed, alpha=1 are kept
val maskEffect = RenderEffect.createShaderEffect(maskShader)

val withMask = RenderEffect.createBlendModeEffect(
    dst = withTint,      // the blur+noise+tint result
    src = maskEffect,    // the mask defines what's visible
    blendMode = BlendMode.DST_IN
)
```

`DST_IN` keeps the destination (blur) only where the source (mask) has non-zero alpha. This is the standard alpha-masking blend mode.

The mask shader can be:
- A `RadialGradientShader` for circular blur shapes
- A `LinearGradientShader` for directional fade-in/out
- A custom `RuntimeShader` for complex shapes

---

## Complete Assembly

```kotlin
fun buildFrostedGlassEffect(
    blurRadius: Float,
    tintColor: Int,
    noiseBitmap: Bitmap,
    maskShader: Shader?
): RenderEffect {
    // 1. Blur
    var effect: RenderEffect = RenderEffect.createBlurEffect(
        blurRadius, blurRadius, Shader.TileMode.CLAMP
    )

    // 2. Noise
    val noiseShader = BitmapShader(noiseBitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    val noiseEffect = RenderEffect.createShaderEffect(noiseShader)
    effect = RenderEffect.createBlendModeEffect(noiseEffect, effect, BlendMode.SOFT_LIGHT)

    // 3. Tint
    effect = RenderEffect.createColorFilterEffect(
        BlendModeColorFilter(tintColor, BlendMode.SRC_OVER),
        effect
    )

    // 4. Mask (optional)
    if (maskShader != null) {
        val maskEffect = RenderEffect.createShaderEffect(maskShader)
        effect = RenderEffect.createBlendModeEffect(effect, maskEffect, BlendMode.DST_IN)
    }

    return effect
}
```

---

## BlendMode Reference for This Pipeline

| BlendMode | Used for | Effect |
|-----------|----------|--------|
| `SOFT_LIGHT` | Noise overlay | Adds texture via photographic dodge/burn |
| `SRC_OVER` | Tint | Standard alpha compositing — tint on top of blur |
| `DST_IN` | Mask | Keeps blur only where mask has alpha |

---

## Caching the Pipeline

Because `RenderEffect` objects are expensive to create and this pipeline has 3–4 layers, caching is especially important here. Key the cache on: blur radius, tint color, blend mode, noise factor, mask shape. If none of those change, reuse the cached `RenderEffect` (see Topic 9).

---

## What to Read Next

- **Topic 9: RenderEffect Caching** — `LruCache` pattern for the full effect pipeline
- **Topic 2: RenderEffect** — the underlying API used throughout this pipeline
