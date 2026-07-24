package dev.supergooey.liquidklass.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key.Companion.D
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.shaders.squircleDisplacementShader
import dev.supergooey.liquidklass.ui.theme.Blue400
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

@Composable
fun LiquidSlider() {
    val shader = remember { RuntimeShader(squircleDisplacementShader) }
    val knobSize = remember { DpSize(width = 40.dp, height = 40.dp) }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = Modifier
            .graphicsLayer {
                val progress = knobOffset.x.coerceIn(0f, size.width)
                with(shader) {
                    setFloatUniform(
                        "center",
                        progress,
                        size.height / 2f
                    )
                    setFloatUniform(
                        "halfSize",
                        knobSize.width.toPx()/2,
                        knobSize.height.toPx()/2
                    )
                    setFloatUniform(
                        "squircleN",
                        2f
                    )
                    setFloatUniform(
                        "strength",
                        80f
                    )
                    setFloatUniform(
                        "rampPower",
                        4f
                    )
                }
                renderEffect = RenderEffect.createRuntimeShaderEffect(
                    shader,
                    "background"
                ).asComposeRenderEffect()
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    knobOffset = down.position
                    horizontalDrag(down.id) { change ->
                        knobOffset = change.position
                        change.consume()
                    }
                }
            }
            .fillMaxWidth()
            .fillMaxHeight()
            .background(color = Color.White)
    ) {
        val progress = Math.clamp(knobOffset.x, 0f, size.width)
        drawLine(
            color = Color.LightGray,
            start = Offset(x = 0f, y = size.center.y),
            end = Offset(x = size.width, y = size.center.y),
            strokeWidth = 12f,
        )
        drawLine(
            color = Blue400,
            start = Offset(x = 0f, y = size.center.y),
            end = Offset(x = progress, y = size.center.y),
            strokeWidth = 12f,
        )
//        drawCircle(color = Blue400, radius = 30f, center = Offset(progress, size.center.y))
    }
}

@Preview
@Composable
private fun LiquidSliderPreview() {
    LiquidKlassTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.White)
                .padding(32.dp)
            ,
            contentAlignment = Alignment.Center
        ) {
            LiquidSlider()
        }
    }
}