package dev.supergooey.liquidklass.material

import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.FloatingToolbarDefaults.floatingToolbarVerticalNestedScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
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
    var selected by remember { mutableStateOf(NavigationTabs.Home) }
    var expanded by remember { mutableStateOf(true) }

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
        
        CustomHorizontalFloatingToolbar(modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun CustomHorizontalFloatingToolbar(modifier: Modifier = Modifier) {
    var showFab by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (showFab) 0f else 1f,
        animationSpec = tween(easing = EaseOutQuad, durationMillis = 1000)
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        // Main Content
        Row(
            modifier = Modifier
                .padding(8.dp)
                .clip(CircleShape)
                .background(color = MaterialTheme.colorScheme.primaryContainer)
                .zIndex(1f)
        ) {
            IconButton(onClick = {
                showFab = !showFab
            }) {
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
                .graphicsLayer {
                    this.alpha = 1f - progress
                }
            ,
            onClick = {},
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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