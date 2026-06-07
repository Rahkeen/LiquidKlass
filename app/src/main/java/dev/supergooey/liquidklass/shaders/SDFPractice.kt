package dev.supergooey.liquidklass.shaders

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import org.intellij.lang.annotations.Language
import java.nio.file.WatchEvent

@Language("AGSL")
val lessonOne = """
    uniform float radius;
    uniform float2 center;
    
    float sdCircle(float2 coord, float2 center, float radius) {
        return length(coord - center) - radius;
    }
    
    half4 main(float2 coord) {
        float d = sdCircle(coord, center, radius);
        half3 color = d < 0.0 ? half3(0.0) : half3(1.0);
        return half4(color, 1.0);
    }
""".trimIndent()

@Composable
private fun LessonOne() {
    val shader = remember { RuntimeShader(lessonOne) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        shader.setFloatUniform(
            "radius",
            150.dp.toPx()
        )
        shader.setFloatUniform(
            "center",
            size.width / 2f,
            size.height / 2f
        )
        drawRect(
            brush = ShaderBrush(shader)
        )
    }
}

@Language("AGSL")
val lessonTwo = """
    uniform float radius;
    uniform float2 center;
    
    float sdCircle(float2 coord, float2 center, float radius) {
        return length(coord - center) - radius;
    }
    
    half4 main(float2 coord) {
        float d = sdCircle(coord, center, radius);
        float interp = smoothstep(-1.0, 1.0, d);
        half3 color = mix(half3(0.0), half3(1.0), interp);
      
        return half4(color, 1.0);
    }
    
""".trimIndent()

@Composable
private fun LessonTwo() {
    val shader = remember { RuntimeShader(lessonTwo) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        shader.setFloatUniform(
            "radius",
            150.dp.toPx()
        )
        shader.setFloatUniform(
            "center",
            size.width / 2f,
            size.height / 2f
        )
        drawRect(
            brush = ShaderBrush(shader)
        )
    }
}


@Language("AGSL")
val lessonThree = """
    uniform float radius;
    uniform float2 circleCenter;
    uniform float2 pillCenter;
    uniform float2 pillSize;
    
    float sdCircle(float2 coord, float2 center, float radius) {
        return length(coord - center) - radius;
    }
    
    float sdRoundedBox(float2 coord, float2 center, float2 size, float radius) {
        float2 q = abs(coord-center)-size+radius;
        return min(max(q.x,q.y),0.0) + length(max(q,0.0)) - radius;
    }
    
    float smoothMin(float a, float b, float k) {
        float h = max(k - abs(a - b), 0.0) / k;
        return min(a, b) - h * h * k * 0.25;
    }

    half4 main(float2 coord) {
        float d1 = sdCircle(coord, circleCenter, radius);
        float d2 = sdRoundedBox(coord,  pillCenter, pillSize, pillSize.y);
        float d = smoothMin(d1, d2, 80.0);
        
        half4 color = d > 0 ? half4(0.0) : half4(half3(0.0), 1.0);
      
        return color;
    }
    
""".trimIndent()


@Preview
@Composable
private fun LessonThree() {
    val shader = remember { RuntimeShader(lessonThree) }
    var circleCenter by remember { mutableStateOf(Offset(0f, 0f)) }
    val circleRadius = remember { 40.dp }
    var pillCenter by remember { mutableStateOf(Offset(0f, 0f)) }
    val pillSize = remember { DpSize(200.dp, 80.dp) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(R.drawable.lillies),
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )
        Row(
            modifier = Modifier
                .drawWithCache {
                    with(shader) {
                        setFloatUniform(
                            "radius",
                            circleRadius.toPx()
                        )
                        setFloatUniform(
                            "circleCenter",
                            circleCenter.x,
                            circleCenter.y
                        )
                        setFloatUniform(
                            "pillCenter",
                            pillCenter.x,
                            pillCenter.y
                        )
                        setFloatUniform(
                            "pillSize",
                            pillSize.width.toPx() / 2f,
                            pillSize.height.toPx() / 2f
                        )
                        onDrawWithContent {
                            drawRect(
                                brush = ShaderBrush(shader),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
                }
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Box(
                modifier = Modifier
                    .onPlaced { coords ->
                        pillCenter = coords.boundsInParent().center
                    }
                    .size(pillSize)
                    .clip(CircleShape)
                    .background(color = Color.Red)
            )
            Box(
                modifier = Modifier
                    .onPlaced { coords ->
                        circleCenter = coords.boundsInParent().center
                    }
                    .size(circleRadius*2)
                    .clip(CircleShape)
                    .background(color = Color.Red)
            )
        }
    }
}
