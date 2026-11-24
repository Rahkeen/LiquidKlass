package dev.supergooey.liquidklass.material

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import kotlin.math.roundToInt

/**
 * Floating, draggable panel for tuning [GlassAppearance] live. Grab the header
 * to drag it out of the way of whatever shape you're looking at, or collapse it
 * down to the header alone.
 */
@Composable
fun GlassControlPanel(
    appearance: GlassAppearance,
    onAppearanceChange: (GlassAppearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var expanded by remember { mutableStateOf(true) }
    // Live on-screen geometry, so a drag can be clamped to the window and the
    // panel can never be dragged somewhere you can't grab it again.
    var panelPosition by remember { mutableStateOf(Offset.Zero) }
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    var windowSize by remember { mutableStateOf(IntSize.Zero) }
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .onGloballyPositioned { coords ->
                panelPosition = coords.positionInRoot()
                panelSize = coords.size
                windowSize = coords.findRootCoordinates().size
            }
            .width(280.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        GlassControlHeader(
            expanded = expanded,
            onToggleExpanded = { expanded = !expanded },
            onReset = { onAppearanceChange(GlassAppearance()) },
            onDrag = { delta ->
                val maxX = (windowSize.width - panelSize.width).toFloat().coerceAtLeast(0f)
                val maxY = (windowSize.height - panelSize.height).toFloat().coerceAtLeast(0f)
                // Clamp the panel's resulting window position, then keep only
                // the part of the delta that survived.
                dragOffset += Offset(
                    (panelPosition.x + delta.x).coerceIn(0f, maxX) - panelPosition.x,
                    (panelPosition.y + delta.y).coerceIn(0f, maxY) - panelPosition.y,
                )
            },
        )

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                ControlSlider(
                    label = "Bevel width",
                    value = appearance.bevelWidth.value,
                    // Zero would divide by zero in the shader's height ramp.
                    valueRange = 1f..80f,
                    readout = { "${it.roundToInt()} dp" },
                    onValueChange = { onAppearanceChange(appearance.copy(bevelWidth = it.dp)) },
                )
                ControlSlider(
                    label = "Extrusion",
                    value = appearance.extrusion.value,
                    valueRange = 0f..40f,
                    readout = { "${it.roundToInt()} dp" },
                    onValueChange = { onAppearanceChange(appearance.copy(extrusion = it.dp)) },
                )
                ControlSlider(
                    label = "Strength",
                    value = appearance.strength,
                    valueRange = 0f..200f,
                    readout = { "${it.roundToInt()} px" },
                    onValueChange = { onAppearanceChange(appearance.copy(strength = it)) },
                )
            }
        }
    }
}

@Composable
private fun GlassControlHeader(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onReset: () -> Unit,
    onDrag: (Offset) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount -> onDrag(dragAmount) }
            }
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
    ) {
        Text(
            text = "Shader",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Row(modifier = Modifier.align(Alignment.CenterEnd)) {
            IconButton(onClick = onReset) {
                Icon(Icons.Filled.Refresh, contentDescription = "Reset to defaults")
            }
            IconButton(onClick = onToggleExpanded) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                )
            }
        }
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    readout: (Float) -> String,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            Text(
                text = readout(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
        )
    }
}

@Preview
@Composable
private fun GlassControlPanelPreview() {
    LiquidKlassTheme {
        var appearance by remember { mutableStateOf(GlassAppearance()) }
        GlassControlPanel(
            appearance = appearance,
            onAppearanceChange = { appearance = it },
        )
    }
}
