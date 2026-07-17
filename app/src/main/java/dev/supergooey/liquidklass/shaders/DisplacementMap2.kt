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

/**
 * Visualizing the gradient (direction) field of a circle SDF, masked to the
 * circle interior so it can be seen in context against the image — no rim
 * falloff, no displacement, just color = direction.
 */

@Language("AGSL")
private val gradientVisualizerShader = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float radius;

    half4 main(float2 point) {
        float2 p = point - center;
        float dist = length(p);

        if (dist > radius) {
            return background.eval(point);
        }

        float2 dir = normalize(p); // gradient of the circle SDF: radial, points away from center
        // remap from [-1, 1] to [0, 1] so it can be shown as a color
        float2 vis = dir * 0.5 + 0.5;
        float inner = abs(dist / radius);
        inner = inner;
        
        return half4(vis.x*inner, vis.y*inner, 0.0, 1.0);
    }
""".trimIndent()

@Preview
@Composable
fun DisplacementMap2() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(gradientVisualizerShader) }
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
