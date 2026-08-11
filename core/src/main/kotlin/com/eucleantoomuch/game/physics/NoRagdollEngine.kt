package com.eucleantoomuch.game.physics

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3

/**
 * Ragdoll backend for platforms without native physics - currently the browser build.
 *
 * Everything is a no-op and [isReady] stays false, which the game already treats as "physics
 * unavailable": the player's crash plays the scripted fall animation instead, and hit
 * pedestrians simply are not turned into ragdolls. Nothing else changes.
 */
class NoRagdollEngine : RagdollEngine {

    override var onRagdollCollision: ((RagdollTypes.ColliderType) -> Unit)? = null
    override var onSecondaryRagdollCollision: ((RagdollTypes.ColliderType) -> Unit)? = null
    override var onRagdollHitPedestrian: ((Vector3, Vector3) -> Unit)? = null
    override var onRagdollGroundImpact: ((Vector3) -> Unit)? = null

    override fun isReady(): Boolean = false
    override fun isActive(): Boolean = false

    override fun startFall(
        eucPosition: Vector3,
        eucYaw: Float,
        playerVelocity: Float,
        sideLean: Float,
        forwardLean: Float
    ) = Unit

    override fun update(delta: Float) = Unit
    override fun freeze() = Unit
    override fun stop() = Unit

    override fun addBoxCollider(
        position: Vector3,
        halfExtents: Vector3,
        yaw: Float,
        type: RagdollTypes.ColliderType
    ) = Unit

    override fun addCylinderCollider(
        position: Vector3,
        radius: Float,
        height: Float,
        type: RagdollTypes.ColliderType
    ) = Unit

    override fun clearWorldColliders() = Unit

    override fun getEucTransform(): Matrix4? = null
    override fun getEucPosition(out: Vector3): Vector3 = out.setZero()
    override fun getHeadTransform(): Matrix4? = null
    override fun getTorsoTransform(): Matrix4? = null
    override fun getTorsoPosition(out: Vector3): Vector3 = out.setZero()
    override fun getLeftUpperArmTransform(): Matrix4? = null
    override fun getLeftLowerArmTransform(): Matrix4? = null
    override fun getRightUpperArmTransform(): Matrix4? = null
    override fun getRightLowerArmTransform(): Matrix4? = null
    override fun getLeftUpperLegTransform(): Matrix4? = null
    override fun getLeftLowerLegTransform(): Matrix4? = null
    override fun getRightUpperLegTransform(): Matrix4? = null
    override fun getRightLowerLegTransform(): Matrix4? = null

    override fun addPedestrianRagdoll(
        position: Vector3,
        yaw: Float,
        playerVelocity: Float,
        playerDirection: Vector3,
        entityIndex: Int,
        shirtColor: Color
    ): Int = -1

    override fun getPedestrianCount(): Int = 0
    override fun getPedestrianShirtColor(index: Int): Color? = null
    override fun getPedestrianTransform(index: Int): Matrix4? = null
    override fun getPedestrianHeadTransform(index: Int): Matrix4? = null
    override fun getPedestrianTorsoTransform(index: Int): Matrix4? = null
    override fun getPedestrianLeftArmTransform(index: Int): Matrix4? = null
    override fun getPedestrianRightArmTransform(index: Int): Matrix4? = null
    override fun getPedestrianLeftLegTransform(index: Int): Matrix4? = null
    override fun getPedestrianRightLegTransform(index: Int): Matrix4? = null

    override fun addTrashCanRagdoll(
        position: Vector3,
        playerVelocity: Float,
        playerDirection: Vector3,
        entityIndex: Int
    ): Int = -1

    override fun getDynamicObjectTransform(index: Int): Matrix4? = null

    override fun getActiveRagdollBodies(minVelocity: Float): List<RagdollTypes.RagdollBodyInfo> =
        emptyList()

    override fun applyExternalImpulse(impactPosition: Vector3, impactVelocity: Vector3) = Unit
    override fun retireRagdollsBehind(minZ: Float) = Unit

    override fun dispose() = Unit
}
