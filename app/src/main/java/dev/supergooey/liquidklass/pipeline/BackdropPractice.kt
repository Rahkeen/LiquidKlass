package dev.supergooey.liquidklass.pipeline

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language

@Language("AGSL")
private val zoomShader = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float radius;
    uniform float distortion;
    uniform float grainStrength;
    uniform float3 lightDir;
    uniform float rimStrength;
    uniform float rimSharpness;

    float hash12(float2 p) {
        return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
    }

    float2 grainOffset(float2 p) {
        return float2(hash12(p), hash12(p + 17.0)) - 0.5;
    }

    float2 distort(float2 p) {
        float d = length(p);
        float z = sqrt(distortion + d * d * -distortion);
        float r = atan(d, z) / 3.1415926535;
        float phi = atan(p.y, p.x);
        return float2(r * cos(phi), r * sin(phi));
    }

    half4 main(float2 coords) {
        float2 p = (coords - center) / radius;
        float d = length(p);
        if (d < 1.0) {
            float2 dp = distort(p) * 2.0;
            float2 distortedCoords = center + dp * radius;
            float2 jitter = grainOffset(coords) * grainStrength;
            half4 base = background.eval(distortedCoords + jitter);

            float3 N = float3(p, sqrt(1.0 - d * d));
            float3 L = normalize(lightDir);
            float rim = pow(d, rimSharpness) * max(dot(N, L), 0.0);
            return half4(base.rgb + half3(rim * rimStrength), base.a);
        }
        return background.eval(coords);
    }
""".trimIndent()

@Composable
fun BackdropScene() {
    Box(modifier = Modifier.fillMaxSize()) {
        val backdropLayer = rememberGraphicsLayer()
        var backdropOffset by remember { mutableStateOf(Offset.Zero) }
        val effectLayer = rememberGraphicsLayer()
        val shader = remember { RuntimeShader(zoomShader) }

        var glassPosition by remember { mutableStateOf(Offset.Zero)}

        // background
        Image(
            modifier = Modifier
                .drawWithContent {
                    backdropLayer.record { this@drawWithContent.drawContent() }
                    drawContent()
                }
                .fillMaxSize(),
            painter = painterResource(R.drawable.lillies),
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )

        // Blur Component
        Box(
            modifier = Modifier
                .offset { glassPosition.round() }
                .size(200.dp)
                .onPlaced { coords ->
                    backdropOffset = coords.positionInParent()
                    Log.d("Hello", "Position: $backdropOffset")
                }
                .align(Alignment.Center)
                .drawWithCache {
                    shader.setFloatUniform(
                        "resolution",
                        size.width,
                        size.height
                    )
                    shader.setFloatUniform(
                        "center",
                        size.width / 2f,
                        size.height / 2f
                    )
                    shader.setFloatUniform(
                        "radius",
                        100.dp.toPx()
                    )
                    shader.setFloatUniform(
                        "distortion",
                        3.0f
                    )
                    shader.setFloatUniform(
                        "grainStrength",
                        1.0f
                    )
                    shader.setFloatUniform(
                        "lightDir",
                        -1f, -1f, 1f
                    )
                    shader.setFloatUniform(
                        "rimStrength",
                        0.8f
                    )
                    shader.setFloatUniform(
                        "rimSharpness",
                        4.0f
                    )
                    onDrawWithContent {
                        effectLayer.record {
                            translate(left = -backdropOffset.x, top = -backdropOffset.y) {
                                drawLayer(backdropLayer)
                            }
                        }
                        val shaderEffect = RenderEffect.createRuntimeShaderEffect(
                            shader,
                            "background"
                        )

                        val blurEffect = RenderEffect.createBlurEffect(
                            32f,
                            32f,
                            Shader.TileMode.CLAMP
                        )
                        val chain = RenderEffect.createChainEffect(
                            shaderEffect,
                                    blurEffect,
                        )

                        effectLayer.renderEffect = shaderEffect.asComposeRenderEffect()
                        drawLayer(effectLayer)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { _, dragAmount ->
                        glassPosition += dragAmount
                    }
                }
        )
    }
}

@Preview
@Composable
private fun BackdropScenePreview() {
    LiquidKlassTheme {
        BackdropScene()
    }
}