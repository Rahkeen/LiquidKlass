package dev.supergooey.liquidklass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiquidKlassTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val backgroundLayer = rememberGraphicsLayer()
                    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
                    val blurAmount by remember { mutableFloatStateOf(24f) }

                    // background
                    Image(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                backgroundLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(backgroundLayer)
                            },
                        painter = painterResource(R.drawable.icecream),
                        contentScale = ContentScale.Crop,
                        contentDescription = null
                    )

                    // apply background blur

                    val blurLayer = rememberGraphicsLayer()

                    Box(
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .width(200.dp)
                            .height(100.dp)
                            .clip(CircleShape)
                            .align(Alignment.BottomCenter)
                            .onGloballyPositioned { coords ->
                                overlayOffset = coords.positionInRoot()
                            }
                            .drawWithContent {
                                blurLayer.record {
                                    translate(-overlayOffset.x, -overlayOffset.y) {
                                        drawLayer(backgroundLayer)
                                    }
                                }
                                blurLayer.renderEffect =
                                    RenderEffect.createBlurEffect(blurAmount, blurAmount, Shader.TileMode.CLAMP)
                                        .asComposeRenderEffect()
                                drawLayer(blurLayer)
                                drawContent()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Hello", fontSize = 32.sp)
                    }
                }
            }
        }
    }
}
