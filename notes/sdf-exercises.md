# Liquid Glass for Jetpack Compose — SDF Practice Exercises

A progressive set of exercises for learning SDF-based rendering in AGSL + Compose. Each exercise introduces one new concept. Build them in order; resist polishing.

## Exercise 1: Single SDF, hard edge

**Goal:** Render a black circle on white using an SDF. No smoothing, just pure inside/outside.

**Learn:** Basic AGSL setup in Compose, how `RuntimeShader` connects to `ShaderBrush`, what `fragCoord` means, the "if distance < 0, you're inside" pattern.

```glsl
float d = length(fragCoord - center) - radius;
half3 color = d < 0.0 ? half3(0.0) : half3(1.0);
```

**Success:** A pixelated black circle. The aliased edge is intentional.

---

## Exercise 2: Antialiased edge

**Goal:** Same circle, but with a smooth 1-pixel edge.

**Learn:** Why `smoothstep` matters, the relationship between SDF distance and pixel coverage.

```glsl
float d = length(fragCoord - center) - radius;
float alpha = 1.0 - smoothstep(-1.0, 1.0, d);
```

**Success:** Crisp edge instead of jagged. Try changing the smoothstep range to see it get softer or sharper.

---

## Exercise 3: Two shapes, hard union

**Goal:** A circle and a rounded rect, both black, no merging.

**Learn:** `min(d1, d2)` as SDF union, the rounded rect SDF (the workhorse SDF of UI work).

```glsl
float d1 = sdCircle(fragCoord, c1, r1);
float d2 = sdRoundedBox(fragCoord, c2, halfSize, cornerRadius);
float d = min(d1, d2);
```

**Success:** Two distinct shapes. When they overlap, the union is a hard boolean — no merging.

---

## Exercise 4: Smoothmin merging

**Goal:** Same two shapes, but they "blob" into each other when close.

**Learn:** The polynomial smoothmin formula, the role of the `k` parameter, how smoothness scales with shape size.

```glsl
float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}
```

**Success:** Move/animate one shape and watch them merge. Play with `k` — small = sharp union, large = aggressive blobbing.

**Bonus:** Animate position with `Animatable<Offset>` from Compose. This is the bottom nav active indicator mechanic.

---

## Exercise 5: Inner glow / edge highlight

**Goal:** Black shapes with a colored glow on the inside of the edge.

**Learn:** Using the SDF distance directly to drive color falloff, not just inside/outside masking.

```glsl
float d = sceneSdf(fragCoord);
float edge = smoothstep(-edgeWidth, 0.0, d) * smoothstep(0.0, -edgeWidth*2.0, d);
half3 color = mix(fillColor, glowColor, edge);
half a = 1.0 - smoothstep(-1.0, 1.0, d);
```

**Success:** Bright ring tracking the inside edge of every shape, including curved "necks" where shapes merge.

---

## Exercise 6: Standalone pixellate shader

**Goal:** Apply a pixellate shader to any composable as a `RenderEffect`. No SDFs.

**Learn:** `Modifier.graphicsLayer { renderEffect = ... }`, wiring a shader that takes content as input rather than drawing from scratch, `setInputShader` API.

```glsl
uniform shader content;
uniform float pixelSize;

half4 main(float2 fragCoord) {
    float2 snapped = floor(fragCoord / pixelSize) * pixelSize;
    return content.eval(snapped);
}
```

**Success:** Image looks like blocky retro pixels. Animate `pixelSize` to see it dissolve.

---

## Exercise 7: Standalone magnify shader

**Goal:** A circular region of an image is magnified, like a magnifying glass.

**Learn:** Sampling content at a different coordinate than the current fragment — the foundation of every refraction/distortion effect.

```glsl
uniform shader content;
uniform float2 lensCenter;
uniform float lensRadius;
uniform float zoom;

half4 main(float2 fragCoord) {
    float2 offset = fragCoord - lensCenter;
    if (length(offset) < lensRadius) {
        return content.eval(lensCenter + offset / zoom);
    }
    return content.eval(fragCoord);
}
```

**Success:** A circular magnifier over an image. Hard boundary is fine for now.

---

## Exercise 8: SDF-masked magnify (the bridge exercise)

**Goal:** Combine 5, 6, 7 — use the SDF shape from exercise 4 as the region where magnification happens.

**Learn:** The crucial pattern of "SDF defines where, effect defines what." This is the architectural core of liquid glass.

```glsl
uniform shader content;
// ... SDF uniforms ...

half4 main(float2 fragCoord) {
    float d = sceneSdf(fragCoord);
    float mask = 1.0 - smoothstep(-1.0, 1.0, d);

    half4 normal = content.eval(fragCoord);
    half4 magnified = content.eval(/* offset toward shape center */);

    return mix(normal, magnified, mask);
}
```

**Success:** Blobby SDF shapes act as magnifying lenses over the background. They merge via smoothmin and their magnification regions merge with them.

---

## Exercise 9: Refraction via SDF normals

**Goal:** Replace "magnify toward center" with proper refraction using the SDF gradient.

**Learn:** Computing normals via finite differences on the SDF, using normals to offset background sampling — the physics-inspired core of glass rendering.

```glsl
float2 sdfNormal(float2 p) {
    float e = 1.0;
    return normalize(float2(
        sceneSdf(p + float2(e, 0)) - sceneSdf(p - float2(e, 0)),
        sceneSdf(p + float2(0, e)) - sceneSdf(p - float2(0, e))
    ));
}

// In main:
float2 normal = sdfNormal(fragCoord);
float2 sampleCoord = fragCoord - normal * refractionStrength;
half4 refracted = content.eval(sampleCoord);
```

**Success:** Background bends as it passes "through" glass shapes, and the bending follows the merged geometry naturally.

---

## Exercise 10: Stack everything

**Goal:** Background + refraction + edge glow + smoothmin SDF, all in one shader, over real content (Row of cards, image gallery).

**Learn:** How the pieces compose, where performance issues hit, what knobs feel good to tune.

---

## Workflow per exercise

1. Get it working in isolation — single `Box` with `ShaderBrush`, no other UI.
2. Expose every magic number as a Compose `var` with a `Slider` so you can tune by feel.
3. Screenshot or short capture when it works — useful for reference and the conference talk.
4. Move on. Don't polish. The next exercise reveals what actually matters.

Exercises 1–5: single sitting each. 6 and 7: independent, parallelizable. 8: where things click. 9 and 10: where it starts looking like Liquid Glass.
