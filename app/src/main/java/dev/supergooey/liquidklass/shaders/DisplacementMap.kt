package dev.supergooey.liquidklass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.supergooey.liquidklass.R
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme
import org.intellij.lang.annotations.Language

/**
 * We are gonna be practicing creating a glass like effect using computed displacement maps.
 *
 * Currently:
 * - Setting up the shader + environment
 * - Getting some videos lined up for displacement maps
 */

@Language("AGSL")
private val displacementShader = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float radius;
    
    const float refraction = 0.7;
    const float rimBoost = 0.4;
    const float rim = 10.0;
    
    float sdCircle(float2 p, float2 c, float r) {
        return length(p - c) - r;
    }
    
    half4 main(float2 point) {
        float2 p = point - center; // point relative to circle center
        float dist = length(p);
        float d = dist - radius;
        
        if (d > rim) {
            return background.eval(point);
        }
        
        float2 dir = normalize(p);
        
        // spherical bulge
        float t = clamp(dist / radius, 0.0, 1.0);
        float baseFalloff = t * t;
        
        // rim boost mask
        float rimMask = 1.0 - smoothstep(0.0, rim, abs(d));
        
        // combine 
        float totalStrength = refraction * baseFalloff + rimBoost * rimMask;
        float2 displacement = dir * totalStrength * radius; 
        float2 targetCoord = point + displacement;
        
        return background.eval(targetCoord);
    }
""".trimIndent()

@Preview
@Composable
private fun DisplacementMap1() {
    LiquidKlassTheme {
        val shader = remember { RuntimeShader(displacementShader) }
        val radius = remember { 100.dp }

        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        with(shader) {
                            setFloatUniform("resolution", size.width, size.height)
                            setFloatUniform("center", size.width * 0.5f, size.height * 0.5f)
                            setFloatUniform("radius", radius.toPx())
                        }
                        renderEffect = RenderEffect.createRuntimeShaderEffect(
                            shader,
                            "background"
                        ).asComposeRenderEffect()
                    },
                painter = painterResource(R.drawable.bikes),
                contentScale = ContentScale.Crop,
                contentDescription = "bikes"
            )
        }
    }
}