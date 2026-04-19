# SDF & Glass Shader — Learning Plan

A structured path from basic signed distance fields to a physically-grounded glass shader.
All shaders target AGSL (Android Graphics Shading Language) and compose with the platform blur via `RenderEffect`.

Starting geometry: **rounded rectangle** and **circle**.

---

## Phase 1: SDF Foundations

Learn to define shapes as distance fields and render them in AGSL.

### 1. Circle SDF

The simplest SDF — just `length(p) - radius`. Use it to understand:
- What the sign means (negative = inside, zero = boundary, positive = outside)
- How to center the shape using a `uniform float2 center`
- How `smoothstep` on the distance creates anti-aliased edges

**Experiment:** Render a circle. Color the interior, exterior, and a thin edge ring differently so you can *see* the distance field.

### 2. Rounded Rectangle SDF

A rounded rect is the next step up — axis-aligned box SDF with corner rounding:
```
float sdRoundedRect(float2 p, float2 halfSize, float radius) {
    float2 d = abs(p) - halfSize + radius;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0) - radius;
}
```

**Experiment:** Render a rounded rect. Add `uniform float2 size` and `uniform float cornerRadius` so you can tweak from Kotlin. Visualize the raw distance field as a color gradient (e.g. map distance to a blue→white ramp) to build intuition.

### 3. Composing Multiple SDFs

Combine the circle and rounded rect:
- `min(d1, d2)` — union (hard merge)
- `max(d1, d2)` — intersection
- `max(-d1, d2)` — subtraction

**Experiment:** Place the circle overlapping the rounded rect. Render union, intersection, and subtraction side by side (or toggle with a uniform).

**Checkpoint:** You can define any shape as a distance, combine shapes with boolean ops, and render them with clean edges.

---

## Phase 2: SDF Blending — The Gooey Effect

### 4. Smooth Minimum (`smin`)

The key to the gooey/liquid look. `smin` blends two distance fields with a smooth transition instead of a hard seam:
```
float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}
```

Understand:
- `k` controls the blend radius — how far apart shapes start merging
- Why this creates the organic "pulling toward each other" look
- The math: it's a smooth interpolation that subtracts a bump at the seam

**Experiment:** Blend the rounded rect and circle with `smin`. Add a `uniform float blendRadius` and animate it from 0 (hard union) to 0.3+ (gooey). Make the circle position driven by touch/drag so you can pull it away and watch the goo stretch.

### 5. Animated Gooey Interaction

Put it together as an interactive component:
- Circle follows a drag gesture
- Rounded rect is fixed (e.g. a pill-shaped button)
- `smin` blends them as the circle approaches
- The merged shape is filled with the blurred background (your existing blur pipeline)

**Experiment:** Wire up `Modifier.pointerInput` to drive the circle's center uniform. The shape should look like a liquid blob pulling away from / merging into the pill.

**Checkpoint:** You have an interactive gooey merge between a rounded rect and circle, rendered as an SDF with smooth blending.

---

## Phase 3: SDF Gradients → Normal Map

Bridge from 2D shapes to 3D-looking surfaces.

### 6. SDF Gradient = Surface Normal

The gradient of an SDF tells you the direction of steepest distance change — that's the surface normal. For a 2D SDF, you compute it numerically:
```
float eps = 0.5;  // half-pixel offset
float2 gradient = float2(
    sdf(p + float2(eps, 0)) - sdf(p - float2(eps, 0)),
    sdf(p + float2(0, eps)) - sdf(p - float2(0, eps))
);
```
Then construct a 3D normal: `normalize(float3(gradient, 1.0))`. The z-component controls how "tall" the surface feels — a small z = sharp bumps, a large z = gentle dome.

**Experiment:** Compute the gradient of your blended SDF. Visualize the normal as a color (map xyz to rgb — this is exactly what a normal map texture looks like). You should see the shape appear to have 3D curvature.

### 7. Basic Lighting with Normals

With normals, you can compute simple directional lighting:
```
float3 lightDir = normalize(float3(0.5, -0.5, 1.0));
float diffuse = max(dot(normal, lightDir), 0.0);
```

And specular highlights:
```
float3 viewDir = float3(0, 0, 1);
float3 halfVec = normalize(lightDir + viewDir);
float specular = pow(max(dot(normal, halfVec), 0.0), 32.0);
```

**Experiment:** Light the gooey shape. Add a `uniform float2 lightPosition` driven by touch so you can move the light around and see the shape react. The gooey blend region should catch highlights as if it has real 3D curvature.

**Checkpoint:** Your SDF shape looks 3D. The gooey blend region has visible curvature in the lighting. You understand that SDF gradient = normal = the bridge to all lighting/refraction effects.

---

## Phase 4: Glass Shader — Dispersion & Distortion

### 8. Normal-Driven Refraction

Replace the barrel-distortion approximation in the current `GlassShader` with proper refraction driven by the SDF normal:
```
// Offset sample position based on the surface normal
// Stronger normals (edges) = more distortion
float2 refractOffset = normal.xy * refractStrength;
half4 color = composable.eval(coords + refractOffset);
```

This means the distortion follows the actual shape curvature — the gooey blend region distorts differently than the flat center.

**Experiment:** Apply the normal-driven refraction to the blurred background. Compare it against the old radial barrel distortion — the difference should be most visible in the gooey neck region where the shapes merge.

### 9. Chromatic Aberration (Dispersion)

Real glass separates wavelengths. With normals, each color channel gets a different refraction strength:
```
float2 rOffset = normal.xy * refractStrength * 0.95;
float2 gOffset = normal.xy * refractStrength * 1.00;
float2 bOffset = normal.xy * refractStrength * 1.05;

float r = composable.eval(coords + rOffset).r;
float g = composable.eval(coords + gOffset).g;
float b = composable.eval(coords + bOffset).b;
```

The spread between channels is the dispersion — the rainbow fringing you see at glass edges.

**Experiment:** Add dispersion to the glass shader. It should be most visible at the edges of the shape where normals are strongest. Add a `uniform float dispersion` to control the spread.

### 10. Fresnel with Real Normals

Replace the distance-based fresnel approximation with the actual dot product between view direction and surface normal:
```
float cosTheta = max(dot(normal, float3(0, 0, 1)), 0.0);
float fresnelAmount = fresnel(cosTheta, 1.5);
```

This gives you physically accurate edge glow that follows the true shape curvature — including the gooey blend region.

**Experiment:** Compare the old distance-based fresnel vs normal-based. The gooey neck should now catch fresnel highlights correctly since its normals point sideways.

**Checkpoint:** You have a glass shader driven entirely by SDF normals — refraction, dispersion, and fresnel all respond to the actual shape geometry, including gooey blended regions.

---

## Dependency Graph

```
Phase 1 (SDF)           Phase 2 (Gooey)         Phase 3 (Normals)      Phase 4 (Glass)
─────────────           ───────────────          ─────────────────      ───────────────
1 Circle SDF        →   4 smin blending     →    6 SDF gradient    →   8  Refraction
2 Rounded Rect SDF  →   5 Interactive goo   →    7 Basic lighting  →   9  Dispersion
3 Boolean ops                                                      →   10 Fresnel
```

Each phase builds directly on the previous. By the end, the gooey liquid shape is also a glass lens — distortion, rainbow fringing, and reflections all driven by the same SDF.
