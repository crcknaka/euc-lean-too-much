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
 * Post-processing effects for the game.
 * Currently supports radial motion blur based on speed.
 */
class PostProcessing : Disposable {
    private var frameBuffer: FrameBuffer? = null
    private var shader: ShaderProgram? = null
    private var screenQuad: Mesh? = null

    // Effect parameters
    var blurStrength = 0f  // 0 = no blur, 1 = max blur
    var blurCenter = 0.5f to 0.5f  // Screen center (0-1)

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

    private val fragmentShader = """
        #ifdef GL_ES
        precision mediump float;
        #endif

        varying vec2 v_texCoord;
        uniform sampler2D u_texture;
        uniform float u_blurStrength;
        uniform vec2 u_blurCenter;

        const int SAMPLES = 8;

        void main() {
            vec2 dir = v_texCoord - u_blurCenter;
            float dist = length(dir);

            // Blur increases with distance from center
            float blur = u_blurStrength * dist * 0.5;

            vec4 color = vec4(0.0);
            float total = 0.0;

            for (int i = 0; i < SAMPLES; i++) {
                float t = float(i) / float(SAMPLES - 1);
                float weight = 1.0 - t * 0.5;  // Closer samples have more weight
                vec2 offset = dir * blur * t;
                color += texture2D(u_texture, v_texCoord - offset) * weight;
                total += weight;
            }

            gl_FragColor = color / total;
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
                -1f, -1f, 0f, 0f,  // bottom-left
                 1f, -1f, 1f, 0f,  // bottom-right
                 1f,  1f, 1f, 1f,  // top-right
                -1f,  1f, 0f, 1f   // top-left
            ))
            setIndices(shortArrayOf(0, 1, 2, 2, 3, 0))
        }

        initialized = true
    }

    fun begin() {
        if (!enabled || !initialized) return

        // Create or resize framebuffer if needed
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

        // Skip effect if blur is negligible
        if (blurStrength < 0.01f) {
            renderWithoutEffect()
            return
        }

        // Apply radial blur effect
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        shader?.bind()
        shader?.setUniformf("u_blurStrength", blurStrength)
        shader?.setUniformf("u_blurCenter", blurCenter.first, blurCenter.second)
        shader?.setUniformi("u_texture", 0)

        frameBuffer?.colorBufferTexture?.bind(0)
        screenQuad?.render(shader, GL20.GL_TRIANGLES)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    private fun renderWithoutEffect() {
        // Just blit the framebuffer without any effect
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        // Simple passthrough - use shader with zero blur
        shader?.bind()
        shader?.setUniformf("u_blurStrength", 0f)
        shader?.setUniformf("u_blurCenter", 0.5f, 0.5f)
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
