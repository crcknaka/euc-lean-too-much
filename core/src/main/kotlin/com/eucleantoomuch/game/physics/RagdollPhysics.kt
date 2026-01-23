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
    // HashMap for O(1) lookup of collider by rigid body (optimization)
    private val worldColliderMap = mutableMapOf<btRigidBody, StaticCollider>()

    /**
     * Types of objects that can collide with ragdoll during flight.
     */
    enum class ColliderType {
        GROUND,
        STREET_LIGHT,
        RECYCLE_BIN,
        CAR,
        PEDESTRIAN,
        BENCH,
        BUILDING,
        TREE,
        GENERIC
    }

    data class StaticCollider(
        val shape: btCollisionShape,
        val body: btRigidBody,
        val motionState: btDefaultMotionState,
        val type: ColliderType = ColliderType.GENERIC
    )

    // Collision callback - called when player ragdoll hits something during flight
    var onRagdollCollision: ((ColliderType) -> Unit)? = null

    // Secondary collision callback - when ragdoll (player or pedestrian) hits world objects
    // Called with lower volume for "chain reaction" collisions
    var onSecondaryRagdollCollision: ((ColliderType) -> Unit)? = null

    // Callback when a ragdoll body hits a standing pedestrian (to knock them down)
    // Returns the entity that was hit and the impact velocity/direction
    var onRagdollHitPedestrian: ((pedestrianPosition: Vector3, impactVelocity: Vector3) -> Unit)? = null

    // Callback when player ragdoll hits the ground (for startling pigeons, etc.)
    // Returns the impact position
    var onRagdollGroundImpact: ((impactPosition: Vector3) -> Unit)? = null

    // Track which colliders have already triggered sound (to avoid spam)
    private val triggeredColliders = mutableSetOf<btRigidBody>()
    private val triggeredSecondaryColliders = mutableSetOf<btRigidBody>()  // For secondary collisions
    private var lastCollisionTime = 0f
    private var lastSecondaryCollisionTime = 0f
    private val minCollisionInterval = 0.15f  // Minimum time between collision sounds
    private val minSecondaryCollisionInterval = 0.2f  // Slightly longer for secondary

    // Track ground impacts for startling pigeons
    private var hasTriggeredGroundImpact = false
    private var lastGroundImpactTime = 0f
    private val minGroundImpactInterval = 0.5f  // Don't spam ground impact events

    // Pedestrian ragdoll bodies - simplified 6-part ragdoll for performance
    data class PedestrianRagdoll(
        val head: BodyPart = BodyPart(),
        val torso: BodyPart = BodyPart(),
        val leftArm: BodyPart = BodyPart(),   // Single arm piece
        val rightArm: BodyPart = BodyPart(),  // Single arm piece
        val leftLeg: BodyPart = BodyPart(),   // Single leg piece
        val rightLeg: BodyPart = BodyPart(),  // Single leg piece
        val constraints: MutableList<btTypedConstraint> = mutableListOf(),
        var entityIndex: Int = -1,
        var shirtColor: com.badlogic.gdx.graphics.Color = com.badlogic.gdx.graphics.Color.GREEN  // Store shirt color
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

    // Dynamic object ragdolls (trash cans, etc.)
    data class DynamicObjectRagdoll(
        var shape: btCollisionShape? = null,
        var body: btRigidBody? = null,
        var motionState: btDefaultMotionState? = null,
        var entityIndex: Int = -1
    )
    private val dynamicObjects = mutableListOf<DynamicObjectRagdoll>()

    // State
    private var isActive = false
    private var isFrozen = false  // When frozen, ragdoll stays visible but doesn't simulate

    // Temp vectors for calculations
    private val tempMatrix = Matrix4()
    private val tempMatrix2 = Matrix4()
    private val tempInertia = Vector3()
    private val tempShapeHalfExtents = Vector3()

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
        dynamicsWorld.gravity = Vector3(0f, -15f, 0f)  // Stronger gravity for more realistic falls

        // Create ground plane at Y = 0 (road surface level)
        // Use a thick box instead of infinite plane for more reliable collision
        groundShape = btStaticPlaneShape(Vector3(0f, 1f, 0f), 0f)
        groundMotionState = btDefaultMotionState()
        val groundInfo = btRigidBody.btRigidBodyConstructionInfo(0f, groundMotionState, groundShape, Vector3.Zero)
        groundBody = btRigidBody(groundInfo)
        groundBody.friction = 0.9f  // High friction for asphalt
        groundBody.restitution = 0.1f  // Slight bounce
        groundBody.collisionFlags = groundBody.collisionFlags or btCollisionObject.CollisionFlags.CF_STATIC_OBJECT
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
    }

    private fun createEucWheel(
        eucPosition: Vector3,
        eucYaw: Float,
        playerVelocity: Float,
        sideLean: Float,
        @Suppress("UNUSED_PARAMETER") forwardLean: Float,
        yawRad: Float
    ) {
        // Simple box shape for EUC - stable and predictable physics
        val wheelWidth = 0.15f
        val wheelHeight = 0.5f
        val wheelDepth = 0.35f
        tempShapeHalfExtents.set(wheelWidth / 2f, wheelHeight / 2f, wheelDepth / 2f)
        eucShape = btBoxShape(tempShapeHalfExtents)

        val eucMass = 20f
        tempInertia.setZero()
        eucShape!!.calculateLocalInertia(eucMass, tempInertia)

        // Determine fall direction
        val fallDirection = if (kotlin.math.abs(sideLean) > 0.1f) {
            if (sideLean > 0) 1f else -1f
        } else {
            if (com.badlogic.gdx.math.MathUtils.random() > 0.5f) 1f else -1f
        }

        // Start position - slightly above ground
        tempMatrix.idt()
        tempMatrix.translate(eucPosition.x, wheelHeight / 2f + 0.05f, eucPosition.z)
        tempMatrix.rotate(Vector3.Y, eucYaw)

        eucMotionState = btDefaultMotionState(tempMatrix)
        val eucInfo = btRigidBody.btRigidBodyConstructionInfo(eucMass, eucMotionState, eucShape, tempInertia)
        eucBody = btRigidBody(eucInfo)
        eucBody!!.friction = 0.8f
        eucBody!!.restitution = 0.1f
        eucBody!!.setDamping(0.3f, 0.4f)
        eucBody!!.activationState = 4  // DISABLE_DEACTIVATION

        // Simple velocity - just forward momentum and slight side push
        val forwardX = kotlin.math.sin(yawRad) * playerVelocity * 0.5f
        val forwardZ = kotlin.math.cos(yawRad) * playerVelocity * 0.5f
        val sideX = kotlin.math.cos(yawRad) * fallDirection * 1.5f
        val sideZ = -kotlin.math.sin(yawRad) * fallDirection * 1.5f

        eucBody!!.linearVelocity = Vector3(forwardX + sideX, 0.5f, forwardZ + sideZ)

        // Simple angular velocity - just tip over to the side
        eucBody!!.angularVelocity = Vector3(
            fallDirection * 3f,  // Tip over sideways
            0f,
            0f
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
        val riderScale = 1.55f

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

        // Create body parts with positions (reuse tempShapeHalfExtents to avoid Vector3 allocations)
        // Torso (center of mass at chest level) - use box shape
        tempShapeHalfExtents.set(torsoWidth, torsoHeight / 2, torsoDepth)
        createBodyPart(
            torso, btBoxShape(tempShapeHalfExtents), torsoMass,
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
        tempShapeHalfExtents.set(upperArmRadius, upperArmLength / 2, upperArmRadius)
        createBodyPart(
            leftUpperArm, btBoxShape(tempShapeHalfExtents), upperArmMass,
            baseX - shoulderOffset, shoulderY - upperArmLength / 2, baseZ, eucYaw
        )
        tempShapeHalfExtents.set(lowerArmRadius, lowerArmLength / 2, lowerArmRadius)
        createBodyPart(
            leftLowerArm, btBoxShape(tempShapeHalfExtents), lowerArmMass,
            baseX - shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, baseZ, eucYaw
        )

        // Right arm
        tempShapeHalfExtents.set(upperArmRadius, upperArmLength / 2, upperArmRadius)
        createBodyPart(
            rightUpperArm, btBoxShape(tempShapeHalfExtents), upperArmMass,
            baseX + shoulderOffset, shoulderY - upperArmLength / 2, baseZ, eucYaw
        )
        tempShapeHalfExtents.set(lowerArmRadius, lowerArmLength / 2, lowerArmRadius)
        createBodyPart(
            rightLowerArm, btBoxShape(tempShapeHalfExtents), lowerArmMass,
            baseX + shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, baseZ, eucYaw
        )

        // Legs - positioned at hip level
        val hipOffset = 0.1f * riderScale

        // Left leg
        tempShapeHalfExtents.set(upperLegRadius, upperLegLength / 2, upperLegRadius)
        createBodyPart(
            leftUpperLeg, btBoxShape(tempShapeHalfExtents), upperLegMass,
            baseX - hipOffset, baseY - upperLegLength / 2, baseZ, eucYaw
        )
        tempShapeHalfExtents.set(lowerLegRadius, lowerLegLength / 2, lowerLegRadius)
        createBodyPart(
            leftLowerLeg, btBoxShape(tempShapeHalfExtents), lowerLegMass,
            baseX - hipOffset, baseY - upperLegLength - lowerLegLength / 2, baseZ, eucYaw
        )

        // Right leg
        tempShapeHalfExtents.set(upperLegRadius, upperLegLength / 2, upperLegRadius)
        createBodyPart(
            rightUpperLeg, btBoxShape(tempShapeHalfExtents), upperLegMass,
            baseX + hipOffset, baseY - upperLegLength / 2, baseZ, eucYaw
        )
        tempShapeHalfExtents.set(lowerLegRadius, lowerLegLength / 2, lowerLegRadius)
        createBodyPart(
            rightLowerLeg, btBoxShape(tempShapeHalfExtents), lowerLegMass,
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

        tempInertia.setZero()
        shape.calculateLocalInertia(mass, tempInertia)

        tempMatrix.idt()
        tempMatrix.translate(x, y, z)
        tempMatrix.rotate(Vector3.Y, yaw)

        part.motionState = btDefaultMotionState(tempMatrix)
        val info = btRigidBody.btRigidBodyConstructionInfo(mass, part.motionState, shape, tempInertia)
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
        val riderScale = 1.55f
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
        // Run simulation if main ragdoll is active, or there are pedestrian ragdolls, or dynamic objects
        if (!isActive && pedestrianRagdolls.isEmpty() && dynamicObjects.isEmpty()) return

        // Clamp delta to prevent physics explosions on lag spikes
        val clampedDelta = delta.coerceIn(0.001f, 0.033f)  // Max ~30 FPS equivalent

        // Step physics world with more substeps for smoother simulation
        dynamicsWorld.stepSimulation(clampedDelta, 8, 1f / 120f)

        // Update collision timers
        lastCollisionTime += delta
        lastSecondaryCollisionTime += delta
        lastGroundImpactTime += delta

        // Check for collisions and trigger sounds
        if (isActive) {
            checkRagdollCollisions()
        }

        // Check for secondary collisions (pedestrian ragdolls hitting world objects)
        if (pedestrianRagdolls.isNotEmpty()) {
            checkSecondaryRagdollCollisions()
        }
    }

    /**
     * Check contact manifolds for ragdoll collisions with world objects.
     */
    private fun checkRagdollCollisions() {
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

            // Check for ground collision (for startling pigeons)
            if (worldBody == groundBody) {
                checkGroundImpact(ragdollBody)
                continue
            }

            // Find the collider type (O(1) HashMap lookup instead of O(n) list search)
            val collider = worldColliderMap[worldBody]
            val colliderType = collider?.type ?: ColliderType.GENERIC

            // Skip ground collisions (handled separately)
            if (colliderType == ColliderType.GROUND) continue

            // Skip if already triggered for this specific collider body
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

            // Only trigger sound for significant impacts (lowered threshold for better feedback)
            if (maxImpulse > 2f) {
                triggeredColliders.add(worldBody)
                lastCollisionTime = 0f
                onRagdollCollision?.invoke(colliderType)

                // Also trigger ground impact callback for startling pigeons at any collision
                if (lastGroundImpactTime >= minGroundImpactInterval) {
                    val impactPos = Vector3()
                    ragdollBody.worldTransform.getTranslation(impactPos)
                    lastGroundImpactTime = 0f
                    onRagdollGroundImpact?.invoke(impactPos)
                }
            }
        }
    }

    /**
     * Check if ragdoll body hitting ground should trigger ground impact event.
     * Used to startle pigeons when player falls near them.
     */
    private fun checkGroundImpact(ragdollBody: btRigidBody) {
        if (onRagdollGroundImpact == null) return
        if (lastGroundImpactTime < minGroundImpactInterval) return

        // Get ragdoll body position for the impact location
        val impactPos = Vector3()
        ragdollBody.worldTransform.getTranslation(impactPos)

        // Trigger ground impact event
        lastGroundImpactTime = 0f
        onRagdollGroundImpact?.invoke(impactPos)
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
     * Check if a body is part of any pedestrian ragdoll.
     * @return The ragdoll body if found, null otherwise
     */
    private fun findPedestrianRagdollBody(body0: btRigidBody, body1: btRigidBody): btRigidBody? {
        for (ragdoll in pedestrianRagdolls) {
            val bodies = listOfNotNull(
                ragdoll.head.body, ragdoll.torso.body,
                ragdoll.leftArm.body, ragdoll.rightArm.body,
                ragdoll.leftLeg.body, ragdoll.rightLeg.body
            )
            if (body0 in bodies) return body0
            if (body1 in bodies) return body1
        }
        return null
    }

    /**
     * Check for secondary collisions - pedestrian ragdolls hitting world objects.
     * These play sounds at lower volume.
     */
    private fun checkSecondaryRagdollCollisions() {
        if (onSecondaryRagdollCollision == null && onRagdollHitPedestrian == null) return

        val numManifolds = dispatcher.numManifolds

        for (i in 0 until numManifolds) {
            val manifold = dispatcher.getManifoldByIndexInternal(i)
            val numContacts = manifold.numContacts

            if (numContacts == 0) continue

            // Get the two bodies involved
            val body0 = manifold.body0 as? btRigidBody ?: continue
            val body1 = manifold.body1 as? btRigidBody ?: continue

            // Check if one is a pedestrian ragdoll part
            val pedRagdollBody = findPedestrianRagdollBody(body0, body1)
            if (pedRagdollBody != null) {
                val worldBody = if (pedRagdollBody == body0) body1 else body0

                // Check if hitting a world collider (not ground, not another ragdoll body)
                // O(1) HashMap lookup instead of O(n) list search
                val collider = worldColliderMap[worldBody]

                if (collider != null && collider.type != ColliderType.GROUND) {
                    // Skip if already triggered
                    if (worldBody in triggeredSecondaryColliders) continue
                    if (lastSecondaryCollisionTime < minSecondaryCollisionInterval) continue

                    // Check contact impulse
                    var maxImpulse = 0f
                    for (j in 0 until numContacts) {
                        val pt = manifold.getContactPoint(j)
                        val impulse = pt.appliedImpulse
                        if (impulse > maxImpulse) maxImpulse = impulse
                    }

                    // Only trigger for significant impacts (higher threshold for secondary)
                    if (maxImpulse > 8f) {
                        triggeredSecondaryColliders.add(worldBody)
                        lastSecondaryCollisionTime = 0f
                        onSecondaryRagdollCollision?.invoke(collider.type)
                    }
                }
            }
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
        val collider = StaticCollider(shape, body, motionState, type)
        worldColliders.add(collider)
        worldColliderMap[body] = collider  // O(1) lookup support
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
        val collider = StaticCollider(shape, body, motionState, type)
        worldColliders.add(collider)
        worldColliderMap[body] = collider  // O(1) lookup support
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
        worldColliderMap.clear()  // Clear HashMap for O(1) lookup
        triggeredColliders.clear()
        triggeredSecondaryColliders.clear()
        lastCollisionTime = 0f
        lastSecondaryCollisionTime = 0f
        lastGroundImpactTime = 0f
    }

    /**
     * Add a simplified 6-part pedestrian ragdoll (head, torso, 2 arms, 2 legs).
     * Optimized for performance - fewer bodies and constraints than full articulated ragdoll.
     * @param position Center position of the pedestrian (feet level)
     * @param yaw Rotation around Y axis in degrees
     * @param playerVelocity Player's speed at collision (used for impact force)
     * @param playerDirection Direction player was moving (normalized)
     * @param entityIndex Index to track which entity this belongs to
     * @param shirtColor Color of the pedestrian's shirt for rendering
     * @return Index of the created pedestrian ragdoll
     */
    fun addPedestrianRagdoll(
        position: Vector3,
        yaw: Float,
        playerVelocity: Float,
        playerDirection: Vector3,
        entityIndex: Int,
        shirtColor: com.badlogic.gdx.graphics.Color = com.badlogic.gdx.graphics.Color.GREEN
    ): Int {
        val ragdoll = PedestrianRagdoll(entityIndex = entityIndex, shirtColor = shirtColor)

        // Pedestrian scale (1.7x for better visibility)
        val scale = 1.7f
        val legScale = 0.85f  // Shorter legs

        // Base position (hip level)
        val baseX = position.x
        val baseY = position.y + 0.9f * scale * legScale  // Hip height (lower due to shorter legs)
        val baseZ = position.z

        // Simplified body dimensions (combined upper+lower parts)
        val torsoWidth = 0.15f * scale
        val torsoHeight = 0.5f * scale
        val torsoDepth = 0.1f * scale
        val headRadius = 0.1f * scale
        val armRadius = 0.04f * scale
        val armLength = 0.5f * scale  // Combined arm length
        val legRadius = 0.055f * scale
        val legLength = 0.75f * scale * legScale  // Combined leg length

        // Simplified masses
        val torsoMass = 25f
        val headMass = 4f
        val armMass = 3f  // Combined arm mass
        val legMass = 10f  // Combined leg mass

        // Create torso (reuse tempShapeHalfExtents to avoid Vector3 allocations)
        tempShapeHalfExtents.set(torsoWidth, torsoHeight / 2, torsoDepth)
        createPedestrianBodyPart(
            ragdoll.torso, btBoxShape(tempShapeHalfExtents), torsoMass,
            baseX, baseY + torsoHeight / 2, baseZ, yaw
        )

        // Create head
        createPedestrianBodyPart(
            ragdoll.head, btSphereShape(headRadius), headMass,
            baseX, baseY + torsoHeight + headRadius * 0.8f, baseZ, yaw
        )

        // Arms - single piece each, positioned at shoulder level
        val shoulderY = baseY + torsoHeight - 0.05f * scale
        val shoulderOffset = torsoWidth + 0.02f * scale

        // Left arm (single piece)
        tempShapeHalfExtents.set(armRadius, armLength / 2, armRadius)
        createPedestrianBodyPart(
            ragdoll.leftArm, btBoxShape(tempShapeHalfExtents), armMass,
            baseX - shoulderOffset, shoulderY - armLength / 2, baseZ, yaw
        )

        // Right arm (single piece)
        tempShapeHalfExtents.set(armRadius, armLength / 2, armRadius)
        createPedestrianBodyPart(
            ragdoll.rightArm, btBoxShape(tempShapeHalfExtents), armMass,
            baseX + shoulderOffset, shoulderY - armLength / 2, baseZ, yaw
        )

        // Legs - single piece each, positioned at hip level
        val hipOffset = 0.08f * scale

        // Left leg (single piece)
        tempShapeHalfExtents.set(legRadius, legLength / 2, legRadius)
        createPedestrianBodyPart(
            ragdoll.leftLeg, btBoxShape(tempShapeHalfExtents), legMass,
            baseX - hipOffset, baseY - legLength / 2, baseZ, yaw
        )

        // Right leg (single piece)
        tempShapeHalfExtents.set(legRadius, legLength / 2, legRadius)
        createPedestrianBodyPart(
            ragdoll.rightLeg, btBoxShape(tempShapeHalfExtents), legMass,
            baseX + hipOffset, baseY - legLength / 2, baseZ, yaw
        )

        // Create simplified constraints (only 5 instead of 8)
        createSimplifiedPedestrianConstraints(ragdoll, torsoHeight / 2, torsoWidth, armLength, legLength, hipOffset, scale)

        // Apply initial velocities from impact
        applySimplifiedPedestrianImpactVelocities(ragdoll, playerVelocity, playerDirection)

        pedestrianRagdolls.add(ragdoll)

        return pedestrianRagdolls.size - 1
    }

    /**
     * Add a dynamic trash can that can be knocked over.
     * @param position Position of the trash can
     * @param playerVelocity Player's speed at collision
     * @param playerDirection Direction player was moving (normalized)
     * @param entityIndex Index to track which entity this belongs to
     * @return Index of the created dynamic object
     */
    fun addTrashCanRagdoll(
        position: Vector3,
        playerVelocity: Float,
        playerDirection: Vector3,
        entityIndex: Int
    ): Int {
        val obj = DynamicObjectRagdoll(entityIndex = entityIndex)

        // Trash can dimensions (scaled 1.6x as per WorldGenerator)
        val scale = 1.6f
        val radius = 0.25f * scale
        val height = 1f * scale
        val halfHeight = height / 2

        // Use cylinder shape for trash can
        tempShapeHalfExtents.set(radius, halfHeight, radius)
        obj.shape = btCylinderShape(tempShapeHalfExtents)

        val mass = 12f  // Light enough to tip over easily
        tempInertia.setZero()
        obj.shape!!.calculateLocalInertia(mass, tempInertia)

        // Start upright at ground level
        tempMatrix.idt()
        tempMatrix.translate(position.x, halfHeight, position.z)

        obj.motionState = btDefaultMotionState(tempMatrix)
        val info = btRigidBody.btRigidBodyConstructionInfo(mass, obj.motionState, obj.shape, tempInertia)
        obj.body = btRigidBody(info)
        obj.body!!.friction = 0.5f
        obj.body!!.restitution = 0.2f
        obj.body!!.setDamping(0.05f, 0.1f)  // Less damping for more tumbling

        // Disable deactivation so it keeps moving
        obj.body!!.activationState = 4  // DISABLE_DEACTIVATION

        // Enable CCD to prevent tunneling
        obj.body!!.setCcdMotionThreshold(0.1f)
        obj.body!!.setCcdSweptSphereRadius(radius * 0.8f)

        dynamicsWorld.addRigidBody(obj.body)
        info.dispose()

        // Apply impulse at the TOP of the trash can to create tipping torque
        // This is the key to making it tip over instead of just sliding
        val impactSpeed = playerVelocity.coerceIn(5f, 15f)
        val impulseStrength = impactSpeed * mass * 0.3f

        // Point of impact - at the top of the trash can, offset in player direction
        val impactPoint = Vector3(
            position.x + playerDirection.x * radius * 0.5f,
            halfHeight + halfHeight * 0.7f,  // Hit near top
            position.z + playerDirection.z * radius * 0.5f
        )

        // Impulse direction - mostly forward, slight upward
        val impulse = Vector3(
            playerDirection.x * impulseStrength,
            impulseStrength * 0.2f,
            playerDirection.z * impulseStrength
        )

        // Apply impulse at offset point - this creates both linear and angular motion
        obj.body!!.applyImpulse(impulse, impactPoint.sub(position.x, halfHeight, position.z))

        dynamicObjects.add(obj)
        return dynamicObjects.size - 1
    }

    /**
     * Get transform for a dynamic object (trash can, etc.).
     * @param index Index returned by addTrashCanRagdoll
     * @return Transform matrix or null if invalid
     */
    fun getDynamicObjectTransform(index: Int): Matrix4? {
        if (index < 0 || index >= dynamicObjects.size) return null
        val obj = dynamicObjects[index]
        if (obj.body == null) return null
        obj.motionState?.getWorldTransform(tempMatrix2)
        return tempMatrix2
    }

    private fun createPedestrianBodyPart(
        part: BodyPart,
        shape: btCollisionShape,
        mass: Float,
        x: Float, y: Float, z: Float,
        yaw: Float
    ) {
        part.shape = shape

        tempInertia.setZero()
        shape.calculateLocalInertia(mass, tempInertia)

        tempMatrix.idt()
        tempMatrix.translate(x, y, z)
        tempMatrix.rotate(Vector3.Y, yaw)

        part.motionState = btDefaultMotionState(tempMatrix)
        val info = btRigidBody.btRigidBodyConstructionInfo(mass, part.motionState, shape, tempInertia)
        part.body = btRigidBody(info)
        part.body!!.friction = 0.6f
        part.body!!.restitution = 0.1f
        part.body!!.setDamping(0.1f, 0.3f)
        part.body!!.activationState = 4  // DISABLE_DEACTIVATION

        dynamicsWorld.addRigidBody(part.body)
        info.dispose()
    }

    private fun createSimplifiedPedestrianConstraints(
        ragdoll: PedestrianRagdoll,
        torsoHalfHeight: Float,
        torsoWidth: Float,
        armLength: Float,
        legLength: Float,
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

        // Left shoulder (single arm)
        createPedestrianConeTwistConstraint(
            ragdoll, ragdoll.torso.body!!, ragdoll.leftArm.body!!,
            Vector3(-torsoWidth, torsoHalfHeight - 0.05f * scale, 0f),
            Vector3(0f, armLength / 2, 0f),
            90f, 60f, 45f
        )

        // Right shoulder (single arm)
        createPedestrianConeTwistConstraint(
            ragdoll, ragdoll.torso.body!!, ragdoll.rightArm.body!!,
            Vector3(torsoWidth, torsoHalfHeight - 0.05f * scale, 0f),
            Vector3(0f, armLength / 2, 0f),
            90f, 60f, 45f
        )

        // Left hip (single leg)
        createPedestrianConeTwistConstraint(
            ragdoll, ragdoll.torso.body!!, ragdoll.leftLeg.body!!,
            Vector3(-hipOffset, -torsoHalfHeight, 0f),
            Vector3(0f, legLength / 2, 0f),
            60f, 45f, 30f
        )

        // Right hip (single leg)
        createPedestrianConeTwistConstraint(
            ragdoll, ragdoll.torso.body!!, ragdoll.rightLeg.body!!,
            Vector3(hipOffset, -torsoHalfHeight, 0f),
            Vector3(0f, legLength / 2, 0f),
            60f, 45f, 30f
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

    private fun applySimplifiedPedestrianImpactVelocities(
        ragdoll: PedestrianRagdoll,
        playerVelocity: Float,
        playerDirection: Vector3
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

        // Apply to simplified body parts (6 instead of 10)
        applyVelocityToPedestrianPart(ragdoll.torso, baseLinearVelocity, baseAngularVelocity, 1.0f)
        applyVelocityToPedestrianPart(ragdoll.head, baseLinearVelocity, baseAngularVelocity, 1.1f)
        applyVelocityToPedestrianPart(ragdoll.leftArm, baseLinearVelocity, baseAngularVelocity, 0.9f)
        applyVelocityToPedestrianPart(ragdoll.rightArm, baseLinearVelocity, baseAngularVelocity, 0.9f)
        applyVelocityToPedestrianPart(ragdoll.leftLeg, baseLinearVelocity, baseAngularVelocity, 0.75f)
        applyVelocityToPedestrianPart(ragdoll.rightLeg, baseLinearVelocity, baseAngularVelocity, 0.75f)
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
     * Get left arm transform for a pedestrian ragdoll (simplified single arm).
     */
    fun getPedestrianLeftArmTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.leftArm.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get right arm transform for a pedestrian ragdoll (simplified single arm).
     */
    fun getPedestrianRightArmTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.rightArm.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get left leg transform for a pedestrian ragdoll (simplified single leg).
     */
    fun getPedestrianLeftLegTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.leftLeg.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get right leg transform for a pedestrian ragdoll (simplified single leg).
     */
    fun getPedestrianRightLegTransform(index: Int): Matrix4? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val ragdoll = pedestrianRagdolls[index]
        ragdoll.rightLeg.motionState?.getWorldTransform(tempMatrix)
        return tempMatrix
    }

    /**
     * Get shirt color for a pedestrian ragdoll.
     */
    fun getPedestrianShirtColor(index: Int): com.badlogic.gdx.graphics.Color? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        return pedestrianRagdolls[index].shirtColor
    }

    /**
     * Get number of active pedestrian ragdolls.
     */
    fun getPedestrianCount(): Int = pedestrianRagdolls.size

    /**
     * Data class for returning ragdoll body info for collision checking.
     */
    data class RagdollBodyInfo(
        val position: Vector3,
        val velocity: Vector3,
        val isPlayerRagdoll: Boolean
    )

    /**
     * Get positions and velocities of all active ragdoll torsos (main body parts).
     * Used by EucGame to check collisions with standing pedestrians.
     * Only returns bodies with significant velocity (moving fast enough to knock someone down).
     */
    fun getActiveRagdollBodies(minVelocity: Float = 2f): List<RagdollBodyInfo> {
        val result = mutableListOf<RagdollBodyInfo>()
        val pos = Vector3()
        val vel = Vector3()

        // Player ragdoll torso
        if (isActive && torso.body != null) {
            torso.motionState?.getWorldTransform(tempMatrix)
            tempMatrix.getTranslation(pos)
            vel.set(torso.body!!.linearVelocity)
            if (vel.len() >= minVelocity) {
                result.add(RagdollBodyInfo(Vector3(pos), Vector3(vel), true))
            }
        }

        // Pedestrian ragdoll torsos
        for (ragdoll in pedestrianRagdolls) {
            if (ragdoll.torso.body != null) {
                ragdoll.torso.motionState?.getWorldTransform(tempMatrix)
                tempMatrix.getTranslation(pos)
                vel.set(ragdoll.torso.body!!.linearVelocity)
                if (vel.len() >= minVelocity) {
                    result.add(RagdollBodyInfo(Vector3(pos), Vector3(vel), false))
                }
            }
        }

        return result
    }

    /**
     * Apply external impulse to the player ragdoll (e.g., from a car hitting the ragdoll).
     * @param impactPosition Position where the impact occurred
     * @param impactVelocity Velocity/direction of the impacting object (used for impulse direction)
     */
    fun applyExternalImpulse(impactPosition: Vector3, impactVelocity: Vector3) {
        if (!isActive) return

        // Calculate impulse strength based on impact velocity
        val impactSpeed = impactVelocity.len()
        val impulseStrength = impactSpeed * 15f  // Scale factor for visible effect

        // Apply impulse to torso (main body)
        torso.body?.let { body ->
            val impulse = Vector3(impactVelocity).nor().scl(impulseStrength)
            impulse.y += impulseStrength * 0.3f  // Add some upward component
            body.applyCentralImpulse(impulse)

            // Also add some angular impulse for tumbling effect
            val angularImpulse = Vector3(
                impactVelocity.z * 2f,  // Tumble based on impact direction
                0f,
                -impactVelocity.x * 2f
            )
            body.applyTorqueImpulse(angularImpulse)
        }

        // Apply smaller impulse to head
        head.body?.let { body ->
            val impulse = Vector3(impactVelocity).nor().scl(impulseStrength * 0.8f)
            impulse.y += impulseStrength * 0.4f
            body.applyCentralImpulse(impulse)
        }

        // Apply to limbs with decreasing strength
        val limbBodies = listOfNotNull(
            leftUpperArm.body, leftLowerArm.body,
            rightUpperArm.body, rightLowerArm.body,
            leftUpperLeg.body, leftLowerLeg.body,
            rightUpperLeg.body, rightLowerLeg.body
        )
        for (body in limbBodies) {
            val impulse = Vector3(impactVelocity).nor().scl(impulseStrength * 0.5f)
            impulse.y += impulseStrength * 0.2f
            body.applyCentralImpulse(impulse)
        }

        // Trigger ground impact callback (for pigeons) at impact position
        onRagdollGroundImpact?.invoke(impactPosition)
    }

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

            // Remove simplified body parts (6 instead of 10)
            cleanupPart(ragdoll.head)
            cleanupPart(ragdoll.torso)
            cleanupPart(ragdoll.leftArm)
            cleanupPart(ragdoll.rightArm)
            cleanupPart(ragdoll.leftLeg)
            cleanupPart(ragdoll.rightLeg)
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

        // Clear dynamic objects (trash cans, etc.)
        clearDynamicObjects()
    }

    /**
     * Clear all dynamic objects (trash cans, etc.).
     */
    private fun clearDynamicObjects() {
        for (obj in dynamicObjects) {
            obj.body?.let {
                dynamicsWorld.removeRigidBody(it)
                it.dispose()
            }
            obj.motionState?.dispose()
            obj.shape?.dispose()
        }
        dynamicObjects.clear()
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
