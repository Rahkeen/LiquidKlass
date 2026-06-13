package dev.supergooey.liquidklass.pipeline

import android.graphics.RenderEffect
import android.graphics.Shader
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

@Composable
fun BackdropScene() {
    Box(modifier = Modifier.fillMaxSize()) {
        val backdropLayer = rememberGraphicsLayer()
        var backdropOffset by remember { mutableStateOf(Offset.Zero) }
        val blurLayer = rememberGraphicsLayer()

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
                .size(200.dp)
                .onPlaced { coords ->
                    backdropOffset = coords.positionInParent()
                    Log.d("Hello", "Position: $backdropOffset")
                }
                .align(Alignment.Center)
                .drawWithCache {
                    onDrawWithContent {
                        blurLayer.record {
                            translate(left = -backdropOffset.x, top = -backdropOffset.y) {
                                drawLayer(backdropLayer)
                            }
                        }
                        blurLayer.renderEffect = RenderEffect.createBlurEffect(32f, 32f, Shader.TileMode.CLAMP).asComposeRenderEffect()
                        clipRect {
                            drawLayer(blurLayer)
                        }
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