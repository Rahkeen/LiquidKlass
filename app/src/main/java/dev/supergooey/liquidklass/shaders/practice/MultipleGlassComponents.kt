package dev.supergooey.liquidklass.shaders.practice

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language

// Max glass shapes the shader can composite in one pass. The uniform arrays are
// fixed-size, so this is the hard cap; bump it (here and in the shader) together.
private const val MAX_GLASS_COMPONENTS = 8

// A single glass shape's geometry in the root's pixel space. Appearance (bevel,
// refraction, rim) is shared across all shapes, so only geometry lives here.
private data class GlassBounds(
    val center: Offset,
    val halfSize: Size,
    val cornerRadius: Float,
)

// Multi-shape version of the glass shader: the geometry of every component is fed
// in as fixed-size uniform arrays and composited in a loop. Because the whole
// background is the shader input, refraction/rim sampling always has real content
// to read (no per-component layer clipping).
@Language("AGSL")
private val multiGlassShader = """
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

    const int MAX_GLASS = $MAX_GLASS_COMPONENTS;

    // positive inside, 0 at edge, negative outside.
    float sdRoundedRect(float2 p, float2 halfSize, float cornerRadius) {
        cornerRadius = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(p) - halfSize + cornerRadius;
        return -(length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius);
    }

    float heightAt(float2 point, float2 halfSize, float cornerRadius) {
        float d = sdRoundedRect(point, halfSize, cornerRadius);
        float t = clamp(d / bevelWidth, 0.0, 1.0);
        return extrusion * sqrt(1.0 - (1.0 - t) * (1.0 - t));
    }

    float3 computeNormal(float2 point, float2 halfSize, float cornerRadius, float eps) {
        float hL = heightAt(point - float2(eps, 0.0), halfSize, cornerRadius);
        float hR = heightAt(point + float2(eps, 0.0), halfSize, cornerRadius);
        float hD = heightAt(point - float2(0.0, eps), halfSize, cornerRadius);
        float hU = heightAt(point + float2(0.0, eps), halfSize, cornerRadius);
        return normalize(float3(hL - hR, hD - hU, 2.0 * eps));
    }

    // Composite one glass shape over the running color.
    half4 applyGlass(float2 point, float2 c, float2 hs, float cr, half4 base) {
        float2 p = point - c;
        float d = sdRoundedRect(p, hs, cr);
        float aa = 1.0;
        float mask = smoothstep(-aa, aa, d);
        if (mask <= 0.0) {
            return base;
        }

        float eps = 2.0;
        float3 n = computeNormal(p, hs, cr, eps);

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
        float2 rimDir = normalize(n.xy + float2(1e-5, 0.0));
        float axis = dot(rimDir, lightDir);
        float directional = pow(abs(axis), rimSharpness);
        float rim = fresnel * (rimFloor + (1.0 - rimFloor) * directional) * rimStrength;
        half3 lit = clamp(refracted_color + half3(rim), 0.0, 1.0);

        return mix(base, half4(lit, 1.0), mask);
    }

    half4 main(float2 point) {
        half4 col = background.eval(point);
        for (int i = 0; i < MAX_GLASS; i++) {
            if (i < count) {
                col = applyGlass(point, centers[i], halfSizes[i], cornerRadii[i], col);
            }
        }
        return col;
    }
""".trimIndent()

@Composable
fun LiquidGlassScreen(modifier: Modifier = Modifier) {
    val shader = remember { RuntimeShader(multiGlassShader) }
    // Each component reports its measured bounds here, keyed by a stable id.
    val bounds = remember { mutableStateMapOf<Int, GlassBounds>() }
    // Root coordinates so children can report positions in the shader's space
    // (the Image fills the root, so its local space == the root's space).
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoordinates = it }
    ) {
        // The single layer that draws all the glass. Its render effect reads the
        // reported bounds and composites every shape in one pass.
        Image(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    with(shader) {
                        setFloatUniform("extrusion", 10.dp.toPx())
                        setFloatUniform("bevelWidth", 20.dp.toPx())
                        setFloatUniform("strength", 30f)
                        setFloatUniform("aberration", 4f)
                        setFloatUniform("lightAngle", Math.toRadians(45.0).toFloat())
                        setFloatUniform("fresnelPower", 2f)
                        setFloatUniform("rimSharpness", 4f)
                        setFloatUniform("rimStrength", 1f)
                        setFloatUniform("rimFloor", 0.15f)

                        val shapes = bounds.values.take(MAX_GLASS_COMPONENTS)
                        val centers = FloatArray(MAX_GLASS_COMPONENTS * 2)
                        val halfSizes = FloatArray(MAX_GLASS_COMPONENTS * 2)
                        val radii = FloatArray(MAX_GLASS_COMPONENTS)
                        shapes.forEachIndexed { i, b ->
                            centers[i * 2] = b.center.x
                            centers[i * 2 + 1] = b.center.y
                            halfSizes[i * 2] = b.halfSize.width
                            halfSizes[i * 2 + 1] = b.halfSize.height
                            radii[i] = b.cornerRadius
                        }
                        setIntUniform("count", shapes.size)
                        setFloatUniform("centers", centers)
                        setFloatUniform("halfSizes", halfSizes)
                        setFloatUniform("cornerRadii", radii)
                    }
                    renderEffect = RenderEffect
                        .createRuntimeShaderEffect(shader, "background")
                        .asComposeRenderEffect()
                },
            painter = painterResource(R.drawable.icecream),
            contentScale = ContentScale.Crop,
            contentDescription = "Cool"
        )

        // The glass components. They draw nothing themselves — they only lay out
        // and report their bounds; the shader above renders the glass at those
        // positions. Add/remove components here and the shader follows.
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassComponent(
                id = 0,
                modifier = Modifier.size(80.dp),
                cornerRadius = 40.dp,
                rootCoordinates = { rootCoordinates },
                onBounds = { id, b -> bounds[id] = b }
            )
            GlassComponent(
                id = 1,
                modifier = Modifier.size(width = 140.dp, height = 80.dp),
                cornerRadius = 40.dp,
                rootCoordinates = { rootCoordinates },
                onBounds = { id, b -> bounds[id] = b }
            )
            GlassComponent(
                id = 2,
                modifier = Modifier.size(80.dp),
                cornerRadius = 16.dp,
                rootCoordinates = { rootCoordinates },
                onBounds = { id, b -> bounds[id] = b }
            )
        }
    }
}

// A glass "slot": lays out at the given size and reports its bounds (in root
// space) so the root shader can draw the glass over it.
@Composable
private fun GlassComponent(
    id: Int,
    cornerRadius: Dp,
    rootCoordinates: () -> LayoutCoordinates?,
    onBounds: (Int, GlassBounds) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val root = rootCoordinates() ?: return@onGloballyPositioned
                val topLeft = root.localPositionOf(coords, Offset.Zero)
                val width = coords.size.width.toFloat()
                val height = coords.size.height.toFloat()
                onBounds(
                    id,
                    GlassBounds(
                        center = Offset(topLeft.x + width / 2f, topLeft.y + height / 2f),
                        halfSize = Size(width / 2f, height / 2f),
                        cornerRadius = with(density) { cornerRadius.toPx() }
                    )
                )
            }
    )
}

@Preview
@Composable
private fun LiquidGlassScreenPreview() {
    LiquidKlassTheme {
        LiquidGlassScreen()
    }
}
