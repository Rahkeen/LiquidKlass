package dev.supergooey.liquidklass.material

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

/**
 * Stacked comparison of Material3's built-in HorizontalFloatingToolbar-with-FAB
 * (top) against the custom liquid-glass toolbar (bottom), centered on screen
 * over the same scrolling image grid so the two can be sized and styled
 * against each other directly.
 */
@Composable
fun ToolbarComparisonScreen() {
    val backgroundLayer = rememberGraphicsLayer()
    val shader = remember { RuntimeShader(multiGlassShader) }
    val glassShapes = remember { mutableStateListOf<GlassBounds?>(null, null) }

    var showFab by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (showFab) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow)
    )
    val blobBoost = (4f * progress * (1f - progress)).coerceIn(0f, 1f)
    val mergeRadius = 22.dp + 8.dp * blobBoost

    var m3Expanded by remember { mutableStateOf(true) }
    // Vibrant colors put the toolbar pill on primaryContainer and the FAB on
    // tertiaryContainer, same as the custom glass toolbar below.
    val vibrantColors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()

    Box(modifier = Modifier.fillMaxSize()) {
        ImageGrid(
            modifier = Modifier
                .drawWithContent {
                    backgroundLayer.record { this@drawWithContent.drawContent() }
                    drawContent()
                }
        )

        GlassPanel(shader, backgroundLayer, glassShapes.filterNotNull(), mergeRadius = mergeRadius)

        // Both toolbars anchored as one stack in the middle of the screen.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // M3's own floating toolbar.
            HorizontalFloatingToolbar(
                expanded = m3Expanded,
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(
                        onClick = { m3Expanded = !m3Expanded },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                    }
                },
                colors = vibrantColors,
            ) {
                IconButton(onClick = {}) {
                    Icon(painter = painterResource(R.drawable.ic_home), contentDescription = "Home")
                }
                IconButton(onClick = {}) {
                    Icon(painter = painterResource(R.drawable.ic_person), contentDescription = "Profile")
                }
                IconButton(onClick = {}) {
                    Icon(painter = painterResource(R.drawable.ic_search), contentDescription = "Search")
                }
                IconButton(onClick = {}) {
                    Icon(painter = painterResource(R.drawable.ic_chat), contentDescription = "Chat")
                }
            }

            // Custom glass toolbar.
            CustomHorizontalFloatingToolbar(
                glassShapes = glassShapes,
                progress = progress,
                onToggleFab = { showFab = !showFab },
            )
        }
    }
}

@Preview
@Composable
private fun ToolbarComparisonScreenPreview() {
    LiquidKlassTheme {
        ToolbarComparisonScreen()
    }
}
