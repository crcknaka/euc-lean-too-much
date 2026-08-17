package com.eucleantoomuch.game.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.util.Easing

/**
 * Billboard particles: tyre spray, splashes, impact debris, dust on landing.
 *
 * Written by hand rather than taken from a library on purpose. libGDX's own 3D particle system
 * loads its effects through the asset manager with reflection, which the browser build cannot
 * be relied on to survive, and every third-party post/particle extension ships a GWT module but
 * not a TeaVM one. This is a few hundred lines that behave identically on all three platforms
 * and keep the timing curves under our control - which, for particles, is the whole game.
 *
 * One mesh is rebuilt each frame and drawn in a single call, so a few hundred particles cost
 * one draw. The quads are turned to face the camera on the CPU, which is cheaper than it sounds
 * at these counts and avoids needing a geometry stage that WebGL 1 does not have.
 */
class ParticleSystem(private val maxParticles: Int = 400) : Disposable {

    private class Particle {
        val position = Vector3()
        val velocity = Vector3()
        var life = 0f          // Seconds remaining
        var maxLife = 1f
        var size = 0.2f
        var endSize = 0.4f
        var drag = 1f
        var gravity = -4f
        val colorStart = Color()
        val colorEnd = Color()
        var active = false
    }

    private val particles = Array(maxParticles) { Particle() }
    private var nextIndex = 0

    private val mesh = Mesh(
        false, maxParticles * 4, maxParticles * 6,
        VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
        VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 4, ShaderProgram.COLOR_ATTRIBUTE),
        VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, "${ShaderProgram.TEXCOORD_ATTRIBUTE}0")
    ).apply {
        // Index order never changes, so it is uploaded once
        val indices = ShortArray(maxParticles * 6)
        for (i in 0 until maxParticles) {
            val v = (i * 4).toShort()
            val o = i * 6
            indices[o] = v
            indices[o + 1] = (v + 1).toShort()
            indices[o + 2] = (v + 2).toShort()
            indices[o + 3] = (v + 2).toShort()
            indices[o + 4] = (v + 3).toShort()
            indices[o + 5] = v
        }
        setIndices(indices)
    }

    private val vertices = FloatArray(maxParticles * 4 * VERTEX_FLOATS)

    private val shader = ShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER).also {
        if (!it.isCompiled) Gdx.app.error("ParticleSystem", "shader failed: ${it.log}")
    }

    /** A soft round blob, generated rather than shipped - it is 16 kB of gradient. */
    private val texture: Texture = run {
        val size = 64
        val pixmap = Pixmap(size, size, Pixmap.Format.RGBA8888)
        val centre = (size - 1) / 2f
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (x - centre) / centre
                val dy = (y - centre) / centre
                val d = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(1f)
                // Squared falloff reads as a soft puff; linear looks like a flat disc
                val a = (1f - d) * (1f - d)
                pixmap.setColor(1f, 1f, 1f, a)
                pixmap.drawPixel(x, y)
            }
        }
        Texture(pixmap).also { pixmap.dispose(); it.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
    }

    private val right = Vector3()
    private val up = Vector3()
    private val tmp = Vector3()

    fun update(deltaTime: Float) {
        for (p in particles) {
            if (!p.active) continue
            p.life -= deltaTime
            if (p.life <= 0f) {
                p.active = false
                continue
            }
            // Air drag as an exponential fade rather than a subtraction: debris slows sharply
            // at first and then coasts, and never reverses direction the way a linear damp can
            val damp = Easing.decay(deltaTime, p.drag)
            p.velocity.scl(damp)
            p.velocity.y += p.gravity * deltaTime
            tmp.set(p.velocity).scl(deltaTime)
            p.position.add(tmp)
        }
    }

    fun render(camera: Camera) {
        var count = 0
        // Billboards face the camera, so every quad is built from the same two view axes
        right.set(camera.direction).crs(camera.up).nor()
        up.set(right).crs(camera.direction).nor()

        for (p in particles) {
            if (!p.active) continue
            val t = 1f - (p.life / p.maxLife).coerceIn(0f, 1f)

            // Grows quickly then settles, and fades out over the whole life with the last
            // stretch accelerating - a linear fade is the thing that makes smoke look like a
            // decal being turned down with a dial
            val size = MathUtils.lerp(p.size, p.endSize, Easing.outN(t, 2))
            val alphaCurve = 1f - Easing.inN(t, 2)

            val r = MathUtils.lerp(p.colorStart.r, p.colorEnd.r, t)
            val g = MathUtils.lerp(p.colorStart.g, p.colorEnd.g, t)
            val b = MathUtils.lerp(p.colorStart.b, p.colorEnd.b, t)
            val a = MathUtils.lerp(p.colorStart.a, p.colorEnd.a, t) * alphaCurve

            val half = size * 0.5f
            var o = count * 4 * VERTEX_FLOATS
            // Corners: bottom-left, bottom-right, top-right, top-left
            o = putVertex(o, p.position, -half, -half, r, g, b, a, 0f, 1f)
            o = putVertex(o, p.position, half, -half, r, g, b, a, 1f, 1f)
            o = putVertex(o, p.position, half, half, r, g, b, a, 1f, 0f)
            putVertex(o, p.position, -half, half, r, g, b, a, 0f, 0f)

            count++
            if (count >= maxParticles) break
        }

        if (count == 0) return

        mesh.setVertices(vertices, 0, count * 4 * VERTEX_FLOATS)

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        // Particles read the depth buffer so they hide behind geometry, but do not write to it -
        // otherwise the soft edges of one puff cut holes in the one behind it
        Gdx.gl.glDepthMask(false)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)

        shader.bind()
        shader.setUniformMatrix("u_projTrans", camera.combined)
        texture.bind(0)
        shader.setUniformi("u_texture", 0)
        mesh.render(shader, GL20.GL_TRIANGLES, 0, count * 6)

        // Put the state back the way ModelBatch left it. With post-processing switched off
        // nothing downstream resets this, and a depth test left enabled makes the HUD fight
        // the depth buffer of a scene it was never part of.
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    private fun putVertex(
        offset: Int, centre: Vector3, dx: Float, dy: Float,
        r: Float, g: Float, b: Float, a: Float, u: Float, v: Float
    ): Int {
        vertices[offset] = centre.x + right.x * dx + up.x * dy
        vertices[offset + 1] = centre.y + right.y * dx + up.y * dy
        vertices[offset + 2] = centre.z + right.z * dx + up.z * dy
        vertices[offset + 3] = r
        vertices[offset + 4] = g
        vertices[offset + 5] = b
        vertices[offset + 6] = a
        vertices[offset + 7] = u
        vertices[offset + 8] = v
        return offset + VERTEX_FLOATS
    }

    private fun spawn(): Particle? {
        // Oldest-first reuse: at the cap the newest burst matters more than the tail of an
        // older one, and hunting for a free slot every frame is wasted work
        repeat(maxParticles) {
            val p = particles[nextIndex]
            nextIndex = (nextIndex + 1) % maxParticles
            if (!p.active) return p
        }
        val p = particles[nextIndex]
        nextIndex = (nextIndex + 1) % maxParticles
        return p
    }

    private fun emit(
        x: Float, y: Float, z: Float,
        vx: Float, vy: Float, vz: Float,
        life: Float, size: Float, endSize: Float,
        start: Color, end: Color,
        gravity: Float, drag: Float
    ) {
        val p = spawn() ?: return
        p.position.set(x, y, z)
        p.velocity.set(vx, vy, vz)
        p.maxLife = life
        p.life = life
        p.size = size
        p.endSize = endSize
        p.colorStart.set(start)
        p.colorEnd.set(end)
        p.gravity = gravity
        p.drag = drag
        p.active = true
    }

    // === Effects ===

    /** Dust kicked up behind the tyre. Called every frame while rolling; [rate] is per second. */
    fun tyreSpray(position: Vector3, heading: Float, speed: Float, deltaTime: Float) {
        if (speed < 4f) return
        val intensity = ((speed - 4f) / 16f).coerceIn(0f, 1f)
        val toEmit = TYRE_RATE * intensity * deltaTime
        val n = toEmit.toInt() + if (MathUtils.random() < toEmit % 1f) 1 else 0

        val rad = heading * MathUtils.degreesToRadians
        val backX = -MathUtils.sin(rad)
        val backZ = -MathUtils.cos(rad)

        repeat(n) {
            emit(
                position.x + MathUtils.random(-0.06f, 0.06f), position.y + 0.05f, position.z + MathUtils.random(-0.06f, 0.06f),
                backX * speed * 0.18f + MathUtils.random(-0.4f, 0.4f),
                MathUtils.random(0.4f, 1.2f),
                backZ * speed * 0.18f + MathUtils.random(-0.4f, 0.4f),
                MathUtils.random(0.35f, 0.6f),
                0.07f, MathUtils.random(0.3f, 0.5f),
                DUST_START, DUST_END,
                gravity = -1.2f, drag = 0.35f
            )
        }
    }

    /** Water thrown sideways and up when the wheel goes through a puddle. */
    fun splash(position: Vector3, speed: Float) {
        val strength = (speed / 14f).coerceIn(0.35f, 1.4f)
        repeat((18 * strength).toInt().coerceAtLeast(6)) {
            val angle = MathUtils.random(0f, MathUtils.PI2)
            val out = MathUtils.random(1.2f, 3.4f) * strength
            emit(
                position.x + MathUtils.random(-0.15f, 0.15f), position.y + 0.05f, position.z + MathUtils.random(-0.15f, 0.15f),
                MathUtils.cos(angle) * out,
                MathUtils.random(2.2f, 4.6f) * strength,
                MathUtils.sin(angle) * out,
                MathUtils.random(0.35f, 0.7f),
                0.05f, 0.11f,
                WATER_START, WATER_END,
                gravity = -9f, drag = 1.4f
            )
        }
    }

    /** Debris off a solid hit - heavier, thrown further, settles fast. */
    fun impact(position: Vector3, strength: Float = 1f) {
        repeat((14 * strength).toInt().coerceIn(6, 26)) {
            val angle = MathUtils.random(0f, MathUtils.PI2)
            val out = MathUtils.random(1.5f, 4.5f) * strength
            emit(
                position.x, position.y + 0.2f, position.z,
                MathUtils.cos(angle) * out,
                MathUtils.random(1.5f, 4f) * strength,
                MathUtils.sin(angle) * out,
                MathUtils.random(0.3f, 0.65f),
                0.06f, 0.16f,
                DEBRIS_START, DEBRIS_END,
                gravity = -11f, drag = 0.9f
            )
        }
    }

    /** A low puff where something heavy met the ground. */
    fun groundPuff(position: Vector3, strength: Float = 1f) {
        repeat((12 * strength).toInt().coerceIn(5, 20)) {
            val angle = MathUtils.random(0f, MathUtils.PI2)
            val out = MathUtils.random(0.6f, 2.2f) * strength
            emit(
                position.x, position.y + 0.08f, position.z,
                MathUtils.cos(angle) * out,
                MathUtils.random(0.3f, 1.1f),
                MathUtils.sin(angle) * out,
                MathUtils.random(0.5f, 0.9f),
                0.12f, MathUtils.random(0.5f, 0.8f),
                DUST_START, DUST_END,
                gravity = -0.8f, drag = 0.4f
            )
        }
    }

    fun clear() {
        for (p in particles) p.active = false
    }

    override fun dispose() {
        mesh.dispose()
        shader.dispose()
        texture.dispose()
    }

    private companion object {
        const val VERTEX_FLOATS = 9   // xyz + rgba + uv
        const val TYRE_RATE = 70f

        val DUST_START = Color(0.72f, 0.68f, 0.60f, 0.42f)
        val DUST_END = Color(0.78f, 0.75f, 0.70f, 0f)
        val WATER_START = Color(0.75f, 0.85f, 0.95f, 0.75f)
        val WATER_END = Color(0.85f, 0.92f, 1f, 0f)
        val DEBRIS_START = Color(0.55f, 0.52f, 0.48f, 0.85f)
        val DEBRIS_END = Color(0.45f, 0.43f, 0.40f, 0f)

        // GLES2 / WebGL 1 shaders: no in/out, no explicit locations
        val VERTEX_SHADER = """
            attribute vec4 ${ShaderProgram.POSITION_ATTRIBUTE};
            attribute vec4 ${ShaderProgram.COLOR_ATTRIBUTE};
            attribute vec2 ${ShaderProgram.TEXCOORD_ATTRIBUTE}0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoord;
            void main() {
                v_color = ${ShaderProgram.COLOR_ATTRIBUTE};
                v_texCoord = ${ShaderProgram.TEXCOORD_ATTRIBUTE}0;
                gl_Position = u_projTrans * ${ShaderProgram.POSITION_ATTRIBUTE};
            }
        """.trimIndent()

        val FRAGMENT_SHADER = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoord;
            uniform sampler2D u_texture;
            void main() {
                gl_FragColor = v_color * texture2D(u_texture, v_texCoord);
            }
        """.trimIndent()
    }
}
