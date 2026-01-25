package com.eucleantoomuch.game.rendering

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the EUC headlight effect at night.
 * Shows a light cone and ground spot ahead of the player.
 */
class HeadlightRenderer : Disposable {
    private var groundSpotModel: Model? = null
    private var groundSpotInstance: ModelInstance? = null

    private val tempMatrix = Matrix4()

    fun initialize() {
        val modelBuilder = ModelBuilder()
        val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

        // Ground light spot - subtle ellipse on the road
        modelBuilder.begin()
        val spotMaterial = Material(
            ColorAttribute.createDiffuse(Color(1f, 1f, 0.8f, 0.15f)),  // Reduced brightness
            BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE)
        )
        val spotPart = modelBuilder.part("spot", GL20.GL_TRIANGLES, attributes, spotMaterial)
        spotPart.ellipse(8f, 12f, 16, 0f, 0f, 0f, 0f, 1f, 0f)
        groundSpotModel = modelBuilder.end()
        groundSpotInstance = ModelInstance(groundSpotModel!!)
    }

    fun render(
        modelBatch: ModelBatch,
        playerPosition: Vector3,
        playerYaw: Float,
        forwardLean: Float
    ) {
        // Ground spot ahead of player
        // Player faces along +Z when yaw=0, yaw rotates around Y axis
        val yawRad = Math.toRadians(playerYaw.toDouble()).toFloat()
        val spotDistance = 8f

        // Forward direction based on yaw (in world space)
        val forwardX = -sin(yawRad) * spotDistance
        val forwardZ = cos(yawRad) * spotDistance

        tempMatrix.idt()
        tempMatrix.translate(
            playerPosition.x + forwardX,
            0.18f,  // Above sidewalk level (curb is 0.15f)
            playerPosition.z + forwardZ
        )

        groundSpotInstance?.transform?.set(tempMatrix)
        groundSpotInstance?.let { modelBatch.render(it) }
    }

    override fun dispose() {
        groundSpotModel?.dispose()
    }
}
