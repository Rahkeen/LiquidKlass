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
import dev.supergooey.liquidklass.R
import org.intellij.lang.annotations.Language

@Language("AGSL")
val bevelGlassShader = """
    uniform shader background;
    
    half4 main(float2 point) {
        return background.eval(point).bgra;
    }
    
""".trimIndent()

@Preview
@Composable
fun BevelGlass() {
    val shader = remember { RuntimeShader(bevelGlassShader) }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    renderEffect = RenderEffect.createRuntimeShaderEffect(
                        shader,
                        "background"
                    ).asComposeRenderEffect()
                },
            painter = painterResource(R.drawable.bikes),
            contentScale = ContentScale.Crop,
            contentDescription = "Bikes"
        )
    }
}
