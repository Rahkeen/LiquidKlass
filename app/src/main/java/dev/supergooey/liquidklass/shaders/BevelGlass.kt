package dev.supergooey.liquidklass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language

// Height-field bevel glass — step 1: visualize the surface normal only.
//
// Instead of faking a bevel from a 2D SDF gradient, we extrude the rounded-rect silhouette
// into a 3D height field (flat interior plateau + quarter-circle rounded edge), then take the
// real surface normal via central differences. This step just paints that normal as RGB so we
// can confirm the geometry looks right before wiring up refraction / fresnel.
//
// Sign convention throughout: sdRoundedRect is POSITIVE inside, zero at the edge, negative
// outside (negation of the usual IQ form) so heightProfile can assume positive-inside.
@Language("AGSL")
val bevelNormalShader = """
    uniform shader background;

    uniform float2 center;
    uniform float2 halfSize;
    uniform float cornerRadius;
    uniform float bevelWidth;

    // positive inside, 0 at edge, negative outside
    float sdRoundedRect(float2 p, float2 halfSize, float cornerRadius) {
        cornerRadius = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(p) - halfSize + cornerRadius;
        return -(length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius);
    }

    // flat plateau in the interior, quarter-circle arc across the bevel band
    float heightProfile(float d, float bevelWidth) {
        if (d <= 0.0) return 0.0;
        if (d >= bevelWidth) return bevelWidth;
        float x = bevelWidth - d;
        return sqrt(max(bevelWidth * bevelWidth - x * x, 0.0));
    }

    float height(float2 p, float2 halfSize, float cornerRadius, float bevelWidth) {
        float d = sdRoundedRect(p, halfSize, cornerRadius);
        return heightProfile(d, bevelWidth);
    }

    float3 computeNormal(float2 p, float2 halfSize, float cornerRadius, float bevelWidth, float eps) {
        float hL = height(p - float2(eps, 0.0), halfSize, cornerRadius, bevelWidth);
        float hR = height(p + float2(eps, 0.0), halfSize, cornerRadius, bevelWidth);
        float hD = height(p - float2(0.0, eps), halfSize, cornerRadius, bevelWidth);
        float hU = height(p + float2(0.0, eps), halfSize, cornerRadius, bevelWidth);
        return normalize(float3(hL - hR, hD - hU, 2.0 * eps));
    }

    half4 main(float2 point) {
        float2 p = point - center;

        float d = sdRoundedRect(p, halfSize, cornerRadius);

        // clean AA mask straight off the distance, not the height
        float aa = 1.0;
        float mask = smoothstep(-aa, aa, d);

        // outside the shape: show the untouched background for context
        half4 outside = background.eval(point);
        if (mask <= 0.0) {
            return outside;
        }

        float eps = clamp(bevelWidth / 3.0, 1.0, 4.0);
        float3 n = computeNormal(p, halfSize, cornerRadius, bevelWidth, eps);

        // encode normal into color: xyz [-1,1] -> rgb [0,1]
        half3 color = half3(n * 0.5 + 0.5);

        return mix(outside, half4(color, 1.0), mask);
    }

""".trimIndent()

// Height-field bevel glass — step 2: refraction + fresnel rim.
//
// Same height field / normal as bevelNormalShader, but now the normal drives a real refract():
// the view ray bends through the glass and offsets where we sample the background, so the bevel
// band acts like the rounded edge of a real slab. The flat interior has normal (0,0,1) so its
// refracted offset is zero — undistorted glass — and distortion concentrates in the rim, which
// is the look we're after. Fresnel (derived from normal.z) adds a white specular rim that's
// strongest right at the true edge where the surface faces most sideways.
@Language("AGSL")
val bevelGlassShader = """
    uniform shader background;

    uniform float2 center;
    uniform float2 halfSize;
    uniform float cornerRadius;
    uniform float bevelWidth;
    uniform float refractionStrength; // px offset scale; flip sign to invert bend direction
    uniform float fresnelExponent;    // rim falloff curve, e.g. 2.0
    uniform float rimIntensity;       // 0..1, how much the rim mixes toward white
    uniform float aberration;         // px split between R/B channels along the bend direction
    uniform float shadowOpacity;      // 0..1 darkness of the drop shadow

    const float eta = 1.0 / 1.5; // air -> glass-ish IOR

    // drop shadow geometry: same silhouette translated along the light axis and blurred, drawn
    // behind the shape so it reads as lifted off the background (mirrors the squircle version).
    const float2 lightDir = float2(0.70710678, 0.70710678); // 45 degrees
    const float shadowDistance = 10.0;
    const float2 shadowOffset = lightDir * shadowDistance;
    const float shadowBlur = 24.0;

    // positive inside, 0 at edge, negative outside
    float sdRoundedRect(float2 p, float2 halfSize, float cornerRadius) {
        cornerRadius = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(p) - halfSize + cornerRadius;
        return -(length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius);
    }

    float heightProfile(float d, float bevelWidth) {
        if (d <= 0.0) return 0.0;
        if (d >= bevelWidth) return bevelWidth;
        float x = bevelWidth - d;
        return sqrt(max(bevelWidth * bevelWidth - x * x, 0.0));
    }

    float height(float2 p, float2 halfSize, float cornerRadius, float bevelWidth) {
        float d = sdRoundedRect(p, halfSize, cornerRadius);
        return heightProfile(d, bevelWidth);
    }

    float3 computeNormal(float2 p, float2 halfSize, float cornerRadius, float bevelWidth, float eps) {
        float hL = height(p - float2(eps, 0.0), halfSize, cornerRadius, bevelWidth);
        float hR = height(p + float2(eps, 0.0), halfSize, cornerRadius, bevelWidth);
        float hD = height(p - float2(0.0, eps), halfSize, cornerRadius, bevelWidth);
        float hU = height(p + float2(0.0, eps), halfSize, cornerRadius, bevelWidth);
        return normalize(float3(hL - hR, hD - hU, 2.0 * eps));
    }

    half4 main(float2 point) {
        float2 p = point - center;

        float d = sdRoundedRect(p, halfSize, cornerRadius);

        float aa = 1.0;
        float mask = smoothstep(-aa, aa, d);

        half4 outside = background.eval(point);

        // drop shadow: re-evaluate the silhouette shifted by shadowOffset (positive inside), soft
        // edge via shadowBlur. Only shows where the shape itself isn't covering (1.0 - mask).
        float shadowD = sdRoundedRect(p - shadowOffset, halfSize, cornerRadius);
        float shadowMask = smoothstep(-shadowBlur, shadowBlur, shadowD);
        half4 withShadow = mix(outside, half4(0.0, 0.0, 0.0, 1.0), shadowMask * shadowOpacity * (1.0 - mask));

        if (mask <= 0.0) {
            return withShadow;
        }

        float eps = clamp(bevelWidth / 3.0, 1.0, 4.0);
        float3 n = computeNormal(p, halfSize, cornerRadius, bevelWidth, eps);

        // refract the view ray through the surface; interior normal (0,0,1) -> zero xy offset
        float3 viewDir = float3(0.0, 0.0, 1.0);
        float3 refracted = refract(viewDir, n, eta);
        float2 baseOffset = refracted.xy * refractionStrength;

        // chromatic aberration: split R/B channels along the bend direction. Scaled by the bend
        // itself (refracted.xy) so it's zero in the flat interior and grows toward the rim.
        float2 abOffset = refracted.xy * aberration;
        half rr = background.eval(point + baseOffset + abOffset).r;
        half g  = background.eval(point + baseOffset).g;
        half b  = background.eval(point + baseOffset - abOffset).b;
        half3 refractedColor = half3(rr, g, b);

        // fresnel: 0 in the flat interior, rising toward the true edge as the surface tilts
        float fresnel = pow(1.0 - clamp(n.z, 0.0, 1.0), fresnelExponent);
        half3 color = mix(refractedColor, half3(1.0), fresnel * rimIntensity);

        return mix(withShadow, half4(color, 1.0), mask);
    }

""".trimIndent()

/** Shape + bevel parameters for the bevel shaders, in dp (plus unitless refraction/rim knobs). */
data class BevelShaderConfig(
    val halfWidthDp: Float,
    val halfHeightDp: Float,
    val cornerRadiusDp: Float,
    val bevelWidthDp: Float = 20f,
    val refractionStrength: Float = 40f,
    val fresnelExponent: Float = 2f,
    val rimIntensity: Float = 0.8f,
    val aberration: Float = 8f,
    val shadowOpacity: Float = 0.35f,
)

// A rounded rect, a circle, and a pill — all the same primitive, different ratios.
private val RoundedRectConfig = BevelShaderConfig(
    halfWidthDp = 130f, halfHeightDp = 80f, cornerRadiusDp = 44f, bevelWidthDp = 8f
)
private val CircleConfig = BevelShaderConfig(
    halfWidthDp = 40f, halfHeightDp = 40f, cornerRadiusDp = 40f, bevelWidthDp = 8f
)
private val PillConfig = BevelShaderConfig(
    halfWidthDp = 140f, halfHeightDp = 50f, cornerRadiusDp = 50f, bevelWidthDp = 8f
)

@Preview
@Composable
fun BevelNormalRoundedRect() {
    BevelNormalScaffold(RoundedRectConfig)
}

@Preview
@Composable
fun BevelNormalCircle() {
    BevelNormalScaffold(CircleConfig)
}

@Preview
@Composable
fun BevelNormalPill() {
    BevelNormalScaffold(PillConfig)
}

@Preview
@Composable
fun BevelGlassRoundedRect() {
    BevelGlassScaffold(RoundedRectConfig)
}

@Preview
@Composable
fun BevelGlassCircle() {
    BevelGlassScaffold(CircleConfig)
}

@Preview
@Composable
fun BevelGlassPill() {
    BevelGlassScaffold(PillConfig)
}

/**
 * Draggable preview scaffold for [bevelNormalShader]: draw the background image, apply the
 * shader as a full-screen render effect, and let the shape be dragged around so the normal
 * map can be inspected over different content.
 */
@Composable
private fun BevelNormalScaffold(config: BevelShaderConfig) {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(bevelNormalShader) }
        var center by remember { mutableStateOf(Offset.Unspecified) }

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val fallback = Offset(size.width * 0.5f, size.height * 0.5f)
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val base = if (center == Offset.Unspecified) fallback else center
                            center = base + dragAmount
                        }
                    }
                    .graphicsLayer {
                        val c = if (center == Offset.Unspecified) {
                            Offset(size.width * 0.5f, size.height * 0.5f)
                        } else {
                            center
                        }
                        with(shader) {
                            setFloatUniform("center", c.x, c.y)
                            setFloatUniform(
                                "halfSize",
                                config.halfWidthDp.dp.toPx(),
                                config.halfHeightDp.dp.toPx()
                            )
                            setFloatUniform("cornerRadius", config.cornerRadiusDp.dp.toPx())
                            setFloatUniform("bevelWidth", config.bevelWidthDp.dp.toPx())
                        }
                        renderEffect = RenderEffect.createRuntimeShaderEffect(
                            shader,
                            "background"
                        ).asComposeRenderEffect()
                    },
                painter = painterResource(R.drawable.bikes),
                contentScale = ContentScale.Crop,
                contentDescription = "Bikes"
            )
        }
    }
}

/**
 * Draggable preview scaffold for [bevelGlassShader]: same wiring as [BevelNormalScaffold] but
 * feeds the extra refraction / fresnel uniforms so the shape reads as refracting glass.
 */
@Composable
private fun BevelGlassScaffold(config: BevelShaderConfig) {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(bevelGlassShader) }
        var center by remember { mutableStateOf(Offset.Unspecified) }

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val fallback = Offset(size.width * 0.5f, size.height * 0.5f)
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val base = if (center == Offset.Unspecified) fallback else center
                            center = base + dragAmount
                        }
                    }
                    .graphicsLayer {
                        val c = if (center == Offset.Unspecified) {
                            Offset(size.width * 0.5f, size.height * 0.5f)
                        } else {
                            center
                        }
                        with(shader) {
                            setFloatUniform("center", c.x, c.y)
                            setFloatUniform(
                                "halfSize",
                                config.halfWidthDp.dp.toPx(),
                                config.halfHeightDp.dp.toPx()
                            )
                            setFloatUniform("cornerRadius", config.cornerRadiusDp.dp.toPx())
                            setFloatUniform("bevelWidth", config.bevelWidthDp.dp.toPx())
                            setFloatUniform("refractionStrength", config.refractionStrength)
                            setFloatUniform("fresnelExponent", config.fresnelExponent)
                            setFloatUniform("rimIntensity", config.rimIntensity)
                            setFloatUniform("aberration", config.aberration)
                            setFloatUniform("shadowOpacity", config.shadowOpacity)
                        }
                        renderEffect = RenderEffect.createRuntimeShaderEffect(
                            shader,
                            "background"
                        ).asComposeRenderEffect()
                    },
                painter = painterResource(R.drawable.bikes),
                contentScale = ContentScale.Crop,
                contentDescription = "Bikes"
            )
        }
    }
}
