# Topic 9: RenderEffect Caching

`RenderEffect` objects are expensive to create. On a screen where blur parameters stay constant, this isn't a problem — you build the effect once and reuse it. But when parameters change — during animations, on scroll, when a tint color is driven by dynamic content — a naive implementation creates a new `RenderEffect` on every frame, causing jank.

---

## Why RenderEffect Is Expensive

Creating a `RenderEffect` involves:
- Allocating GPU-side state descriptors
- Validating parameter ranges
- Setting up the render graph for the effect chain

For a single Gaussian blur this is modest, but the full frosted glass pipeline (blur + noise + tint + mask) chains 3–4 `RenderEffect` objects. Doing this 60–120 times per second during an animation is measurably slow.

---

## The LruCache Pattern

Haze uses an `LruCache` keyed on all parameters that define the `RenderEffect`:

```kotlin
data class RenderEffectParams(
    val blurRadiusPx: Float,
    val tintColor: Int,
    val tintBlendMode: BlendMode,
    val noiseFactor: Float,
    val maskType: MaskType?,
    val tileMode: Shader.TileMode
)

val cache = LruCache<RenderEffectParams, RenderEffect>(maxSize = 50)

fun getOrCreateEffect(params: RenderEffectParams): RenderEffect {
    return cache[params] ?: buildEffect(params).also { cache.put(params, it) }
}
```

`maxSize = 50` means up to 50 distinct parameter combinations are cached before the least-recently-used entries are evicted.

---

## Choosing the Cache Key

The cache key must capture **every parameter that affects the output**. Missing a parameter means two visually different effects share a cache entry and you get the wrong effect shown.

Typical parameters to include in the key:
- Blur radius (X and Y if they differ)
- Tile mode
- Tint color (as ARGB int)
- Tint blend mode
- Noise factor
- Noise bitmap reference (if you support custom noise textures)
- Mask type / gradient parameters (if using alpha masks)

Avoid including anything that doesn't affect the `RenderEffect` itself — like the layer's position or the composable's size. Those affect *how* you draw the layer, not the effect attached to it.

---

## Cache Sizing

50 entries is Haze's default. The right size for your implementation depends on how many distinct parameter combinations you expect at runtime:

- Fixed blur with no animation → cache size of 1–5 is fine
- Animated blur radius with 5 discrete tint colors → cache size of ~20–50
- Fully dynamic parameters across many simultaneous blur overlays → consider 50–100

`LruCache` is memory-bounded by entry count, not memory size. `RenderEffect` objects are small on the Java heap (they're mostly GPU-side handles), so large cache sizes are not a memory concern.

---

## LruCache Basics

```kotlin
import android.util.LruCache

val cache = LruCache<Key, Value>(maxSize)

cache.put(key, value)         // insert
val value = cache[key]        // retrieve (null if not cached)
cache.remove(key)             // explicit eviction
cache.evictAll()              // clear entire cache
```

`LruCache` is thread-safe for concurrent reads and writes.

---

## When to Invalidate the Cache

`RenderEffect` objects don't hold references to mutable state — they're fully self-contained. The cache never needs to be invalidated due to underlying data changes. The only reason to clear it:

- **Noise bitmap changed** — if you allow custom noise textures, a new bitmap means a new shader, which means new effects
- **Global config change** — e.g., system font scale (affects dp-to-px conversion if radii are in dp)
- **Testing / debugging** — force a full rebuild to verify correctness

---

## Where to Scope the Cache

The cache should live at a scope that outlasts individual composable instances but doesn't leak across unrelated screens:

| Scope | Appropriate when |
|-------|-----------------|
| `companion object` / singleton | Single blur style across the entire app |
| `ViewModel` | Blur parameters tied to screen-level state |
| `CompositionLocal` | Per-subtree blur configuration |
| Local to modifier node | Acceptable if parameter space is tiny (1–2 variants) |

Haze uses a global-ish scope for the cache, which works because `RenderEffect` objects are immutable and safe to share.

---

## Combining with Dirty Tracking

The cache and dirty tracking work together:

```kotlin
override fun ContentDrawScope.draw() {
    if (isDirty(DirtyField.BLUR_PARAMS)) {
        // Parameters changed — fetch from cache (or build and cache)
        currentEffect = effectCache.getOrCreate(currentParams)
        blurLayer.renderEffect = currentEffect
        clearDirty(DirtyField.BLUR_PARAMS)
    }
    // If not dirty, currentEffect is still valid — skip cache lookup entirely
    drawLayer(blurLayer)
    drawContent()
}
```

When parameters haven't changed, you skip even the cache lookup — the already-attached `RenderEffect` is reused as-is.

---

## What to Read Next

- **Topic 8: Dirty Tracking** — how parameter changes are detected to trigger cache lookups
- **Topic 7: RenderEffect Composition** — the full pipeline that gets cached
