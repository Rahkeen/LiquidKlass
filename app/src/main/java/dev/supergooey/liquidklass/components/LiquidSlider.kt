package dev.supergooey.liquidklass.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.ui.theme.Blue400
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

@Composable
fun LiquidSlider() {
    var offset by remember { mutableStateOf(Offset.Zero) }

    Canvas(
        modifier = Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    offset = down.position
                    horizontalDrag(down.id) { change ->
                        offset = change.position
                        change.consume()
                    }
                }
            }
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp),
    ) {
        val progress = Math.clamp(offset.x / size.width, 0f, 1f)
        drawLine(
            color = Color.LightGray,
            start = Offset(x = 0f, y = size.center.y),
            end = Offset(x = size.width, y = size.center.y),
            strokeWidth = 12f,
        )
        drawLine(
            color = Blue400,
            start = Offset(x = 0f, y = size.center.y),
            end = Offset(x = size.width * progress, y = size.center.y),
            strokeWidth = 12f,
        )
        drawCircle(color = Blue400, radius = 30f, center = Offset(size.width * progress, size.center.y))
    }
}

@Preview
@Composable
private fun LiquidSliderPreview() {
    LiquidKlassTheme {
        Box(modifier = Modifier.fillMaxSize().background(color = Color.White), contentAlignment = Alignment.Center) {
            LiquidSlider()
        }
    }
}