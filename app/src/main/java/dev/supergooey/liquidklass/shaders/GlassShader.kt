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
val glassShader = """
    // Inputs from Kotlin.
    uniform shader background;   // The image (optionally blurred) sitting behind the lens.
    uniform float2 resolution;   // Canvas size in pixels.
    uniform float2 center;       // Lens center, in pixels.
    uniform float  radius;       // Lens radius, in pixels.
    uniform float  thickness;    // Width of the refracting bevel — see notes in surfaceNormal.
    uniform float  noiseScale;   // Spatial frequency of the interior jitter (1/px).
    uniform float  noiseStrength;// How many pixels of jitter to apply inside the flat top.

    // ---- Lens shape ----------------------------------------------------------
    // SDF of the lens. Negative inside, zero on the boundary, positive outside.
    float sdCircle(float2 p, float2 c, float r) {
        return length(p - c) - r;
    }

    float sceneSdf(float2 xy) {
        return sdCircle(xy, center, radius);
    }

    // In-plane gradient of the SDF — points outward from the surface.
    // We use it as the xy direction of the refracting surface normal.
    float2 sdfGradient(float2 p) {
        const float epsilon = 0.001;
        float dx = sceneSdf(p + float2(epsilon, 0.0)) - sceneSdf(p - float2(epsilon, 0.0));
        float dy = sceneSdf(p + float2(0.0, epsilon)) - sceneSdf(p - float2(0.0, epsilon));
        return float2(dx, dy) / (2.0 * epsilon);
    }

    // ---- 2D value noise ------------------------------------------------------
    // Cheap hash-based smoothed noise. Returns a scalar in [0, 1].
    float hash21(float2 p) {
        p = fract(p * float2(123.34, 456.21));
        p += dot(p, p + 78.233);
        return fract(p.x * p.y);
    }

    float valueNoise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);            // smoothstep interpolation
        float a = hash21(i);
        float b = hash21(i + float2(1.0, 0.0));
        float c = hash21(i + float2(0.0, 1.0));
        float d = hash21(i + float2(1.0, 1.0));
        return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
    }

    // Two decorrelated noise samples remapped to [-1, 1] for a 2D offset.
    float2 noiseOffset(float2 p) {
        return float2(
            valueNoise(p) * 2.0 - 1.0,
            valueNoise(p + float2(31.4, 27.1)) * 2.0 - 1.0
        );
    }

    // ---- Virtual 3D lens surface --------------------------------------------
    // We pretend the lens is a quarter-pipe bevel of width `thickness` running
    // around the SDF boundary, with a flat top in the interior:
    //
    //   sd ==          0   -> normal lies in the xy plane (max refraction).
    //   sd == -thickness   -> normal points straight up   (no refraction).
    //   sd <  -thickness   -> clamped flat (interior of the lens).
    //
    // That is why all the distortion shows up as a ring near the edge —
    // anything more than `thickness` pixels inside the circle is treated as
    // a flat pane of glass. Increase `thickness` to widen the bevel.
    float3 surfaceNormal(float sd, float2 gradient) {
        float nCos = max(thickness + sd, 0.0) / thickness;  // cos of angle with xy plane
        float nSin = sqrt(1.0 - nCos * nCos);
        return normalize(float3(gradient.x * nCos, gradient.y * nCos, nSin));
    }

    // Height of the lens surface above the background plane.
    // Matches the quarter-circle profile assumed by surfaceNormal.
    float lensHeight(float sd) {
        if (sd >= 0.0)        return 0.0;
        if (sd < -thickness)  return thickness;
        float x = thickness + sd;
        return sqrt(thickness * thickness - x * x);
    }

    // ---- Shading -------------------------------------------------------------
    half4 shadeGlass(float sd, float2 gradient, float2 fragCoord) {
        const float ior                 = 1.5;   // refractive index of glass
        const float chromaticAberration = 0.03;  // RGB split strength
        const float distortionScale     = 2.0;   // exaggerate the xy normal
        const float transmission        = 0.9;   // fraction of light passing through

        float3 normal   = surfaceNormal(sd, gradient * distortionScale);
        float3 incident = float3(0.0, 0.0, -1.0); // viewer looking into the screen

        // Snell refraction through the surface.
        float3 refractVec = refract(incident, normal, 1.0 / ior);

        // Walk the refracted ray from the lens surface down to the background
        // plane and find where it lands.
        float h          = lensHeight(sd);
        float baseHeight = thickness * 8.0; // virtual distance to the background
        float rayLength  = (h + baseHeight) / dot(float3(0.0, 0.0, -1.0), refractVec);
        float2 hitCoord  = fragCoord + refractVec.xy * rayLength;

        // Noise jitter for the flat top of the lens.
        // `flatness` is 0 along the bevel and ramps to 1 once we're a full
        // `thickness` past the bevel, so the noise doesn't fight the rim
        // refraction — it only kicks in where the bevel produces no offset.
        float flatness = clamp((-sd - thickness) / thickness, 0.0, 1.0);
        hitCoord += noiseOffset(fragCoord * noiseScale) * noiseStrength * flatness;

        float2 uv        = hitCoord / resolution;

        // Sample R/G/B with a small offset for chromatic dispersion.
        float2 dispersion = refractVec.xy * chromaticAberration;
        float r = background.eval((uv - dispersion) * resolution).r;
        float g = background.eval( uv               * resolution).g;
        float b = background.eval((uv + dispersion) * resolution).b;
        half4 refracted = half4(r, g, b, 1.0);

        // Fresnel: edges reflect more, the center transmits more.
        // Reflection color is left at black for a subtle edge darkening.
        float fresnel  = pow(1.0 - abs(dot(incident, normal)), 3.0);
        half4 reflected = half4(0.0);
        return mix(refracted, reflected, fresnel * (1.0 - transmission));
    }

    half4 render(float2 xy) {
        float sd = sceneSdf(xy);
        if (sd > 0.0) {
            return background.eval(xy);                  // outside the lens — pass through
        }
        return shadeGlass(sd, sdfGradient(xy), xy);
    }

    // 4x4 supersampling for smooth edges.
    half4 main(float2 coord) {
        const int   samples = 4;
        const float weight  = 1.0 / float(samples * samples);
        half4 finalColor = half4(0.0);
        for (int m = 0; m < samples; m++) {
            for (int n = 0; n < samples; n++) {
                float2 offset = float2(float(m), float(n)) / float(samples) - 0.5 / float(samples);
                finalColor += render(coord + offset) * weight;
            }
        }
        return finalColor;
    }
""".trimIndent()

@Preview
@Composable
private fun GlassShaderPlayground() {
    val shader = remember { RuntimeShader(glassShader) }
    val radius = remember { 100.dp }
    val thickness = remember { 5.dp }     // play with this — wider = bigger refracting bevel
    val noiseScale = remember { 0.2f }    // 1/px; higher = finer grain
    val noiseStrength = remember { 6f }    // px of jitter at the center of the flat top

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shader.setFloatUniform("resolution", size.width, size.height)
                    shader.setFloatUniform("center", size.width / 2f, size.height / 2f)
                    shader.setFloatUniform("radius", radius.toPx())
                    shader.setFloatUniform("thickness", thickness.toPx())
                    shader.setFloatUniform("noiseScale", noiseScale)
                    shader.setFloatUniform("noiseStrength", noiseStrength)

                    val glass = RenderEffect.createRuntimeShaderEffect(shader, "background")
                    renderEffect = glass.asComposeRenderEffect()
                },
            painter = painterResource(R.drawable.icecream),
            contentScale = ContentScale.Crop,
            contentDescription = "ice cream"
        )
    }
}
