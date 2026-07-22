package dev.supergooey.liquidklass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language
import kotlin.math.cos
import kotlin.math.sin

// completely different tack from the SDF-based shaders in DisplacementMapPractice.kt: instead
// of "nearest edge" distance (which is a Voronoi diagram with a hard medial-axis seam by
// construction), this uses a superellipse/squircle implicit field f(p) = |x/a|^n + |y/b|^n.
// Its gradient is a closed-form algebraic expression with no min/max/branches anywhere, so
// there's no seam to smooth over in the first place, and it stays seamless at any sheet size.
// squircleN trades off roundedness (2 = ellipse) for boxiness (higher = flatter faces, sharper
// corners); there's no explicit corner-radius uniform since the corner shape falls out of n
// rather than a radius.
@Language("AGSL")
val squircleDisplacementShader = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float2 halfSize;
    uniform float squircleN;
    uniform float strength;
    uniform float aberration;
    uniform float rimWidth;
    uniform float rimIntensity;
    uniform float2 lightDir;
    uniform float rampPower;
    uniform float showGradient;

    // r: 0 at center, 1 at the squircle boundary, growing smoothly and monotonically outward.
    // gradR: analytic gradient of r, already the outward normal direction (unnormalized).
    float4 sdgSquircle(float2 p, float2 halfSize, float n) {
        float2 a = halfSize;
        float2 ax = abs(p) / a;
        float2 signP = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);

        float r = pow(ax.x, n) + pow(ax.y, n);
        float2 gradR = n * float2(pow(ax.x, n - 1.0), pow(ax.y, n - 1.0)) * signP / a;

        float2 dir = gradR / max(length(gradR), 0.0001);
        // first-order distance estimate from the implicit surface (r == 1), so mask/rim AA
        // stays a consistent pixel width regardless of halfSize or n
        float d = (r - 1.0) / max(length(gradR), 0.0001);
        return float4(dir, d, r);
    }

    half4 main(float2 point) {
        float2 p = point - center;

        float4 sdg = sdgSquircle(p, halfSize, squircleN);
        float2 dir = sdg.xy; // outward normal, -1..1
        float d = sdg.z; // approx signed distance from boundary, negative inside
        float r = sdg.w; // 0 at center, 1 at boundary

        float mask = 1.0 - smoothstep(-1.0, 1.0, d); // ~2px AA edge, 1 inside

        float t = clamp(r, 0.0, 1.0); // 0 at center, 1 at edge — smooth by construction, no seam
        float ramp = pow(t, rampPower); // change ramp curve via uniform
        float2 displacement = dir * ramp; // apply that ramp to dir

        float2 offset = displacement * strength;
        float rr = background.eval(point - offset - dir * aberration).r;
        float g = background.eval(point - offset).g;
        float b = background.eval(point - offset + dir * aberration).b;
        half4 refracted = half4(rr, g, b, 1.0);

        // rim highlight: thin specular band around the edge, brightest toward the light
        float rim = 1.0 - smoothstep(0.0, rimWidth, abs(d));
        float lightDot = abs(dot(dir, normalize(lightDir))); // mirror: light hits both ends of this axis
        float highlight = rim * pow(lightDot, 2.0);
        half4 withRim = mix(refracted, half4(1.0), highlight * rimIntensity);

        half4 outside = background.eval(point);
        half4 result = mix(outside, withRim, mask);

        // gradient visualization: displacement (dir * ramp) remapped from [-1,1] to [0,1]
        // as red/green, dimmed background so the field reads clearly against it
        half4 gradientColor = half4(displacement.x * 0.5 + 0.5, displacement.y * 0.5 + 0.5, 0.0, 1.0);
        half4 gradientVis = mix(half4(0.05, 0.05, 0.05, 1.0), gradientColor, mask);

        return mix(result, gradientVis, showGradient);
    }

""".trimIndent()

// fixed, non-configurable defaults for the params we aren't exposing as sliders
private const val DEFAULT_ABERRATION = 2f
private const val DEFAULT_RIM_WIDTH = 10f
private const val DEFAULT_RIM_INTENSITY = 0.8f
private const val DEFAULT_LIGHT_ANGLE_DEG = 225f

// shape state at rest (small circle) vs. pressed (large sheet-sized squircle)
private const val CIRCLE_BOX_DP = 100f
private const val CIRCLE_STRENGTH = 100f
private const val CIRCLE_RAMP_POWER = 2f

private const val SQUIRCLE_WIDTH_DP = 200f
private const val SQUIRCLE_HEIGHT_DP = 200f

private val PressSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)

@Preview
@Composable
fun SquircleDisplacementMap() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(squircleDisplacementShader) }
        var center by remember { mutableStateOf(Offset.Unspecified) }
        var pressed by remember { mutableStateOf(false) }

        val boxWidthDp by animateFloatAsState(
            targetValue = if (pressed) SQUIRCLE_WIDTH_DP else CIRCLE_BOX_DP,
            animationSpec = PressSpring,
            label = "boxWidth"
        )
        val boxHeightDp by animateFloatAsState(
            targetValue = if (pressed) SQUIRCLE_HEIGHT_DP else CIRCLE_BOX_DP,
            animationSpec = PressSpring,
            label = "boxHeight"
        )
        val squircleN by animateFloatAsState(
            targetValue = if (pressed) 4f else 2f,
            animationSpec = PressSpring,
            label = "squircleN"
        )

        var showGradient by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize()) {
            CheckerBoard(
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
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                tryAwaitRelease()
                                pressed = false
                            }
                        )
                    }
                    .graphicsLayer {
                        val c = if (center == Offset.Unspecified) {
                            Offset(size.width * 0.5f, size.height * 0.5f)
                        } else {
                            center
                        }
                        val angleRad = Math.toRadians(DEFAULT_LIGHT_ANGLE_DEG.toDouble())
                        with(shader) {
                            setFloatUniform("resolution", size.width, size.height)
                            setFloatUniform("center", c.x, c.y)
                            setFloatUniform(
                                "halfSize",
                                boxWidthDp.dp.toPx() / 2,
                                boxHeightDp.dp.toPx() / 2
                            )
                            setFloatUniform("squircleN", squircleN)
                            setFloatUniform("strength", CIRCLE_STRENGTH)
                            setFloatUniform("aberration", DEFAULT_ABERRATION)
                            setFloatUniform("rimWidth", DEFAULT_RIM_WIDTH)
                            setFloatUniform("rimIntensity", DEFAULT_RIM_INTENSITY)
                            setFloatUniform(
                                "lightDir",
                                cos(angleRad).toFloat(),
                                sin(angleRad).toFloat()
                            )
                            setFloatUniform("rampPower", CIRCLE_RAMP_POWER)
                            setFloatUniform("showGradient", if (showGradient) 1f else 0f)
                        }

                        renderEffect = RenderEffect.createRuntimeShaderEffect(
                            shader,
                            "background"
                        ).asComposeRenderEffect()
                    }
            )
        }
    }
}
