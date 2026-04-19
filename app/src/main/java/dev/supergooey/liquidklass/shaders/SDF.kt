package dev.supergooey.liquidklass.shaders

import android.graphics.RuntimeShader
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.intellij.lang.annotations.Language

@Language("AGSL")
val circleSdf = """
    uniform float2 resolution;
    
    float sdCircle(float2 p, float r) {
        return length(p) - r;
    }
    
    half4 main(float2 coord) {
        // normalized coords, -1..1, origin at center
        float2 uv = (2.0*coord-resolution.xy) / resolution.y;
        float radius = 0.5;
        float2 center = float2(0.5, 0.5 );
        float d = smoothstep(radius, radius+0.01, length(uv - center));
        float3 color = float3(d);
        
        return half4(color, 1.0); 
    }
""".trimIndent()

@Language("AGSL")
val iqCircleSdf = """
    uniform float2 resolution;
    
    float sdCircle(float2 p, float r) {
        return length(p) - r;
    }
    
    half4 main(float2 coord) {
        // normalized coords, -1..1, origin at center
        float2 uv = (2.0*coord-resolution.xy) / resolution.y;
        float radius = 0.5;
        float2 center = float2(0.0);
        float d = sdCircle(uv-center, radius);
        
        float3 color = (d > 0.0) ? float3(0.9, 0.6, 0.3) : float3(0.65, 0.85, 1.0);
        color *= 1.0 - exp(-6.0*abs(d));
        color *= 0.8 + 0.2*cos(150.0*d);
        color = mix(color, float3(1.0), 1.0 - smoothstep(0.0, 0.01, abs(d)));

        
        return half4(color, 1.0); 
    }
""".trimIndent()

@Language("AGSL")
val roundedRectSdf = """
    uniform float2 resolution;
    
    float sdCircle(float2 p, float r) {
        return length(p) - r;
    }
    
    float sdRoundedBox(float2 p, float2 b, float r) {
        vec2 q = abs(p)-b+r;
        return min(max(q.x,q.y),0.0) + length(max(q,0.0)) - r;
    }
    
    half4 main(float2 coord) {
        // normalized coords, -1..1, origin at center
        float2 uv = (2.0*coord-resolution.xy) / resolution.y;
        float d = sdRoundedBox(uv, float2(0.8, 0.3), 0.3);
        
        float3 color = (d > 0.0) ? float3(0.9, 0.6, 0.3) : float3(0.65, 0.85, 1.0);
        color *= 1.0 - exp(-6.0*abs(d));
        color *= 0.8 + 0.2*cos(150.0*d);
        color = mix(color, float3(1.0), 1.0 - smoothstep(0.0, 0.01, abs(d)));
        
        return half4(color, 1.0);
    }
""".trimIndent()

@Preview
@Composable
private fun RoundedRectSDF() {
    val shader = remember { RuntimeShader(roundedRectSdf) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        shader.setFloatUniform(
            "resolution",
            size.width,
            size.height
        )
        drawRect(
            brush = ShaderBrush(shader)
        )
    }
}

@Language("AGSL")
val gooeySdf = """
    uniform float2 resolution;
    uniform float2 circleCenter;
    uniform float  circleRadius;
    uniform float2 rectCenter;
    uniform float2 rectSize;
    uniform float  rectRadius;
    uniform float  smoothK;
    
    float smin(float a, float b, float k) {
        float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
        return mix(b, a, h) - k * h * (1.0 - h);
    }
    
    float sdCircle(float2 p, float2 center, float r) {
        return length(p - center) - r;
    }
    
    float sdRoundRect(float2 p, float2 center, float2 b, float r) {
        float2 q = abs(p - center) - b + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }
    
    half4 main(float2 fragCoord) {
        float d1 = sdCircle(fragCoord, circleCenter, circleRadius);
        float d2 = sdRoundRect(fragCoord, rectCenter, rectSize * 0.5, rectRadius);
    
        // Merge SDFs
        float d = smin(d1, d2, smoothK);
        half3 color = half3(0.0);
        float alpha = 1.0 - smoothstep(-1.0, 1.0, d);
        return half4(color, alpha);
    }
""".trimIndent()


@Preview
@Composable
private fun GooeySDF() {
    val infinite = rememberInfiniteTransition()
    val circleOffset by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 96f,
        animationSpec = infiniteRepeatable(
            animation = tween(easing = LinearEasing, durationMillis = 3000),
            repeatMode = RepeatMode.Reverse
        )
    )

    var circleCenter by remember { mutableStateOf(Offset.Zero) }
    val circleRadius = remember { 40.dp }
    var rectCenter by remember { mutableStateOf(Offset.Zero) }
    val rectSize = remember { DpSize(width = 200.dp, height = 80.dp) }

    val shader = remember { RuntimeShader(gooeySdf) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .drawWithCache {
                shader.setFloatUniform(
                    "resolution",
                    size.width,
                    size.height
                )

                shader.setFloatUniform(
                    "circleCenter",
                    circleCenter.x,
                    circleCenter.y
                )
                shader.setFloatUniform(
                    "circleRadius",
                    circleRadius.toPx()
                )

                shader.setFloatUniform(
                    "rectCenter",
                    rectCenter.x,
                    rectCenter.y
                )

                shader.setFloatUniform(
                    "rectSize",
                    rectSize.width.toPx(),
                    rectSize.height.toPx()
                )

                shader.setFloatUniform(
                    "rectRadius",
                    circleRadius.toPx()
                )

                shader.setFloatUniform(
                    "smoothK",
                    40f
                )

                onDrawWithContent {
                    drawRect(ShaderBrush(shader))
                }
            }
        ,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(80.dp)
                .onGloballyPositioned { coords ->
                    rectCenter = coords.boundsInRoot().center
                }
        )
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = -(circleOffset).dp.toPx()
                }
                .size(80.dp)
                .onGloballyPositioned { coords ->
                    circleCenter = coords.boundsInRoot().center
                }
        )
    }
}
