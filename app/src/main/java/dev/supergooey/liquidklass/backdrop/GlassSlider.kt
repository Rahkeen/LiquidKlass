package dev.supergooey.liquidklass.backdrop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.supergooey.liquidklass.ui.theme.Blue400
import dev.supergooey.liquidklass.ui.theme.Green400
import dev.supergooey.liquidklass.ui.theme.Purple400
import dev.supergooey.liquidklass.ui.theme.Red400

@Preview
@Composable
private fun Scene() {
    Box(modifier = Modifier.fillMaxSize()) {
        val backgroundColor = Color.White
        val backdrop = rememberLayerBackdrop {
            drawRect(backgroundColor)
            drawContent()
        }

        GridBackground(modifier = Modifier.layerBackdrop(backdrop))
        Box(
            modifier = Modifier
                .safeContentPadding()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape },
                    effects = {
                        lens(16.dp.toPx(), 50.dp.toPx())
                    }
                )
                .height(100.dp)
                .fillMaxWidth()
                .align(Alignment.Center)
        )
    }
}

@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(color = Color.White),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(10) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier
                    .height(100.dp)
                    .weight(1f)
                    .background(Green400))
                Box(modifier = Modifier
                    .height(100.dp)
                    .weight(1f)
                    .background(Blue400))
                Box(modifier = Modifier
                    .height(100.dp)
                    .weight(1f)
                    .background(Red400))
                Box(modifier = Modifier
                    .height(100.dp)
                    .weight(1f)
                    .background(Purple400))
            }
        }
    }
}

