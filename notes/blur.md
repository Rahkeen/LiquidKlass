# Android Backdrop Blur — How Haze Does It

A deep-dive into how the [Haze](https://github.com/chrisbanes/haze) library implements backdrop blur on Android, distilled for anyone who wants to build their own implementation using the same techniques.

---

## Architecture Overview

Haze splits the blur problem into two distinct roles:

| Role | Modifier | Node |
|------|----------|------|
| **Source** — the content *behind* the blur | `Modifier.haze(state)` | `HazeSourceNode` |
| **Effect** — the frosted glass overlay | `Modifier.hazeEffect(state)` | `HazeEffectNode` |

`HazeState` is the shared glue. It holds a list of `HazeArea` objects — one per source — each carrying a `GraphicsLayer` that has recorded the background content.

```
┌─────────────────────────────────────────────────────┐
│  HazeState (shared)                                 │
│    └─ areas: List<HazeArea>                         │
│         └─ contentLayer: GraphicsLayer?             │
└──────────────┬──────────────────────────────────────┘
               │
   ┌───────────┴────────────┐
   │                        │
HazeSourceNode         HazeEffectNode
(records content)      (draws blurred result)
```

---

## Step 1 — Capturing the Background (HazeSourceNode)

`HazeSourceNode` wraps its content in a `GraphicsLayer`. Every time the content changes, the layer is re-recorded. The layer is attached to a `HazeArea` and registered with `HazeState`.

**Key APIs:**
- `GraphicsLayer` — Compose's off-screen rendering container (analogous to `View.setLayerType(LAYER_TYPE_HARDWARE)`)
- `recordingComposable { ... }` / `graphicsLayer.record { ... }` — captures drawing commands without rendering to screen yet

**Android-specific lifecycle concern** (`HazeSourceNode.android.kt`):
After an Activity goes through `stop → start`, the graphics layer can lose its repaint tracking. Haze fixes this by listening to `Lifecycle.State.CREATED` and releasing/re-recording the layer.

---

## Step 2 — Applying the Blur (HazeEffectNode)

`HazeEffectNode` is a `DrawModifierNode`. During its draw pass it:

1. Calls `drawContent()` to draw what's beneath it normally.
2. Looks up the `HazeArea` layers from `HazeState`.
3. Composites those layers into a single scaled-down `GraphicsLayer` (the *content layer*).
4. Passes that layer to the active `VisualEffect` (blur) delegate.
5. Draws the blurred result on top.

---

## Step 3 — Choosing a Blur Strategy

Android has three possible blur paths, selected at runtime in `BlurVisualEffect.android.kt`:

```
API 31+ AND hardware canvas?
    └─ YES → RenderEffectBlurVisualEffectDelegate   ← preferred
    └─ NO  → blurEnabled?
                └─ YES → RenderScriptBlurVisualEffectDelegate  ← legacy fallback
                └─ NO  → ScrimBlurVisualEffectDelegate         ← color-only fallback
```

### Path A — RenderEffect (API 31+) ✅ Recommended

Uses `android.graphics.RenderEffect`, applied to a `GraphicsLayer` via `graphicsLayer.renderEffect = ...`.

**Blur creation chain:**

```kotlin
// 1. Base Gaussian blur
var effect: RenderEffect = RenderEffect.createBlurEffect(radiusX, radiusY, tileMode)

// 2. Noise texture overlay (optional)
effect = effect.blend(noiseEffect, blendMode = BlendMode.SOFTLIGHT)

// 3. Tint / color filter layers
effect = effect.withColorTint(color, blendMode)

// 4. Alpha mask (for gradient or custom shape blur)
effect = effect.withMask(maskShader, blendMode = BlendMode.DST_IN)
```

The final `RenderEffect` is set on the graphics layer. The GPU handles everything else.

**Caching:** `RenderEffect` objects are expensive to create. Haze uses an `LruCache<RenderEffectParams, RenderEffect>` (max size 50) keyed on all relevant parameters.

### Path B — RenderScript (API < 31) ⚠️ Deprecated but functional

Uses `ScriptIntrinsicBlur`, the classic pre-31 blur workaround.

**Constraints:**
- Max blur radius: **25px**. Larger radii require scaling the input down first.
- Blur runs on a background thread (async via `Channel<Unit>` + `onBufferAvailableListener`).

**Process:**
1. Draw the content `GraphicsLayer` to an `ImageReader` `Surface` via `lockHardwareCanvas()`.
2. Feed the resulting bitmap into `ScriptIntrinsicBlur`.
3. Read back the blurred bitmap and paint it into the destination layer.

```kotlin
val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
script.setRadius(blurRadius.coerceAtMost(25f))
script.setInput(inputAllocation)
script.forEach(outputAllocation)
```

### Path C — Scrim (all platforms) 🟡 Color-only

When blur is disabled or unavailable, a solid or gradient color overlay is drawn instead. No actual blur occurs. Useful as a graceful degradation.

---

## Input Scaling Optimization

Before passing content to any blur path, Haze scales it down. Blur is a low-frequency effect — full-resolution input wastes GPU cycles.

| Condition | Scale factor |
|-----------|-------------|
| `blurRadius < 7.dp` | 1.0 (no scale — visible at small radii) |
| Progressive or masked blur | 0.5 |
| Default | 0.333 (1/3 resolution) |

For RenderScript, if the requested radius exceeds 25px:
```kotlin
scaleFactor *= (25f / blurRadiusPx)
blurRadiusPx = 25f
```

This keeps the blur within the API limit while preserving the visual appearance.

---

## Noise Texture

Haze overlays a noise texture on top of every blur to break up banding and add visual texture (the "frosted" feel). On Android:

- A bitmap `R.drawable.haze_noise` is decoded once and cached globally.
- It's wrapped in a `BitmapShader` with `REPEAT` tile mode.
- The shader is applied as a `RenderEffect` layer blended with `SRC_IN` and `SOFTLIGHT`.
- `noiseFactor` (0–1) controls the alpha/intensity of the noise layer.

---

## Progressive (Gradient) Blur

Gradient blur fades the blur in/out across the composable — e.g. sharp at top, fully blurred at bottom.

Three approaches depending on API level:

### API 33+ — Runtime Shaders (SkSL)
Two-pass Gaussian blur using `RuntimeShader` (exposed in Compose as `ShaderBrush`):
1. Horizontal blur pass with gradient-weighted radius
2. Vertical blur pass with gradient-weighted radius

The SkSL shader reads a mask texture to determine blur intensity at each pixel.

```glsl
// Conceptual GLSL/SkSL for one axis
uniform shader content;
uniform shader mask;
uniform float blurRadius;

half4 main(float2 coord) {
    float intensity = mask.eval(coord).a;
    float radius = blurRadius * intensity;
    // ... sample content in gaussian kernel of `radius`
}
```

### API 31–32 — Multi-Layer Approximation
Renders the content multiple times (default step height: 64dp per segment), each with an interpolated blur radius. Layers are composited together. Visually convincing but more draw calls.

### Fallback — Alpha Mask
Converts the `HazeProgressive` gradient into a `Brush` alpha mask and applies it via `BlendMode.DST_IN` after blurring. The blur radius is uniform but the opacity fades — simpler, cheaper.

---

## Color Effects

`HazeColorEffect` lets you tint the blurred output. Three types:

| Type | Description |
|------|-------------|
| `TintColor` | Solid color + blend mode |
| `TintBrush` | Gradient or pattern brush tint |
| `ColorFilter` | Compose `ColorFilter` (matrix, lighting, etc.) |

Each is applied as an additional `RenderEffect` layer stacked on top of the blur.

---

## Position Tracking

The effect node needs to know *where* the source content is relative to itself, in order to sample the right portion of the background. Haze supports three strategies:

| Strategy | API used | When |
|----------|----------|------|
| `Local` | `LayoutCoordinates.positionInRoot()` | Same window, simple layouts |
| `Screen` | `LayoutCoordinates.positionOnScreen()` | Cross-window, `Dialog`, `Popup` |
| `Auto` | Starts at Local, promotes to Screen | Default |

`LocalView.windowId` is used to detect when source and effect are in different windows, triggering auto-promotion to Screen strategy.

---

## Dirty Tracking & Invalidation

Recomposition alone doesn't trigger redraws when the *background content* changes (e.g., a scrolling list behind the blur). Haze handles this with:

- **`HazeArea.preDrawListeners`** — registered via `ViewTreeObserver.addOnPreDrawListener`. Every frame before drawing, the effect node checks if it needs to invalidate.
- **Bitmask dirty flags** — `BlurDirtyFields` and internal HazeEffectNode flags track exactly what changed (radius, color, position, area offsets, etc.) to minimize unnecessary work.

On API 31, there's a known issue where `RenderEffect` doesn't trigger redraws automatically, so Haze forces a manual invalidation.

---

## Key Compose APIs Summary

| API | Purpose |
|-----|---------|
| `GraphicsLayer` | Off-screen render target. Records draw commands, can have `RenderEffect` applied. |
| `graphicsLayer.renderEffect` | Sets the `android.graphics.RenderEffect` on a layer (API 31+) |
| `DrawModifierNode` | Modifier node that participates in the draw pass |
| `ContentDrawScope.drawContent()` | Draws the composable's own content in a custom draw modifier |
| `ContentDrawScope.drawLayer(layer)` | Renders a pre-recorded `GraphicsLayer` |
| `CompositingStrategy.Offscreen` | Forces off-screen rendering for correct alpha blending with masks |
| `BlendMode` | Compositing operators — `DstIn` for masking, `SrcOver` for layering, `Softlight` for noise |
| `Brush` / `ShaderBrush` | Used for gradient masks and custom shader effects |
| `RuntimeShader` | SkSL GPU shader (API 33+), used for per-pixel gradient blur |

---

## Building Your Own: Minimal Android Backdrop Blur

Here's the conceptual minimum to reproduce Haze's core trick on API 31+:

```kotlin
// 1. Record the background into a GraphicsLayer
val backgroundLayer = rememberGraphicsLayer()

Box(
    Modifier.drawWithContent {
        // Record background content
        backgroundLayer.record { this@drawWithContent.drawContent() }
        // Draw it normally too
        drawLayer(backgroundLayer)
    }
)

// 2. In your overlay composable, read that layer and blur it
Box(
    Modifier.drawWithContent {
        // Draw the captured background, blurred
        val blurredLayer = rememberGraphicsLayer().apply {
            record { drawLayer(backgroundLayer) }
            renderEffect = RenderEffect
                .createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
        }
        drawLayer(blurredLayer)

        // Draw overlay tint
        drawRect(Color.White.copy(alpha = 0.3f))

        // Draw own content on top
        drawContent()
    }
)
```

**Real-world additions you'll likely need:**
- Position offset math (where in the background layer does your overlay sit?)
- Input scaling before blur (pass a 1/3-size layer for performance)
- Noise texture overlay for the frosted look
- API level checks and RenderScript fallback for pre-31

---

## Key Files in This Repo

| File | What it does |
|------|-------------|
| `haze/src/commonMain/.../Haze.kt` | `HazeState`, `HazeArea` data model |
| `haze/src/commonMain/.../HazeEffectNode.kt` | Core draw pipeline (565 lines) |
| `haze/src/commonMain/.../HazeSourceNode.kt` | Background capture node |
| `haze-blur/src/androidMain/.../BlurVisualEffect.android.kt` | Delegate selection logic |
| `haze-blur/src/androidMain/.../RenderEffectBlurVisualEffectDelegate.android.kt` | Progressive blur for API 31–32 |
| `haze-blur/src/androidMain/.../RenderScriptBlurVisualEffectDelegate.kt` | Pre-31 RenderScript path |
| `haze-blur/src/androidMain/.../RenderScriptContext.kt` | RenderScript wrapper |
| `haze-blur/src/androidMain/.../BlurRenderEffect.android.kt` | Noise texture implementation |
| `haze-blur/src/commonMain/.../BlurRenderEffect.kt` | `RenderEffect` creation & chaining |
| `haze-blur/src/commonMain/.../BlurVisualEffectUtils.kt` | `LruCache` for `RenderEffect` objects |
| `haze-blur/src/commonMain/.../HazeProgressive.kt` | Gradient blur configuration |
| `haze-blur/src/commonMain/.../BlurHelpers.kt` | Scaled content layer creation, scrim drawing |
| `haze/src/androidMain/.../HazeSourceNode.android.kt` | Activity lifecycle layer cleanup |
| `haze/src/androidMain/.../Utils.android.kt` | Window ID tracking |
