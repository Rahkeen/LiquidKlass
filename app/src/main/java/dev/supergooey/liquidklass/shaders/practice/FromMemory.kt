package dev.supergooey.liquidklass.shaders.practice

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language

@Language("AGSL")
private val glassyShader = """
    uniform shader background;
    uniform float2 center1;
    uniform float2 center2;
    uniform float radius;
    uniform float extrusion;
    uniform float bevelWidth;
    uniform float strength;
    uniform float aberration;
    uniform int showNormal;

    // positive inside, negative out
    float sdfCircle(float2 point) {
        return radius - length(point);
    }
    
    float2 gradient(float2 point, float eps) {
        float dX = sdfCircle(float2(point.x + eps, point.y)) - sdfCircle(float2(point.x - eps, point.y));
        float dY = sdfCircle(float2(point.x, point.y + eps)) - sdfCircle(float2(point.x, point.y - eps));
        return float2(dX, dY) / (2.0 * eps);
    }
    
    float height(float2 point, float radius, float extrusion, float bevelWidth) {
        float d = sdfCircle(point);
        float t = clamp(d / bevelWidth, 0.0, 1.0);
        return extrusion * sqrt(1.0 - (1.0 - t) * (1.0 - t));
    }
    
    float3 computeNormal(float2 point, float radius, float extrusion, float bevelWidth, float eps) {
        float hL = height(point - float2(eps, 0.0), radius, extrusion, bevelWidth);
        float hR = height(point + float2(eps, 0.0), radius, extrusion, bevelWidth);
        float hD = height(point - float2(0.0, eps), radius, extrusion, bevelWidth);
        float hU = height(point + float2(0.0, eps), radius, extrusion, bevelWidth);
        
        return normalize(float3(hL - hR, hD - hU, 2.0*eps));
    }
    
    half4 main(float2 point) {
        float2 p1 = point - center1;
        float2 p2 = point - center2;
        float d = sdfCircle(p1);
        float aa = 1.0;
        float mask = smoothstep(-aa, aa, d); // smoothes out the the SDF edge
        
        half4 outside = background.eval(point);
        if (mask <= 0.0) {
            return outside;
        }
        
        float eps = 2.0;
        
        // 2d gradient based approach
        float2 dir = gradient(p1, eps);
        float t = 1.0 - clamp((d / radius), 0.0, 1.0);
        float easing = pow(t, 4.0);
        float2 eased_dir = dir * easing;
        half4 displacement_map = half4(eased_dir.xy * 0.5 + 0.5, 0.5, 1.0);
        
        // 3d shape approach
        float3 n = computeNormal(p1, radius, extrusion, bevelWidth, eps);
        half4 normal_map = half4(n * 0.5 + 0.5, 1.0);
        
        float3 viewDir = float3(0.0, 0.0, 1.0);
        float eta = 1.0 / 1.5;
        float3 refracted = refract(viewDir, n, eta);

        float2 offset = refracted.xy * strength;
        float2 abOffset = refracted.xy * aberration;
        half r = background.eval(point + offset + abOffset).r;
        half g = background.eval(point + offset).g;
        half b = background.eval(point + offset - abOffset).b;
        half4 refracted_color = half4(r,g,b,1.0);

        if (showNormal == 1) {
            return mix(outside, normal_map, mask);
        }
        return mix(outside, refracted_color, mask);
    }
""".trimIndent()

@Preview
@Composable
private fun FromMemoryPlayground() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(glassyShader) }
        var offset1 by remember { mutableStateOf(Offset.Zero) }
        var offset2 by remember { mutableStateOf(Offset.Zero) }

        var extrusion by remember { mutableStateOf(10f) }   // dp
        var bevelWidth by remember { mutableStateOf(40f) }  // dp
        var strength by remember { mutableStateOf(60f) }    // px
        var aberration by remember { mutableStateOf(6f) }   // px
        var showNormal by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize()) {
            // Top half: the image with the shader applied.
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { size ->
                        offset1 = Offset(size.center.x.toFloat(), size.height * 0.25f)
                        offset2 = Offset(size.center.x.toFloat(), size.height * 0.75f)
                    }
                    .graphicsLayer {
                        with(shader) {
                            setFloatUniform("center1", offset1.x, offset1.y)
                            setFloatUniform("center2", offset2.x, offset2.y)
                            setFloatUniform("radius", 40.dp.toPx())
                            setFloatUniform("extrusion", extrusion.dp.toPx())
                            setFloatUniform("bevelWidth", bevelWidth.dp.toPx())
                            setFloatUniform("strength", strength)
                            setFloatUniform("aberration", aberration)
                            setIntUniform("showNormal", if (showNormal) 1 else 0)
                        }
                        renderEffect = RenderEffect.createRuntimeShaderEffect(
                            shader,
                            "background"
                        ).asComposeRenderEffect()
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            offset1 += dragAmount
                            offset2 += dragAmount
                        }
                    },
                painter = painterResource(R.drawable.bikes),
                contentScale = ContentScale.Crop,
                contentDescription = "Bikes"
            )

            // Bottom half: the control panel.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                LabeledSlider("Extrusion", extrusion, 0f..40f) { extrusion = it }
                LabeledSlider("Bevel Width", bevelWidth, 1f..80f) { bevelWidth = it }
                LabeledSlider("Strength", strength, 0f..200f) { strength = it }
                LabeledSlider("Aberration", aberration, 0f..30f) { aberration = it }
                Button(onClick = { showNormal = !showNormal }) {
                    Text(if (showNormal) "Show Glass" else "Show Normals")
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${"%.0f".format(value)}")
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}