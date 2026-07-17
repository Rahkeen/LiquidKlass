package dev.supergooey.liquidklass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * Simple circle SDF visualized as a grayscale gradient, masked to the circle
 * interior. Starting point for playing around with other displacement map
 * generation techniques.
 */

@Language("AGSL")
private val circleSdfShader = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float radius;
    
    float3 sdgCircle(float2 p, float2 c, float r) {
        float2 centered = p - c;
        float dist = length(centered);
        float d = dist - r;
        
        return float3(centered / dist, d);
    }

    half4 main(float2 point) {
        float3 sdg = sdgCircle(point, center, radius);
        float2 dir = sdg.xy;
        float d = sdg.z;
        
        if (d > 0) {
            return background.eval(point);
        }
        
        float t = 1.0 + d / radius; // moves t to 0..1
        t = t*t;
        float2 displacement = dir * t;
        float2 offset = displacement * 60;
//        half4 color = half4(displacement * 0.5 + 0.5, 0.5, 1.0);
        half4 color = background.eval(point - offset);
        return color;
    }
""".trimIndent()

@Preview
@Composable
fun DisplacementMap3() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(circleSdfShader) }
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
                painter = painterResource(R.drawable.bikes),
                contentScale = ContentScale.Crop,
                contentDescription = "bikes"
            )
        }
    }
}
