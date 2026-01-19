package com.eucleantoomuch.game.physics

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable

/**
 * Renders ragdoll physics bodies as visible 3D shapes.
 * Uses boxes for limbs, sphere for head. EUC wheel uses original model.
 */
class RagdollRenderer : Disposable {

    private val modelBuilder = ModelBuilder()

    // Body part models - player ragdoll
    private lateinit var headModel: Model
    private lateinit var torsoModel: Model
    private lateinit var upperArmModel: Model
    private lateinit var lowerArmModel: Model
    private lateinit var upperLegModel: Model
    private lateinit var lowerLegModel: Model

    // Body part models - pedestrian ragdoll (smaller scale)
    private lateinit var pedHeadModel: Model
    private lateinit var pedTorsoModel: Model
    private lateinit var pedUpperArmModel: Model
    private lateinit var pedLowerArmModel: Model
    private lateinit var pedUpperLegModel: Model
    private lateinit var pedLowerLegModel: Model

    // Model instances (reused each frame) - player
    private val headInstance: ModelInstance
    private val torsoInstance: ModelInstance
    private val leftUpperArmInstance: ModelInstance
    private val leftLowerArmInstance: ModelInstance
    private val rightUpperArmInstance: ModelInstance
    private val rightLowerArmInstance: ModelInstance
    private val leftUpperLegInstance: ModelInstance
    private val leftLowerLegInstance: ModelInstance
    private val rightUpperLegInstance: ModelInstance
    private val rightLowerLegInstance: ModelInstance

    // Model instances for pedestrians (multiple sets for multiple pedestrians)
    private val pedestrianInstances = mutableListOf<PedestrianModelInstances>()

    // Helper class to hold all instances for one pedestrian
    private data class PedestrianModelInstances(
        val head: ModelInstance,
        val torso: ModelInstance,
        val leftUpperArm: ModelInstance,
        val leftLowerArm: ModelInstance,
        val rightUpperArm: ModelInstance,
        val rightLowerArm: ModelInstance,
        val leftUpperLeg: ModelInstance,
        val leftLowerLeg: ModelInstance,
        val rightUpperLeg: ModelInstance,
        val rightLowerLeg: ModelInstance
    )

    // Colors for body parts - match ProceduralModels colors exactly
    private val skinColor = Color(0.9f, 0.7f, 0.6f, 1f)    // Same as riderSkinColor
    private val torsoColor = Color(0.3f, 0.5f, 0.7f, 1f)   // Same as riderBodyColor (blue jacket)
    private val pantsColor = Color(0.2f, 0.2f, 0.3f, 1f)   // Same as riderPantsColor
    private val helmetColor = Color(0.15f, 0.15f, 0.15f, 1f) // Same as helmetColor

    // Pedestrian colors (varied)
    private val pedSkinColor = Color(0.85f, 0.65f, 0.55f, 1f)  // Slightly different skin
    private val pedShirtColor = Color(0.4f, 0.6f, 0.4f, 1f)    // Green shirt
    private val pedPantsColor = Color(0.25f, 0.25f, 0.35f, 1f) // Dark pants
    private val pedHairColor = Color(0.3f, 0.2f, 0.15f, 1f)    // Brown hair

    init {
        createModels()
        createPedestrianModels()

        headInstance = ModelInstance(headModel)
        torsoInstance = ModelInstance(torsoModel)
        leftUpperArmInstance = ModelInstance(upperArmModel)
        leftLowerArmInstance = ModelInstance(lowerArmModel)
        rightUpperArmInstance = ModelInstance(upperArmModel)
        rightLowerArmInstance = ModelInstance(lowerArmModel)
        leftUpperLegInstance = ModelInstance(upperLegModel)
        leftLowerLegInstance = ModelInstance(lowerLegModel)
        rightUpperLegInstance = ModelInstance(upperLegModel)
        rightLowerLegInstance = ModelInstance(lowerLegModel)
    }

    private fun createModels() {
        val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

        // Create opaque material helper function - ensures no transparency/blending
        fun opaqueMaterial(color: Color): Material {
            return Material(
                ColorAttribute.createDiffuse(color),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f)  // Fully opaque
            )
        }

        // Rider scale to match ProceduralModels
        val riderScale = 1.4f

        // Head with helmet - composite model
        modelBuilder.begin()
        // Face (skin sphere, slightly forward)
        var part = modelBuilder.part("face", GL20.GL_TRIANGLES, attrs, opaqueMaterial(skinColor))
        part.sphere(0.18f * riderScale, 0.22f * riderScale, 0.18f * riderScale, 8, 8,
            0f, 360f, 0f, 180f)
        // Helmet shell (covers top and back)
        part = modelBuilder.part("helmet", GL20.GL_TRIANGLES, attrs, opaqueMaterial(helmetColor))
        part.setVertexTransform(Matrix4().translate(0f, 0.06f * riderScale, -0.02f * riderScale))
        part.sphere(0.26f * riderScale, 0.24f * riderScale, 0.26f * riderScale, 10, 10,
            0f, 360f, 0f, 180f)
        headModel = modelBuilder.end()

        // Torso - box like in ProceduralModels
        torsoModel = modelBuilder.createBox(
            0.35f * riderScale, 0.6f * riderScale, 0.22f * riderScale,
            opaqueMaterial(torsoColor),
            attrs
        )

        // Upper arm - box
        upperArmModel = modelBuilder.createBox(
            0.09f * riderScale, 0.35f * riderScale, 0.09f * riderScale,
            opaqueMaterial(torsoColor),
            attrs
        )

        // Lower arm (forearm + hand)
        lowerArmModel = modelBuilder.createBox(
            0.07f * riderScale, 0.38f * riderScale, 0.07f * riderScale,
            opaqueMaterial(torsoColor),
            attrs
        )

        // Upper leg - box
        upperLegModel = modelBuilder.createBox(
            0.12f * riderScale, 0.35f * riderScale, 0.14f * riderScale,
            opaqueMaterial(pantsColor),
            attrs
        )

        // Lower leg - box
        lowerLegModel = modelBuilder.createBox(
            0.12f * riderScale, 0.35f * riderScale, 0.14f * riderScale,
            opaqueMaterial(pantsColor),
            attrs
        )
    }

    private fun createPedestrianModels() {
        val attrs = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

        // Create opaque material helper function
        fun opaqueMaterial(color: Color): Material {
            return Material(
                ColorAttribute.createDiffuse(color),
                BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f)
            )
        }

        // Pedestrian scale (1.0 = normal human, no helmet)
        val scale = 1.0f

        // Head (no helmet, just hair and face)
        modelBuilder.begin()
        // Face (skin sphere)
        var part = modelBuilder.part("face", GL20.GL_TRIANGLES, attrs, opaqueMaterial(pedSkinColor))
        part.sphere(0.14f * scale, 0.18f * scale, 0.14f * scale, 8, 8,
            0f, 360f, 0f, 180f)
        // Hair (top of head)
        part = modelBuilder.part("hair", GL20.GL_TRIANGLES, attrs, opaqueMaterial(pedHairColor))
        part.setVertexTransform(Matrix4().translate(0f, 0.04f * scale, -0.01f * scale))
        part.sphere(0.16f * scale, 0.12f * scale, 0.16f * scale, 8, 8,
            0f, 360f, 0f, 90f)
        pedHeadModel = modelBuilder.end()

        // Torso - box
        pedTorsoModel = modelBuilder.createBox(
            0.30f * scale, 0.50f * scale, 0.20f * scale,
            opaqueMaterial(pedShirtColor),
            attrs
        )

        // Upper arm - box
        pedUpperArmModel = modelBuilder.createBox(
            0.07f * scale, 0.28f * scale, 0.07f * scale,
            opaqueMaterial(pedShirtColor),
            attrs
        )

        // Lower arm (forearm + hand)
        pedLowerArmModel = modelBuilder.createBox(
            0.06f * scale, 0.25f * scale, 0.06f * scale,
            opaqueMaterial(pedSkinColor),
            attrs
        )

        // Upper leg - box
        pedUpperLegModel = modelBuilder.createBox(
            0.10f * scale, 0.40f * scale, 0.10f * scale,
            opaqueMaterial(pedPantsColor),
            attrs
        )

        // Lower leg - box
        pedLowerLegModel = modelBuilder.createBox(
            0.08f * scale, 0.40f * scale, 0.08f * scale,
            opaqueMaterial(pedPantsColor),
            attrs
        )
    }

    /**
     * Ensure we have enough pedestrian model instances for the given count.
     */
    private fun ensurePedestrianInstances(count: Int) {
        while (pedestrianInstances.size < count) {
            pedestrianInstances.add(PedestrianModelInstances(
                head = ModelInstance(pedHeadModel),
                torso = ModelInstance(pedTorsoModel),
                leftUpperArm = ModelInstance(pedUpperArmModel),
                leftLowerArm = ModelInstance(pedLowerArmModel),
                rightUpperArm = ModelInstance(pedUpperArmModel),
                rightLowerArm = ModelInstance(pedLowerArmModel),
                leftUpperLeg = ModelInstance(pedUpperLegModel),
                leftLowerLeg = ModelInstance(pedLowerLegModel),
                rightUpperLeg = ModelInstance(pedUpperLegModel),
                rightLowerLeg = ModelInstance(pedLowerLegModel)
            ))
        }
    }

    /**
     * Render ragdoll bodies using transforms from physics simulation.
     * Note: EUC wheel is NOT rendered here - original model stays visible
     */
    fun render(modelBatch: ModelBatch, ragdollPhysics: RagdollPhysics, environment: Environment) {
        if (!ragdollPhysics.isActive()) return

        // Head
        ragdollPhysics.getHeadTransform()?.let { transform ->
            headInstance.transform.set(transform)
            modelBatch.render(headInstance, environment)
        }

        // Torso
        ragdollPhysics.getTorsoTransform()?.let { transform ->
            torsoInstance.transform.set(transform)
            modelBatch.render(torsoInstance, environment)
        }

        // Left arm (box shapes, vertical orientation - no extra rotation needed)
        ragdollPhysics.getLeftUpperArmTransform()?.let { transform ->
            leftUpperArmInstance.transform.set(transform)
            modelBatch.render(leftUpperArmInstance, environment)
        }
        ragdollPhysics.getLeftLowerArmTransform()?.let { transform ->
            leftLowerArmInstance.transform.set(transform)
            modelBatch.render(leftLowerArmInstance, environment)
        }

        // Right arm
        ragdollPhysics.getRightUpperArmTransform()?.let { transform ->
            rightUpperArmInstance.transform.set(transform)
            modelBatch.render(rightUpperArmInstance, environment)
        }
        ragdollPhysics.getRightLowerArmTransform()?.let { transform ->
            rightLowerArmInstance.transform.set(transform)
            modelBatch.render(rightLowerArmInstance, environment)
        }

        // Left leg
        ragdollPhysics.getLeftUpperLegTransform()?.let { transform ->
            leftUpperLegInstance.transform.set(transform)
            modelBatch.render(leftUpperLegInstance, environment)
        }
        ragdollPhysics.getLeftLowerLegTransform()?.let { transform ->
            leftLowerLegInstance.transform.set(transform)
            modelBatch.render(leftLowerLegInstance, environment)
        }

        // Right leg
        ragdollPhysics.getRightUpperLegTransform()?.let { transform ->
            rightUpperLegInstance.transform.set(transform)
            modelBatch.render(rightUpperLegInstance, environment)
        }
        ragdollPhysics.getRightLowerLegTransform()?.let { transform ->
            rightLowerLegInstance.transform.set(transform)
            modelBatch.render(rightLowerLegInstance, environment)
        }

        // Render pedestrian ragdolls
        renderPedestrians(modelBatch, ragdollPhysics, environment)
    }

    /**
     * Render all pedestrian ragdolls.
     */
    fun renderPedestrians(modelBatch: ModelBatch, ragdollPhysics: RagdollPhysics, environment: Environment) {
        val pedCount = ragdollPhysics.getPedestrianCount()
        if (pedCount == 0) return

        ensurePedestrianInstances(pedCount)

        for (i in 0 until pedCount) {
            val instances = pedestrianInstances[i]

            // Head
            ragdollPhysics.getPedestrianHeadTransform(i)?.let { transform ->
                instances.head.transform.set(transform)
                modelBatch.render(instances.head, environment)
            }

            // Torso
            ragdollPhysics.getPedestrianTorsoTransform(i)?.let { transform ->
                instances.torso.transform.set(transform)
                modelBatch.render(instances.torso, environment)
            }

            // Left arm
            ragdollPhysics.getPedestrianLeftUpperArmTransform(i)?.let { transform ->
                instances.leftUpperArm.transform.set(transform)
                modelBatch.render(instances.leftUpperArm, environment)
            }
            ragdollPhysics.getPedestrianLeftLowerArmTransform(i)?.let { transform ->
                instances.leftLowerArm.transform.set(transform)
                modelBatch.render(instances.leftLowerArm, environment)
            }

            // Right arm
            ragdollPhysics.getPedestrianRightUpperArmTransform(i)?.let { transform ->
                instances.rightUpperArm.transform.set(transform)
                modelBatch.render(instances.rightUpperArm, environment)
            }
            ragdollPhysics.getPedestrianRightLowerArmTransform(i)?.let { transform ->
                instances.rightLowerArm.transform.set(transform)
                modelBatch.render(instances.rightLowerArm, environment)
            }

            // Left leg
            ragdollPhysics.getPedestrianLeftUpperLegTransform(i)?.let { transform ->
                instances.leftUpperLeg.transform.set(transform)
                modelBatch.render(instances.leftUpperLeg, environment)
            }
            ragdollPhysics.getPedestrianLeftLowerLegTransform(i)?.let { transform ->
                instances.leftLowerLeg.transform.set(transform)
                modelBatch.render(instances.leftLowerLeg, environment)
            }

            // Right leg
            ragdollPhysics.getPedestrianRightUpperLegTransform(i)?.let { transform ->
                instances.rightUpperLeg.transform.set(transform)
                modelBatch.render(instances.rightUpperLeg, environment)
            }
            ragdollPhysics.getPedestrianRightLowerLegTransform(i)?.let { transform ->
                instances.rightLowerLeg.transform.set(transform)
                modelBatch.render(instances.rightLowerLeg, environment)
            }
        }
    }

    override fun dispose() {
        headModel.dispose()
        torsoModel.dispose()
        upperArmModel.dispose()
        lowerArmModel.dispose()
        upperLegModel.dispose()
        lowerLegModel.dispose()

        pedHeadModel.dispose()
        pedTorsoModel.dispose()
        pedUpperArmModel.dispose()
        pedLowerArmModel.dispose()
        pedUpperLegModel.dispose()
        pedLowerLegModel.dispose()
    }
}
