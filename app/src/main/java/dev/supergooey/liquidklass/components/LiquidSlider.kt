package dev.supergooey.liquidklass.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.shaders.squircleDisplacementShader
import dev.supergooey.liquidklass.ui.theme.Green400
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

@Composable
fun LiquidSlider() {
    val shader = remember { RuntimeShader(squircleDisplacementShader) }
    var pressed by remember { mutableStateOf(false) }

    var knobCenter by remember { mutableStateOf(Offset.Unspecified) }
    val knobShadow by animateFloatAsState(
        targetValue = if (pressed) 0.35f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val knobSize by animateFloatAsState(
        targetValue = if (pressed) 60f else 30f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val knobStrength by animateFloatAsState(
        targetValue = if (pressed) 60f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val knobSquircleN by animateFloatAsState(
        targetValue = if (pressed) 3f else 2f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val idleKnobOpacity by animateFloatAsState(
        targetValue = if (pressed) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

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
                        knobSize,
                        knobSize
                    )
                    setFloatUniform(
                        "squircleN",
                        2f,
                    )
                    setFloatUniform(
                        "strength",
                        knobStrength
                    )
                    setFloatUniform(
                        "rampPower",
                        2f
                    )
                    setFloatUniform(
                        "shadowOpacity",
                        knobShadow
                    )

                    renderEffect = RenderEffect.createRuntimeShaderEffect(
                        shader,
                        "background"
                    ).asComposeRenderEffect()
                }
            }
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.surface)
            .drawBehind {
                val padding = 16.dp.toPx()
                val trackStart = padding
                val trackEnd = size.width - padding
                val knobX = if (knobCenter == Offset.Unspecified) {
                    size.center.x
                } else {
                    knobCenter.x
                }
                drawLine(
                    color = Color.LightGray,
                    start = Offset(trackStart, size.center.y),
                    end = Offset(trackEnd, size.center.y),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Green400,
                    start = Offset(trackStart, size.center.y),
                    end = Offset(knobX, size.center.y),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = Green400.copy(alpha = idleKnobOpacity),
                    radius = knobSize,
                    center = Offset(knobX, size.center.y)
                )
            }
            .pointerInput(Unit) {
                val padding = 16.dp.toPx()
                val trackStart = padding
                val trackEnd = size.width - padding
                val fallback = Offset(size.width * 0.5f, size.height * 0.5f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    pressed = true
                    var current = if (knobCenter == Offset.Unspecified) fallback else knobCenter
                    horizontalDrag(down.id) { change ->
                        current = current.copy(
                            x = (current.x + change.positionChange().x).coerceIn(trackStart, trackEnd)
                        )
                        knobCenter = current
                        change.consume()
                    }
                    pressed = false
                }
            }
    )
}

@Preview
@Composable
private fun LiquidSliderPreview() {
    LiquidKlassTheme {
        LiquidSlider()
    }
}