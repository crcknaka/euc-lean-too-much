package com.eucleantoomuch.game.physics

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.physics.bullet.Bullet
import com.badlogic.gdx.physics.bullet.collision.*
import com.badlogic.gdx.physics.bullet.dynamics.*
import com.badlogic.gdx.physics.bullet.linearmath.*
import com.badlogic.gdx.utils.Disposable

/**
 * Manages Bullet physics for ragdoll fall animation.
 * Full ragdoll with head, torso, arms, legs connected by constraints.
 * Also handles EUC wheel physics.
 */
class RagdollPhysics : Disposable {

    companion object {
        private var bulletInitialized = false

        fun ensureInitialized() {
            if (!bulletInitialized) {
                Bullet.init()
                bulletInitialized = true
                Gdx.app.log("RagdollPhysics", "Bullet physics initialized")
            }
        }
    }

    // Physics world
    private lateinit var collisionConfig: btDefaultCollisionConfiguration
    private lateinit var dispatcher: btCollisionDispatcher
    private lateinit var broadphase: btDbvtBroadphase
    private lateinit var constraintSolver: btSequentialImpulseConstraintSolver
    private lateinit var dynamicsWorld: btDiscreteDynamicsWorld

    // Ground plane
    private lateinit var groundShape: btStaticPlaneShape
    private lateinit var groundBody: btRigidBody
    private var groundMotionState: btDefaultMotionState? = null

    // EUC wheel body
    private var eucShape: btCollisionShape? = null
    private var eucBody: btRigidBody? = null
    private var eucMotionState: btDefaultMotionState? = null

    // Ragdoll body parts
    data class BodyPart(
        var shape: btCollisionShape? = null,
        var body: btRigidBody? = null,
        var motionState: btDefaultMotionState? = null
    )

    private val head = BodyPart()
    private val torso = BodyPart()
    private val leftUpperArm = BodyPart()
    private val leftLowerArm = BodyPart()
    private val rightUpperArm = BodyPart()
    private val rightLowerArm = BodyPart()
    private val leftUpperLeg = BodyPart()
    private val leftLowerLeg = BodyPart()
    private val rightUpperLeg = BodyPart()
    private val rightLowerLeg = BodyPart()

    // Constraints (joints) between body parts
    private val constraints = mutableListOf<btTypedConstraint>()

    // Static world colliders (obstacles, cars, etc.)
    private val worldColliders = mutableListOf<StaticCollider>()

    data class StaticCollider(
        val shape: btCollisionShape,
        val body: btRigidBody,
        val motionState: btDefaultMotionState
    )

    // State
    private var isActive = false
    private var isFrozen = false  // When frozen, ragdoll stays visible but doesn't simulate

    // Temp vectors for calculations
    private val tempVec = Vector3()
    private val tempMatrix = Matrix4()
    private val tempMatrix2 = Matrix4()

    init {
        ensureInitialized()
        createPhysicsWorld()
    }

    private fun createPhysicsWorld() {
        // Collision configuration
        collisionConfig = btDefaultCollisionConfiguration()
        dispatcher = btCollisionDispatcher(collisionConfig)
        broadphase = btDbvtBroadphase()
        constraintSolver = btSequentialImpulseConstraintSolver()

        // Create dynamics world
        dynamicsWorld = btDiscreteDynamicsWorld(dispatcher, broadphase, constraintSolver, collisionConfig)
        dynamicsWorld.gravity = Vector3(0f, -12f, 0f)  // Gravity

        // Create ground plane (y = 0.05 to be slightly above road surface to prevent clipping)
        groundShape = btStaticPlaneShape(Vector3(0f, 1f, 0f), 0.05f)
        groundMotionState = btDefaultMotionState()
        val groundInfo = btRigidBody.btRigidBodyConstructionInfo(0f, groundMotionState, groundShape, Vector3.Zero)
        groundBody = btRigidBody(groundInfo)
        groundBody.friction = 0.8f
        groundBody.restitution = 0.2f
        dynamicsWorld.addRigidBody(groundBody)
        groundInfo.dispose()
    }

    /**
     * Start ragdoll simulation for a fall.
     * @param eucPosition Initial position of EUC
     * @param eucYaw Initial yaw rotation in degrees
     * @param playerVelocity Initial velocity of player (m/s forward)
     * @param sideLean Side lean at fall moment (-1 to 1)
     * @param forwardLean Forward lean at fall moment (-1 to 1)
     */
    fun startFall(
        eucPosition: Vector3,
        eucYaw: Float,
        playerVelocity: Float,
        sideLean: Float,
        forwardLean: Float
    ) {
        cleanup()

        val yawRad = Math.toRadians(eucYaw.toDouble()).toFloat()

        // Create EUC wheel
        createEucWheel(eucPosition, eucYaw, playerVelocity, sideLean, forwardLean, yawRad)

        // Create ragdoll body parts
        createRagdoll(eucPosition, eucYaw, playerVelocity, sideLean, forwardLean, yawRad)

        isActive = true
        Gdx.app.log("RagdollPhysics", "Ragdoll fall started: vel=$playerVelocity, sideLean=$sideLean, forwardLean=$forwardLean")
    }

    private fun createEucWheel(
        eucPosition: Vector3,
        eucYaw: Float,
        playerVelocity: Float,
        sideLean: Float,
        forwardLean: Float,
        yawRad: Float
    ) {
        // EUC dimensions as box: width ~0.12m, height ~0.44m, depth ~0.44m
        eucShape = btBoxShape(Vector3(0.06f, 0.22f, 0.22f))

        val eucMass = 20f  // 20 kg EUC
        val eucInertia = Vector3()
        eucShape!!.calculateLocalInertia(eucMass, eucInertia)

        // Initial transform
        tempMatrix.idt()
        tempMatrix.translate(eucPosition.x, eucPosition.y + 0.22f, eucPosition.z)
        tempMatrix.rotate(Vector3.Y, eucYaw)

        eucMotionState = btDefaultMotionState(tempMatrix)
        val eucInfo = btRigidBody.btRigidBodyConstructionInfo(eucMass, eucMotionState, eucShape, eucInertia)
        eucBody = btRigidBody(eucInfo)
        eucBody!!.friction = 0.5f
        eucBody!!.restitution = 0.3f
        eucBody!!.setDamping(0.05f, 0.2f)

        // Apply initial velocity
        val forwardX = kotlin.math.sin(yawRad) * playerVelocity * 0.7f
        val forwardZ = kotlin.math.cos(yawRad) * playerVelocity * 0.7f
        val sideKick = sideLean * 4f
        val upKick = 1.5f + kotlin.math.abs(forwardLean) * 2f

        eucBody!!.linearVelocity = Vector3(forwardX + sideKick, upKick, forwardZ)
        eucBody!!.angularVelocity = Vector3(
            playerVelocity * 3f,  // Wheel spin
            sideLean * 4f,
            forwardLean * 2f
        )

        dynamicsWorld.addRigidBody(eucBody)
        eucInfo.dispose()
    }

    private fun createRagdoll(
        eucPosition: Vector3,
        eucYaw: Float,
        playerVelocity: Float,
        sideLean: Float,
        forwardLean: Float,
        yawRad: Float
    ) {
        // Rider scale to match visual models
        val riderScale = 1.4f

        // Base position (hip level, above EUC)
        val baseX = eucPosition.x
        val baseY = eucPosition.y + 0.7f * riderScale  // Hip height (above legs)
        val baseZ = eucPosition.z

        // Body dimensions scaled to match visual models
        val torsoWidth = 0.175f * riderScale   // Half of 0.35f box width
        val torsoHeight = 0.6f * riderScale
        val headRadius = 0.13f * riderScale
        val upperArmRadius = 0.045f * riderScale
        val upperArmLength = 0.35f * riderScale
        val lowerArmRadius = 0.035f * riderScale
        val lowerArmLength = 0.38f * riderScale
        val upperLegRadius = 0.06f * riderScale
        val upperLegLength = 0.35f * riderScale
        val lowerLegRadius = 0.06f * riderScale
        val lowerLegLength = 0.35f * riderScale

        // Masses
        val torsoMass = 30f
        val headMass = 5f
        val upperArmMass = 2.5f
        val lowerArmMass = 1.5f
        val upperLegMass = 8f
        val lowerLegMass = 4f

        // Torso depth for box shape
        val torsoDepth = 0.11f * riderScale  // Half of 0.22f

        // Create body parts with positions
        // Torso (center of mass at chest level) - use box shape
        createBodyPart(
            torso, btBoxShape(Vector3(torsoWidth, torsoHeight / 2, torsoDepth)), torsoMass,
            baseX, baseY + torsoHeight / 2, baseZ, eucYaw
        )

        // Head (on top of torso)
        createBodyPart(
            head, btSphereShape(headRadius), headMass,
            baseX, baseY + torsoHeight + headRadius * 0.8f, baseZ, eucYaw
        )

        // Arms - positioned at shoulder level (hanging down)
        val shoulderY = baseY + torsoHeight - 0.05f * riderScale
        val shoulderOffset = torsoWidth + 0.02f * riderScale

        // Left arm - vertical orientation (hanging down)
        createBodyPart(
            leftUpperArm, btBoxShape(Vector3(upperArmRadius, upperArmLength / 2, upperArmRadius)), upperArmMass,
            baseX - shoulderOffset, shoulderY - upperArmLength / 2, baseZ, eucYaw
        )
        createBodyPart(
            leftLowerArm, btBoxShape(Vector3(lowerArmRadius, lowerArmLength / 2, lowerArmRadius)), lowerArmMass,
            baseX - shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, baseZ, eucYaw
        )

        // Right arm
        createBodyPart(
            rightUpperArm, btBoxShape(Vector3(upperArmRadius, upperArmLength / 2, upperArmRadius)), upperArmMass,
            baseX + shoulderOffset, shoulderY - upperArmLength / 2, baseZ, eucYaw
        )
        createBodyPart(
            rightLowerArm, btBoxShape(Vector3(lowerArmRadius, lowerArmLength / 2, lowerArmRadius)), lowerArmMass,
            baseX + shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, baseZ, eucYaw
        )

        // Legs - positioned at hip level
        val hipOffset = 0.1f * riderScale

        // Left leg
        createBodyPart(
            leftUpperLeg, btBoxShape(Vector3(upperLegRadius, upperLegLength / 2, upperLegRadius)), upperLegMass,
            baseX - hipOffset, baseY - upperLegLength / 2, baseZ, eucYaw
        )
        createBodyPart(
            leftLowerLeg, btBoxShape(Vector3(lowerLegRadius, lowerLegLength / 2, lowerLegRadius)), lowerLegMass,
            baseX - hipOffset, baseY - upperLegLength - lowerLegLength / 2, baseZ, eucYaw
        )

        // Right leg
        createBodyPart(
            rightUpperLeg, btBoxShape(Vector3(upperLegRadius, upperLegLength / 2, upperLegRadius)), upperLegMass,
            baseX + hipOffset, baseY - upperLegLength / 2, baseZ, eucYaw
        )
        createBodyPart(
            rightLowerLeg, btBoxShape(Vector3(lowerLegRadius, lowerLegLength / 2, lowerLegRadius)), lowerLegMass,
            baseX + hipOffset, baseY - upperLegLength - lowerLegLength / 2, baseZ, eucYaw
        )

        // Create constraints between body parts
        createConstraints(torsoHeight, upperArmLength, lowerArmLength, upperLegLength, lowerLegLength, hipOffset)

        // Apply initial velocities to all body parts
        applyInitialVelocities(playerVelocity, sideLean, forwardLean, yawRad)
    }

    private fun createBodyPart(
        part: BodyPart,
        shape: btCollisionShape,
        mass: Float,
        x: Float, y: Float, z: Float,
        yaw: Float
    ) {
        part.shape = shape

        val inertia = Vector3()
        shape.calculateLocalInertia(mass, inertia)

        tempMatrix.idt()
        tempMatrix.translate(x, y, z)
        tempMatrix.rotate(Vector3.Y, yaw)

        part.motionState = btDefaultMotionState(tempMatrix)
        val info = btRigidBody.btRigidBodyConstructionInfo(mass, part.motionState, shape, inertia)
        part.body = btRigidBody(info)
        part.body!!.friction = 0.6f
        part.body!!.restitution = 0.1f
        part.body!!.setDamping(0.1f, 0.3f)

        // Disable deactivation so ragdoll keeps moving
        part.body!!.activationState = 4  // DISABLE_DEACTIVATION

        dynamicsWorld.addRigidBody(part.body)
        info.dispose()
    }

    private fun createConstraints(
        torsoHeight: Float,
        upperArmLength: Float,
        lowerArmLength: Float,
        upperLegLength: Float,
        lowerLegLength: Float,
        hipOffset: Float
    ) {
        // torsoHeight is full height, box half-extent is torsoHeight/2
        val torsoHalfHeight = torsoHeight / 2
        val riderScale = 1.4f
        val torsoWidth = 0.175f * riderScale

        // Neck joint (head to torso) - connect top of torso to bottom of head
        createConeTwistConstraint(
            torso.body!!, head.body!!,
            Vector3(0f, torsoHalfHeight, 0f),  // Top of torso
            Vector3(0f, -0.13f * riderScale, 0f),  // Bottom of head
            40f, 40f, 30f  // Swing limits and twist
        )

        // Left shoulder - arms hang down from shoulders
        createConeTwistConstraint(
            torso.body!!, leftUpperArm.body!!,
            Vector3(-torsoWidth, torsoHalfHeight - 0.05f * riderScale, 0f),  // Left side of torso, near top
            Vector3(0f, upperArmLength / 2, 0f),  // Top of upper arm
            90f, 60f, 45f
        )

        // Left elbow
        createHingeConstraint(
            leftUpperArm.body!!, leftLowerArm.body!!,
            Vector3(0f, -upperArmLength / 2, 0f),  // Bottom of upper arm
            Vector3(0f, lowerArmLength / 2, 0f),  // Top of lower arm
            Vector3.X, 0f, 140f
        )

        // Right shoulder
        createConeTwistConstraint(
            torso.body!!, rightUpperArm.body!!,
            Vector3(torsoWidth, torsoHalfHeight - 0.05f * riderScale, 0f),  // Right side of torso
            Vector3(0f, upperArmLength / 2, 0f),  // Top of upper arm
            90f, 60f, 45f
        )

        // Right elbow
        createHingeConstraint(
            rightUpperArm.body!!, rightLowerArm.body!!,
            Vector3(0f, -upperArmLength / 2, 0f),
            Vector3(0f, lowerArmLength / 2, 0f),
            Vector3.X, 0f, 140f
        )

        // Left hip
        createConeTwistConstraint(
            torso.body!!, leftUpperLeg.body!!,
            Vector3(-hipOffset, -torsoHalfHeight, 0f),  // Bottom of torso
            Vector3(0f, upperLegLength / 2, 0f),  // Top of upper leg
            60f, 45f, 30f
        )

        // Left knee
        createHingeConstraint(
            leftUpperLeg.body!!, leftLowerLeg.body!!,
            Vector3(0f, -upperLegLength / 2, 0f),
            Vector3(0f, lowerLegLength / 2, 0f),
            Vector3.X, 0f, 140f
        )

        // Right hip
        createConeTwistConstraint(
            torso.body!!, rightUpperLeg.body!!,
            Vector3(hipOffset, -torsoHalfHeight, 0f),  // Bottom of torso
            Vector3(0f, upperLegLength / 2, 0f),  // Top of upper leg
            60f, 45f, 30f
        )

        // Right knee
        createHingeConstraint(
            rightUpperLeg.body!!, rightLowerLeg.body!!,
            Vector3(0f, -upperLegLength / 2, 0f),
            Vector3(0f, lowerLegLength / 2, 0f),
            Vector3.X, 0f, 140f
        )
    }

    private fun createConeTwistConstraint(
        bodyA: btRigidBody, bodyB: btRigidBody,
        pivotA: Vector3, pivotB: Vector3,
        swingSpan1: Float, swingSpan2: Float, twistSpan: Float
    ) {
        tempMatrix.idt()
        tempMatrix.translate(pivotA)
        tempMatrix2.idt()
        tempMatrix2.translate(pivotB)

        val constraint = btConeTwistConstraint(bodyA, bodyB, tempMatrix, tempMatrix2)
        constraint.setLimit(
            Math.toRadians(swingSpan1.toDouble()).toFloat(),
            Math.toRadians(swingSpan2.toDouble()).toFloat(),
            Math.toRadians(twistSpan.toDouble()).toFloat()
        )

        dynamicsWorld.addConstraint(constraint, true)
        constraints.add(constraint)
    }

    private fun createHingeConstraint(
        bodyA: btRigidBody, bodyB: btRigidBody,
        pivotA: Vector3, pivotB: Vector3,
        axis: Vector3,
        lowLimit: Float, highLimit: Float
    ) {
        val constraint = btHingeConstraint(bodyA, bodyB, pivotA, pivotB, axis, axis)
        constraint.setLimit(
            Math.toRadians(lowLimit.toDouble()).toFloat(),
            Math.toRadians(highLimit.toDouble()).toFloat()
        )

        dynamicsWorld.addConstraint(constraint, true)
        constraints.add(constraint)
    }

    private fun applyInitialVelocities(
        playerVelocity: Float,
        sideLean: Float,
        forwardLean: Float,
        yawRad: Float
    ) {
        // Calculate base velocity (thrown forward)
        val forwardX = kotlin.math.sin(yawRad) * playerVelocity * 1.3f
        val forwardZ = kotlin.math.cos(yawRad) * playerVelocity * 1.3f
        val sideVel = sideLean * 2f
        val upVel = 1f + kotlin.math.abs(forwardLean) * 1.5f

        val baseLinearVelocity = Vector3(forwardX + sideVel, upVel, forwardZ)

        // Angular velocity (tumbling)
        val tumbleX = playerVelocity * 0.4f + forwardLean * 2f  // Forward tumble
        val tumbleY = sideLean * 1.5f
        val tumbleZ = sideLean * 0.5f
        val baseAngularVelocity = Vector3(tumbleX, tumbleY, tumbleZ)

        // Apply to all body parts with slight variations
        applyVelocityToPart(torso, baseLinearVelocity, baseAngularVelocity, 1.0f)
        applyVelocityToPart(head, baseLinearVelocity, baseAngularVelocity, 1.1f)
        applyVelocityToPart(leftUpperArm, baseLinearVelocity, baseAngularVelocity, 0.9f)
        applyVelocityToPart(leftLowerArm, baseLinearVelocity, baseAngularVelocity, 0.85f)
        applyVelocityToPart(rightUpperArm, baseLinearVelocity, baseAngularVelocity, 0.9f)
        applyVelocityToPart(rightLowerArm, baseLinearVelocity, baseAngularVelocity, 0.85f)
        applyVelocityToPart(leftUpperLeg, baseLinearVelocity, baseAngularVelocity, 0.8f)
        applyVelocityToPart(leftLowerLeg, baseLinearVelocity, baseAngularVelocity, 0.7f)
        applyVelocityToPart(rightUpperLeg, baseLinearVelocity, baseAngularVelocity, 0.8f)
        applyVelocityToPart(rightLowerLeg, baseLinearVelocity, baseAngularVelocity, 0.7f)
    }

    private fun applyVelocityToPart(part: BodyPart, linearVel: Vector3, angularVel: Vector3, factor: Float) {
        part.body?.let {
            it.linearVelocity = Vector3(linearVel.x * factor, linearVel.y * factor, linearVel.z * factor)
            it.angularVelocity = Vector3(angularVel.x * factor, angularVel.y * factor, angularVel.z * factor)
        }
    }

    /**
     * Step the physics simulation.
     */
    fun update(delta: Float) {
        if (!isActive) return

        // Step physics world (max 4 substeps for stability)
        dynamicsWorld.stepSimulation(delta, 4, 1f / 60f)
    }

    /**
     * Get current EUC transform for rendering.
     */
    fun getEucTransform(): Matrix4? {
        if ((!isActive && !isFrozen) || eucBody == null) return null
        eucMotionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get EUC position only.
     */
    fun getEucPosition(out: Vector3): Vector3 {
        if ((!isActive && !isFrozen) || eucBody == null) return out.setZero()
        eucMotionState?.getWorldTransform(tempMatrix)
        tempMatrix.getTranslation(out)
        return out
    }

    // Body part transform getters for rendering
    fun getHeadTransform(): Matrix4? = getPartTransform(head)
    fun getTorsoTransform(): Matrix4? = getPartTransform(torso)
    fun getLeftUpperArmTransform(): Matrix4? = getPartTransform(leftUpperArm)
    fun getLeftLowerArmTransform(): Matrix4? = getPartTransform(leftLowerArm)
    fun getRightUpperArmTransform(): Matrix4? = getPartTransform(rightUpperArm)
    fun getRightLowerArmTransform(): Matrix4? = getPartTransform(rightLowerArm)
    fun getLeftUpperLegTransform(): Matrix4? = getPartTransform(leftUpperLeg)
    fun getLeftLowerLegTransform(): Matrix4? = getPartTransform(leftLowerLeg)
    fun getRightUpperLegTransform(): Matrix4? = getPartTransform(rightUpperLeg)
    fun getRightLowerLegTransform(): Matrix4? = getPartTransform(rightLowerLeg)

    private fun getPartTransform(part: BodyPart): Matrix4? {
        if ((!isActive && !isFrozen) || part.body == null) return null
        part.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    // Position getters
    fun getHeadPosition(out: Vector3): Vector3 = getPartPosition(head, out)
    fun getTorsoPosition(out: Vector3): Vector3 = getPartPosition(torso, out)

    private fun getPartPosition(part: BodyPart, out: Vector3): Vector3 {
        if ((!isActive && !isFrozen) || part.body == null) return out.setZero()
        part.motionState?.getWorldTransform(tempMatrix)
        tempMatrix.getTranslation(out)
        return out
    }

    /**
     * Check if simulation is active (or frozen but still visible).
     */
    fun isActive(): Boolean = isActive || isFrozen

    /**
     * Freeze the ragdoll in place - stops physics but keeps it visible.
     */
    fun freeze() {
        if (isActive) {
            isActive = false
            isFrozen = true
        }
    }

    /**
     * Add a box collider for world objects (cars, buildings, etc.)
     * @param position Center position of the box
     * @param halfExtents Half-size in each dimension (width/2, height/2, depth/2)
     * @param yaw Rotation around Y axis in degrees
     */
    fun addBoxCollider(position: Vector3, halfExtents: Vector3, yaw: Float = 0f) {
        val shape = btBoxShape(halfExtents)

        tempMatrix.idt()
        tempMatrix.translate(position)
        if (yaw != 0f) {
            tempMatrix.rotate(Vector3.Y, yaw)
        }

        val motionState = btDefaultMotionState(tempMatrix)
        val info = btRigidBody.btRigidBodyConstructionInfo(0f, motionState, shape, Vector3.Zero)
        val body = btRigidBody(info)
        body.friction = 0.5f
        body.restitution = 0.3f

        dynamicsWorld.addRigidBody(body)
        worldColliders.add(StaticCollider(shape, body, motionState))
        info.dispose()
    }

    /**
     * Add a cylinder collider (for street lights, poles, etc.)
     * @param position Center position
     * @param radius Radius of cylinder
     * @param height Height of cylinder
     */
    fun addCylinderCollider(position: Vector3, radius: Float, height: Float) {
        val shape = btCylinderShape(Vector3(radius, height / 2f, radius))

        tempMatrix.idt()
        tempMatrix.translate(position)

        val motionState = btDefaultMotionState(tempMatrix)
        val info = btRigidBody.btRigidBodyConstructionInfo(0f, motionState, shape, Vector3.Zero)
        val body = btRigidBody(info)
        body.friction = 0.5f
        body.restitution = 0.2f

        dynamicsWorld.addRigidBody(body)
        worldColliders.add(StaticCollider(shape, body, motionState))
        info.dispose()
    }

    /**
     * Clear all world colliders (call before adding new ones for a new fall).
     */
    fun clearWorldColliders() {
        for (collider in worldColliders) {
            dynamicsWorld.removeRigidBody(collider.body)
            collider.body.dispose()
            collider.motionState.dispose()
            collider.shape.dispose()
        }
        worldColliders.clear()
    }

    /**
     * Stop the simulation and cleanup bodies.
     */
    fun stop() {
        cleanup()
        isActive = false
        isFrozen = false
    }

    private fun cleanup() {
        // Remove constraints first
        for (constraint in constraints) {
            dynamicsWorld.removeConstraint(constraint)
            constraint.dispose()
        }
        constraints.clear()

        // Cleanup world colliders
        clearWorldColliders()

        // Cleanup EUC
        cleanupBody(eucBody, eucMotionState, eucShape)
        eucBody = null
        eucMotionState = null
        eucShape = null

        // Cleanup body parts
        cleanupPart(head)
        cleanupPart(torso)
        cleanupPart(leftUpperArm)
        cleanupPart(leftLowerArm)
        cleanupPart(rightUpperArm)
        cleanupPart(rightLowerArm)
        cleanupPart(leftUpperLeg)
        cleanupPart(leftLowerLeg)
        cleanupPart(rightUpperLeg)
        cleanupPart(rightLowerLeg)
    }

    private fun cleanupBody(body: btRigidBody?, motionState: btDefaultMotionState?, shape: btCollisionShape?) {
        body?.let {
            dynamicsWorld.removeRigidBody(it)
            it.dispose()
        }
        motionState?.dispose()
        shape?.dispose()
    }

    private fun cleanupPart(part: BodyPart) {
        part.body?.let {
            dynamicsWorld.removeRigidBody(it)
            it.dispose()
        }
        part.body = null
        part.motionState?.dispose()
        part.motionState = null
        part.shape?.dispose()
        part.shape = null
    }

    override fun dispose() {
        cleanup()

        // Dispose ground
        dynamicsWorld.removeRigidBody(groundBody)
        groundBody.dispose()
        groundMotionState?.dispose()
        groundShape.dispose()

        // Dispose world
        dynamicsWorld.dispose()
        constraintSolver.dispose()
        broadphase.dispose()
        dispatcher.dispose()
        collisionConfig.dispose()

        Gdx.app.log("RagdollPhysics", "Physics disposed")
    }
}
