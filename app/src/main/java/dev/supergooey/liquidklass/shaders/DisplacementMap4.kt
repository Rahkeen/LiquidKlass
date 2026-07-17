package dev.supergooey.liquidklass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
 * Sphere SDF: reconstructs a hemisphere height field from the 2D circle
 * (z = sqrt(r^2 - dist^2)) to get a 3D surface normal, then refracts a
 * straight-into-the-screen view ray off that normal (Snell's law via
 * refract()) and uses the bent ray's xy as a pixel offset to sample the
 * background — a physically-motivated lens/glass displacement.
 */

@Language("AGSL")
private val sphereSdfShader = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float radius;
    uniform float ior;
    uniform float strength;
    uniform float showNormals;
    uniform float aberration;

    float4 sdgSphere(float2 p, float2 c, float r) {
        float2 centered = p - c;
        float dist = length(centered);
        float d = dist - r;

        float z = sqrt(max(r * r - dist * dist, 0.0));
        float3 normal = dist > 0.0 ? normalize(float3(centered, z)) : float3(0.0, 0.0, 1.0);

        return float4(normal, d);
    }

    half4 main(float2 point) {
        float4 sdg = sdgSphere(point, center, radius);
        float3 normal = sdg.xyz;
        float d = sdg.w;

        if (d > 0) {
            return background.eval(point);
        }

        if (showNormals > 0.5) {
            half3 vis = half3(normal * 0.5 + 0.5);
            return half4(vis, 1.0);
        }

        float3 viewDir = float3(0.0, 0.0, 1.0); // ray traveling straight into the screen

        // dispersion: shorter wavelengths (blue) bend more than longer ones (red)
        float3 iorPerChannel = float3(ior - aberration, ior, ior + aberration);

        float3 refractedR = refract(viewDir, normal, 1.0 / iorPerChannel.r);
        float3 refractedG = refract(viewDir, normal, 1.0 / iorPerChannel.g);
        float3 refractedB = refract(viewDir, normal, 1.0 / iorPerChannel.b);

        float2 offsetR = refractedR.xy * strength;
        float2 offsetG = refractedG.xy * strength;
        float2 offsetB = refractedB.xy * strength;

        half r = background.eval(point + offsetR).r;
        half g = background.eval(point + offsetG).g;
        half b = background.eval(point + offsetB).b;

        return half4(r, g, b, 1.0);
    }
""".trimIndent()

@Preview
@Composable
fun DisplacementMap4() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(sphereSdfShader) }
        val radius = remember { 100.dp }
        val ior = remember { 1.5f }
        val strength = remember { 60f }
        val aberration = remember { 0.4f }
        var showNormals by remember { mutableStateOf(false) }
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
                        shader.setFloatUniform("ior", ior)
                        shader.setFloatUniform("strength", strength)
                        shader.setFloatUniform("showNormals", if (showNormals) 1f else 0f)
                        shader.setFloatUniform("aberration", aberration)

                        renderEffect = RenderEffect.createRuntimeShaderEffect(
                            shader,
                            "background"
                        ).asComposeRenderEffect()
                    },
                painter = painterResource(R.drawable.bikes),
                contentScale = ContentScale.Crop,
                contentDescription = "bikes"
            )
            Button(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp),
                onClick = { showNormals = !showNormals }
            ) {
                Text(if (showNormals) "Show Refraction" else "Show Normals")
            }
        }
    }
}
