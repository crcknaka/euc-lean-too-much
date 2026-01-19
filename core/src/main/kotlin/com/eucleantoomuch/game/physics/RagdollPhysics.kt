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

    /**
     * Types of objects that can collide with ragdoll during flight.
     */
    enum class ColliderType {
        GROUND,
        STREET_LIGHT,
        RECYCLE_BIN,
        CAR,
        PEDESTRIAN,
        GENERIC
    }

    data class StaticCollider(
        val shape: btCollisionShape,
        val body: btRigidBody,
        val motionState: btDefaultMotionState,
        val type: ColliderType = ColliderType.GENERIC
    )

    // Collision callback - called when ragdoll hits something during flight
    var onRagdollCollision: ((ColliderType) -> Unit)? = null

    // Track which colliders have already triggered sound (to avoid spam)
    private val triggeredColliders = mutableSetOf<btRigidBody>()
    private var lastCollisionTime = 0f
    private val minCollisionInterval = 0.15f  // Minimum time between collision sounds

    // Pedestrian ragdoll bodies - full articulated ragdoll like player
    data class PedestrianRagdoll(
        val head: BodyPart = BodyPart(),
        val torso: BodyPart = BodyPart(),
        val leftUpperArm: BodyPart = BodyPart(),
        val leftLowerArm: BodyPart = BodyPart(),
        val rightUpperArm: BodyPart = BodyPart(),
        val rightLowerArm: BodyPart = BodyPart(),
        val leftUpperLeg: BodyPart = BodyPart(),
        val leftLowerLeg: BodyPart = BodyPart(),
        val rightUpperLeg: BodyPart = BodyPart(),
        val rightLowerLeg: BodyPart = BodyPart(),
        val constraints: MutableList<btTypedConstraint> = mutableListOf(),
        var entityIndex: Int = -1
    )
    private val pedestrianRagdolls = mutableListOf<PedestrianRagdoll>()

    // Legacy simple pedestrian bodies (kept for compatibility)
    data class PedestrianBody(
        var shape: btCollisionShape? = null,
        var body: btRigidBody? = null,
        var motionState: btDefaultMotionState? = null,
        var entityIndex: Int = -1  // To track which entity this belongs to
    )
    private val pedestrianBodies = mutableListOf<PedestrianBody>()

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

        val eucMass = 35f  // 35 kg EUC (heavy wheel like Begode, Veteran)
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

        // Apply initial velocity - based on lean direction
        val forwardX = kotlin.math.sin(yawRad) * playerVelocity * 0.7f
        val forwardZ = kotlin.math.cos(yawRad) * playerVelocity * 0.7f
        val sideKick = sideLean * 3f
        // Only add upward kick if there's significant lean (collision), otherwise just continue momentum
        val leanMagnitude = kotlin.math.sqrt(sideLean * sideLean + forwardLean * forwardLean)
        val upKick = if (leanMagnitude > 0.3f) 1f + leanMagnitude * 1.5f else 0.5f

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
        // Calculate lean magnitude to determine fall type
        val leanMagnitude = kotlin.math.sqrt(sideLean * sideLean + forwardLean * forwardLean)
        val isHardFall = leanMagnitude > 0.3f  // Collision vs wobble/speed fall

        // Calculate base velocity (continue forward momentum)
        val velocityMultiplier = if (isHardFall) 1.3f else 1.0f
        val forwardX = kotlin.math.sin(yawRad) * playerVelocity * velocityMultiplier
        val forwardZ = kotlin.math.cos(yawRad) * playerVelocity * velocityMultiplier
        val sideVel = sideLean * 2f
        // Only significant upward velocity on hard falls (collisions)
        val upVel = if (isHardFall) 0.5f + leanMagnitude * 1.5f else 0.2f

        val baseLinearVelocity = Vector3(forwardX + sideVel, upVel, forwardZ)

        // Angular velocity (tumbling) - more on hard falls
        val tumbleFactor = if (isHardFall) 1.0f else 0.5f
        val tumbleX = (playerVelocity * 0.3f + forwardLean * 2f) * tumbleFactor  // Forward tumble
        val tumbleY = sideLean * 1.5f * tumbleFactor
        val tumbleZ = sideLean * 0.5f * tumbleFactor
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
     * Runs if main ragdoll is active OR if there are falling pedestrians.
     */
    fun update(delta: Float) {
        // Run simulation if main ragdoll is active or there are pedestrian ragdolls
        if (!isActive && pedestrianRagdolls.isEmpty()) return

        // Step physics world (max 4 substeps for stability)
        dynamicsWorld.stepSimulation(delta, 4, 1f / 60f)

        // Check for collisions and trigger sounds
        if (isActive) {
            lastCollisionTime += delta
            checkRagdollCollisions()
        }
    }

    /**
     * Check contact manifolds for ragdoll collisions with world objects.
     */
    private fun checkRagdollCollisions() {
        if (onRagdollCollision == null) return

        val numManifolds = dispatcher.numManifolds

        for (i in 0 until numManifolds) {
            val manifold = dispatcher.getManifoldByIndexInternal(i)
            val numContacts = manifold.numContacts

            if (numContacts == 0) continue

            // Get the two bodies involved
            val body0 = manifold.body0 as? btRigidBody ?: continue
            val body1 = manifold.body1 as? btRigidBody ?: continue

            // Check if one is a ragdoll part and the other is a world collider
            val ragdollBody = findRagdollBody(body0, body1)
            val worldBody = if (ragdollBody == body0) body1 else if (ragdollBody == body1) body0 else null

            if (ragdollBody == null || worldBody == null) continue

            // Find the collider type
            val collider = worldColliders.find { it.body == worldBody }

            // Skip if already triggered for this collider or if ground
            if (collider != null) {
                if (collider.type == ColliderType.GROUND) continue
                if (worldBody in triggeredColliders) continue

                // Check if enough time has passed since last collision sound
                if (lastCollisionTime < minCollisionInterval) continue

                // Check contact impulse - only trigger for significant impacts
                var maxImpulse = 0f
                for (j in 0 until numContacts) {
                    val pt = manifold.getContactPoint(j)
                    val impulse = pt.appliedImpulse
                    if (impulse > maxImpulse) maxImpulse = impulse
                }

                // Only trigger sound for significant impacts
                if (maxImpulse > 5f) {
                    triggeredColliders.add(worldBody)
                    lastCollisionTime = 0f
                    onRagdollCollision?.invoke(collider.type)
                }
            }
        }
    }

    /**
     * Check if a body is part of the player ragdoll (or EUC).
     */
    private fun findRagdollBody(body0: btRigidBody, body1: btRigidBody): btRigidBody? {
        val ragdollBodies = listOfNotNull(
            eucBody, head.body, torso.body,
            leftUpperArm.body, leftLowerArm.body,
            rightUpperArm.body, rightLowerArm.body,
            leftUpperLeg.body, leftLowerLeg.body,
            rightUpperLeg.body, rightLowerLeg.body
        )

        return when {
            body0 in ragdollBodies -> body0
            body1 in ragdollBodies -> body1
            else -> null
        }
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
     * @param type Type of obstacle for collision sounds
     */
    fun addBoxCollider(position: Vector3, halfExtents: Vector3, yaw: Float = 0f, type: ColliderType = ColliderType.GENERIC) {
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
        worldColliders.add(StaticCollider(shape, body, motionState, type))
        info.dispose()
    }

    /**
     * Add a cylinder collider (for street lights, poles, etc.)
     * @param position Center position
     * @param radius Radius of cylinder
     * @param height Height of cylinder
     * @param type Type of obstacle for collision sounds
     */
    fun addCylinderCollider(position: Vector3, radius: Float, height: Float, type: ColliderType = ColliderType.STREET_LIGHT) {
        val shape = btCylinderShape(Vector3(radius, height / 2f, radius))

        tempMatrix.idt()
        tempMatrix.translate(position)

        val motionState = btDefaultMotionState(tempMatrix)
        val info = btRigidBody.btRigidBodyConstructionInfo(0f, motionState, shape, Vector3.Zero)
        val body = btRigidBody(info)
        body.friction = 0.5f
        body.restitution = 0.2f

        dynamicsWorld.addRigidBody(body)
        worldColliders.add(StaticCollider(shape, body, motionState, type))
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
        triggeredColliders.clear()
        lastCollisionTime = 0f
    }

    /**
     * Add a full articulated pedestrian ragdoll (with head, torso, arms, legs).
     * @param position Center position of the pedestrian (feet level)
     * @param yaw Rotation around Y axis in degrees
     * @param playerVelocity Player's speed at collision (used for impact force)
     * @param playerDirection Direction player was moving (normalized)
     * @param entityIndex Index to track which entity this belongs to
     * @return Index of the created pedestrian ragdoll
     */
    fun addPedestrianRagdoll(
        position: Vector3,
        yaw: Float,
        playerVelocity: Float,
        playerDirection: Vector3,
        entityIndex: Int
    ): Int {
        val ragdoll = PedestrianRagdoll(entityIndex = entityIndex)

        val yawRad = Math.toRadians(yaw.toDouble()).toFloat()

        // Pedestrian scale (slightly smaller than player)
        val scale = 1.0f

        // Base position (hip level)
        val baseX = position.x
        val baseY = position.y + 0.9f * scale  // Hip height
        val baseZ = position.z

        // Body dimensions
        val torsoWidth = 0.15f * scale
        val torsoHeight = 0.5f * scale
        val torsoDepth = 0.1f * scale
        val headRadius = 0.1f * scale
        val upperArmRadius = 0.035f * scale
        val upperArmLength = 0.28f * scale
        val lowerArmRadius = 0.03f * scale
        val lowerArmLength = 0.25f * scale
        val upperLegRadius = 0.05f * scale
        val upperLegLength = 0.4f * scale
        val lowerLegRadius = 0.04f * scale
        val lowerLegLength = 0.4f * scale

        // Masses (lighter than player, realistic)
        val torsoMass = 25f
        val headMass = 4f
        val upperArmMass = 2f
        val lowerArmMass = 1.2f
        val upperLegMass = 7f
        val lowerLegMass = 3.5f

        // Create torso
        createPedestrianBodyPart(
            ragdoll.torso, btBoxShape(Vector3(torsoWidth, torsoHeight / 2, torsoDepth)), torsoMass,
            baseX, baseY + torsoHeight / 2, baseZ, yaw
        )

        // Create head
        createPedestrianBodyPart(
            ragdoll.head, btSphereShape(headRadius), headMass,
            baseX, baseY + torsoHeight + headRadius * 0.8f, baseZ, yaw
        )

        // Arms - positioned at shoulder level
        val shoulderY = baseY + torsoHeight - 0.05f * scale
        val shoulderOffset = torsoWidth + 0.02f * scale

        // Left arm
        createPedestrianBodyPart(
            ragdoll.leftUpperArm, btBoxShape(Vector3(upperArmRadius, upperArmLength / 2, upperArmRadius)), upperArmMass,
            baseX - shoulderOffset, shoulderY - upperArmLength / 2, baseZ, yaw
        )
        createPedestrianBodyPart(
            ragdoll.leftLowerArm, btBoxShape(Vector3(lowerArmRadius, lowerArmLength / 2, lowerArmRadius)), lowerArmMass,
            baseX - shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, baseZ, yaw
        )

        // Right arm
        createPedestrianBodyPart(
            ragdoll.rightUpperArm, btBoxShape(Vector3(upperArmRadius, upperArmLength / 2, upperArmRadius)), upperArmMass,
            baseX + shoulderOffset, shoulderY - upperArmLength / 2, baseZ, yaw
        )
        createPedestrianBodyPart(
            ragdoll.rightLowerArm, btBoxShape(Vector3(lowerArmRadius, lowerArmLength / 2, lowerArmRadius)), lowerArmMass,
            baseX + shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, baseZ, yaw
        )

        // Legs - positioned at hip level
        val hipOffset = 0.08f * scale

        // Left leg
        createPedestrianBodyPart(
            ragdoll.leftUpperLeg, btBoxShape(Vector3(upperLegRadius, upperLegLength / 2, upperLegRadius)), upperLegMass,
            baseX - hipOffset, baseY - upperLegLength / 2, baseZ, yaw
        )
        createPedestrianBodyPart(
            ragdoll.leftLowerLeg, btBoxShape(Vector3(lowerLegRadius, lowerLegLength / 2, lowerLegRadius)), lowerLegMass,
            baseX - hipOffset, baseY - upperLegLength - lowerLegLength / 2, baseZ, yaw
        )

        // Right leg
        createPedestrianBodyPart(
            ragdoll.rightUpperLeg, btBoxShape(Vector3(upperLegRadius, upperLegLength / 2, upperLegRadius)), upperLegMass,
            baseX + hipOffset, baseY - upperLegLength / 2, baseZ, yaw
        )
        createPedestrianBodyPart(
            ragdoll.rightLowerLeg, btBoxShape(Vector3(lowerLegRadius, lowerLegLength / 2, lowerLegRadius)), lowerLegMass,
            baseX + hipOffset, baseY - upperLegLength - lowerLegLength / 2, baseZ, yaw
        )

        // Create constraints between body parts
        createPedestrianConstraints(ragdoll, torsoHeight / 2, torsoWidth, upperArmLength, lowerArmLength, upperLegLength, lowerLegLength, hipOffset, scale)

        // Apply initial velocities from impact
        applyPedestrianImpactVelocities(ragdoll, playerVelocity, playerDirection, yawRad)

        pedestrianRagdolls.add(ragdoll)

        Gdx.app.log("RagdollPhysics", "Added articulated pedestrian ragdoll at $position, velocity=$playerVelocity")

        return pedestrianRagdolls.size - 1
    }

    private fun createPedestrianBodyPart(
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
        part.body!!.activationState = 4  // DISABLE_DEACTIVATION

        dynamicsWorld.addRigidBody(part.body)
        info.dispose()
    }

    private fun createPedestrianConstraints(
        ragdoll: PedestrianRagdoll,
        torsoHalfHeight: Float,
        torsoWidth: Float,
        upperArmLength: Float,
        lowerArmLength: Float,
        upperLegLength: Float,
        lowerLegLength: Float,
        hipOffset: Float,
        scale: Float
    ) {
        // Neck joint (head to torso)
        createPedestrianConeTwistConstraint(
            ragdoll, ragdoll.torso.body!!, ragdoll.head.body!!,
            Vector3(0f, torsoHalfHeight, 0f),
            Vector3(0f, -0.1f * scale, 0f),
            40f, 40f, 30f
        )

        // Left shoulder
        createPedestrianConeTwistConstraint(
            ragdoll, ragdoll.torso.body!!, ragdoll.leftUpperArm.body!!,
            Vector3(-torsoWidth, torsoHalfHeight - 0.05f * scale, 0f),
            Vector3(0f, upperArmLength / 2, 0f),
            90f, 60f, 45f
        )

        // Left elbow
        createPedestrianHingeConstraint(
            ragdoll, ragdoll.leftUpperArm.body!!, ragdoll.leftLowerArm.body!!,
            Vector3(0f, -upperArmLength / 2, 0f),
            Vector3(0f, lowerArmLength / 2, 0f),
            Vector3.X, 0f, 140f
        )

        // Right shoulder
        createPedestrianConeTwistConstraint(
            ragdoll, ragdoll.torso.body!!, ragdoll.rightUpperArm.body!!,
            Vector3(torsoWidth, torsoHalfHeight - 0.05f * scale, 0f),
            Vector3(0f, upperArmLength / 2, 0f),
            90f, 60f, 45f
        )

        // Right elbow
        createPedestrianHingeConstraint(
            ragdoll, ragdoll.rightUpperArm.body!!, ragdoll.rightLowerArm.body!!,
            Vector3(0f, -upperArmLength / 2, 0f),
            Vector3(0f, lowerArmLength / 2, 0f),
            Vector3.X, 0f, 140f
        )

        // Left hip
        createPedestrianConeTwistConstraint(
            ragdoll, ragdoll.torso.body!!, ragdoll.leftUpperLeg.body!!,
            Vector3(-hipOffset, -torsoHalfHeight, 0f),
            Vector3(0f, upperLegLength / 2, 0f),
            60f, 45f, 30f
        )

        // Left knee
        createPedestrianHingeConstraint(
            ragdoll, ragdoll.leftUpperLeg.body!!, ragdoll.leftLowerLeg.body!!,
            Vector3(0f, -upperLegLength / 2, 0f),
            Vector3(0f, lowerLegLength / 2, 0f),
            Vector3.X, 0f, 140f
        )

        // Right hip
        createPedestrianConeTwistConstraint(
            ragdoll, ragdoll.torso.body!!, ragdoll.rightUpperLeg.body!!,
            Vector3(hipOffset, -torsoHalfHeight, 0f),
            Vector3(0f, upperLegLength / 2, 0f),
            60f, 45f, 30f
        )

        // Right knee
        createPedestrianHingeConstraint(
            ragdoll, ragdoll.rightUpperLeg.body!!, ragdoll.rightLowerLeg.body!!,
            Vector3(0f, -upperLegLength / 2, 0f),
            Vector3(0f, lowerLegLength / 2, 0f),
            Vector3.X, 0f, 140f
        )
    }

    private fun createPedestrianConeTwistConstraint(
        ragdoll: PedestrianRagdoll,
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
        ragdoll.constraints.add(constraint)
    }

    private fun createPedestrianHingeConstraint(
        ragdoll: PedestrianRagdoll,
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
        ragdoll.constraints.add(constraint)
    }

    private fun applyPedestrianImpactVelocities(
        ragdoll: PedestrianRagdoll,
        playerVelocity: Float,
        playerDirection: Vector3,
        yawRad: Float
    ) {
        // Calculate impact force (realistic stumble, not flying)
        val impactForce = playerVelocity * 1.5f  // Realistic stumble force
        val upwardForce = 0.3f + playerVelocity * 0.03f  // Minimal upward

        val baseLinearVelocity = Vector3(
            playerDirection.x * impactForce,
            upwardForce,
            playerDirection.z * impactForce
        )

        // Angular velocity (tumbling)
        val tumbleX = playerVelocity * 0.3f  // Forward tumble
        val tumbleY = playerDirection.x * 0.5f
        val tumbleZ = -playerDirection.z * 0.2f
        val baseAngularVelocity = Vector3(tumbleX, tumbleY, tumbleZ)

        // Apply to all body parts with variations
        applyVelocityToPedestrianPart(ragdoll.torso, baseLinearVelocity, baseAngularVelocity, 1.0f)
        applyVelocityToPedestrianPart(ragdoll.head, baseLinearVelocity, baseAngularVelocity, 1.1f)
        applyVelocityToPedestrianPart(ragdoll.leftUpperArm, baseLinearVelocity, baseAngularVelocity, 0.9f)
        applyVelocityToPedestrianPart(ragdoll.leftLowerArm, baseLinearVelocity, baseAngularVelocity, 0.85f)
        applyVelocityToPedestrianPart(ragdoll.rightUpperArm, baseLinearVelocity, baseAngularVelocity, 0.9f)
        applyVelocityToPedestrianPart(ragdoll.rightLowerArm, baseLinearVelocity, baseAngularVelocity, 0.85f)
        applyVelocityToPedestrianPart(ragdoll.leftUpperLeg, baseLinearVelocity, baseAngularVelocity, 0.8f)
        applyVelocityToPedestrianPart(ragdoll.leftLowerLeg, baseLinearVelocity, baseAngularVelocity, 0.7f)
        applyVelocityToPedestrianPart(ragdoll.rightUpperLeg, baseLinearVelocity, baseAngularVelocity, 0.8f)
        applyVelocityToPedestrianPart(ragdoll.rightLowerLeg, baseLinearVelocity, baseAngularVelocity, 0.7f)
    }

    private fun applyVelocityToPedestrianPart(part: BodyPart, linearVel: Vector3, angularVel: Vector3, factor: Float) {
        part.body?.let {
            it.linearVelocity = Vector3(linearVel.x * factor, linearVel.y * factor, linearVel.z * factor)
            it.angularVelocity = Vector3(angularVel.x * factor, angularVel.y * factor, angularVel.z * factor)
        }
    }

    /**
     * Get torso transform for a pedestrian ragdoll (main body position).
     * @param index Index returned by addPedestrianRagdoll
     * @return Transform matrix or null if invalid
     */
    fun getPedestrianTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.torso.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get head transform for a pedestrian ragdoll.
     */
    fun getPedestrianHeadTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.head.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get torso transform for a pedestrian ragdoll.
     */
    fun getPedestrianTorsoTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.torso.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get left upper arm transform for a pedestrian ragdoll.
     */
    fun getPedestrianLeftUpperArmTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.leftUpperArm.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get left lower arm transform for a pedestrian ragdoll.
     */
    fun getPedestrianLeftLowerArmTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.leftLowerArm.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get right upper arm transform for a pedestrian ragdoll.
     */
    fun getPedestrianRightUpperArmTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.rightUpperArm.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get right lower arm transform for a pedestrian ragdoll.
     */
    fun getPedestrianRightLowerArmTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.rightLowerArm.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get left upper leg transform for a pedestrian ragdoll.
     */
    fun getPedestrianLeftUpperLegTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.leftUpperLeg.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get left lower leg transform for a pedestrian ragdoll.
     */
    fun getPedestrianLeftLowerLegTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.leftLowerLeg.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get right upper leg transform for a pedestrian ragdoll.
     */
    fun getPedestrianRightUpperLegTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.rightUpperLeg.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get right lower leg transform for a pedestrian ragdoll.
     */
    fun getPedestrianRightLowerLegTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.rightLowerLeg.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get number of active pedestrian ragdolls.
     */
    fun getPedestrianCount(): Int = pedestrianRagdolls.size

    /**
     * Clear all pedestrian ragdolls.
     */
    private fun clearPedestrianBodies() {
        for (ragdoll in pedestrianRagdolls) {
            // Remove constraints first
            for (constraint in ragdoll.constraints) {
                dynamicsWorld.removeConstraint(constraint)
                constraint.dispose()
            }
            ragdoll.constraints.clear()

            // Remove body parts
            cleanupPart(ragdoll.head)
            cleanupPart(ragdoll.torso)
            cleanupPart(ragdoll.leftUpperArm)
            cleanupPart(ragdoll.leftLowerArm)
            cleanupPart(ragdoll.rightUpperArm)
            cleanupPart(ragdoll.rightLowerArm)
            cleanupPart(ragdoll.leftUpperLeg)
            cleanupPart(ragdoll.leftLowerLeg)
            cleanupPart(ragdoll.rightUpperLeg)
            cleanupPart(ragdoll.rightLowerLeg)
        }
        pedestrianRagdolls.clear()

        // Also clear legacy simple bodies
        for (pedestrian in pedestrianBodies) {
            pedestrian.body?.let {
                dynamicsWorld.removeRigidBody(it)
                it.dispose()
            }
            pedestrian.motionState?.dispose()
            pedestrian.shape?.dispose()
        }
        pedestrianBodies.clear()
    }

    /**
     * Stop the simulation and cleanup bodies.
     */
    fun stop() {
        cleanup(clearPedestrians = true)  // Full cleanup including pedestrians
        isActive = false
        isFrozen = false
    }

    private fun cleanup(clearPedestrians: Boolean = false) {
        // Remove constraints first
        for (constraint in constraints) {
            dynamicsWorld.removeConstraint(constraint)
            constraint.dispose()
        }
        constraints.clear()

        // Cleanup world colliders
        clearWorldColliders()

        // Only cleanup pedestrian ragdolls if explicitly requested
        // (don't clear them when player ragdoll starts - they should keep falling)
        if (clearPedestrians) {
            clearPedestrianBodies()
        }

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
