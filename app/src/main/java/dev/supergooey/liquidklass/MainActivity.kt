package dev.supergooey.liquidklass

import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.shaders.gooeySdf
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

//@Language("AGSL")
//val GlassShader = """
//    // Glass lens shader for AGSL (Android Graphics Shading Language).
//    // Applies refraction distortion, chromatic aberration, and fresnel
//    // to an input composable (typically an already-blurred background).
//    // Blur is handled by the platform via RenderEffect.createBlurEffect(),
//    // so this shader only handles the optical glass effects.
//
//    uniform shader composable;  // Input content (blurred background layer)
//    uniform float2 resolution;  // Size of the element in pixels
//
//    // --- Fresnel reflectance (Schlick approximation) ---
//    // Simulates how glass reflects more light at glancing angles.
//    // ior = index of refraction (1.5 for typical glass)
//    float fresnel(float cosTheta, float ior) {
//        float r0 = (1.0 - ior) / (1.0 + ior);
//        r0 = r0 * r0;
//        return r0 + (1.0 - r0) * pow(1.0 - cosTheta, 5.0);
//    }
//
//    half4 main(float2 coords) {
//        // Center of the element
//        float2 center = resolution * 0.5;
//
//        // Vector from center to current pixel
//        float2 offset = coords - center;
//
//        // Normalized distance from center (0 at center, 1 at shortest edge)
//        float dist = length(offset) / min(resolution.x, resolution.y) * 2.0;
//
//        // --- Barrel lens distortion ---
//        // Pixels near the center are pushed outward (magnified),
//        // pixels near the edge are pushed inward (compressed).
//        // This creates the look of a convex glass lens.
//        float distortionStrength = 0.1;
//        float distortion = 1.0 - distortionStrength * dist * dist;
//
//        // --- Chromatic aberration ---
//        // Real glass refracts red, green, and blue wavelengths differently.
//        // We sample each channel at a slightly different distortion level.
//        float2 redCoords   = center + offset * distortion * 0.9;
//        float2 greenCoords = center + offset * distortion;
//        float2 blueCoords  = center + offset * distortion * 1.1 ;
//
//        float r = composable.eval(redCoords).r;
//        float g = composable.eval(greenCoords).g;
//        float b = composable.eval(blueCoords).b;
//
//        half3 color = half3(r, g, b);
//
//        // --- Glass tint ---
//        // Slight cool tint and brightness boost to sell the glass look
////        color *= half3(0.95, 0.98, 1.0);
////        color += half3(0.15);
//
//        // --- Fresnel edge glow ---
//        // At glancing angles (edges of the lens), glass reflects more light.
//        // We approximate the view angle using distance from center.
//        float cosTheta = max(1.0 - dist, 0.0);
//        float fresnelAmount = fresnel(cosTheta, 1.5);
//        color = mix(color, half3(1.0), half3(fresnelAmount * 0.25));
//
//        return half4(color, 1.0);
//    }
//""".trimIndent()


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiquidKlassTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val backgroundLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    val infinite = rememberInfiniteTransition()
    var circleOffset by remember { mutableStateOf(Offset.Zero) }
    var circleCenter by remember { mutableStateOf(Offset.Zero) }
    val circleRadius = remember { 50.dp }
    var rectCenter by remember { mutableStateOf(Offset.Zero) }
    val rectSize = remember { DpSize(width = 200.dp, height = 100.dp) }
    val blurStrength by remember { mutableFloatStateOf(6 0f) }

    val shader = remember { RuntimeShader(gooeySdf) }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    backgroundLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(backgroundLayer)
                },
            painter = painterResource(R.drawable.lillies),
            contentScale = ContentScale.Crop,
            contentDescription = ""
        )

        Row(
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .fillMaxSize()
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
                        100f
                    )

                    onDrawWithContent {
                        blurLayer.record { drawLayer(backgroundLayer) }
                        blurLayer.renderEffect = RenderEffect
                            .createBlurEffect(blurStrength, blurStrength, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                        drawLayer(graphicsLayer = blurLayer)
                        // Tint layer — makes the glass region visually distinct
                        drawRect(color = Color.White.copy(alpha = 0.15f))
                        // Mask everything to the gooey SDF shape
                        drawRect(brush = ShaderBrush(shader), blendMode = BlendMode.DstIn)
                    }
                }
            ,
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(rectSize)
                    .onGloballyPositioned { coords ->
                        rectCenter = coords.boundsInRoot().center
                    }
            )
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = circleOffset.x
                        translationY = circleOffset.y
                    }
                    .size(circleRadius*2)
                    .onGloballyPositioned { coords ->
                        circleCenter = coords.boundsInRoot().center
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            circleOffset += dragAmount
                        }
                    }
            )
        }

    }
}