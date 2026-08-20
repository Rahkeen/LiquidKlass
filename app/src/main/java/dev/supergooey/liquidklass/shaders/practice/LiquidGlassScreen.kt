package dev.supergooey.liquidklass.shaders.practice

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language

private const val MAX_GLASS_COMPONENTS = 4

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

// Sets the appearance uniforms shared by every glass shape (bevel, refraction,
// rim lighting, merge fillet). These are fixed for now, not per-shape.
private fun RuntimeShader.setAppearanceUniforms(density: Density) = with(density) {
    setFloatUniform("extrusion", 10.dp.toPx())
    setFloatUniform("bevelWidth", 30.dp.toPx())
    setFloatUniform("strength", 60f)
    setFloatUniform("aberration", 12f)
    setFloatUniform("lightAngle", Math.toRadians(45.0).toFloat())
    setFloatUniform("fresnelPower", 1f)
    setFloatUniform("rimSharpness", 4f)
    setFloatUniform("rimStrength", 1f)
    setFloatUniform("rimFloor", 0.15f)
    setFloatUniform("mergeRadius", 50.dp.toPx())
}

// Flattens up to MAX_GLASS_COMPONENTS shapes into the shader's fixed-size
// geometry uniforms.
private fun RuntimeShader.setGeometryUniforms(shapes: List<GlassBounds>) {
    val active = shapes.take(MAX_GLASS_COMPONENTS)
    val centers = FloatArray(MAX_GLASS_COMPONENTS * 2)
    val halfSizes = FloatArray(MAX_GLASS_COMPONENTS * 2)
    val radii = FloatArray(MAX_GLASS_COMPONENTS)
    active.forEachIndexed { i, b ->
        centers[i * 2] = b.center.x
        centers[i * 2 + 1] = b.center.y
        halfSizes[i * 2] = b.halfSize.width
        halfSizes[i * 2 + 1] = b.halfSize.height
        radii[i] = b.cornerRadius
    }
    setIntUniform("count", active.size)
    setFloatUniform("centers", centers)
    setFloatUniform("halfSizes", halfSizes)
    setFloatUniform("cornerRadii", radii)
}

private fun glassRenderEffect(shader: RuntimeShader, blurRadiusPx: Float) =
    RenderEffect.createChainEffect(
        RenderEffect.createRuntimeShaderEffect(shader, "background"),
        RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
    ).asComposeRenderEffect()

private fun Modifier.reportGlassBounds(
    density: Density,
    cornerRadius: Dp,
    onBounds: (GlassBounds) -> Unit,
): Modifier = onPlaced { coords ->
    onBounds(
        GlassBounds(
            center = coords.boundsInParent().center,
            halfSize = Size(coords.size.width / 2f, coords.size.height / 2f),
            cornerRadius = with(density) { cornerRadius.toPx() }
        )
    )
}

// Renders the glass effect for the given shapes: reads backgroundLayer,
// blurs + refracts it through the shader, masks to the shapes' bounds. Draws
// nothing else, so it must sit between the background and any glass content
// in z-order for that content to render on top of (not under) the glass.
@Composable
private fun GlassPanel(
    shader: RuntimeShader,
    backgroundLayer: GraphicsLayer,
    shapes: List<GlassBounds>,
    modifier: Modifier = Modifier,
) {
    val glassLayer = rememberGraphicsLayer()
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                with(shader) {
                    setAppearanceUniforms(this@drawWithContent)
                    setGeometryUniforms(shapes)
                }
                glassLayer.record {
                    drawLayer(backgroundLayer)
                }
                glassLayer.renderEffect = glassRenderEffect(shader, 2.dp.toPx())
                drawLayer(glassLayer)
            }
    )
}

@Composable
fun LiquidGlassScreen(modifier: Modifier = Modifier) {
    val shader = remember { RuntimeShader(multiGlassShader) }
    val backgroundLayer = rememberGraphicsLayer()
    // One slot per glass shape on screen. Just the nav row for now, so one
    // slot; add more as more glass elements show up.
    val glassShapes = remember { mutableStateListOf<GlassBounds?>(null) }
    val density = LocalDensity.current

    Box(modifier = modifier.fillMaxSize()) {
        // Background: draws itself normally, and records that same output into
        // backgroundLayer for GlassPanel to reuse.
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
        
        GlassPanel(shader, backgroundLayer, glassShapes.filterNotNull())

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .clip(CircleShape)
                .reportGlassBounds(density, cornerRadius = 32.dp) { glassShapes[0] = it }
                .padding(4.dp)
            ,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(color = Color.White.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.Home,
                    tint = Color.Black,
                    contentDescription = ""
                )
            }
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.Search,
                    tint = Color.Black,
                    contentDescription = ""
                )
            }
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.Add,
                    tint = Color.Black,
                    contentDescription = ""
                )
            }
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(30.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.Settings,
                    tint = Color.Black,
                    contentDescription = ""
                )
            }
        }
    }
}

@Preview
@Composable
private fun LiquidGlassScreenPreview() {
    LiquidKlassTheme {
        LiquidGlassScreen()
    }
}
