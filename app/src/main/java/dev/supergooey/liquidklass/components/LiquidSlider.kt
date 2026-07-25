package dev.supergooey.liquidklass.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.shaders.squircleDisplacementShader
import dev.supergooey.liquidklass.ui.theme.Green400
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

@Composable
fun LiquidSlider() {
    val shader = remember { RuntimeShader(squircleDisplacementShader) }
    var knobCenter by remember { mutableStateOf(Offset.Unspecified) }
    val knobSize by remember { mutableStateOf(20.dp) }

    Box(
        modifier = Modifier
            .graphicsLayer {
                val center = if (knobCenter == Offset.Unspecified) {
                    size.center
                } else {
                    knobCenter
                }
                with(shader) {
                    setFloatUniform(
                        "center",
                        center.x,
                        center.y,
                    )
                    setFloatUniform(
                        "halfSize",
                        knobSize.toPx(),
                        knobSize.toPx(),
                    )
                    setFloatUniform(
                        "squircleN",
                        2f,
                    )
                    setFloatUniform(
                        "strength",
                        60f
                    )
                    setFloatUniform(
                        "rampPower",
                        2f
                    )

                    renderEffect = RenderEffect.createRuntimeShaderEffect(
                        shader,
                        "background"
                    ).asComposeRenderEffect()
                }
            }
            .fillMaxSize()
            .background(color = Color.White)
            .drawBehind {
                val padding = 16.dp.toPx()
                drawLine(
                    color = Color.LightGray,
                    start = Offset(padding, size.center.y),
                    end = Offset(size.width - padding, size.center.y),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Green400,
                    start = Offset(padding, size.center.y),
                    end = Offset((size.width - padding) * 0.5f, size.center.y),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            .pointerInput(Unit) {
                val fallback = Offset(size.width * 0.5f, size.height * 0.5f)
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val base = if (knobCenter == Offset.Unspecified) fallback else knobCenter
                    knobCenter = base + dragAmount
                }            }

    )
}

@Preview
@Composable
private fun LiquidSliderPreview() {
    LiquidKlassTheme {
        LiquidSlider()
    }
}