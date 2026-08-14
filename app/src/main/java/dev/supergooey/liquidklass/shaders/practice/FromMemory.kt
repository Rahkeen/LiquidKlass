@file:OptIn(ExperimentalMaterial3Api::class)

package dev.supergooey.liquidklass.shaders.practice

import android.graphics.Path
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.Blue400
import dev.supergooey.liquidklass.ui.theme.Cyan400
import dev.supergooey.liquidklass.ui.theme.Green400
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import dev.supergooey.liquidklass.ui.theme.Orange400
import dev.supergooey.liquidklass.ui.theme.Purple400
import dev.supergooey.liquidklass.ui.theme.Red400
import dev.supergooey.liquidklass.ui.theme.Yellow400
import org.intellij.lang.annotations.Language
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Language("AGSL")
private val glassyShader = """
    uniform shader background;
    uniform float2 center;
    uniform float2 halfSize;
    uniform float cornerRadius;
    uniform float extrusion;
    uniform float bevelWidth;
    uniform float strength;
    uniform float aberration;
    uniform int showNormal;

    // positive inside, 0 at edge, negative outside.
    // circle / pill / rounded rect are all this primitive at different ratios.
    float sdRoundedRect(float2 p, float2 halfSize, float cornerRadius) {
        cornerRadius = min(cornerRadius, min(halfSize.x, halfSize.y));
        float2 q = abs(p) - halfSize + cornerRadius;
        return -(length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius);
    }

    float height(float2 point, float2 halfSize, float cornerRadius, float extrusion, float bevelWidth) {
        float d = sdRoundedRect(point, halfSize, cornerRadius);
        float t = clamp(d / bevelWidth, 0.0, 1.0);
        return extrusion * sqrt(1.0 - (1.0 - t) * (1.0 - t));
    }

    float3 computeNormal(float2 point, float2 halfSize, float cornerRadius, float extrusion, float bevelWidth, float eps) {
        float hL = height(point - float2(eps, 0.0), halfSize, cornerRadius, extrusion, bevelWidth);
        float hR = height(point + float2(eps, 0.0), halfSize, cornerRadius, extrusion, bevelWidth);
        float hD = height(point - float2(0.0, eps), halfSize, cornerRadius, extrusion, bevelWidth);
        float hU = height(point + float2(0.0, eps), halfSize, cornerRadius, extrusion, bevelWidth);

        return normalize(float3(hL - hR, hD - hU, 2.0*eps));
    }

    half4 main(float2 point) {
        float2 p = point - center;
        float d = sdRoundedRect(p, halfSize, cornerRadius);
        float aa = 1.0;
        float mask = smoothstep(-aa, aa, d); // smoothes out the the SDF edge

        half4 outside = background.eval(point);
        if (mask <= 0.0) {
            return outside;
        }

        float eps = 2.0;

        float3 n = computeNormal(p, halfSize, cornerRadius, extrusion, bevelWidth, eps);
        half4 normal_map = half4(n * 0.5 + 0.5, 1.0);

        float3 viewDir = float3(0.0, 0.0, 1.0);
        float eta = 1.0 / 1.5;
        float3 refracted = refract(viewDir, n, eta);

        float2 offset = refracted.xy * strength;
        float2 abOffset = refracted.xy * aberration;
        half r = background.eval(point + offset + abOffset).r;
        half g = background.eval(point + offset).g;
        half b = background.eval(point + offset - abOffset).b;
        half4 refracted_color = half4(r,g,b,1.0);

        if (showNormal == 1) {
            return mix(outside, normal_map, mask);
        }
        return mix(outside, refracted_color, mask);
    }
""".trimIndent()

// Circle, rounded rect, and pill are the same primitive — just different
// half-size to corner-radius ratios. (dp)
private enum class GlassShape(
    val halfWidthDp: Float,
    val halfHeightDp: Float,
    val cornerRadiusDp: Float,
) {
    Circle(40f, 40f, 40f),
    RoundedRect(110f, 70f, 40f),
    Pill(120f, 44f, 44f),
}

@Preview
@Composable
fun FromMemoryPlayground() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(glassyShader) }
        var center by remember { mutableStateOf(Offset.Zero) }

        var extrusion by remember { mutableFloatStateOf(10f) }
        var bevelWidth by remember { mutableFloatStateOf(40f) }
        var strength by remember { mutableFloatStateOf(60f) }
        var aberration by remember { mutableFloatStateOf(6f) }
        var showNormal by remember { mutableStateOf(false) }
        var shape by remember { mutableStateOf(GlassShape.Circle) }

        // Animate the geometry toward the selected shape so it morphs between presets.
        val morphSpec = spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
        val halfWidth by animateFloatAsState(shape.halfWidthDp, morphSpec, label = "halfWidth")
        val halfHeight by animateFloatAsState(shape.halfHeightDp, morphSpec, label = "halfHeight")
        val cornerRadius by animateFloatAsState(
            shape.cornerRadiusDp,
            morphSpec,
            label = "cornerRadius"
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top half: the image with the shader applied.
            Image(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { size ->
                        center = Offset(size.center.x.toFloat(), size.height * 0.5f)
                    }
                    .graphicsLayer {
                        with(shader) {
                            setFloatUniform("center", center.x, center.y)
                            setFloatUniform(
                                "halfSize",
                                halfWidth.dp.toPx(),
                                halfHeight.dp.toPx()
                            )
                            setFloatUniform("cornerRadius", cornerRadius.dp.toPx())
                            setFloatUniform("extrusion", extrusion.dp.toPx())
                            setFloatUniform("bevelWidth", bevelWidth.dp.toPx())
                            setFloatUniform("strength", strength)
                            setFloatUniform("aberration", aberration)
                            setIntUniform("showNormal", if (showNormal) 1 else 0)
                        }
                        renderEffect = RenderEffect.createRuntimeShaderEffect(
                            shader,
                            "background"
                        ).asComposeRenderEffect()
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount ->
                            center += dragAmount
                        }
                    },
                painter = painterResource(R.drawable.icecream),
                contentScale = ContentScale.Crop,
                contentDescription = "Bikes"
            )
            // Bottom half: the control panel.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(color = MaterialTheme.colorScheme.background)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShapePresetSection(
                    shape = shape,
                    onShapeSelected = { shape = it },
                    showNormal = showNormal,
                    onShowNormalChange = { showNormal = it }
                )

                HeightProfileSection(
                    extrusion = extrusion,
                    onExtrusionChange = { extrusion = it },
                    bevelWidth = bevelWidth,
                    onBevelWidthChange = { bevelWidth = it },
                    halfWidth = halfWidth,
                    halfHeight = halfHeight,
                    cornerRadius = cornerRadius
                )

                RefractionSection(
                    strength = strength,
                    onStrengthChange = { strength = it },
                    aberration = aberration,
                    onAberrationChange = { aberration = it }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ShapePresetSection(
    shape: GlassShape,
    onShapeSelected: (GlassShape) -> Unit,
    showNormal: Boolean,
    onShowNormalChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color = MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassShape.entries.forEach { option ->
            val onClick = { onShapeSelected(option) }
            val color = if (shape == option) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }

            when (option) {
                GlassShape.Circle -> {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClick)
                            .background(color = color)
                    )
                }

                GlassShape.RoundedRect -> {
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onClick)
                            .background(color = color)
                    )
                }

                GlassShape.Pill -> {
                    Box(
                        modifier = Modifier
                            .size(width = 60.dp, height = 40.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClick)
                            .background(color = color)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier
                .height(40.dp)
                .widthIn(min = 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onShowNormalChange(!showNormal) }
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Text(
                text = "Normals",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelSmall
            )
            Box(contentAlignment = Alignment.Center) {
                // Light
                Box(
                    modifier = Modifier
                        .alpha(if (showNormal) 1f else 0f)
                        .blur(
                            radius = 16.dp,
                            edgeTreatment = BlurredEdgeTreatment.Unbounded
                        )
                        .size(20.dp)
                        .background(color = MaterialTheme.colorScheme.primary)
                )
                // LED
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            shape = CircleShape,
                            color = if (showNormal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceBright
                        )
                )

            }
        }
    }
}

@Composable
private fun HeightProfileSection(
    extrusion: Float,
    onExtrusionChange: (Float) -> Unit,
    bevelWidth: Float,
    onBevelWidthChange: (Float) -> Unit,
    halfWidth: Float,
    halfHeight: Float,
    cornerRadius: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color = MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            DebugSlider(
                label = "Extrusion",
                value = extrusion,
                range = 0f..40f,
                onValueChange = onExtrusionChange
            )
            DebugSlider(
                label = "Bevel",
                value = bevelWidth,
                range = 0f..60f,
                onValueChange = onBevelWidthChange
            )
        }
        val heightProfileColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .width(100.dp)
                .height(80.dp)
                .clip(shape = RoundedCornerShape(6.dp))
                .background(color = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            val halfSize = Offset(halfWidth.dp.toPx(), halfHeight.dp.toPx())
            val radius = min(cornerRadius.dp.toPx(), min(halfSize.x, halfSize.y))
            val bevelWidthPx = bevelWidth.dp.toPx()
            val extrusionPx = extrusion.dp.toPx()
            val profileScale = size.width / (halfSize.x * 2f)

            val path = Path().apply {
                moveTo(0f, size.height)

                for (canvasX in 0..size.width.toInt()) {
                    val pointX = canvasX / profileScale - halfSize.x
                    val qx = abs(pointX) - halfSize.x + radius
                    val qy = -halfSize.y + radius
                    val distance = -(
                            hypot(max(qx, 0f), max(qy, 0f)) +
                                    min(max(qx, qy), 0f) - radius
                            )
                    val t = (distance / bevelWidthPx).coerceIn(0f, 1f)
                    val height = extrusionPx * sqrt(1f - (1f - t) * (1f - t))

                    lineTo(canvasX.toFloat(), size.height - height * profileScale)
                }

                lineTo(size.width, size.height)
                close()
            }

            drawPath(path.asComposePath(), color = heightProfileColor)
        }
    }
}

@ExperimentalMaterial3Api
@Composable
private fun RefractionSection(
    strength: Float,
    onStrengthChange: (Float) -> Unit,
    aberration: Float,
    onAberrationChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color = MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        DebugSlider(
            label = "Strength",
            value = strength,
            range = 0f..100f,
            onValueChange = onStrengthChange
        )
        DebugSlider(
            label = "Aberration",
            value = aberration,
            range = 0f..20f,
            onValueChange = onAberrationChange
        )
    }
}

@ExperimentalMaterial3Api
@Composable
private fun DebugSlider(
    modifier: Modifier = Modifier,
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy((-8).dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
            )
            Box(
                modifier = Modifier.wrapContentSize()
            ) {
                Text(
                    text = "${value.roundToInt()}",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        Slider(
            modifier = modifier,
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    thumbSize = DpSize(4.dp, 20.dp)
                )
            },
        )
    }
}