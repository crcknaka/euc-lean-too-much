package com.eucleantoomuch.game.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.utils.Disposable

/**
 * Post-processing effects for the game:
 * - Motion blur: directional blur based on speed
 * - Vignette: darkening at screen edges, intensifies with danger
 * - Danger tint: red overlay when PWM is high
 * - Chromatic aberration: color separation during wobble/impact
 */
class PostProcessing : Disposable {
    private var frameBuffer: FrameBuffer? = null
    private var shader: ShaderProgram? = null
    private var screenQuad: Mesh? = null

    // Motion blur parameters
    var blurStrength = 0f  // 0 = no blur, 1 = max blur
    var blurDirection = 0f to -1f  // Direction of movement (normalized)

    // Vignette parameters
    var vignetteStrength = 0.3f  // Base vignette (always present)
    var vignetteDanger = 0f  // Extra vignette when in danger (0-1)

    // Danger tint (red overlay)
    var dangerTint = 0f  // 0 = no tint, 1 = full red danger

    // Chromatic aberration
    var chromaticAberration = 0f  // 0 = none, 1 = strong

    private var enabled = true
    private var initialized = false

    private val vertexShader = """
        attribute vec4 a_position;
        attribute vec2 a_texCoord0;
        varying vec2 v_texCoord;

        void main() {
            v_texCoord = a_texCoord0;
            gl_Position = a_position;
        }
    """.trimIndent()

    // Combined post-processing shader
    private val fragmentShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        varying vec2 v_texCoord;
        uniform sampler2D u_texture;

        // Motion blur
        uniform float u_blurStrength;
        uniform vec2 u_blurDirection;

        // Vignette
        uniform float u_vignetteStrength;
        uniform float u_vignetteDanger;

        // Danger tint
        uniform float u_dangerTint;

        // Chromatic aberration
        uniform float u_chromatic;

        const int BLUR_SAMPLES = 12;

        void main() {
            vec2 center = vec2(0.5, 0.5);
            vec2 fromCenter = v_texCoord - center;
            float distFromCenter = length(fromCenter) * 1.4;

            // === CHROMATIC ABERRATION ===
            vec4 color;
            if (u_chromatic > 0.001) {
                // Offset RGB channels based on distance from center
                float aberrationAmount = u_chromatic * distFromCenter * 0.02;
                vec2 dir = normalize(fromCenter + vec2(0.001));

                float r = texture2D(u_texture, v_texCoord + dir * aberrationAmount).r;
                float g = texture2D(u_texture, v_texCoord).g;
                float b = texture2D(u_texture, v_texCoord - dir * aberrationAmount).b;
                color = vec4(r, g, b, 1.0);
            } else {
                color = texture2D(u_texture, v_texCoord);
            }

            // === MOTION BLUR ===
            if (u_blurStrength > 0.001) {
                float edgeFactor = distFromCenter * distFromCenter;
                float sideBlur = abs(fromCenter.x) * 2.0;
                float blur = u_blurStrength * (edgeFactor + sideBlur * 0.3);

                if (blur > 0.001) {
                    vec2 blurVec = u_blurDirection * blur * 0.15;

                    vec4 blurColor = vec4(0.0);
                    float totalWeight = 0.0;

                    for (int i = 0; i < BLUR_SAMPLES; i++) {
                        float t = float(i) / float(BLUR_SAMPLES - 1) - 0.5;
                        float weight = 1.0 - abs(t) * 1.5;
                        weight = max(weight, 0.1);

                        vec2 samplePos = clamp(v_texCoord + blurVec * t, 0.0, 1.0);

                        if (u_chromatic > 0.001) {
                            // Apply chromatic aberration to blur samples too
                            float aberrationAmount = u_chromatic * distFromCenter * 0.02;
                            vec2 dir = normalize(fromCenter + vec2(0.001));
                            float r = texture2D(u_texture, samplePos + dir * aberrationAmount).r;
                            float g = texture2D(u_texture, samplePos).g;
                            float b = texture2D(u_texture, samplePos - dir * aberrationAmount).b;
                            blurColor += vec4(r, g, b, 1.0) * weight;
                        } else {
                            blurColor += texture2D(u_texture, samplePos) * weight;
                        }
                        totalWeight += weight;
                    }
                    color = blurColor / totalWeight;
                }
            }

            // === DANGER TINT ===
            if (u_dangerTint > 0.001) {
                // Red tint that pulses slightly
                vec3 dangerColor = vec3(0.8, 0.1, 0.1);
                color.rgb = mix(color.rgb, dangerColor, u_dangerTint * 0.3);
                // Also desaturate slightly
                float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
                color.rgb = mix(color.rgb, vec3(gray), u_dangerTint * 0.2);
            }

            // === VIGNETTE ===
            float vignetteAmount = u_vignetteStrength + u_vignetteDanger * 0.5;
            float vignette = 1.0 - distFromCenter * vignetteAmount;
            vignette = clamp(vignette, 0.0, 1.0);
            vignette = vignette * vignette;  // Quadratic falloff

            // Danger vignette has red tint
            if (u_vignetteDanger > 0.01) {
                vec3 dangerVignetteColor = vec3(0.15, 0.0, 0.0);
                float dangerVignetteMask = (1.0 - vignette) * u_vignetteDanger;
                color.rgb = mix(color.rgb, dangerVignetteColor, dangerVignetteMask * 0.7);
            }

            color.rgb *= vignette;

            gl_FragColor = color;
        }
    """.trimIndent()

    fun initialize() {
        if (initialized) return

        ShaderProgram.pedantic = false
        shader = ShaderProgram(vertexShader, fragmentShader)

        if (!shader!!.isCompiled) {
            Gdx.app.error("PostProcessing", "Shader compilation failed: ${shader!!.log}")
            enabled = false
            return
        }

        // Create fullscreen quad
        screenQuad = Mesh(
            true, 4, 6,
            VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "a_texCoord0")
        ).apply {
            setVertices(floatArrayOf(
                -1f, -1f, 0f, 0f,
                 1f, -1f, 1f, 0f,
                 1f,  1f, 1f, 1f,
                -1f,  1f, 0f, 1f
            ))
            setIndices(shortArrayOf(0, 1, 2, 2, 3, 0))
        }

        initialized = true
    }

    fun begin() {
        if (!enabled || !initialized) return

        val width = Gdx.graphics.width
        val height = Gdx.graphics.height

        if (frameBuffer == null || frameBuffer!!.width != width || frameBuffer!!.height != height) {
            frameBuffer?.dispose()
            frameBuffer = FrameBuffer(Pixmap.Format.RGBA8888, width, height, true)
        }

        frameBuffer?.begin()
    }

    fun end() {
        if (!enabled || !initialized || frameBuffer == null) return

        frameBuffer?.end()

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        shader?.bind()
        shader?.setUniformf("u_blurStrength", blurStrength)
        shader?.setUniformf("u_blurDirection", blurDirection.first, blurDirection.second)
        shader?.setUniformf("u_vignetteStrength", vignetteStrength)
        shader?.setUniformf("u_vignetteDanger", vignetteDanger)
        shader?.setUniformf("u_dangerTint", dangerTint)
        shader?.setUniformf("u_chromatic", chromaticAberration)
        shader?.setUniformi("u_texture", 0)

        frameBuffer?.colorBufferTexture?.bind(0)
        screenQuad?.render(shader, GL20.GL_TRIANGLES)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun isEnabled() = enabled && initialized

    override fun dispose() {
        frameBuffer?.dispose()
        shader?.dispose()
        screenQuad?.dispose()
        frameBuffer = null
        shader = null
        screenQuad = null
        initialized = false
    }
}
