package dev.supergooey.liquidklass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val sigma by remember { mutableFloatStateOf(4f) }
    val displacementShader = remember { RuntimeShader(displacementSdf) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background scrolling list — captured into a layer for reuse by the glass effect
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    backgroundLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backgroundLayer)
                },
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sampleRows) { row ->
                SampleRow(row)
            }
        }

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
                    displacementShader.setFloatUniform("strength", 80f)

                    onDrawWithContent {
                        // 1. Record background into displacement layer with chained effects
                        displacementLayer.record { drawLayer(backgroundLayer) }
                        val displacementEffect = RenderEffect.createRuntimeShaderEffect(displacementShader, "contents")
                        val blurEffect = RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.CLAMP)
                        val chain = RenderEffect.createChainEffect(
                            displacementEffect,
                            blurEffect,
                        )
                        displacementLayer.renderEffect = displacementEffect.asComposeRenderEffect()
                        drawLayer(displacementLayer)

                        // 2. Tint to make the shape region visible
                        drawRect(color = Color.White.copy(alpha = 0.25f))

                        // 3. Mask to gooey SDF shape
                        drawRect(brush = ShaderBrush(shader), blendMode = BlendMode.DstIn)
                        drawContent()
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
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Hello", fontSize = 24.sp)
            }
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
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = Icons.Default.Add,
                    contentDescription = ""
                )
            }
        }
    }
}

private data class RowItem(val title: String, val subtitle: String, val color: Color)

private val sampleRows = listOf(
    RowItem("Sunset Coral", "Warm and inviting", Color(0xFFFF6B6B)),
    RowItem("Tangerine", "Bright citrus pop", Color(0xFFFF9F43)),
    RowItem("Goldenrod", "Cozy afternoon glow", Color(0xFFFFD166)),
    RowItem("Spring Leaf", "Fresh growth", Color(0xFF06D6A0)),
    RowItem("Lagoon", "Cool water hue", Color(0xFF1B9AAA)),
    RowItem("Indigo Dusk", "Late evening sky", Color(0xFF3D5A80)),
    RowItem("Lavender", "Soft and dreamy", Color(0xFFB8A1FF)),
    RowItem("Magenta", "Bold statement", Color(0xFFE63946)),
    RowItem("Mint", "Cool refreshment", Color(0xFF8DECB4)),
    RowItem("Cocoa", "Rich and earthy", Color(0xFF6F4E37)),
    RowItem("Slate", "Quiet neutral", Color(0xFF6C757D)),
    RowItem("Rose", "Gentle blush", Color(0xFFEF476F)),
    RowItem("Teal", "Balanced depth", Color(0xFF118AB2)),
    RowItem("Olive", "Grounded green", Color(0xFF8A9A5B)),
    RowItem("Plum", "Deep and moody", Color(0xFF7B2D5C)),
    RowItem("Sky", "Clear morning", Color(0xFF8ECAE6)),
    RowItem("Amber", "Honey warmth", Color(0xFFFFB703)),
    RowItem("Forest", "Quiet woodland", Color(0xFF2D6A4F)),
    RowItem("Crimson", "Striking red", Color(0xFFD00000)),
    RowItem("Periwinkle", "Soft blue-violet", Color(0xFF9DB4FF)),
)

@Composable
private fun SampleRow(item: RowItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(item.color)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Column {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = item.subtitle,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        }
    }
}
