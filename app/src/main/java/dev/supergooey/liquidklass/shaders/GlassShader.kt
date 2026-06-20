package dev.supergooey.liquidklass.shaders

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
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
import org.intellij.lang.annotations.Language

@Language("AGSL")
val glass = """
    uniform shader background;
    uniform float2 resolution;
    uniform float2 center;
    uniform float radius;
    
    float sdCircle(float2 coord, float2 center, float r) {
        return length(coord - center) - r;
    }
    
    float sdRoundedBox(vec2 p, in vec2 b, in vec4 r ) {
        r.xy = (p.x>0.0) ? r.xy : r.zw;
        r.x  = (p.y>0.0) ? r.x  : r.y;
        vec2 q = abs(p)-b+r.x;
        float d = min(max(q.x,q.y),0.0) + length(max(q,0.0)) - r.x;
        return d;
    }
    
    float smin(float a, float b, float k) {
        float h = clamp(0.5 + 0.5*(a-b)/k, 0.0, 1.0);
        return mix(a, b, h) - k*h*(1.0-h);
    }
    
    float sdf(float2 xy) {
        return sdCircle(xy, center, radius);
    }
    
    float2 calculateGradient(vec2 p) {
        const float epsilon = 0.001;
        float dx = sdf(p + vec2(epsilon, 0.0)) - sdf(p - vec2(epsilon, 0.0));
        float dy = sdf(p + vec2(0.0, epsilon)) - sdf(p - vec2(0.0, epsilon));
        return vec2(dx, dy) / (2.0 * epsilon);
    }
    
    float3 getNormal(float sd, float2 gradient, float thickness)
    {
        float dx = gradient.x;
        float dy = gradient.y;
        // The cosine and sine between normal and the xy plane.
        float n_cos = max(thickness + sd, 0.0) / thickness;
        float n_sin = sqrt(1.0 - n_cos * n_cos);
        return normalize(float3(dx * n_cos, dy * n_cos, n_sin));
    }
    
    float height(float sd, float thickness)
    {
        if(sd >= 0.0)
        {
            return 0.0;
        }
        if(sd < -thickness)
        {
            return thickness;
        }
        float x = thickness + sd;
        return sqrt(thickness * thickness - x * x);
    }
    
    vec4 calculateLiquidGlass(float sd, vec2 g, vec2 fragCoord)
    {
        float thickness = 14.0;
        float transmission = 0.9;          // Transmission strength
        float roughness = 0.1;             // Surface roughness
        float ior = 1.5;                   // Index of refraction
        float chromaticAberration = 0.03;  // Chromatic aberration strength
        float distortionScale = 2.0;       // Distortion multiplier
        
        vec3 normal = getNormal(sd, g * distortionScale, thickness);
        vec3 incident = vec3(0.0, 0.0, -1.0);
        
        // Fresnel effect - more reflection at grazing angles
        float fresnel = pow(1.0 - abs(dot(incident, normal)), 3.0);
        
        // Base refraction
        vec3 refract_vec = refract(incident, normal, 1.0/ior);
        float h = height(sd, thickness);
        float base_height = thickness * 8.0;
        float refract_length = (h + base_height) / dot(vec3(0.0, 0.0, -1.0), refract_vec);
        
        // Chromatic aberration - sample RGB channels separately
        vec2 base_coord = fragCoord + refract_vec.xy * refract_length;
        vec2 uv_base = base_coord / resolution.xy;
        
        // Offset each color channel slightly for dispersion
        vec2 offset = refract_vec.xy * chromaticAberration;
        float r = background.eval((uv_base - offset) * resolution).r;
        float g_channel = background.eval(uv_base * resolution).g;
        float b = background.eval((uv_base + offset) * resolution).b;
        vec4 refract_color = vec4(r, g_channel, b, 1.0);
        
        // Roughness-based reflection blur (simplified)
        vec3 reflect_vec = reflect(incident, normal);
        vec4 reflect_color = vec4(0.0);
        
        // Mix reflection and refraction based on fresnel and transmission
        vec4 glass_color = mix(refract_color, reflect_color, fresnel * (1.0 - transmission));
        
        return glass_color;
    }
    
    half4 render(float2 xy) {
      float d = sdf(xy);
      float2 g = calculateGradient(xy);
      if (d > 0.0) {
        return background.eval(xy);
      } else {
        return calculateLiquidGlass(d, g, xy);
      }
    }
    
    half4 main(float2 coord) {
        const int samples = 4;
        float sampleStrength = 1.0/float(samples*samples);
        vec4 finalColor = vec4(0.0);
        
        // Perform supersampling
        for(int m = 0; m < samples; m++) {
          for(int n = 0; n < samples; n++) {
            // Calculate offset for this sample (only if using AA)
            vec2 offset = (samples > 1) ? 
                (vec2(float(m), float(n)) / float(samples) - 0.5/float(samples)) : 
                vec2(0.0);
            
            // Get pixel position with the offset
            vec2 p = coord + offset;
            
            // Render this sample
            vec4 color = render(p);
            // Accumulate color
            finalColor += color * sampleStrength;
          }
        }
        
        return finalColor;
    }
""".trimIndent()

@Preview
@Composable
private fun GlassShaderPlayground() {
    val shader = remember { RuntimeShader(glass) }
    val radius = remember { 100.dp }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shader.setFloatUniform(
                       "resolution",
                        size.width,
                        size.height
                    )
                    shader.setFloatUniform(
                        "center",
                        size.width/2,
                        size.height/2
                    )
                    shader.setFloatUniform(
                        "radius",
                        radius.toPx()
                    )
                    val shaderEffect = RenderEffect.createRuntimeShaderEffect(
                        shader,
                        "background"
                    )
                    val blurEffect = RenderEffect.createBlurEffect(24f, 24f, shaderEffect, Shader.TileMode.CLAMP)
                    renderEffect = shaderEffect.asComposeRenderEffect()
                },
            painter = painterResource(R.drawable.icecream),
            contentScale = ContentScale.Crop,
            contentDescription = "ice cream"
        )
    }
}