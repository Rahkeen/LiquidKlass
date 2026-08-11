package dev.supergooey.liquidklass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.supergooey.liquidklass.shaders.practice.FromMemoryPlayground
import dev.supergooey.liquidklass.ui.theme.LiquidKlassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiquidKlassTheme {
                FromMemoryPlayground()
            }
        }
    }
}