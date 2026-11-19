package dev.supergooey.liquidklass.material

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.intellij.lang.annotations.Language

const val MAX_GLASS_COMPONENTS = 4

// Position/size are in root coordinates so any number of GlassPanels, each
// nested arbitrarily deep in the tree, can report into the same shader space.
data class GlassBounds(
    val center: Offset,
    val halfSize: Size,
    val cornerRadius: Float,
)

@Language("AGSL")
val multiGlassShader = """
    uniform shader background;

    // Per-component geometry (indices 0..count-1 are valid).
    uniform int count;
    uniform float2 centers[$MAX_GLASS_COMPONENTS];
    uniform float2 halfSizes[$MAX_GLASS_COMPONENTS];
    uniform float cornerRadii[$MAX_GLASS_COMPONENTS];

    // Shared appearance.
    uniform float extrusion;
    uniform float bevelWidth;
    uniform float strength;
    uniform float aberration;
    uniform float lightAngle;
    uniform float fresnelPower;
    uniform float rimSharpness;
    uniform float rimStrength;
    uniform float rimFloor;
    uniform float mergeRadius;

    const int MAX_GLASS = $MAX_GLASS_COMPONENTS;
    const float eps = 2.0;

    struct Shape {
        float2 center;
        float2 halfSize;
        float radius;
    };

    // positive inside, 0 at edge, negative outside.
    float sdRoundedRect(float2 p, float2 halfSize, float cornerRadius) {
        cornerRadius = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(p) - halfSize + cornerRadius;
        return -(length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius);
    }

    float smoothUnion(float a, float b, float k) {
        if (k <= 0.0) {
            return max(a, b);
        }
        float h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);
        return mix(b, a, h) + k * h * (1.0 - h);
    }

    // Merged distance field across every active shape.
    float sceneSDF(float2 point, Shape shapes[MAX_GLASS]) {
        float d = -1.0e5;
        for (int i = 0; i < MAX_GLASS; i++) {
            if (i < count) {
                Shape s = shapes[i];
                float di = sdRoundedRect(point - s.center, s.halfSize, s.radius);
                d = smoothUnion(d, di, mergeRadius);
            }
        }
        return d;
    }

    float sceneHeight(float2 point, Shape shapes[MAX_GLASS]) {
        float d = sceneSDF(point, shapes);
        float t = clamp(d / bevelWidth, 0.0, 1.0);
        return extrusion * sqrt(1.0 - (1.0 - t) * (1.0 - t));
    }

    float3 sceneNormal(float2 point, Shape shapes[MAX_GLASS]) {
        float hL = sceneHeight(point - float2(eps, 0.0), shapes);
        float hR = sceneHeight(point + float2(eps, 0.0), shapes);
        float hD = sceneHeight(point - float2(0.0, eps), shapes);
        float hU = sceneHeight(point + float2(0.0, eps), shapes);
        return normalize(float3(hL - hR, hD - hU, 2.0 * eps));
    }

    half4 main(float2 point) {
        Shape shapes[MAX_GLASS];
        for (int i = 0; i < MAX_GLASS; i++) {
            shapes[i] = Shape(centers[i], halfSizes[i], cornerRadii[i]);
        }

        float d = sceneSDF(point, shapes);
        float aa = 1.0;
        float mask = smoothstep(-aa, aa, d);
        if (mask <= 0.0) {
            return half4(0.0);
        }

        float3 n = sceneNormal(point, shapes);

        float3 viewDir = float3(0.0, 0.0, 1.0);
        float eta = 1.0 / 1.5;
        float3 refracted = refract(viewDir, n, eta);

        float2 offset = refracted.xy * strength;
        float2 abOffset = refracted.xy * aberration;
        half r = background.eval(point + offset + abOffset).r;
        half g = background.eval(point + offset).g;
        half b = background.eval(point + offset - abOffset).b;
        half3 refracted_color = half3(r, g, b);

        // Angled, mirrored rim lighting.
        float fresnel = pow(1.0 - dot(n, viewDir), fresnelPower);
        float2 lightDir = float2(cos(lightAngle), sin(lightAngle));
        float2 rimDir = normalize(n.xy + float2(1e-5, 0.0)); // adding epsilon nudge to avoid NaN
        float axis = dot(rimDir, lightDir);
        float directional = pow(abs(axis), rimSharpness);
        float rim = fresnel * (rimFloor + (1.0 - rimFloor) * directional) * rimStrength;
        half3 lit = clamp(refracted_color + half3(rim), 0.0, 1.0);

        return mix(half4(0.0), half4(lit, 1.0), mask);
    }
""".trimIndent()

// Sets the appearance uniforms shared by every glass shape (bevel, refraction,
// rim lighting, merge fillet). These are fixed for now, not per-shape, except
// mergeRadius which callers can animate (e.g. to blob shapes together as they
// approach each other).
fun RuntimeShader.setGlassAppearanceUniforms(density: Density, mergeRadius: Dp = 24.dp) = with(density) {
    setFloatUniform("extrusion", 4.dp.toPx())
    setFloatUniform("bevelWidth", 30.dp.toPx())
    setFloatUniform("strength", 60f)
    setFloatUniform("aberration", 16f)
    setFloatUniform("lightAngle", Math.toRadians(45.0).toFloat())
    setFloatUniform("fresnelPower", 1f)
    setFloatUniform("rimSharpness", 4f)
    setFloatUniform("rimStrength", 2f)
    setFloatUniform("rimFloor", 0.15f)
    setFloatUniform("mergeRadius", mergeRadius.toPx())
}

// Flattens up to MAX_GLASS_COMPONENTS shapes into the shader's fixed-size
// geometry uniforms. `panelOrigin` re-bases each shape's root-space center
// into the GlassPanel's own local draw space.
fun RuntimeShader.setGlassGeometryUniforms(shapes: List<GlassBounds>, panelOrigin: Offset) {
    val active = shapes.take(MAX_GLASS_COMPONENTS)
    val centers = FloatArray(MAX_GLASS_COMPONENTS * 2)
    val halfSizes = FloatArray(MAX_GLASS_COMPONENTS * 2)
    val radii = FloatArray(MAX_GLASS_COMPONENTS)
    active.forEachIndexed { i, b ->
        centers[i * 2] = b.center.x - panelOrigin.x
        centers[i * 2 + 1] = b.center.y - panelOrigin.y
        halfSizes[i * 2] = b.halfSize.width
        halfSizes[i * 2 + 1] = b.halfSize.height
        radii[i] = b.cornerRadius
    }
    setIntUniform("count", active.size)
    setFloatUniform("centers", centers)
    setFloatUniform("halfSizes", halfSizes)
    setFloatUniform("cornerRadii", radii)
}

fun glassRenderEffect(shader: RuntimeShader, blurRadiusPx: Float) =
    RenderEffect.createChainEffect(
        RenderEffect.createRuntimeShaderEffect(shader, "background"),
        RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
    ).asComposeRenderEffect()

// Reports this node's bounds, in root coordinates, as a glass shape. Safe to
// use from anywhere in the tree regardless of how deeply nested relative to
// the GlassPanel that will render it.
fun Modifier.reportGlassBounds(
    density: Density,
    cornerRadius: Dp,
    onBounds: (GlassBounds) -> Unit,
): Modifier = onPlaced { coords ->
    val size = coords.size
    onBounds(
        GlassBounds(
            center = coords.positionInRoot() + Offset(size.width / 2f, size.height / 2f),
            halfSize = Size(size.width / 2f, size.height / 2f),
            cornerRadius = with(density) { cornerRadius.toPx() }
        )
    )
}

// Renders the glass effect for the given shapes: reads backgroundLayer,
// blurs + refracts it through the shader, masks to the shapes' bounds. Draws
// nothing else, so it must sit between the background and any glass content
// in z-order for that content to render on top of (not under) the glass.
@Composable
fun GlassPanel(
    shader: RuntimeShader,
    backgroundLayer: GraphicsLayer,
    shapes: List<GlassBounds>,
    modifier: Modifier = Modifier,
    mergeRadius: Dp = 24.dp,
) {
    val glassLayer = rememberGraphicsLayer()
    var panelOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { panelOrigin = it.positionInRoot() }
            .drawWithContent {
                with(shader) {
                    setGlassAppearanceUniforms(this@drawWithContent, mergeRadius)
                    setGlassGeometryUniforms(shapes, panelOrigin)
                }
                glassLayer.record {
                    drawLayer(backgroundLayer)
                }
                glassLayer.renderEffect = glassRenderEffect(shader, 4.dp.toPx())
                drawLayer(glassLayer)
            }
    )
}
