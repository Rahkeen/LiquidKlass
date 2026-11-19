package dev.supergooey.liquidklass.material

import android.graphics.RuntimeShader
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.shaders.practice.images
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import kotlin.math.roundToInt

enum class NavigationTabs {
    Chat,
    Profile,
    Search,
    Home,
}

@Composable
fun ToolbarExamples() {
    val backgroundLayer = rememberGraphicsLayer()
    val shader = remember { RuntimeShader(multiGlassShader) }
    // One slot per glass shape in the toolbar: the pill, and the fab.
    val glassShapes = remember { mutableStateListOf<GlassBounds?>(null, null) }
    var selected by remember { mutableStateOf(NavigationTabs.Home) }
    var expanded by remember { mutableStateOf(true) }

    var showFab by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (showFab) 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow)
    )
    // The pill and fab blob together via mergeRadius while they're
    // approaching each other, settling back to a tighter radius once fully
    // joined or fully apart.
    val blobBoost = (4f * progress * (1f - progress)).coerceIn(0f, 1f)
    val mergeRadius = 22.dp + 8.dp * blobBoost

    Box(modifier = Modifier.fillMaxSize()) {
        ImageGrid(
            modifier = Modifier
                .drawWithContent {
                    backgroundLayer.record { this@drawWithContent.drawContent() }
                    drawContent()
                }
                .floatingToolbarVerticalNestedScroll(
                    expanded = expanded,
                    onExpand = { expanded = true },
                    onCollapse = { expanded = false }
                )
        )

        GlassPanel(shader, backgroundLayer, glassShapes.filterNotNull(), mergeRadius = mergeRadius)

        CustomHorizontalFloatingToolbar(
            glassShapes = glassShapes,
            progress = progress,
            onToggleFab = { showFab = !showFab },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun CustomHorizontalFloatingToolbar(
    glassShapes: MutableList<GlassBounds?>,
    progress: Float,
    onToggleFab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Shrinking (and fading) lags behind the slide so the fab's glass shape
    // stays full-size while sliding toward the pill, giving mergeRadius time
    // to blob the two shapes together before the fab shape shrinks away.
    val shrinkDelay = 0.35f
    val shrinkProgress = ((progress - shrinkDelay) / (1f - shrinkDelay)).coerceIn(0f, 1f)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Main Content
        Row(
            modifier = Modifier
                .padding(8.dp)
                .clip(CircleShape)
                .reportGlassBounds(density, cornerRadius = 999.dp) { glassShapes[0] = it }
                .zIndex(1f)
        ) {
            IconButton(onClick = onToggleFab) {
                Icon(
                    painter = painterResource(R.drawable.ic_home),
                    contentDescription = "Home",
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(R.drawable.ic_person),
                    contentDescription = "Profile",
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = "Search",
                )
            }
            IconButton(onClick = {}) {
                Icon(
                    painter = painterResource(R.drawable.ic_chat),
                    contentDescription = "Chat",
                )
            }
        }

        // Fab
        FloatingActionButton(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val padding = 8.dp.toPx()
                    val offsetWidth = placeable.width + padding
                    val fabOffset = (progress * offsetWidth).roundToInt()
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(-fabOffset, 0)
                    }
                }
                // Placed below graphicsLayer so it reports the un-transformed
                // layout box; shrink the reported glass shape by
                // `shrinkProgress` ourselves to track the FAB's visual
                // scale-out, delayed relative to the slide.
                .onPlaced { coords ->
                    val size = coords.size
                    val scale = 1f - shrinkProgress
                    val halfSize = Size(size.width / 2f * scale, size.height / 2f * scale)
                    glassShapes[1] = GlassBounds(
                        center = coords.positionInRoot() + Offset(size.width / 2f, size.height / 2f),
                        halfSize = halfSize,
                        // Material3's regular FAB corner radius, not a full circle.
                        cornerRadius = with(density) { 16.dp.toPx() } * scale,
                    )
                }
                .graphicsLayer {
                    alpha = 1f - shrinkProgress
                }
            ,
            onClick = {},
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "Add"
            )
        }
    }
}

@Composable
private fun ImageGrid(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(images.chunked(2)) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { cell ->
                    AsyncImage(
                        modifier = Modifier
                            .weight(1f)
                            .height(300.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        model = cell.url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ToolbarExamplesPreview() {
    LiquidKlassTheme {
        ToolbarExamples()
    }
}