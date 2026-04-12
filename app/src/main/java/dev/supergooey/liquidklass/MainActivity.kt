package dev.supergooey.liquidklass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language

@Language("AGSL")
val GlassShader = """
    // Glass lens shader for AGSL (Android Graphics Shading Language).
    // Applies refraction distortion, chromatic aberration, and fresnel
    // to an input composable (typically an already-blurred background).
    // Blur is handled by the platform via RenderEffect.createBlurEffect(),
    // so this shader only handles the optical glass effects.

    uniform shader composable;  // Input content (blurred background layer)
    uniform float2 resolution;  // Size of the element in pixels

    // --- Fresnel reflectance (Schlick approximation) ---
    // Simulates how glass reflects more light at glancing angles.
    // ior = index of refraction (1.5 for typical glass)
    float fresnel(float cosTheta, float ior) {
        float r0 = (1.0 - ior) / (1.0 + ior);
        r0 = r0 * r0;
        return r0 + (1.0 - r0) * pow(1.0 - cosTheta, 5.0);
    }

    half4 main(float2 coords) {
        // Center of the element
        float2 center = resolution * 0.5;

        // Vector from center to current pixel
        float2 offset = coords - center;

        // Normalized distance from center (0 at center, 1 at shortest edge)
        float dist = length(offset) / min(resolution.x, resolution.y) * 2.0;

        // --- Barrel lens distortion ---
        // Pixels near the center are pushed outward (magnified),
        // pixels near the edge are pushed inward (compressed).
        // This creates the look of a convex glass lens.
        float distortionStrength = 0.1;
        float distortion = 1.0 - distortionStrength * dist * dist;

        // --- Chromatic aberration ---
        // Real glass refracts red, green, and blue wavelengths differently.
        // We sample each channel at a slightly different distortion level.
        float2 redCoords   = center + offset * distortion * 0.9;
        float2 greenCoords = center + offset * distortion;
        float2 blueCoords  = center + offset * distortion * 1.1 ;

        float r = composable.eval(redCoords).r;
        float g = composable.eval(greenCoords).g;
        float b = composable.eval(blueCoords).b;

        half3 color = half3(r, g, b);

        // --- Glass tint ---
        // Slight cool tint and brightness boost to sell the glass look
//        color *= half3(0.95, 0.98, 1.0);
//        color += half3(0.15);

        // --- Fresnel edge glow ---
        // At glancing angles (edges of the lens), glass reflects more light.
        // We approximate the view angle using distance from center.
        float cosTheta = max(1.0 - dist, 0.0);
        float fresnelAmount = fresnel(cosTheta, 1.5);
        color = mix(color, half3(1.0), half3(fresnelAmount * 0.25));

        return half4(color, 1.0);
    }
""".trimIndent()


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiquidKlassTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val backgroundLayer = rememberGraphicsLayer()
                    var overlayOffset by remember { mutableStateOf(Offset.Zero) }
                    val blurAmount by remember { mutableFloatStateOf(8f) }
                    val shader = remember { RuntimeShader(GlassShader) }

                    // background
                    Image(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                backgroundLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(backgroundLayer)
                            },
                        painter = painterResource(R.drawable.icecream),
                        contentScale = ContentScale.Crop,
                        contentDescription = null
                    )

                    // apply background blur

                    val blurLayer = rememberGraphicsLayer()

                    Box(
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .width(200.dp)
                            .height(100.dp)
                            .clip(CircleShape)
                            .align(Alignment.BottomCenter)
                            .onGloballyPositioned { coords ->
                                overlayOffset = coords.positionInRoot()
                            }
                            .drawWithContent {
                                blurLayer.record {
                                    translate(-overlayOffset.x, -overlayOffset.y) {
                                        drawLayer(backgroundLayer)
                                    }
                                }

                                // Set the resolution uniform so the shader knows the element size
                                shader.setFloatUniform("resolution", size.width, size.height)

                                // Chain: blur first (platform-level), then glass distortion shader
                                val blurEffect = RenderEffect.createBlurEffect(blurAmount, blurAmount, Shader.TileMode.CLAMP)
                                val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "composable")
                                blurLayer.renderEffect = RenderEffect.createChainEffect(
                                    blurEffect,
                                    shaderEffect,
                                ).asComposeRenderEffect()
                                
                                drawLayer(blurLayer)
                                drawContent()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Hello", fontSize = 32.sp)
                    }
                }
            }
        }
    }
}
