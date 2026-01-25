package com.eucleantoomuch.game.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable

/**
 * Renders a star field in the night sky.
 * Stars are positioned on a large sphere around the camera.
 */
class StarFieldRenderer : Disposable {
    private var starModel: Model? = null
    private var starInstance: ModelInstance? = null
    private val starCount = 300

    fun initialize() {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()

        val material = Material(
            ColorAttribute.createDiffuse(Color.WHITE),
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        )

        val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.ColorUnpacked).toLong()
        val meshBuilder = modelBuilder.part("stars", GL20.GL_TRIANGLES, attributes, material)

        // Generate random star positions on upper hemisphere
        for (i in 0 until starCount) {
            val theta = MathUtils.random(0f, MathUtils.PI2)
            val phi = MathUtils.acos(MathUtils.random(0f, 0.9f))  // Upper hemisphere only
            val radius = 250f

            val x = radius * MathUtils.sin(phi) * MathUtils.cos(theta)
            val y = radius * MathUtils.cos(phi) + 30f  // Offset up
            val z = radius * MathUtils.sin(phi) * MathUtils.sin(theta)

            // Random brightness for twinkling effect
            val brightness = MathUtils.random(0.6f, 1f)
            val size = MathUtils.random(0.3f, 0.8f)

            // Create a small quad for each star (cross shape for better visibility)
            addStar(meshBuilder, x, y, z, size, brightness)
        }

        starModel = modelBuilder.end()
        starInstance = ModelInstance(starModel!!)
    }

    private fun addStar(
        meshBuilder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder,
        x: Float, y: Float, z: Float,
        size: Float, brightness: Float
    ) {
        val c = brightness

        // Horizontal quad
        meshBuilder.rect(
            x - size, y, z - size * 0.3f,
            x + size, y, z - size * 0.3f,
            x + size, y, z + size * 0.3f,
            x - size, y, z + size * 0.3f,
            0f, 1f, 0f
        )

        // Vertical quad
        meshBuilder.rect(
            x - size * 0.3f, y - size, z,
            x + size * 0.3f, y - size, z,
            x + size * 0.3f, y + size, z,
            x - size * 0.3f, y + size, z,
            0f, 0f, 1f
        )
    }

    fun render(modelBatch: ModelBatch, camera: PerspectiveCamera) {
        starInstance?.let {
            // Center stars around camera position
            it.transform.setToTranslation(camera.position.x, 0f, camera.position.z)
            modelBatch.render(it)
        }
    }

    override fun dispose() {
        starModel?.dispose()
    }
}
