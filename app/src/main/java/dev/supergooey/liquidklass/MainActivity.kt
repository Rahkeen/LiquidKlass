package dev.supergooey.liquidklass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.shaders.displacementSdf
import dev.supergooey.liquidklass.shaders.gooeySdf
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiquidKlassTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val backgroundLayer = rememberGraphicsLayer()
    val displacementLayer = rememberGraphicsLayer()
    var circleOffset by remember { mutableStateOf(Offset.Zero) }
    var circleCenter by remember { mutableStateOf(Offset.Zero) }
    val circleRadius = remember { 50.dp }
    var rectCenter by remember { mutableStateOf(Offset.Zero) }
    val rectSize = remember { DpSize(width = 260.dp, height = 100.dp) }
    val shader = remember { RuntimeShader(gooeySdf) }
    val displacementShader = remember { RuntimeShader(displacementSdf) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image — captured into a layer for reuse
        Image(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    backgroundLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backgroundLayer)
                },
            painter = painterResource(R.drawable.lillies),
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )

        // Glass overlay — displaced + blurred background masked to the gooey SDF shape
        Row(
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .fillMaxSize()
                .drawWithCache {
                    shader.setFloatUniform("resolution", size.width, size.height)
                    shader.setFloatUniform("circleCenter", circleCenter.x, circleCenter.y)
                    shader.setFloatUniform("circleRadius", circleRadius.toPx())
                    shader.setFloatUniform("rectCenter", rectCenter.x, rectCenter.y)
                    shader.setFloatUniform("rectSize", rectSize.width.toPx(), rectSize.height.toPx())
                    shader.setFloatUniform("rectRadius", circleRadius.toPx())
                    shader.setFloatUniform("smoothK", 100f)

                    displacementShader.setFloatUniform("resolution", size.width, size.height)
                    displacementShader.setFloatUniform("circleCenter", circleCenter.x, circleCenter.y)
                    displacementShader.setFloatUniform("circleRadius", circleRadius.toPx())
                    displacementShader.setFloatUniform("rectCenter", rectCenter.x, rectCenter.y)
                    displacementShader.setFloatUniform("rectSize", rectSize.width.toPx(), rectSize.height.toPx())
                    displacementShader.setFloatUniform("rectRadius", circleRadius.toPx())
                    displacementShader.setFloatUniform("smoothK", 100f)
                    displacementShader.setFloatUniform("strength", 20f)

                    onDrawWithContent {
                        // 1. Record background into displacement layer with chained effects
                        displacementLayer.record { drawLayer(backgroundLayer) }
                        displacementLayer.renderEffect = RenderEffect.createChainEffect(
                            RenderEffect.createRuntimeShaderEffect(displacementShader, "contents"),
                            RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP)
                        ).asComposeRenderEffect()
                        drawLayer(displacementLayer)

                        // 2. Tint to make the shape region visible
                        drawRect(color = Color.White.copy(alpha = 0.25f))

                        // 3. Mask to gooey SDF shape
                        drawRect(brush = ShaderBrush(shader), blendMode = BlendMode.DstIn)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom
        ) {
            // Invisible layout boxes that provide position/size for the SDF uniforms
            Box(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .size(rectSize)
                    .onGloballyPositioned { coords ->
                        rectCenter = coords.boundsInRoot().center
                    }
            )
            Box(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .graphicsLayer {
                        translationX = circleOffset.x
                        translationY = circleOffset.y
                    }
                    .size(circleRadius * 2)
                    .onGloballyPositioned { coords ->
                        circleCenter = coords.boundsInRoot().center
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            circleOffset += dragAmount
                        }
                    }
            )
        }
    }
}
