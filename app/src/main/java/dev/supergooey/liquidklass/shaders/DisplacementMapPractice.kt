package dev.supergooey.liquidklass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * We are gonna use this space to practice what we learned
 */


@Language("AGSL")
val circleDisplacementShader = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float2 halfSize;
    uniform float radius;
    uniform float strength;
    uniform float aberration;
    uniform float rimWidth;
    uniform float rimIntensity;
    uniform float2 lightDir;
    uniform float rampPower;
    uniform float showGradient;
    uniform float gradientEps;

    // scalar-only distance, no hand-derived direction branches — reusable by the FD gradient below
    float sdRect(float2 point, float2 center, float2 halfSize, float radius) {
        float2 p = point - center;
        float2 b = halfSize - radius;
        float2 w = abs(p) - b;
        float2 q = max(w, 0.0);
        float g = max(w.x, w.y);
        return (g > 0.0 ? length(q) : g) - radius;
    }

    // numerical gradient via central differences; eps also acts as a smoothing radius
    // across the medial-axis seam where the analytic direction field is discontinuous
    float3 sdgRectFD(float2 point, float2 center, float2 halfSize, float radius, float eps) {
        float d = sdRect(point, center, halfSize, radius);
        float dx = sdRect(point + float2(eps, 0.0), center, halfSize, radius)
                  - sdRect(point - float2(eps, 0.0), center, halfSize, radius);
        float dy = sdRect(point + float2(0.0, eps), center, halfSize, radius)
                  - sdRect(point - float2(0.0, eps), center, halfSize, radius);
        float2 grad = float2(dx, dy) / (2.0 * eps);
        float2 dir = grad / max(length(grad), 0.0001);
        return float3(dir, d);
    }

    half4 main(float2 point) {
        float clampedRadius = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
        float3 sdg = sdgRectFD(point, center, halfSize, clampedRadius, gradientEps);
        float2 dir = sdg.xy; // outward normal, -1..1
        float d = sdg.z; // signed distance from radius, negative inside

        float mask = 1.0 - smoothstep(-1.0, 1.0, d); // ~2px AA edge, 1 inside
        
        float refractionLength = min(halfSize.x, halfSize.y);
        float t = clamp(1.0 + d / refractionLength, 0.0, 1.0); // 0 close to center, 1 at edge
        float ramp = pow(t, rampPower); // change ramp curve via uniform
        float2 displacement = dir * ramp; // apply that ramp to dir

        float2 offset = displacement * strength;
        float r = background.eval(point - offset - dir * aberration).r;
        float g = background.eval(point - offset).g;
        float b = background.eval(point - offset + dir * aberration).b;
        half4 refracted = half4(r, g, b, 1.0);

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

@Language("AGSL")
val sphereDisplacementShader = """
    
""".trimIndent()

@Preview
@Composable
fun CircleDisplacementMap() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(circleDisplacementShader) }
        var center by remember { mutableStateOf(Offset.Unspecified) }

        var radiusDp by remember { mutableFloatStateOf(40f) }
        var boxWidthDp by remember { mutableFloatStateOf(100f) }
        var boxHeightDp by remember { mutableFloatStateOf(60f) }
        var strength by remember { mutableFloatStateOf(60f) }
        var aberration by remember { mutableFloatStateOf(2f) }
        var rimWidth by remember { mutableFloatStateOf(10f) }
        var rimIntensity by remember { mutableFloatStateOf(0.8f) }
        var lightAngleDeg by remember { mutableFloatStateOf(225f) }
        var rampPower by remember { mutableFloatStateOf(3f) }
        var showGradient by remember { mutableStateOf(false) }
        var gradientEps by remember { mutableFloatStateOf(10f) }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top half: image + shader.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
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
                            val angleRad = Math.toRadians(lightAngleDeg.toDouble())
                            with(shader) {
                                setFloatUniform("resolution", size.width, size.height)
                                setFloatUniform("center", c.x, c.y)
                                setFloatUniform(
                                    "halfSize",
                                    boxWidthDp.dp.toPx() / 2,
                                    boxHeightDp.dp.toPx() / 2
                                )
                                setFloatUniform("radius", radiusDp.dp.toPx())
                                setFloatUniform("strength", strength)
                                setFloatUniform("aberration", aberration)
                                setFloatUniform("rimWidth", rimWidth)
                                setFloatUniform("rimIntensity", rimIntensity)
                                setFloatUniform(
                                    "lightDir",
                                    cos(angleRad).toFloat(),
                                    sin(angleRad).toFloat()
                                )
                                setFloatUniform("rampPower", rampPower)
                                setFloatUniform("showGradient", if (showGradient) 1f else 0f)
                                setFloatUniform("gradientEps", gradientEps)
                            }

                            renderEffect = RenderEffect.createRuntimeShaderEffect(
                                shader,
                                "background"
                            ).asComposeRenderEffect()
                        },
                    painter = painterResource(R.drawable.bikes),
                    contentScale = ContentScale.Crop,
                    contentDescription = "Hi"
                )
            }

            // Bottom half: slider controls.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(color = MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                ParamSlider("Radius", radiusDp, 0f..150f) { radiusDp = it }
                ParamSlider("Box Width", boxWidthDp, 20f..400f) { boxWidthDp = it }
                ParamSlider("Box Height", boxHeightDp, 20f..400f) { boxHeightDp = it }
                ParamSlider("Strength", strength, 0f..150f) { strength = it }
                ParamSlider("Aberration", aberration, 0f..15f) { aberration = it }
                ParamSlider("Rim Width", rimWidth, 0f..30f) { rimWidth = it }
                ParamSlider("Rim Intensity", rimIntensity, 0f..1f, step = 0.05f) { rimIntensity = it }
                ParamSlider("Light Angle", lightAngleDeg, 0f..360f) { lightAngleDeg = it }
                ParamSlider("Ramp Power", rampPower, 1f..6f) { rampPower = it }
                ParamSlider("Gradient Eps", gradientEps, 1f..40f) { gradientEps = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onBackground,
                        text = "Show Gradient"
                    )
                    Switch(checked = showGradient, onCheckedChange = { showGradient = it })
                }
            }
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 1f,
    onValueChange: (Float) -> Unit
) {
    val steps = (((range.endInclusive - range.start) / step).roundToInt() - 1).coerceAtLeast(0)
    val format = if (step >= 1f) "%.0f" else "%.2f"
    Text(color = MaterialTheme.colorScheme.onBackground, text = "$label: ${format.format(value)}")
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range,
        steps = steps
    )
}

@Composable
private fun CheckerBoard(modifier: Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val cellSize = size.width / 11
        val rows = (size.height / cellSize).roundToInt()
        val cols = (size.width / cellSize).roundToInt()

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val color = if ((row + col) % 2 == 0) Color.Black else Color.White
                drawRect(color = color, topLeft = Offset(x = col * cellSize, y = row * cellSize), size = Size(cellSize, cellSize))
            }
        }
    }
}