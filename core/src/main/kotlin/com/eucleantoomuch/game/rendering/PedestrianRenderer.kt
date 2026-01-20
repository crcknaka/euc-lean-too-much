package com.eucleantoomuch.game.rendering

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.ModelComponent
import com.eucleantoomuch.game.ecs.components.PedestrianComponent
import com.eucleantoomuch.game.ecs.components.PedestrianState
import com.eucleantoomuch.game.ecs.components.TransformComponent
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders pedestrians with articulated body parts and walking animation.
 * Each pedestrian is rendered as separate body parts (head, torso, arms, legs)
 * that can be animated independently.
 */
class PedestrianRenderer(private val engine: Engine, private val models: ProceduralModels) : Disposable {

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val pedestrianMapper = ComponentMapper.getFor(PedestrianComponent::class.java)
    private val modelMapper = ComponentMapper.getFor(ModelComponent::class.java)

    // Body part model instances (shared, transform updated per pedestrian)
    private data class BodyPartInstances(
        val head: ModelInstance,
        val hair: ModelInstance,
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

    // Cache of instances per shirt color
    private val instanceCache = mutableMapOf<Int, BodyPartInstances>()

    // Temp matrices for animation calculations
    private val baseMatrix = Matrix4()
    private val partMatrix = Matrix4()
    private val tempVec = Vector3()

    // Body dimensions (match ProceduralModels at 1.7x scale)
    private val scale = 1.7f
    private val legScale = 0.85f  // Shorter legs
    private val hipY = 0.9f * scale * legScale  // Lower hip due to shorter legs
    private val torsoHeight = 0.5f * scale
    private val upperArmLength = 0.28f * scale
    private val lowerArmLength = 0.25f * scale
    private val upperLegLength = 0.4f * scale * legScale
    private val lowerLegLength = 0.4f * scale * legScale
    private val shoulderOffset = 0.17f * scale
    private val hipOffset = 0.08f * scale
    private val shoulderY = hipY + torsoHeight - 0.05f * scale
    private val headY = hipY + torsoHeight + 0.072f * scale

    // Animation parameters
    private val armSwingAngle = 25f  // Degrees
    private val legSwingAngle = 30f  // Degrees

    private fun getOrCreateInstances(shirtColor: Color): BodyPartInstances {
        val colorKey = shirtColor.toIntBits()
        return instanceCache.getOrPut(colorKey) {
            BodyPartInstances(
                head = ModelInstance(models.getPedestrianBodyPartModel("head", shirtColor)),
                hair = ModelInstance(models.getPedestrianBodyPartModel("hair", shirtColor)),
                torso = ModelInstance(models.getPedestrianBodyPartModel("torso", shirtColor)),
                leftUpperArm = ModelInstance(models.getPedestrianBodyPartModel("leftUpperArm", shirtColor)),
                leftLowerArm = ModelInstance(models.getPedestrianBodyPartModel("leftLowerArm", shirtColor)),
                rightUpperArm = ModelInstance(models.getPedestrianBodyPartModel("rightUpperArm", shirtColor)),
                rightLowerArm = ModelInstance(models.getPedestrianBodyPartModel("rightLowerArm", shirtColor)),
                leftUpperLeg = ModelInstance(models.getPedestrianBodyPartModel("leftUpperLeg", shirtColor)),
                leftLowerLeg = ModelInstance(models.getPedestrianBodyPartModel("leftLowerLeg", shirtColor)),
                rightUpperLeg = ModelInstance(models.getPedestrianBodyPartModel("rightUpperLeg", shirtColor)),
                rightLowerLeg = ModelInstance(models.getPedestrianBodyPartModel("rightLowerLeg", shirtColor))
            )
        }
    }

    // Frustum culling radius for pedestrians (~2m tall)
    private val pedestrianCullRadius = 3f

    /**
     * Render all pedestrians with articulated animation.
     * Call this instead of the normal ModelComponent rendering for pedestrians.
     */
    fun render(modelBatch: ModelBatch, environment: Environment, camera: Camera) {
        val pedestrians = engine.getEntitiesFor(Families.pedestrians)

        for (i in 0 until pedestrians.size()) {
            val entity = pedestrians[i]
            val transform = transformMapper.get(entity) ?: continue
            val pedestrian = pedestrianMapper.get(entity) ?: continue
            val modelComp = modelMapper.get(entity) ?: continue

            // Skip if ragdolling (ragdoll renderer handles it)
            if (pedestrian.isRagdolling) continue

            // Skip if model is hidden
            if (!modelComp.visible) continue

            // Frustum culling - skip pedestrians outside camera view
            if (!camera.frustum.sphereInFrustum(transform.position.x, transform.position.y + 1f, transform.position.z, pedestrianCullRadius)) {
                continue
            }

            // Get shirt color from the original model instance material
            val shirtColor = extractShirtColor(modelComp.modelInstance) ?: Color.GREEN

            // Get or create body part instances for this color
            val parts = getOrCreateInstances(shirtColor)

            // Calculate animation values
            val phase = pedestrian.walkAnimPhase
            val isWalking = pedestrian.state == PedestrianState.WALKING ||
                           pedestrian.state == PedestrianState.CROSSING ||
                           pedestrian.state == PedestrianState.WALKING_TO_CROSSING

            // Arm and leg swing angles (opposite for natural walk cycle)
            val leftArmSwing = if (isWalking) sin(phase) * armSwingAngle else 0f
            val rightArmSwing = if (isWalking) -sin(phase) * armSwingAngle else 0f
            val leftLegSwing = if (isWalking) -sin(phase) * legSwingAngle else 0f
            val rightLegSwing = if (isWalking) sin(phase) * legSwingAngle else 0f

            // Base transform (position + yaw rotation)
            baseMatrix.idt()
            baseMatrix.translate(transform.position)
            baseMatrix.rotate(Vector3.Y, -transform.yaw)

            // Render each body part with appropriate transform

            // Head (no animation, just positioned)
            partMatrix.set(baseMatrix)
            partMatrix.translate(0f, headY, 0f)
            parts.head.transform.set(partMatrix)
            modelBatch.render(parts.head, environment)

            // Hair (on top of head)
            partMatrix.set(baseMatrix)
            partMatrix.translate(0f, headY + 0.04f, -0.01f)
            parts.hair.transform.set(partMatrix)
            modelBatch.render(parts.hair, environment)

            // Torso (no animation)
            partMatrix.set(baseMatrix)
            partMatrix.translate(0f, hipY + torsoHeight / 2, 0f)
            parts.torso.transform.set(partMatrix)
            modelBatch.render(parts.torso, environment)

            // Left upper arm (animated)
            partMatrix.set(baseMatrix)
            partMatrix.translate(-shoulderOffset, shoulderY, 0f)
            partMatrix.rotate(Vector3.X, leftArmSwing)
            partMatrix.translate(0f, -upperArmLength / 2, 0f)
            parts.leftUpperArm.transform.set(partMatrix)
            modelBatch.render(parts.leftUpperArm, environment)

            // Left lower arm (follows upper arm)
            partMatrix.set(baseMatrix)
            partMatrix.translate(-shoulderOffset, shoulderY, 0f)
            partMatrix.rotate(Vector3.X, leftArmSwing)
            partMatrix.translate(0f, -upperArmLength - lowerArmLength / 2, 0f)
            parts.leftLowerArm.transform.set(partMatrix)
            modelBatch.render(parts.leftLowerArm, environment)

            // Right upper arm (animated, opposite phase)
            partMatrix.set(baseMatrix)
            partMatrix.translate(shoulderOffset, shoulderY, 0f)
            partMatrix.rotate(Vector3.X, rightArmSwing)
            partMatrix.translate(0f, -upperArmLength / 2, 0f)
            parts.rightUpperArm.transform.set(partMatrix)
            modelBatch.render(parts.rightUpperArm, environment)

            // Right lower arm
            partMatrix.set(baseMatrix)
            partMatrix.translate(shoulderOffset, shoulderY, 0f)
            partMatrix.rotate(Vector3.X, rightArmSwing)
            partMatrix.translate(0f, -upperArmLength - lowerArmLength / 2, 0f)
            parts.rightLowerArm.transform.set(partMatrix)
            modelBatch.render(parts.rightLowerArm, environment)

            // Left upper leg (animated)
            partMatrix.set(baseMatrix)
            partMatrix.translate(-hipOffset, hipY, 0f)
            partMatrix.rotate(Vector3.X, leftLegSwing)
            partMatrix.translate(0f, -upperLegLength / 2, 0f)
            parts.leftUpperLeg.transform.set(partMatrix)
            modelBatch.render(parts.leftUpperLeg, environment)

            // Left lower leg (follows upper leg with slight bend)
            val leftKneeBend = if (isWalking && leftLegSwing < 0) leftLegSwing * 0.5f else 0f
            partMatrix.set(baseMatrix)
            partMatrix.translate(-hipOffset, hipY, 0f)
            partMatrix.rotate(Vector3.X, leftLegSwing)
            partMatrix.translate(0f, -upperLegLength, 0f)
            partMatrix.rotate(Vector3.X, -leftKneeBend)
            partMatrix.translate(0f, -lowerLegLength / 2, 0f)
            parts.leftLowerLeg.transform.set(partMatrix)
            modelBatch.render(parts.leftLowerLeg, environment)

            // Right upper leg (animated, opposite phase)
            partMatrix.set(baseMatrix)
            partMatrix.translate(hipOffset, hipY, 0f)
            partMatrix.rotate(Vector3.X, rightLegSwing)
            partMatrix.translate(0f, -upperLegLength / 2, 0f)
            parts.rightUpperLeg.transform.set(partMatrix)
            modelBatch.render(parts.rightUpperLeg, environment)

            // Right lower leg
            val rightKneeBend = if (isWalking && rightLegSwing < 0) rightLegSwing * 0.5f else 0f
            partMatrix.set(baseMatrix)
            partMatrix.translate(hipOffset, hipY, 0f)
            partMatrix.rotate(Vector3.X, rightLegSwing)
            partMatrix.translate(0f, -upperLegLength, 0f)
            partMatrix.rotate(Vector3.X, -rightKneeBend)
            partMatrix.translate(0f, -lowerLegLength / 2, 0f)
            parts.rightLowerLeg.transform.set(partMatrix)
            modelBatch.render(parts.rightLowerLeg, environment)

            // Hide original model so it doesn't render twice
            // (GameRenderer will skip entities we've already rendered)
        }
    }

    /**
     * Extract shirt color from pedestrian model instance.
     * Looks for torso/shirt material, or infers from any bright color that's not skin/pants.
     */
    private fun extractShirtColor(modelInstance: ModelInstance?): Color? {
        if (modelInstance == null) return null

        // Find the torso material and extract its color
        for (material in modelInstance.materials) {
            val id = material.id?.lowercase() ?: ""
            if (id.contains("torso") || id.contains("shirt") || id.contains("upper")) {
                val colorAttr = material.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse) as? com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                if (colorAttr != null) return colorAttr.color
            }
        }

        // Fallback: return first material color that's not skin/pants/hair
        for (material in modelInstance.materials) {
            val colorAttr = material.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse) as? com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
            if (colorAttr != null) {
                val c = colorAttr.color
                // Skip skin-like colors (high R, medium G, low-medium B)
                if (c.r > 0.7f && c.g > 0.5f && c.b > 0.4f) continue
                // Skip pants-like colors (dark)
                if (c.r < 0.35f && c.g < 0.35f && c.b < 0.45f) continue
                // Skip hair-like colors (brown)
                if (c.r < 0.4f && c.g < 0.3f && c.b < 0.2f) continue
                return c
            }
        }
        return null
    }

    override fun dispose() {
        instanceCache.clear()
    }
}
