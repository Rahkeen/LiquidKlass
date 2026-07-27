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

/** Shape + bevel parameters for [bevelNormalShader], in dp. */
data class BevelShaderConfig(
    val halfWidthDp: Float,
    val halfHeightDp: Float,
    val cornerRadiusDp: Float,
    val bevelWidthDp: Float = 20f,
)

// A rounded rect, a circle, and a pill — all the same primitive, different ratios.
private val RoundedRectConfig = BevelShaderConfig(
    halfWidthDp = 130f, halfHeightDp = 80f, cornerRadiusDp = 44f, bevelWidthDp = 20f
)
private val CircleConfig = BevelShaderConfig(
    halfWidthDp = 90f, halfHeightDp = 90f, cornerRadiusDp = 90f, bevelWidthDp = 16f
)
private val PillConfig = BevelShaderConfig(
    halfWidthDp = 140f, halfHeightDp = 50f, cornerRadiusDp = 50f, bevelWidthDp = 20f
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
