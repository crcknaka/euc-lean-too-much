package com.eucleantoomuch.game.physics

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable

/**
 * Ragdoll physics backend.
 *
 * The game talks only to this interface so the concrete engine can be chosen per platform.
 * That is not cosmetic: the browser build must never even *reference* the Jolt classes,
 * because their native loader calls System.loadLibrary, which TeaVM cannot compile. Keeping
 * the game code free of the concrete type lets TeaVM's dead-code elimination drop Jolt
 * entirely from the web bundle, where [NoRagdollEngine] is used instead.
 *
 * All methods are safe to call before the backend is ready (natives load asynchronously):
 * they no-op, return null / -1 / an empty list until then.
 */
interface RagdollEngine : Disposable {

    /** Fired when a player ragdoll body strikes a world object - drives impact sounds. */
    var onRagdollCollision: ((RagdollTypes.ColliderType) -> Unit)?

    /** Same, for pedestrian ragdolls (quieter chain-reaction hits). */
    var onSecondaryRagdollCollision: ((RagdollTypes.ColliderType) -> Unit)?

    /** Fired when a ragdoll knocks into a standing pedestrian. */
    var onRagdollHitPedestrian: ((pedestrianPosition: Vector3, impactVelocity: Vector3) -> Unit)?

    /** Fired when a ragdoll hits the ground - startles nearby pigeons. */
    var onRagdollGroundImpact: ((impactPosition: Vector3) -> Unit)?

    /** True once the backend is usable (natives loaded and the world built). */
    fun isReady(): Boolean

    /** True while a player ragdoll is simulating (or frozen but still shown). */
    fun isActive(): Boolean

    // --- lifecycle -----------------------------------------------------------------------

    fun startFall(
        eucPosition: Vector3,
        eucYaw: Float,
        playerVelocity: Float,
        sideLean: Float,
        forwardLean: Float
    )

    fun update(delta: Float)

    /** Stop simulating but keep the last pose visible. */
    fun freeze()

    /** Tear down all bodies. */
    fun stop()

    // --- world colliders -----------------------------------------------------------------

    fun addBoxCollider(
        position: Vector3,
        halfExtents: Vector3,
        yaw: Float = 0f,
        type: RagdollTypes.ColliderType = RagdollTypes.ColliderType.GENERIC
    )

    fun addCylinderCollider(
        position: Vector3,
        radius: Float,
        height: Float,
        type: RagdollTypes.ColliderType = RagdollTypes.ColliderType.STREET_LIGHT
    )

    fun clearWorldColliders()

    // --- player ragdoll transforms -------------------------------------------------------

    fun getEucTransform(): Matrix4?
    fun getEucPosition(out: Vector3): Vector3
    fun getHeadTransform(): Matrix4?
    fun getTorsoTransform(): Matrix4?
    fun getTorsoPosition(out: Vector3): Vector3
    fun getLeftUpperArmTransform(): Matrix4?
    fun getLeftLowerArmTransform(): Matrix4?
    fun getRightUpperArmTransform(): Matrix4?
    fun getRightLowerArmTransform(): Matrix4?
    fun getLeftUpperLegTransform(): Matrix4?
    fun getLeftLowerLegTransform(): Matrix4?
    fun getRightUpperLegTransform(): Matrix4?
    fun getRightLowerLegTransform(): Matrix4?

    // --- pedestrian ragdolls -------------------------------------------------------------

    fun addPedestrianRagdoll(
        position: Vector3,
        yaw: Float,
        playerVelocity: Float,
        playerDirection: Vector3,
        entityIndex: Int,
        shirtColor: Color = Color.GREEN
    ): Int

    fun getPedestrianCount(): Int
    fun getPedestrianShirtColor(index: Int): Color?
    fun getPedestrianTransform(index: Int): Matrix4?
    fun getPedestrianHeadTransform(index: Int): Matrix4?
    fun getPedestrianTorsoTransform(index: Int): Matrix4?
    fun getPedestrianLeftArmTransform(index: Int): Matrix4?
    fun getPedestrianRightArmTransform(index: Int): Matrix4?
    fun getPedestrianLeftLegTransform(index: Int): Matrix4?
    fun getPedestrianRightLegTransform(index: Int): Matrix4?

    // --- dynamic objects (trash cans) ----------------------------------------------------

    fun addTrashCanRagdoll(
        position: Vector3,
        playerVelocity: Float,
        playerDirection: Vector3,
        entityIndex: Int
    ): Int

    fun getDynamicObjectTransform(index: Int): Matrix4?

    // --- queries -------------------------------------------------------------------------

    fun getActiveRagdollBodies(minVelocity: Float = 2f): List<RagdollTypes.RagdollBodyInfo>
    fun applyExternalImpulse(impactPosition: Vector3, impactVelocity: Vector3)
    fun retireRagdollsBehind(minZ: Float)
}
