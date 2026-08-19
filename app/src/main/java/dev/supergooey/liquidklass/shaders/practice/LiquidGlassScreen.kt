package dev.supergooey.liquidklass.shaders.practice

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language
import kotlin.math.roundToInt

// Max glass shapes the shader can composite in one pass. The uniform arrays are
// fixed-size, so this is the hard cap; bump it (here and in the shader) together.
private const val MAX_GLASS_COMPONENTS = 8

// A single glass shape's geometry in the glass layer's pixel space. Appearance
// (bevel, refraction, rim) is shared across all shapes, so only geometry lives here.
private data class GlassBounds(
    val center: Offset,
    val halfSize: Size,
    val cornerRadius: Float,
)

// Multi-shape version of the glass shader: the geometry of every component is fed
// in as fixed-size uniform arrays. Rather than compositing each shape's color
// independently, every shape's distance field is merged into one scene-wide
// field first (smoothUnion below), and height/normal/mask are all derived from
// that merged field — so shapes that get close enough actually bulge and fuse
// into one glass blob (metaball-style), instead of just overlapping.
//
// This shader is a mask, not a full repaint: pixels outside every shape are
// left fully transparent, so it must be composited over a separately-drawn
// background layer rather than used as that layer's only content. "background"
// is fed a blurred copy of the scene (see the RenderEffect chain below), so the
// frosted look only ever appears where a shape's mask lets it show through.
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
    // How close two shapes need to be before they start fusing, and how wide
    // the resulting fillet is. 0 disables merging (shapes just overlap).
    uniform float mergeRadius;

    const int MAX_GLASS = $MAX_GLASS_COMPONENTS;

    // positive inside, 0 at edge, negative outside.
    float sdRoundedRect(float2 p, float2 halfSize, float cornerRadius) {
        cornerRadius = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(p) - halfSize + cornerRadius;
        return -(length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius);
    }

    // Polynomial smooth union (the usual Quilez smin, adapted for our SDF's
    // sign convention). Our sdRoundedRect is positive inside / negative
    // outside — the opposite of the textbook SDF — so a smooth "union of
    // interiors" here is a smooth MAX, i.e. smin applied to negated distances
    // and negated back. k = mergeRadius controls the blend width: near 0 this
    // degenerates to a hard max(a, b) with no fillet.
    float smoothUnion(float a, float b, float k) {
        if (k <= 0.0) {
            return max(a, b);
        }
        float h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);
        return mix(b, a, h) + k * h * (1.0 - h);
    }

    // Merged distance field across every active shape.
    float sceneSDF(float2 point) {
        float d = -1.0e5;
        for (int i = 0; i < MAX_GLASS; i++) {
            if (i < count) {
                float di = sdRoundedRect(point - centers[i], halfSizes[i], cornerRadii[i]);
                d = smoothUnion(d, di, mergeRadius);
            }
        }
        return d;
    }

    float sceneHeight(float2 point) {
        float d = sceneSDF(point);
        float t = clamp(d / bevelWidth, 0.0, 1.0);
        return extrusion * sqrt(1.0 - (1.0 - t) * (1.0 - t));
    }

    float3 sceneNormal(float2 point, float eps) {
        float hL = sceneHeight(point - float2(eps, 0.0));
        float hR = sceneHeight(point + float2(eps, 0.0));
        float hD = sceneHeight(point - float2(0.0, eps));
        float hU = sceneHeight(point + float2(0.0, eps));
        return normalize(float3(hL - hR, hD - hU, 2.0 * eps));
    }

    // Transparent everywhere the merged shape doesn't cover: this shader is
    // composited over a separately-drawn sharp background, so unmasked pixels
    // must stay invisible rather than repainting the (blurred) "background"
    // input.
    half4 main(float2 point) {
        float d = sceneSDF(point);
        float aa = 1.0;
        float mask = smoothstep(-aa, aa, d);
        if (mask <= 0.0) {
            return half4(0.0);
        }

        float eps = 2.0;
        float3 n = sceneNormal(point, eps);

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

        return mix(half4(0.0), half4(lit, 1.0), mask);
    }
""".trimIndent()

@Composable
fun LiquidGlassScreen(modifier: Modifier = Modifier) {
    val shader = remember { RuntimeShader(multiGlassShader) }
    // Recorded once from the background Image's own draw. Kept as a separate
    // layer (rather than reading straight off the Image) so it can later be
    // copied and blurred before the glass layer samples it.
    val backgroundLayer = rememberGraphicsLayer()
    // Where the shader's output actually gets rendered.
    val glassLayer = rememberGraphicsLayer()
    // Each component reports its measured bounds here, keyed by a stable id.
    val bounds = remember { mutableStateMapOf<Int, GlassBounds>() }
    // The glass layer (Row) is full size and coincides with the background, so
    // its own coordinate space doubles as the shader's pixel space.
    var glassLayerCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        // Background: draws itself normally, and records that same output into
        // backgroundLayer for the glass layer (or a future blur pass) to reuse.
        Image(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    backgroundLayer.record { this@drawWithContent.drawContent() }
                    drawContent()
                },
            painter = painterResource(R.drawable.icecream),
            contentScale = ContentScale.Crop,
            contentDescription = "Cool"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { glassLayerCoordinates = it }
                .drawWithContent {
                    with(shader) {
                        setFloatUniform("extrusion", 10.dp.toPx())
                        setFloatUniform("bevelWidth", 40.dp.toPx())
                        setFloatUniform("strength", 60f)
                        setFloatUniform("aberration", 12f)
                        setFloatUniform("lightAngle", Math.toRadians(45.0).toFloat())
                        setFloatUniform("fresnelPower", 1f)
                        setFloatUniform("rimSharpness", 4f)
                        setFloatUniform("rimStrength", 1f)
                        setFloatUniform("rimFloor", 0.15f)
                        setFloatUniform("mergeRadius", 50.dp.toPx())

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

                    glassLayer.record {
                        drawLayer(backgroundLayer)
                    }
                    // Blur the raw content first (inner), then let the shader
                    // (outer) read that blurred result as "background" and mask
                    // it down to just the shapes. Order matters: chaining the
                    // other way round would blur the shader's own (already
                    // opaque, unmasked) output and frost the whole screen.
                    val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "background")
                    val blurEffect = RenderEffect.createBlurEffect(
                        2.dp.toPx(),
                        2.dp.toPx(),
                        Shader.TileMode.CLAMP
                    )
                    glassLayer.renderEffect = RenderEffect
                        .createChainEffect(shaderEffect, blurEffect)
                        .asComposeRenderEffect()
                    drawLayer(glassLayer)
                }
        )

        // Draggable circle: tracks raw drag delta in px and reports its own
        // bounds via GlassComponent, same as the static shape. The glass Box
        // above reads `bounds` every frame, so it follows the drag with no
        // extra wiring needed.
        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        GlassComponent(
            id = 0,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-60).dp)
                .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                .size(80.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, amount ->
                        change.consume()
                        dragOffset += amount
                    }
                },
            cornerRadius = 40.dp,
            layerCoordinates = { glassLayerCoordinates },
            onBounds = { id, b -> bounds[id] = b }
        )

        GlassComponent(
            id = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 60.dp)
                .size(width = 140.dp, height = 80.dp),
            cornerRadius = 40.dp,
            layerCoordinates = { glassLayerCoordinates },
            onBounds = { id, b -> bounds[id] = b }
        )
    }
}

// A glass "slot": lays out at the given size and reports its bounds (in the
// glass layer's space) so the shader can draw the glass over it.
@Composable
private fun GlassComponent(
    id: Int,
    cornerRadius: Dp,
    layerCoordinates: () -> LayoutCoordinates?,
    onBounds: (Int, GlassBounds) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val layer = layerCoordinates() ?: return@onGloballyPositioned
                val topLeft = layer.localPositionOf(coords, Offset.Zero)
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
