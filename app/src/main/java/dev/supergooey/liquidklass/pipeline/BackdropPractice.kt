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
    
    float sdCircle(float2 p, float2 c, float r) {
        return length(c - p) - r;
    }
    
    half4 main(float2 coords) {
        float2 pivot = resolution * 0.5;
        float d = sdCircle(coords, center, radius);
        if (d < 0.0) {
            float2 zoomed = (coords - pivot) / 2.0 + pivot;
            return background.eval(zoomed);
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
                            shaderEffect,
                            Shader.TileMode.CLAMP
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