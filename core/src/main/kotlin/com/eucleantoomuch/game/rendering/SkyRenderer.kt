package com.eucleantoomuch.game.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable

/**
 * The sky: a zenith-to-horizon gradient with a sun, drawn behind everything.
 *
 * It used to be a flat clear colour, which is the single thing that most makes an outdoor
 * scene look like a tech demo - there is no sense of up, no light source to read, and the
 * distance fog has nothing to fade into. This paints the whole background in one triangle:
 * the fragment shader reconstructs the view ray for each pixel from the inverse
 * view-projection, so no dome mesh has to follow the camera around.
 *
 * The horizon colour is shared with the fog, so far geometry dissolves into the band of sky
 * it actually stands in front of. The sun is deliberately brighter than white can show, so
 * the bloom pass picks it up.
 */
class SkyRenderer : Disposable {

    private val zenith = Color(0.22f, 0.45f, 0.85f, 1f)
    private val horizon = Color(0.80f, 0.86f, 0.93f, 1f)
    private val ground = Color(0.55f, 0.58f, 0.60f, 1f)
    private val sunDir = Vector3(0.5f, 1f, 0.3f).nor()
    private val sunColor = Color(1.0f, 0.93f, 0.80f, 1f)
    private var sunScale = 1f

    // One triangle that covers the screen with margin to spare - cheaper than a quad, and
    // avoids the seam a quad's diagonal can show on some mobile GPUs
    private val mesh = Mesh(
        true, 3, 0,
        VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE)
    ).apply {
        setVertices(floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f))
    }

    private val shader = ShaderProgram(VERTEX, FRAGMENT).also {
        if (!it.isCompiled) Gdx.app.error("SkyRenderer", "shader failed: ${it.log}")
    }

    fun setPalette(
        zenith: Color, horizon: Color, ground: Color,
        sunDir: Vector3, sunColor: Color, sunScale: Float
    ) {
        this.zenith.set(zenith)
        this.horizon.set(horizon)
        this.ground.set(ground)
        this.sunDir.set(sunDir).nor()
        this.sunColor.set(sunColor)
        this.sunScale = sunScale
    }

    /** Call right after the clear, before any geometry. Leaves depth state as it found it. */
    fun render(camera: Camera) {
        if (!shader.isCompiled) return
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glDisable(GL20.GL_CULL_FACE)

        shader.bind()
        shader.setUniformMatrix("u_invViewProj", camera.invProjectionView)
        shader.setUniformf("u_zenith", zenith.r, zenith.g, zenith.b)
        shader.setUniformf("u_horizon", horizon.r, horizon.g, horizon.b)
        shader.setUniformf("u_ground", ground.r, ground.g, ground.b)
        shader.setUniformf("u_sunDir", sunDir)
        shader.setUniformf("u_sunColor", sunColor.r, sunColor.g, sunColor.b)
        shader.setUniformf("u_sunScale", sunScale)
        mesh.render(shader, GL20.GL_TRIANGLES)

        Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    override fun dispose() {
        mesh.dispose()
        shader.dispose()
    }

    private companion object {
        val VERTEX = """
            attribute vec2 ${ShaderProgram.POSITION_ATTRIBUTE};
            varying vec2 v_ndc;
            void main() {
                v_ndc = ${ShaderProgram.POSITION_ATTRIBUTE};
                gl_Position = vec4(${ShaderProgram.POSITION_ATTRIBUTE}, 1.0, 1.0);
            }
        """.trimIndent()

        val FRAGMENT = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec2 v_ndc;
            uniform mat4 u_invViewProj;
            uniform vec3 u_zenith;
            uniform vec3 u_horizon;
            uniform vec3 u_ground;
            uniform vec3 u_sunDir;
            uniform vec3 u_sunColor;
            uniform float u_sunScale;

            void main() {
                // View ray: unproject the pixel on the near and far planes and take the difference,
                // which sidesteps the precision loss of subtracting a large camera position
                vec4 a = u_invViewProj * vec4(v_ndc, -1.0, 1.0);
                vec4 b = u_invViewProj * vec4(v_ndc,  1.0, 1.0);
                vec3 dir = normalize(b.xyz / b.w - a.xyz / a.w);

                float h = dir.y;
                // Gradient spends most of its range near the horizon, where the eye looks
                float up = pow(clamp(h, 0.0, 1.0), 0.55);
                vec3 sky = mix(u_horizon, u_zenith, up);
                vec3 below = mix(u_horizon, u_ground, clamp(-h * 4.0, 0.0, 1.0));
                vec3 col = h >= 0.0 ? sky : below;

                // Sun: a hard disc, a tight glow, and a wide wash that warms the sky around it
                float s = max(dot(dir, u_sunDir), 0.0);
                float disc = pow(s, 900.0) * 1.6;
                float glow = pow(s, 24.0) * 0.12;
                float wash = pow(s, 4.0) * 0.05;
                col += u_sunColor * (disc + glow + wash) * u_sunScale;

                gl_FragColor = vec4(col, 1.0);
            }
        """.trimIndent()
    }
}
