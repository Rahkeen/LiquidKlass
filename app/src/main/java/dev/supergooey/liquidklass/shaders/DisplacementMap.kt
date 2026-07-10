package dev.supergooey.liquidklass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language

/**
 * We are gonna be practicing creating a glass like effect using computed displacement maps.
 *
 * Currently:
 * - Setting up the shader + environment
 */

@Language("AGSL")
private val displacementShader = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float radius;
    uniform float refraction;
    uniform float rimBoost;
    uniform float rim;
    uniform float aberration;

    half4 main(float2 point) {
        float2 p = point - center; // point relative to circle center
        float dist = length(p);
        float d = dist - radius;
        
        if (d > rim) {
            return background.eval(point);
        }
        
        float2 dir = normalize(p);
        
        // spherical bulge
        float t = clamp(dist / radius, 0.0, 1.0);
        float baseFalloff = t * t;
        
        // rim boost mask
        float rimMask = 1.0 - smoothstep(0.0, rim, abs(d));
        
        // combine
        float totalStrength = refraction * baseFalloff + rimBoost * rimMask;
        float2 displacement = dir * totalStrength * radius;

        // sample each channel with a slightly different displacement so
        // wavelengths separate. R bends least, B bends most.
        float2 rCoord = point + displacement * (1.0 - aberration);
        float2 gCoord = point + displacement;
        float2 bCoord = point + displacement * (1.0 + aberration);

        half r = background.eval(rCoord).r;
        half g = background.eval(gCoord).g;
        half b = background.eval(bCoord).b;
        half a = background.eval(gCoord).a;

        return half4(r, g, b, a);
    }
""".trimIndent()

@Preview
@Composable
private fun DisplacementMap1() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(displacementShader) }
        val radius = remember { 100.dp }
        var center by remember { mutableStateOf(Offset.Unspecified) }

        // Live-tunable shader parameters.
        var refraction by remember { mutableFloatStateOf(0.6f) }
        var rimBoost by remember { mutableFloatStateOf(0.4f) }
        var rim by remember { mutableFloatStateOf(5.0f) }
        var aberration by remember { mutableFloatStateOf(0.2f) }

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
                            with(shader) {
                                setFloatUniform("resolution", size.width, size.height)
                                setFloatUniform("center", c.x, c.y)
                                setFloatUniform("radius", radius.toPx())
                                setFloatUniform("refraction", refraction)
                                setFloatUniform("rimBoost", rimBoost)
                                setFloatUniform("rim", rim)
                                setFloatUniform("aberration", aberration)
                            }
                            renderEffect = RenderEffect.createRuntimeShaderEffect(
                                shader,
                                "background"
                            ).asComposeRenderEffect()
                        },
                    painter = painterResource(R.drawable.bikes),
                    contentScale = ContentScale.Crop,
                    contentDescription = "bikes"
                )
            }

            // Bottom half: slider controls.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                ParamSlider("Refraction", refraction, 0f..1f) { refraction = it }
                ParamSlider("Rim Boost", rimBoost, 0f..1f) { rimBoost = it }
                ParamSlider("Rim", rim, 0f..20f) { rim = it }
                ParamSlider("Aberration", aberration, 0f..0.5f) { aberration = it }
            }
        }
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Text(text = "$label: ${"%.3f".format(value)}")
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = range
    )
}