package dev.supergooey.liquidklass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import kotlin.math.roundToInt

/**
 * We are gonna use this space to practice what we learned
 */


@Language("AGSL")
val circleDisplacementShader = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float radius;
    
    float3 sdgCircle(float2 point, float2 center, float radius) {
        float2 relative = point - center;
        float distance = max(length(relative), 0.0001);
        float d = distance - radius;

        return float3(relative / distance, d);
    }

    half4 main(float2 point) {
        float3 sdg = sdgCircle(point, center, radius);
        float2 dir = sdg.xy; // outward normal, -1..1
        float d = sdg.z; // signed distance from radius, negative inside

        float mask = 1.0 - smoothstep(-1.0, 1.0, d); // ~2px AA edge, 1 inside

        float t = clamp(1.0 + d / radius, 0.0, 1.0); // 0 close to center, 1 at edge
        float ramp = t*t*t; // change ramp from linear to whatever
        float2 displacement = dir * ramp; // apply that ramp to dir

        float strength = 100.0;
        float abberation = 2.0;
        float2 offset = displacement * strength;
        float r = background.eval(point - offset - dir * abberation).r;
        float g = background.eval(point - offset).g;
        float b = background.eval(point - offset + dir * abberation).b;
        half4 refracted = half4(r, g, b, 1.0);

        // rim highlight: thin specular band around the edge, brightest toward the light
        float rimWidth = 3.0;
        float rim = 1.0 - smoothstep(0.0, rimWidth, abs(d));
        float2 lightDir = normalize(float2(1.0, 0.0));
        float lightDot = abs(dot(dir, lightDir)); // mirror: light hits both ends of this axis
        float highlight = rim * pow(lightDot, 2.0);
        half4 withRim = mix(refracted, half4(1.0), highlight * 0.8);

        half4 outside = background.eval(point);
        return mix(outside, withRim, mask);
    }
    
""".trimIndent()

@Language("AGSL")
val sphereDisplacementShader = """
    
""".trimIndent()

@Preview
@Composable
private fun CircleDisplacementMap() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(circleDisplacementShader) }
        val radius = remember { 100.dp }
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
                        shader.setFloatUniform("resolution", size.width, size.height)
                        shader.setFloatUniform("center", c.x, c.y)
                        shader.setFloatUniform("radius", radius.toPx())

                        renderEffect = RenderEffect.createRuntimeShaderEffect(
                            shader,
                            "background"
                        ).asComposeRenderEffect()
                    },
                painter = painterResource(R.drawable.icecream),
                contentScale = ContentScale.Crop,
                contentDescription = "Hi"
            )
        }
    }
}

@Preview
@Composable
private fun SphereDisplacementMap() {

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