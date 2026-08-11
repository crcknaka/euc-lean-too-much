package com.eucleantoomuch.game.physics

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import kotlin.math.PI
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.github.xpenatan.jParser.loader.JParserLibraryLoaderListener
import jolt.Jolt
import jolt.JoltLoader
import jolt.JoltNew
import jolt.core.Factory
import jolt.core.JobSystemThreadPool
import jolt.core.TempAllocatorImpl
import jolt.enums.EActivation
import jolt.enums.EConstraintSpace
import jolt.enums.EMotionQuality
import jolt.enums.EMotionType
import jolt.enums.EOverrideMassProperties
import jolt.enums.ESwingType
import jolt.math.Quat
import jolt.math.Vec3
import jolt.physics.PhysicsSystem
import jolt.physics.body.BodyID
import jolt.physics.body.BodyInterface
import jolt.physics.collision.CollisionGroup
import jolt.physics.collision.GroupFilterTable
import jolt.physics.collision.ObjectLayerPairFilterTable
import jolt.physics.collision.broadphase.BroadPhaseLayer
import jolt.physics.collision.broadphase.BroadPhaseLayerInterfaceTable
import jolt.physics.collision.broadphase.ObjectVsBroadPhaseLayerFilterTable
import jolt.physics.collision.shape.BoxShapeSettings
import jolt.physics.collision.shape.CylinderShapeSettings
import jolt.physics.collision.shape.ShapeSettings
import jolt.physics.collision.shape.SphereShapeSettings
import jolt.physics.constraints.HingeConstraintSettings
import jolt.physics.constraints.SwingTwistConstraintSettings
import jolt.physics.constraints.TwoBodyConstraint

/**
 * Jolt-backed port of [RagdollPhysics].
 *
 * Exposes exactly the same public API as the Bullet implementation so it can be swapped in
 * at the call sites without touching them. Body proportions, masses, damping, joint limits
 * and impulse magnitudes are copied verbatim from the Bullet version.
 *
 * IMPORTANT - asynchronous native loading:
 * `JoltLoader.init(...)` is asynchronous (required for the future web/wasm build), so the
 * constructor only *kicks off* loading and returns immediately. Until the natives are up and
 * the physics world has been built, every public method is a safe no-op / returns null /
 * returns an empty list, and [isReady] reports false. No Jolt API is ever touched before the
 * loader callback has fired, and nothing ever blocks the calling thread.
 *
 * The world itself is built lazily on the *calling* thread the first time a public method runs
 * after the natives finished loading, which guarantees every Jolt call stays on one thread
 * (the render thread) even though the loader callback may fire on another.
 */
class JoltRagdollPhysics : RagdollEngine {

    // ------------------------------------------------------------------ native bootstrap

    /**
     * Process-wide, non-blocking Jolt bootstrap. `JoltLoader.init` is fired once; `Jolt.Init()`
     * / `Factory::sInstance` / `Jolt.RegisterTypes()` happen later on the game thread.
     */
    private object Natives {
        @Volatile
        var loaded: Boolean = false
            private set

        @Volatile
        var failed: Boolean = false
            private set

        private var loadStarted = false
        private var typesRegistered = false

        /** Non-blocking. Safe to call many times. */
        @Synchronized
        fun requestLoad() {
            if (loadStarted) return
            loadStarted = true
            try {
                JoltLoader.init(object : JParserLibraryLoaderListener {
                    override fun onLoad(isSuccess: Boolean, t: Throwable?) {
                        if (isSuccess) {
                            loaded = true
                        } else {
                            failed = true
                            Gdx.app?.error("JoltRagdollPhysics", "Jolt native load failed", t)
                        }
                    }
                })
            } catch (t: Throwable) {
                failed = true
                Gdx.app?.error("JoltRagdollPhysics", "JoltLoader.init threw", t)
            }
        }

        /** Must run on the thread that will own the physics world. */
        @Synchronized
        fun registerTypes(): Boolean {
            if (typesRegistered) return true
            if (failed || !loaded) return false
            return try {
                Jolt.Init()
                // Mandatory: without a Factory instance RegisterTypes() hard-aborts the JVM.
                Factory.set_sInstance(JoltNew.Factory())
                Jolt.RegisterTypes()
                typesRegistered = true
                true
            } catch (t: Throwable) {
                failed = true
                Gdx.app?.error("JoltRagdollPhysics", "Jolt.RegisterTypes failed", t)
                false
            }
        }
    }

    companion object {
        /** Kicks off the asynchronous native load. Never blocks. */
        fun ensureInitialized() = Natives.requestLoad()

        // Object layers
        private const val LAYER_STATIC = 0
        private const val LAYER_DYNAMIC = 1
        private const val NUM_OBJECT_LAYERS = 2
        private const val NUM_BROAD_PHASE_LAYERS = 2

        private const val MAX_BODIES = 4096
        private const val MAX_BODY_PAIRS = 8192
        private const val MAX_CONTACTS = 8192

        // Ground slab (replaces Bullet's infinite btStaticPlaneShape). Top face sits at y = 0.
        private const val GROUND_HALF_EXTENT = 600f
        private const val GROUND_THICKNESS = 25f
        private const val GROUND_RECENTER_DISTANCE = 250f

        // Player ragdoll collision sub-groups (mirrors Bullet's "disable collision between
        // constrained pairs" behaviour of addConstraint(c, true)).
        private const val SG_TORSO = 0
        private const val SG_HEAD = 1
        private const val SG_L_UPPER_ARM = 2
        private const val SG_L_LOWER_ARM = 3
        private const val SG_R_UPPER_ARM = 4
        private const val SG_R_LOWER_ARM = 5
        private const val SG_L_UPPER_LEG = 6
        private const val SG_L_LOWER_LEG = 7
        private const val SG_R_UPPER_LEG = 8
        private const val SG_R_LOWER_LEG = 9
        private const val SG_EUC = 10
        private const val PLAYER_SUBGROUPS = 11

        // Pedestrian ragdoll collision sub-groups
        private const val PSG_TORSO = 0
        private const val PSG_HEAD = 1
        private const val PSG_LEFT_ARM = 2
        private const val PSG_RIGHT_ARM = 3
        private const val PSG_LEFT_LEG = 4
        private const val PSG_RIGHT_LEG = 5
        private const val PEDESTRIAN_SUBGROUPS = 6

        private const val PLAYER_GROUP_ID = 0

        // Contact heuristics used to replace Bullet's manifold polling.
        private const val CONTACT_SLOP = 0.08f
        private const val GROUND_CONTACT_SLOP = 0.10f
        /** Bullet used appliedImpulse > 2f; for a ~30 kg torso that is a very small delta-v. */
        private const val PRIMARY_IMPACT_SPEED = 1.5f
        /** Bullet used appliedImpulse > 8f for secondary (pedestrian) impacts. */
        private const val SECONDARY_IMPACT_SPEED = 3.5f
    }

    // ------------------------------------------------------------------ world state

    private var worldReady = false
    private var worldFailed = false

    private lateinit var tempAllocator: TempAllocatorImpl
    private lateinit var jobSystem: JobSystemThreadPool
    private lateinit var bpLayerInterface: BroadPhaseLayerInterfaceTable
    private lateinit var objectLayerPairFilter: ObjectLayerPairFilterTable
    private lateinit var objectVsBpFilter: ObjectVsBroadPhaseLayerFilterTable
    private lateinit var physicsSystem: PhysicsSystem
    private lateinit var bodyInterface: BodyInterface

    private lateinit var playerGroupFilter: GroupFilterTable
    private lateinit var pedestrianGroupFilter: GroupFilterTable
    private lateinit var scratchGroup: CollisionGroup

    // Reused constraint settings (Jolt copies the values out on Create(), so one of each is enough).
    private lateinit var swingTwistSettings: SwingTwistConstraintSettings
    private lateinit var hingeSettings: HingeConstraintSettings

    // Pre-allocated native scratch - Vec3()/Quat() allocate native memory, never do that per frame.
    private lateinit var vA: Vec3
    private lateinit var vB: Vec3
    private lateinit var vC: Vec3
    private lateinit var vD: Vec3
    private lateinit var outPos: Vec3
    private lateinit var outRot: Quat
    private lateinit var outLin: Vec3
    private lateinit var outAng: Vec3
    private lateinit var qA: Quat

    private var groundId: BodyID? = null
    private var groundCenterX = 0f
    private var groundCenterZ = 0f
    private var broadPhaseDirty = false

    // ------------------------------------------------------------------ body bookkeeping

    /** One simulated rigid body plus the data the (manifold-free) contact heuristic needs. */
    private class Part {
        var id: BodyID? = null

        /** Approximate contact radius (largest half-extent) used for proximity checks. */
        var radius: Float = 0.25f

        /** World position at creation time - needed to place world-space constraints. */
        var spawnX: Float = 0f
        var spawnY: Float = 0f
        var spawnZ: Float = 0f
    }

    private class WorldCollider(
        val id: BodyID,
        val type: RagdollTypes.ColliderType,
        val cx: Float, val cy: Float, val cz: Float,
        val hx: Float, val hy: Float, val hz: Float,
        val cosYaw: Float, val sinYaw: Float
    )

    private class PedRagdoll(val entityIndex: Int, val shirtColor: Color) {
        val head = Part()
        val torso = Part()
        val leftArm = Part()
        val rightArm = Part()
        val leftLeg = Part()
        val rightLeg = Part()
        val constraints = mutableListOf<TwoBodyConstraint>()
    }

    private class DynObject(val entityIndex: Int) {
        var id: BodyID? = null
    }

    // Player ragdoll parts
    private val euc = Part()
    private val head = Part()
    private val torso = Part()
    private val leftUpperArm = Part()
    private val leftLowerArm = Part()
    private val rightUpperArm = Part()
    private val rightLowerArm = Part()
    private val leftUpperLeg = Part()
    private val leftLowerLeg = Part()
    private val rightUpperLeg = Part()
    private val rightLowerLeg = Part()

    private val playerParts = arrayOf(
        euc, torso, head,
        leftUpperArm, leftLowerArm, rightUpperArm, rightLowerArm,
        leftUpperLeg, leftLowerLeg, rightUpperLeg, rightLowerLeg
    )

    private val constraints = mutableListOf<TwoBodyConstraint>()
    private val worldColliders = mutableListOf<WorldCollider>()
    private val pedestrianRagdolls = mutableListOf<PedRagdoll>()
    private val dynamicObjects = mutableListOf<DynObject>()

    private var nextGroupId = 1

    // ------------------------------------------------------------------ public callbacks

    /** Called when the *player* ragdoll hits something during flight. */
    override var onRagdollCollision: ((RagdollTypes.ColliderType) -> Unit)? = null

    /** Called (quieter) when a pedestrian ragdoll hits a world object - "chain reaction". */
    override var onSecondaryRagdollCollision: ((RagdollTypes.ColliderType) -> Unit)? = null

    /** Called when a ragdoll body hits a standing pedestrian. */
    override var onRagdollHitPedestrian: ((pedestrianPosition: Vector3, impactVelocity: Vector3) -> Unit)? = null

    /** Called when the player ragdoll hits the ground (startles pigeons etc). */
    override var onRagdollGroundImpact: ((impactPosition: Vector3) -> Unit)? = null

    // Collision-sound throttling (same fields / semantics as the Bullet version)
    private val triggeredColliders = mutableSetOf<WorldCollider>()
    private val triggeredSecondaryColliders = mutableSetOf<WorldCollider>()
    private var lastCollisionTime = 0f
    private var lastSecondaryCollisionTime = 0f
    private val minCollisionInterval = 0.15f
    private val minSecondaryCollisionInterval = 0.2f

    private var hasTriggeredGroundImpact = false
    private var lastGroundImpactTime = 0f
    private val minGroundImpactInterval = 0.5f

    // ------------------------------------------------------------------ misc state

    private var isActive = false
    private var isFrozen = false

    private val tempMatrix = Matrix4()
    private val tempMatrix2 = Matrix4()
    private val retireCheckPos = Vector3()
    private val impactPos = Vector3()

    init {
        // Non-blocking: kicks off the async native load and returns straight away.
        Natives.requestLoad()
    }

    /** True once the natives are loaded and the physics world has been built. */
    override fun isReady(): Boolean = ensureWorld()

    /**
     * Builds the world lazily, on the calling thread, once the natives have arrived.
     * Returns false (and stays a no-op) while loading, or forever if loading failed.
     */
    private fun ensureWorld(): Boolean {
        if (worldReady) return true
        if (worldFailed) return false
        if (Natives.failed) {
            worldFailed = true
            return false
        }
        if (!Natives.loaded) return false
        if (!Natives.registerTypes()) {
            worldFailed = true
            return false
        }
        return try {
            createPhysicsWorld()
            worldReady = true
            Gdx.app?.log("JoltRagdollPhysics", "Jolt physics initialized")
            true
        } catch (t: Throwable) {
            worldFailed = true
            Gdx.app?.error("JoltRagdollPhysics", "Failed to build Jolt world", t)
            false
        }
    }

    private fun createPhysicsWorld() {
        vA = Vec3(); vB = Vec3(); vC = Vec3(); vD = Vec3()
        outPos = Vec3(); outLin = Vec3(); outAng = Vec3()
        outRot = Quat(); qA = Quat()

        tempAllocator = JoltNew.TempAllocatorImpl(16 * 1024 * 1024)
        jobSystem = JoltNew.JobSystemThreadPool(
            2048, 8, maxOf(1, Runtime.getRuntime().availableProcessors() - 1)
        )

        bpLayerInterface = BroadPhaseLayerInterfaceTable(NUM_OBJECT_LAYERS, NUM_BROAD_PHASE_LAYERS)
        bpLayerInterface.MapObjectToBroadPhaseLayer(LAYER_STATIC, BroadPhaseLayer(0))
        bpLayerInterface.MapObjectToBroadPhaseLayer(LAYER_DYNAMIC, BroadPhaseLayer(1))

        objectLayerPairFilter = ObjectLayerPairFilterTable(NUM_OBJECT_LAYERS)
        objectLayerPairFilter.DisableCollision(LAYER_STATIC, LAYER_STATIC)
        objectLayerPairFilter.EnableCollision(LAYER_STATIC, LAYER_DYNAMIC)
        objectLayerPairFilter.EnableCollision(LAYER_DYNAMIC, LAYER_DYNAMIC)

        objectVsBpFilter = ObjectVsBroadPhaseLayerFilterTable(
            bpLayerInterface, NUM_BROAD_PHASE_LAYERS, objectLayerPairFilter, NUM_OBJECT_LAYERS
        )

        physicsSystem = PhysicsSystem()
        physicsSystem.Init(
            MAX_BODIES, 0, MAX_BODY_PAIRS, MAX_CONTACTS,
            bpLayerInterface, objectVsBpFilter, objectLayerPairFilter
        )
        vA.Set(0f, -15f, 0f)  // same stronger-than-real gravity as the Bullet version
        physicsSystem.SetGravity(vA)
        bodyInterface = physicsSystem.GetBodyInterface()

        // Collision groups: Bullet's addConstraint(c, true) disables collisions between the two
        // constrained bodies only. Reproduce that with a GroupFilterTable per ragdoll layout.
        playerGroupFilter = GroupFilterTable(PLAYER_SUBGROUPS)
        playerGroupFilter.DisableCollision(SG_TORSO, SG_HEAD)
        playerGroupFilter.DisableCollision(SG_TORSO, SG_L_UPPER_ARM)
        playerGroupFilter.DisableCollision(SG_L_UPPER_ARM, SG_L_LOWER_ARM)
        playerGroupFilter.DisableCollision(SG_TORSO, SG_R_UPPER_ARM)
        playerGroupFilter.DisableCollision(SG_R_UPPER_ARM, SG_R_LOWER_ARM)
        playerGroupFilter.DisableCollision(SG_TORSO, SG_L_UPPER_LEG)
        playerGroupFilter.DisableCollision(SG_L_UPPER_LEG, SG_L_LOWER_LEG)
        playerGroupFilter.DisableCollision(SG_TORSO, SG_R_UPPER_LEG)
        playerGroupFilter.DisableCollision(SG_R_UPPER_LEG, SG_R_LOWER_LEG)

        pedestrianGroupFilter = GroupFilterTable(PEDESTRIAN_SUBGROUPS)
        pedestrianGroupFilter.DisableCollision(PSG_TORSO, PSG_HEAD)
        pedestrianGroupFilter.DisableCollision(PSG_TORSO, PSG_LEFT_ARM)
        pedestrianGroupFilter.DisableCollision(PSG_TORSO, PSG_RIGHT_ARM)
        pedestrianGroupFilter.DisableCollision(PSG_TORSO, PSG_LEFT_LEG)
        pedestrianGroupFilter.DisableCollision(PSG_TORSO, PSG_RIGHT_LEG)

        // CRITICAL: GroupFilter is a Jolt RefTarget. A freshly constructed one has refCount 0
        // and the ONLY references it ever gets are the ones CollisionGroup takes. So the moment
        // the last body that uses a table is destroyed - or the shared CollisionGroup below is
        // re-pointed at the other table - Release() drops the count back to 0 and Jolt *deletes*
        // the table while these fields still point at it. Every later SetGroupFilter() /
        // set_mCollisionGroup() then AddRef()s freed memory, corrupting Jolt's heap until
        // CreateAndAddBody() trips an internal assert (SIGTRAP, no exception).
        // Take one owning reference each; released again in dispose().
        playerGroupFilter.AddRef()
        pedestrianGroupFilter.AddRef()

        scratchGroup = CollisionGroup()

        swingTwistSettings = SwingTwistConstraintSettings()
        hingeSettings = HingeConstraintSettings()

        createGround()
    }

    private fun createGround() {
        val shape = BoxShapeSettings(
            vecA(GROUND_HALF_EXTENT, GROUND_THICKNESS, GROUND_HALF_EXTENT)
        )
        val settings = JoltNew.BodyCreationSettings(
            shape, vecB(0f, -GROUND_THICKNESS, 0f), identityQuat(),
            EMotionType.Static, LAYER_STATIC
        )
        settings.set_mFriction(0.9f)    // asphalt
        settings.set_mRestitution(0.1f) // slight bounce
        groundId = ownId(bodyInterface.CreateAndAddBody(settings, EActivation.DontActivate))
        settings.dispose()
        groundCenterX = 0f
        groundCenterZ = 0f
    }

    // ------------------------------------------------------------------ small native helpers

    /** CreateAndAddBody hands back a reused wrapper - copy it before the next call. */
    private fun ownId(shared: BodyID): BodyID = BodyID(shared.GetIndexAndSequenceNumber())

    /** CreateConstraint hands back a reused wrapper - copy it before the next call. */
    private fun ownConstraint(shared: TwoBodyConstraint): TwoBodyConstraint =
        TwoBodyConstraint.native_new().also { it.native_copy(shared) }

    private fun vecA(x: Float, y: Float, z: Float): Vec3 = vA.also { it.Set(x, y, z) }
    private fun vecB(x: Float, y: Float, z: Float): Vec3 = vB.also { it.Set(x, y, z) }
    private fun vecC(x: Float, y: Float, z: Float): Vec3 = vC.also { it.Set(x, y, z) }
    private fun vecD(x: Float, y: Float, z: Float): Vec3 = vD.also { it.Set(x, y, z) }

    private fun identityQuat(): Quat = qA.also { it.Set(0f, 0f, 0f, 1f) }

    /** Rotation about +Y by [yawDegrees] - matches libGDX `Matrix4.rotate(Vector3.Y, yaw)`. */
    /**
     * Build a yaw rotation quaternion.
     *
     * MUST use exact trig, not MathUtils.sin/cos: those are table-based approximations with
     * ~1e-4 error, so sin^2 + cos^2 drifts off 1.0 and Jolt rejects the quaternion with
     * `Jolt/Math/Quat.inl:278: (IsNormalized())`, which aborts the process (SIGTRAP) inside
     * CreateAndAddBody. Renormalising afterwards keeps us safely inside Jolt's ~1e-6 tolerance.
     */
    private fun yawQuat(yawDegrees: Float): Quat {
        val half = (yawDegrees.toDouble() * PI / 180.0) * 0.5
        val s = kotlin.math.sin(half)
        val c = kotlin.math.cos(half)
        val len = kotlin.math.sqrt(s * s + c * c)
        return qA.also { it.Set(0f, (s / len).toFloat(), 0f, (c / len).toFloat()) }
    }

    private fun rad(degrees: Float): Float = degrees * MathUtils.degreesToRadians

    private fun fillMatrix(id: BodyID, out: Matrix4): Matrix4 {
        bodyInterface.GetPositionAndRotation(id, outPos, outRot)
        out.set(
            outPos.GetX(), outPos.GetY(), outPos.GetZ(),
            outRot.GetX(), outRot.GetY(), outRot.GetZ(), outRot.GetW()
        )
        return out
    }

    private fun destroyBody(id: BodyID?) {
        if (id == null) return
        if (bodyInterface.IsAdded(id)) bodyInterface.RemoveBody(id)
        bodyInterface.DestroyBody(id)
        id.dispose()
    }

    private fun destroyPart(part: Part) {
        destroyBody(part.id)
        part.id = null
    }

    /** Convex radius must stay strictly below the smallest half-extent. */
    private fun convexRadiusFor(minHalfExtent: Float): Float =
        minOf(0.05f, minHalfExtent * 0.4f).coerceAtLeast(0.001f)

    private fun boxShape(hx: Float, hy: Float, hz: Float): ShapeSettings =
        BoxShapeSettings(vecA(hx, hy, hz), convexRadiusFor(minOf(hx, hy, hz)))

    private fun cylinderShape(radius: Float, halfHeight: Float): ShapeSettings =
        CylinderShapeSettings(halfHeight, radius, convexRadiusFor(minOf(radius, halfHeight)))

    /**
     * Creates a dynamic body. [shape] is consumed by the BodyCreationSettings, which frees it
     * on dispose() - never dispose the ShapeSettings yourself (double free = JVM abort).
     */
    private fun createDynamicBody(
        shape: ShapeSettings,
        mass: Float,
        x: Float, y: Float, z: Float,
        yaw: Float,
        friction: Float, restitution: Float,
        linearDamping: Float, angularDamping: Float,
        groupFilter: GroupFilterTable?, groupId: Int, subGroupId: Int,
        continuous: Boolean = false
    ): BodyID {
        val settings = JoltNew.BodyCreationSettings(
            shape, vecB(x, y, z), yawQuat(yaw), EMotionType.Dynamic, LAYER_DYNAMIC
        )
        settings.set_mFriction(friction)
        settings.set_mRestitution(restitution)
        settings.set_mLinearDamping(linearDamping)
        settings.set_mAngularDamping(angularDamping)
        // Bullet used activationState = DISABLE_DEACTIVATION for every ragdoll body.
        settings.set_mAllowSleeping(false)
        // Bullet drove the mass explicitly and derived inertia from the shape - same here.
        settings.set_mOverrideMassProperties(EOverrideMassProperties.CalculateInertia)
        settings.get_mMassPropertiesOverride().set_mMass(mass)
        if (continuous) settings.set_mMotionQuality(EMotionQuality.LinearCast)
        if (groupFilter != null) {
            scratchGroup.SetGroupFilter(groupFilter)
            scratchGroup.SetGroupID(groupId)
            scratchGroup.SetSubGroupID(subGroupId)
            settings.set_mCollisionGroup(scratchGroup)
        }
        val id = ownId(bodyInterface.CreateAndAddBody(settings, EActivation.Activate))
        settings.dispose()
        return id
    }

    private fun setVelocity(part: Part, lx: Float, ly: Float, lz: Float, ax: Float, ay: Float, az: Float) {
        val id = part.id ?: return
        bodyInterface.SetLinearAndAngularVelocity(id, vecA(lx, ly, lz), vecB(ax, ay, az))
    }

    // ------------------------------------------------------------------ constraints

    /**
     * Bullet used btConeTwistConstraint with identity-rotation frames, so its twist axis was the
     * body-local X axis, swingSpan1 was the swing about Z and swingSpan2 the swing about Y.
     * Jolt's SwingTwist maps that to: twist = X, plane = Y, normal = X x Y = Z, therefore
     * mPlaneHalfConeAngle == swingSpan1 and mNormalHalfConeAngle == swingSpan2.
     */
    private fun addSwingTwist(
        list: MutableList<TwoBodyConstraint>,
        partA: Part, partB: Part,
        pax: Float, pay: Float, paz: Float,
        pbx: Float, pby: Float, pbz: Float,
        cosYaw: Float, sinYaw: Float,
        swingSpan1: Float, swingSpan2: Float, twistSpan: Float
    ) {
        val idA = partA.id ?: return
        val idB = partB.id ?: return

        val ax = partA.spawnX + pax * cosYaw + paz * sinYaw
        val ay = partA.spawnY + pay
        val az = partA.spawnZ - pax * sinYaw + paz * cosYaw
        val bx = partB.spawnX + pbx * cosYaw + pbz * sinYaw
        val by = partB.spawnY + pby
        val bz = partB.spawnZ - pbx * sinYaw + pbz * cosYaw

        val st = swingTwistSettings
        st.set_mSpace(EConstraintSpace.WorldSpace)
        st.set_mPosition1(vecA(ax, ay, az))
        st.set_mPosition2(vecB(bx, by, bz))
        // body-local X and Y at spawn time, expressed in world space
        st.set_mTwistAxis1(vecC(cosYaw, 0f, -sinYaw))
        st.set_mTwistAxis2(vecC(cosYaw, 0f, -sinYaw))
        st.set_mPlaneAxis1(vecD(0f, 1f, 0f))
        st.set_mPlaneAxis2(vecD(0f, 1f, 0f))
        st.set_mSwingType(ESwingType.Cone)
        st.set_mNormalHalfConeAngle(rad(swingSpan2))
        st.set_mPlaneHalfConeAngle(rad(swingSpan1))
        st.set_mTwistMinAngle(-rad(twistSpan))
        st.set_mTwistMaxAngle(rad(twistSpan))

        val c = ownConstraint(bodyInterface.CreateConstraint(st, idA, idB))
        physicsSystem.AddConstraint(c)
        list.add(c)
    }

    /** Bullet's elbow/knee hinges used the body-local X axis with a [low, high] degree range. */
    private fun addHinge(
        list: MutableList<TwoBodyConstraint>,
        partA: Part, partB: Part,
        pax: Float, pay: Float, paz: Float,
        pbx: Float, pby: Float, pbz: Float,
        cosYaw: Float, sinYaw: Float,
        lowLimit: Float, highLimit: Float
    ) {
        val idA = partA.id ?: return
        val idB = partB.id ?: return

        val ax = partA.spawnX + pax * cosYaw + paz * sinYaw
        val ay = partA.spawnY + pay
        val az = partA.spawnZ - pax * sinYaw + paz * cosYaw
        val bx = partB.spawnX + pbx * cosYaw + pbz * sinYaw
        val by = partB.spawnY + pby
        val bz = partB.spawnZ - pbx * sinYaw + pbz * cosYaw

        val hi = hingeSettings
        hi.set_mSpace(EConstraintSpace.WorldSpace)
        hi.set_mPoint1(vecA(ax, ay, az))
        hi.set_mPoint2(vecB(bx, by, bz))
        hi.set_mHingeAxis1(vecC(cosYaw, 0f, -sinYaw))
        hi.set_mHingeAxis2(vecC(cosYaw, 0f, -sinYaw))
        hi.set_mNormalAxis1(vecD(0f, 1f, 0f))
        hi.set_mNormalAxis2(vecD(0f, 1f, 0f))
        hi.set_mLimitsMin(rad(lowLimit))
        hi.set_mLimitsMax(rad(highLimit))

        val c = ownConstraint(bodyInterface.CreateConstraint(hi, idA, idB))
        physicsSystem.AddConstraint(c)
        list.add(c)
    }

    private fun removeConstraints(list: MutableList<TwoBodyConstraint>) {
        for (c in list) physicsSystem.RemoveConstraint(c)
        list.clear()
    }

    // ------------------------------------------------------------------ start / stop

    /**
     * Start ragdoll simulation for a fall.
     * @param eucPosition Initial position of EUC
     * @param eucYaw Initial yaw rotation in degrees
     * @param playerVelocity Initial velocity of player (m/s forward)
     * @param sideLean Side lean at fall moment (-1 to 1)
     * @param forwardLean Forward lean at fall moment (-1 to 1)
     */
    override fun startFall(
        eucPosition: Vector3,
        eucYaw: Float,
        playerVelocity: Float,
        sideLean: Float,
        forwardLean: Float
    ) {
        if (!ensureWorld()) return

        cleanup()

        val yawRad = eucYaw * MathUtils.degreesToRadians

        createEucWheel(eucPosition, eucYaw, playerVelocity, sideLean, yawRad)
        createRagdoll(eucPosition, eucYaw, playerVelocity, sideLean, forwardLean, yawRad)

        recenterGround(eucPosition.x, eucPosition.z, force = true)

        isActive = true
        isFrozen = false
    }

    private fun createEucWheel(
        eucPosition: Vector3,
        eucYaw: Float,
        playerVelocity: Float,
        sideLean: Float,
        yawRad: Float
    ) {
        val wheelWidth = 0.15f
        val wheelHeight = 0.5f
        val wheelDepth = 0.35f
        val eucMass = 20f

        val hx = wheelWidth / 2f
        val hy = wheelHeight / 2f
        val hz = wheelDepth / 2f

        val y = wheelHeight / 2f + 0.05f
        euc.id = createDynamicBody(
            boxShape(hx, hy, hz), eucMass,
            eucPosition.x, y, eucPosition.z, eucYaw,
            0.8f, 0.1f, 0.3f, 0.4f,
            playerGroupFilter, PLAYER_GROUP_ID, SG_EUC
        )
        euc.radius = maxOf(hx, hy, hz)
        euc.spawnX = eucPosition.x; euc.spawnY = y; euc.spawnZ = eucPosition.z

        val fallDirection = if (kotlin.math.abs(sideLean) > 0.1f) {
            if (sideLean > 0) 1f else -1f
        } else {
            if (MathUtils.random() > 0.5f) 1f else -1f
        }

        val forwardX = kotlin.math.sin(yawRad) * playerVelocity * 0.5f
        val forwardZ = kotlin.math.cos(yawRad) * playerVelocity * 0.5f
        val sideX = kotlin.math.cos(yawRad) * fallDirection * 1.5f
        val sideZ = -kotlin.math.sin(yawRad) * fallDirection * 1.5f

        setVelocity(
            euc,
            forwardX + sideX, 0.5f, forwardZ + sideZ,
            fallDirection * 3f, 0f, 0f
        )
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

        val baseX = eucPosition.x
        val baseY = eucPosition.y + 0.7f * riderScale
        val baseZ = eucPosition.z

        val torsoWidth = 0.175f * riderScale
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

        val torsoMass = 30f
        val headMass = 5f
        val upperArmMass = 2.5f
        val lowerArmMass = 1.5f
        val upperLegMass = 8f
        val lowerLegMass = 4f

        val torsoDepth = 0.11f * riderScale

        createBoxPart(
            torso, torsoWidth, torsoHeight / 2, torsoDepth, torsoMass,
            baseX, baseY + torsoHeight / 2, baseZ, eucYaw, PLAYER_GROUP_ID, SG_TORSO, playerGroupFilter
        )
        createSpherePart(
            head, headRadius, headMass,
            baseX, baseY + torsoHeight + headRadius * 0.8f, baseZ, eucYaw,
            PLAYER_GROUP_ID, SG_HEAD, playerGroupFilter
        )

        val shoulderY = baseY + torsoHeight - 0.05f * riderScale
        val shoulderOffset = torsoWidth + 0.02f * riderScale

        createBoxPart(
            leftUpperArm, upperArmRadius, upperArmLength / 2, upperArmRadius, upperArmMass,
            baseX - shoulderOffset, shoulderY - upperArmLength / 2, baseZ, eucYaw,
            PLAYER_GROUP_ID, SG_L_UPPER_ARM, playerGroupFilter
        )
        createBoxPart(
            leftLowerArm, lowerArmRadius, lowerArmLength / 2, lowerArmRadius, lowerArmMass,
            baseX - shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, baseZ, eucYaw,
            PLAYER_GROUP_ID, SG_L_LOWER_ARM, playerGroupFilter
        )
        createBoxPart(
            rightUpperArm, upperArmRadius, upperArmLength / 2, upperArmRadius, upperArmMass,
            baseX + shoulderOffset, shoulderY - upperArmLength / 2, baseZ, eucYaw,
            PLAYER_GROUP_ID, SG_R_UPPER_ARM, playerGroupFilter
        )
        createBoxPart(
            rightLowerArm, lowerArmRadius, lowerArmLength / 2, lowerArmRadius, lowerArmMass,
            baseX + shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, baseZ, eucYaw,
            PLAYER_GROUP_ID, SG_R_LOWER_ARM, playerGroupFilter
        )

        val hipOffset = 0.1f * riderScale

        createBoxPart(
            leftUpperLeg, upperLegRadius, upperLegLength / 2, upperLegRadius, upperLegMass,
            baseX - hipOffset, baseY - upperLegLength / 2, baseZ, eucYaw,
            PLAYER_GROUP_ID, SG_L_UPPER_LEG, playerGroupFilter
        )
        createBoxPart(
            leftLowerLeg, lowerLegRadius, lowerLegLength / 2, lowerLegRadius, lowerLegMass,
            baseX - hipOffset, baseY - upperLegLength - lowerLegLength / 2, baseZ, eucYaw,
            PLAYER_GROUP_ID, SG_L_LOWER_LEG, playerGroupFilter
        )
        createBoxPart(
            rightUpperLeg, upperLegRadius, upperLegLength / 2, upperLegRadius, upperLegMass,
            baseX + hipOffset, baseY - upperLegLength / 2, baseZ, eucYaw,
            PLAYER_GROUP_ID, SG_R_UPPER_LEG, playerGroupFilter
        )
        createBoxPart(
            rightLowerLeg, lowerLegRadius, lowerLegLength / 2, lowerLegRadius, lowerLegMass,
            baseX + hipOffset, baseY - upperLegLength - lowerLegLength / 2, baseZ, eucYaw,
            PLAYER_GROUP_ID, SG_R_LOWER_LEG, playerGroupFilter
        )

        createConstraints(
            eucYaw, torsoHeight, upperArmLength, lowerArmLength,
            upperLegLength, lowerLegLength, hipOffset, riderScale
        )

        applyInitialVelocities(playerVelocity, sideLean, forwardLean, yawRad)
    }

    private fun createBoxPart(
        part: Part,
        hx: Float, hy: Float, hz: Float,
        mass: Float,
        x: Float, y: Float, z: Float,
        yaw: Float,
        groupId: Int, subGroupId: Int,
        filter: GroupFilterTable
    ) {
        part.id = createDynamicBody(
            boxShape(hx, hy, hz), mass, x, y, z, yaw,
            0.6f, 0.1f, 0.1f, 0.3f,
            filter, groupId, subGroupId
        )
        part.radius = maxOf(hx, hy, hz)
        part.spawnX = x; part.spawnY = y; part.spawnZ = z
    }

    private fun createSpherePart(
        part: Part,
        radius: Float,
        mass: Float,
        x: Float, y: Float, z: Float,
        yaw: Float,
        groupId: Int, subGroupId: Int,
        filter: GroupFilterTable
    ) {
        part.id = createDynamicBody(
            SphereShapeSettings(radius), mass, x, y, z, yaw,
            0.6f, 0.1f, 0.1f, 0.3f,
            filter, groupId, subGroupId
        )
        part.radius = radius
        part.spawnX = x; part.spawnY = y; part.spawnZ = z
    }

    private fun createConstraints(
        eucYaw: Float,
        torsoHeight: Float,
        upperArmLength: Float,
        lowerArmLength: Float,
        upperLegLength: Float,
        lowerLegLength: Float,
        hipOffset: Float,
        riderScale: Float
    ) {
        val torsoHalfHeight = torsoHeight / 2
        val torsoWidth = 0.175f * riderScale
        // Exact trig, not MathUtils: these feed constraint twist/plane axes, which Jolt
        // also requires to be unit length (same IsNormalized assert as the body quaternion).
        val yawRad = eucYaw.toDouble() * PI / 180.0
        val cy = kotlin.math.cos(yawRad).toFloat()
        val sy = kotlin.math.sin(yawRad).toFloat()

        // Neck
        addSwingTwist(
            constraints, torso, head,
            0f, torsoHalfHeight, 0f,
            0f, -0.13f * riderScale, 0f,
            cy, sy, 40f, 40f, 30f
        )
        // Left shoulder
        addSwingTwist(
            constraints, torso, leftUpperArm,
            -torsoWidth, torsoHalfHeight - 0.05f * riderScale, 0f,
            0f, upperArmLength / 2, 0f,
            cy, sy, 90f, 60f, 45f
        )
        // Left elbow
        addHinge(
            constraints, leftUpperArm, leftLowerArm,
            0f, -upperArmLength / 2, 0f,
            0f, lowerArmLength / 2, 0f,
            cy, sy, 0f, 140f
        )
        // Right shoulder
        addSwingTwist(
            constraints, torso, rightUpperArm,
            torsoWidth, torsoHalfHeight - 0.05f * riderScale, 0f,
            0f, upperArmLength / 2, 0f,
            cy, sy, 90f, 60f, 45f
        )
        // Right elbow
        addHinge(
            constraints, rightUpperArm, rightLowerArm,
            0f, -upperArmLength / 2, 0f,
            0f, lowerArmLength / 2, 0f,
            cy, sy, 0f, 140f
        )
        // Left hip
        addSwingTwist(
            constraints, torso, leftUpperLeg,
            -hipOffset, -torsoHalfHeight, 0f,
            0f, upperLegLength / 2, 0f,
            cy, sy, 60f, 45f, 30f
        )
        // Left knee
        addHinge(
            constraints, leftUpperLeg, leftLowerLeg,
            0f, -upperLegLength / 2, 0f,
            0f, lowerLegLength / 2, 0f,
            cy, sy, 0f, 140f
        )
        // Right hip
        addSwingTwist(
            constraints, torso, rightUpperLeg,
            hipOffset, -torsoHalfHeight, 0f,
            0f, upperLegLength / 2, 0f,
            cy, sy, 60f, 45f, 30f
        )
        // Right knee
        addHinge(
            constraints, rightUpperLeg, rightLowerLeg,
            0f, -upperLegLength / 2, 0f,
            0f, lowerLegLength / 2, 0f,
            cy, sy, 0f, 140f
        )
    }

    private fun applyInitialVelocities(
        playerVelocity: Float,
        sideLean: Float,
        forwardLean: Float,
        yawRad: Float
    ) {
        val leanMagnitude = kotlin.math.sqrt(sideLean * sideLean + forwardLean * forwardLean)
        val isHardFall = leanMagnitude > 0.3f

        val velocityMultiplier = if (isHardFall) 1.3f else 1.0f
        val forwardX = kotlin.math.sin(yawRad) * playerVelocity * velocityMultiplier
        val forwardZ = kotlin.math.cos(yawRad) * playerVelocity * velocityMultiplier
        val sideVel = sideLean * 2f
        val upVel = if (isHardFall) 0.5f + leanMagnitude * 1.5f else 0.2f

        val lx = forwardX + sideVel
        val ly = upVel
        val lz = forwardZ

        val tumbleFactor = if (isHardFall) 1.0f else 0.5f
        val tumbleX = (playerVelocity * 0.3f + forwardLean * 2f) * tumbleFactor
        val tumbleY = sideLean * 1.5f * tumbleFactor
        val tumbleZ = sideLean * 0.5f * tumbleFactor

        fun apply(part: Part, factor: Float) = setVelocity(
            part,
            lx * factor, ly * factor, lz * factor,
            tumbleX * factor, tumbleY * factor, tumbleZ * factor
        )

        apply(torso, 1.0f)
        apply(head, 1.1f)
        apply(leftUpperArm, 0.9f)
        apply(leftLowerArm, 0.85f)
        apply(rightUpperArm, 0.9f)
        apply(rightLowerArm, 0.85f)
        apply(leftUpperLeg, 0.8f)
        apply(leftLowerLeg, 0.7f)
        apply(rightUpperLeg, 0.8f)
        apply(rightLowerLeg, 0.7f)
    }

    // ------------------------------------------------------------------ simulation

    /**
     * Step the physics simulation. Runs if the main ragdoll is active OR there are falling
     * pedestrians / dynamic objects. No-op until the natives finished loading.
     */
    override fun update(delta: Float) {
        if (!ensureWorld()) return
        if (!isActive && pedestrianRagdolls.isEmpty() && dynamicObjects.isEmpty()) return

        if (broadPhaseDirty) {
            physicsSystem.OptimizeBroadPhase()
            broadPhaseDirty = false
        }

        // Clamp delta to prevent physics explosions on lag spikes
        val clampedDelta = delta.coerceIn(0.001f, 0.033f)

        // Bullet ran up to 8 internal sub-steps of 1/120s; Jolt's collision-step count is the
        // direct equivalent (it internally splits dt into N equal steps).
        val steps = MathUtils.ceil(clampedDelta * 120f).coerceIn(1, 8)
        physicsSystem.Update(clampedDelta, steps, tempAllocator, jobSystem)

        lastCollisionTime += delta
        lastSecondaryCollisionTime += delta
        lastGroundImpactTime += delta

        keepGroundUnderAction()

        if (isActive) {
            checkRagdollCollisions()
        }
        if (pedestrianRagdolls.isNotEmpty()) {
            checkSecondaryRagdollCollisions()
        }
    }

    /**
     * Bullet used an infinite btStaticPlaneShape; the Jolt ground is a finite slab, so slide it
     * along with whatever is currently being simulated.
     */
    private fun keepGroundUnderAction() {
        val id = playerParts.firstNotNullOfOrNull { it.id }
            ?: pedestrianRagdolls.firstNotNullOfOrNull { it.torso.id }
            ?: dynamicObjects.firstNotNullOfOrNull { it.id }
            ?: return
        bodyInterface.GetPositionAndRotation(id, outPos, outRot)
        recenterGround(outPos.GetX(), outPos.GetZ(), force = false)
    }

    private fun recenterGround(x: Float, z: Float, force: Boolean) {
        val id = groundId ?: return
        if (!force &&
            kotlin.math.abs(x - groundCenterX) < GROUND_RECENTER_DISTANCE &&
            kotlin.math.abs(z - groundCenterZ) < GROUND_RECENTER_DISTANCE
        ) return
        groundCenterX = x
        groundCenterZ = z
        bodyInterface.SetPosition(id, vecA(x, -GROUND_THICKNESS, z), EActivation.DontActivate)
        broadPhaseDirty = true
    }

    // ------------------------------------------------------------------ collision heuristics

    /**
     * Jolt has no contact-manifold polling, so instead of walking the dispatcher's manifolds we
     * compare every ragdoll body against the registered world colliders after each step, using
     * the same cooldowns / one-shot-per-collider semantics as the Bullet version.
     */
    private fun checkRagdollCollisions() {
        val checkColliders = lastCollisionTime >= minCollisionInterval &&
            (onRagdollCollision != null || onRagdollGroundImpact != null)
        val checkGround = onRagdollGroundImpact != null

        for (part in playerParts) {
            val id = part.id ?: continue
            bodyInterface.GetPositionAndRotation(id, outPos, outRot)
            val px = outPos.GetX()
            val py = outPos.GetY()
            val pz = outPos.GetZ()

            // --- ground contact (startles pigeons) ------------------------------------
            if (checkGround && py - part.radius <= GROUND_CONTACT_SLOP) {
                checkGroundImpact(px, py, pz)
            }

            if (!checkColliders) continue

            bodyInterface.GetLinearAndAngularVelocity(id, outLin, outAng)
            val speed = length(outLin.GetX(), outLin.GetY(), outLin.GetZ())
            if (speed < PRIMARY_IMPACT_SPEED) continue

            val reach = part.radius + CONTACT_SLOP
            for (collider in worldColliders) {
                if (collider.type == RagdollTypes.ColliderType.GROUND) continue
                if (collider in triggeredColliders) continue
                if (distanceToCollider(collider, px, py, pz) > reach) continue

                triggeredColliders.add(collider)
                lastCollisionTime = 0f
                onRagdollCollision?.invoke(collider.type)

                // Bullet also fired the ground-impact callback on any significant collision.
                if (lastGroundImpactTime >= minGroundImpactInterval) {
                    lastGroundImpactTime = 0f
                    hasTriggeredGroundImpact = true
                    impactPos.set(px, py, pz)
                    onRagdollGroundImpact?.invoke(impactPos)
                }
                return
            }
        }
    }

    private fun checkGroundImpact(x: Float, y: Float, z: Float) {
        val callback = onRagdollGroundImpact ?: return
        if (lastGroundImpactTime < minGroundImpactInterval) return
        lastGroundImpactTime = 0f
        hasTriggeredGroundImpact = true
        impactPos.set(x, y, z)
        callback.invoke(impactPos)
    }

    /**
     * Secondary collisions - pedestrian ragdolls hitting world objects. Quieter, longer cooldown
     * and a higher impact threshold, matching the Bullet version.
     */
    private fun checkSecondaryRagdollCollisions() {
        if (onSecondaryRagdollCollision == null && onRagdollHitPedestrian == null) return
        if (lastSecondaryCollisionTime < minSecondaryCollisionInterval) return
        if (worldColliders.isEmpty()) return

        for (ragdoll in pedestrianRagdolls) {
            for (part in pedestrianParts(ragdoll)) {
                val id = part.id ?: continue
                bodyInterface.GetLinearAndAngularVelocity(id, outLin, outAng)
                val speed = length(outLin.GetX(), outLin.GetY(), outLin.GetZ())
                if (speed < SECONDARY_IMPACT_SPEED) continue

                bodyInterface.GetPositionAndRotation(id, outPos, outRot)
                val px = outPos.GetX()
                val py = outPos.GetY()
                val pz = outPos.GetZ()
                val reach = part.radius + CONTACT_SLOP

                for (collider in worldColliders) {
                    if (collider.type == RagdollTypes.ColliderType.GROUND) continue
                    if (collider in triggeredSecondaryColliders) continue
                    if (distanceToCollider(collider, px, py, pz) > reach) continue

                    triggeredSecondaryColliders.add(collider)
                    lastSecondaryCollisionTime = 0f
                    onSecondaryRagdollCollision?.invoke(collider.type)
                    return
                }
            }
        }
    }

    private fun pedestrianParts(r: PedRagdoll): Array<Part> =
        arrayOf(r.torso, r.head, r.leftArm, r.rightArm, r.leftLeg, r.rightLeg)

    /** Distance from a world-space point to the collider's (yaw-rotated) box. */
    private fun distanceToCollider(c: WorldCollider, x: Float, y: Float, z: Float): Float {
        val dx = x - c.cx
        val dy = y - c.cy
        val dz = z - c.cz
        // rotate the offset into the collider's local frame (inverse yaw)
        val lx = dx * c.cosYaw - dz * c.sinYaw
        val lz = dx * c.sinYaw + dz * c.cosYaw
        val ex = (kotlin.math.abs(lx) - c.hx).coerceAtLeast(0f)
        val ey = (kotlin.math.abs(dy) - c.hy).coerceAtLeast(0f)
        val ez = (kotlin.math.abs(lz) - c.hz).coerceAtLeast(0f)
        return length(ex, ey, ez)
    }

    private fun length(x: Float, y: Float, z: Float): Float =
        kotlin.math.sqrt(x * x + y * y + z * z)

    // ------------------------------------------------------------------ player transforms

    /** Get current EUC transform for rendering. */
    override fun getEucTransform(): Matrix4? {
        if (!ensureWorld()) return null
        if (!isActive && !isFrozen) return null
        val id = euc.id ?: return null
        return fillMatrix(id, tempMatrix)
    }

    /** Get EUC position only. */
    override fun getEucPosition(out: Vector3): Vector3 {
        if (!ensureWorld()) return out.setZero()
        if (!isActive && !isFrozen) return out.setZero()
        val id = euc.id ?: return out.setZero()
        bodyInterface.GetPositionAndRotation(id, outPos, outRot)
        return out.set(outPos.GetX(), outPos.GetY(), outPos.GetZ())
    }

    override fun getHeadTransform(): Matrix4? = getPartTransform(head)
    override fun getTorsoTransform(): Matrix4? = getPartTransform(torso)
    override fun getLeftUpperArmTransform(): Matrix4? = getPartTransform(leftUpperArm)
    override fun getLeftLowerArmTransform(): Matrix4? = getPartTransform(leftLowerArm)
    override fun getRightUpperArmTransform(): Matrix4? = getPartTransform(rightUpperArm)
    override fun getRightLowerArmTransform(): Matrix4? = getPartTransform(rightLowerArm)
    override fun getLeftUpperLegTransform(): Matrix4? = getPartTransform(leftUpperLeg)
    override fun getLeftLowerLegTransform(): Matrix4? = getPartTransform(leftLowerLeg)
    override fun getRightUpperLegTransform(): Matrix4? = getPartTransform(rightUpperLeg)
    override fun getRightLowerLegTransform(): Matrix4? = getPartTransform(rightLowerLeg)

    private fun getPartTransform(part: Part): Matrix4? {
        if (!ensureWorld()) return null
        if (!isActive && !isFrozen) return null
        val id = part.id ?: return null
        return fillMatrix(id, tempMatrix)
    }

    override fun getTorsoPosition(out: Vector3): Vector3 {
        if (!ensureWorld()) return out.setZero()
        if (!isActive && !isFrozen) return out.setZero()
        val id = torso.id ?: return out.setZero()
        bodyInterface.GetPositionAndRotation(id, outPos, outRot)
        return out.set(outPos.GetX(), outPos.GetY(), outPos.GetZ())
    }

    /** Check if simulation is active (or frozen but still visible). */
    override fun isActive(): Boolean = isActive || isFrozen

    /** Freeze the ragdoll in place - stops physics but keeps it visible. */
    override fun freeze() {
        if (isActive) {
            isActive = false
            isFrozen = true
        }
    }

    // ------------------------------------------------------------------ world colliders

    /**
     * Add a box collider for world objects (cars, buildings, etc.)
     * @param position Center position of the box
     * @param halfExtents Half-size in each dimension (width/2, height/2, depth/2)
     * @param yaw Rotation around Y axis in degrees
     * @param type Type of obstacle for collision sounds
     */
    override fun addBoxCollider(
        position: Vector3,
        halfExtents: Vector3,
        yaw: Float,
        type: RagdollTypes.ColliderType
    ) {
        if (!ensureWorld()) return

        val hx = halfExtents.x.coerceAtLeast(0.01f)
        val hy = halfExtents.y.coerceAtLeast(0.01f)
        val hz = halfExtents.z.coerceAtLeast(0.01f)

        val shape = BoxShapeSettings(vecA(hx, hy, hz), convexRadiusFor(minOf(hx, hy, hz)))
        val settings = JoltNew.BodyCreationSettings(
            shape, vecB(position.x, position.y, position.z), yawQuat(yaw),
            EMotionType.Static, LAYER_STATIC
        )
        settings.set_mFriction(0.5f)
        settings.set_mRestitution(0.3f)
        val id = ownId(bodyInterface.CreateAndAddBody(settings, EActivation.DontActivate))
        settings.dispose()
        broadPhaseDirty = true

        val yawRad = yaw * MathUtils.degreesToRadians
        worldColliders.add(
            WorldCollider(
                id, type,
                position.x, position.y, position.z,
                hx, hy, hz,
                MathUtils.cos(yawRad), MathUtils.sin(yawRad)
            )
        )
    }

    /**
     * Add a cylinder collider (for street lights, poles, etc.)
     * @param position Center position
     * @param radius Radius of cylinder
     * @param height Height of cylinder
     * @param type Type of obstacle for collision sounds
     */
    override fun addCylinderCollider(
        position: Vector3,
        radius: Float,
        height: Float,
        type: RagdollTypes.ColliderType
    ) {
        if (!ensureWorld()) return

        val r = radius.coerceAtLeast(0.01f)
        val halfHeight = (height / 2f).coerceAtLeast(0.01f)

        val settings = JoltNew.BodyCreationSettings(
            cylinderShape(r, halfHeight), vecB(position.x, position.y, position.z), identityQuat(),
            EMotionType.Static, LAYER_STATIC
        )
        settings.set_mFriction(0.5f)
        settings.set_mRestitution(0.2f)
        val id = ownId(bodyInterface.CreateAndAddBody(settings, EActivation.DontActivate))
        settings.dispose()
        broadPhaseDirty = true

        worldColliders.add(
            WorldCollider(
                id, type,
                position.x, position.y, position.z,
                r, halfHeight, r,
                1f, 0f
            )
        )
    }

    /** Clear all world colliders (call before adding new ones for a new fall). */
    override fun clearWorldColliders() {
        if (worldReady) {
            for (collider in worldColliders) destroyBody(collider.id)
        }
        worldColliders.clear()
        triggeredColliders.clear()
        triggeredSecondaryColliders.clear()
        lastCollisionTime = 0f
        lastSecondaryCollisionTime = 0f
        lastGroundImpactTime = 0f
    }

    // ------------------------------------------------------------------ pedestrian ragdolls

    /**
     * Add a simplified 6-part pedestrian ragdoll (head, torso, 2 arms, 2 legs).
     * @return Index of the created pedestrian ragdoll, or -1 while the natives are still loading.
     */
    override fun addPedestrianRagdoll(
        position: Vector3,
        yaw: Float,
        playerVelocity: Float,
        playerDirection: Vector3,
        entityIndex: Int,
        shirtColor: Color
    ): Int {
        if (!ensureWorld()) return -1

        val ragdoll = PedRagdoll(entityIndex, shirtColor)
        val groupId = nextGroupId++

        val scale = 1.7f
        val legScale = 0.85f

        val baseX = position.x
        val baseY = position.y + 0.9f * scale * legScale
        val baseZ = position.z

        val torsoWidth = 0.15f * scale
        val torsoHeight = 0.5f * scale
        val torsoDepth = 0.1f * scale
        val headRadius = 0.1f * scale
        val armRadius = 0.04f * scale
        val armLength = 0.5f * scale
        val legRadius = 0.055f * scale
        val legLength = 0.75f * scale * legScale

        val torsoMass = 25f
        val headMass = 4f
        val armMass = 3f
        val legMass = 10f

        createBoxPart(
            ragdoll.torso, torsoWidth, torsoHeight / 2, torsoDepth, torsoMass,
            baseX, baseY + torsoHeight / 2, baseZ, yaw,
            groupId, PSG_TORSO, pedestrianGroupFilter
        )
        createSpherePart(
            ragdoll.head, headRadius, headMass,
            baseX, baseY + torsoHeight + headRadius * 0.8f, baseZ, yaw,
            groupId, PSG_HEAD, pedestrianGroupFilter
        )

        val shoulderY = baseY + torsoHeight - 0.05f * scale
        val shoulderOffset = torsoWidth + 0.02f * scale

        createBoxPart(
            ragdoll.leftArm, armRadius, armLength / 2, armRadius, armMass,
            baseX - shoulderOffset, shoulderY - armLength / 2, baseZ, yaw,
            groupId, PSG_LEFT_ARM, pedestrianGroupFilter
        )
        createBoxPart(
            ragdoll.rightArm, armRadius, armLength / 2, armRadius, armMass,
            baseX + shoulderOffset, shoulderY - armLength / 2, baseZ, yaw,
            groupId, PSG_RIGHT_ARM, pedestrianGroupFilter
        )

        val hipOffset = 0.08f * scale

        createBoxPart(
            ragdoll.leftLeg, legRadius, legLength / 2, legRadius, legMass,
            baseX - hipOffset, baseY - legLength / 2, baseZ, yaw,
            groupId, PSG_LEFT_LEG, pedestrianGroupFilter
        )
        createBoxPart(
            ragdoll.rightLeg, legRadius, legLength / 2, legRadius, legMass,
            baseX + hipOffset, baseY - legLength / 2, baseZ, yaw,
            groupId, PSG_RIGHT_LEG, pedestrianGroupFilter
        )

        createSimplifiedPedestrianConstraints(
            ragdoll, yaw, torsoHeight / 2, torsoWidth, armLength, legLength, hipOffset, scale
        )
        applySimplifiedPedestrianImpactVelocities(ragdoll, playerVelocity, playerDirection)

        pedestrianRagdolls.add(ragdoll)
        return pedestrianRagdolls.size - 1
    }

    private fun createSimplifiedPedestrianConstraints(
        ragdoll: PedRagdoll,
        yaw: Float,
        torsoHalfHeight: Float,
        torsoWidth: Float,
        armLength: Float,
        legLength: Float,
        hipOffset: Float,
        scale: Float
    ) {
        val yawRad = yaw * MathUtils.degreesToRadians
        val cy = MathUtils.cos(yawRad)
        val sy = MathUtils.sin(yawRad)

        // Neck
        addSwingTwist(
            ragdoll.constraints, ragdoll.torso, ragdoll.head,
            0f, torsoHalfHeight, 0f,
            0f, -0.1f * scale, 0f,
            cy, sy, 40f, 40f, 30f
        )
        // Left shoulder
        addSwingTwist(
            ragdoll.constraints, ragdoll.torso, ragdoll.leftArm,
            -torsoWidth, torsoHalfHeight - 0.05f * scale, 0f,
            0f, armLength / 2, 0f,
            cy, sy, 90f, 60f, 45f
        )
        // Right shoulder
        addSwingTwist(
            ragdoll.constraints, ragdoll.torso, ragdoll.rightArm,
            torsoWidth, torsoHalfHeight - 0.05f * scale, 0f,
            0f, armLength / 2, 0f,
            cy, sy, 90f, 60f, 45f
        )
        // Left hip
        addSwingTwist(
            ragdoll.constraints, ragdoll.torso, ragdoll.leftLeg,
            -hipOffset, -torsoHalfHeight, 0f,
            0f, legLength / 2, 0f,
            cy, sy, 60f, 45f, 30f
        )
        // Right hip
        addSwingTwist(
            ragdoll.constraints, ragdoll.torso, ragdoll.rightLeg,
            hipOffset, -torsoHalfHeight, 0f,
            0f, legLength / 2, 0f,
            cy, sy, 60f, 45f, 30f
        )
    }

    private fun applySimplifiedPedestrianImpactVelocities(
        ragdoll: PedRagdoll,
        playerVelocity: Float,
        playerDirection: Vector3
    ) {
        val impactForce = playerVelocity * 1.5f
        val upwardForce = 0.3f + playerVelocity * 0.03f

        val lx = playerDirection.x * impactForce
        val ly = upwardForce
        val lz = playerDirection.z * impactForce

        val tumbleX = playerVelocity * 0.3f
        val tumbleY = playerDirection.x * 0.5f
        val tumbleZ = -playerDirection.z * 0.2f

        fun apply(part: Part, factor: Float) = setVelocity(
            part,
            lx * factor, ly * factor, lz * factor,
            tumbleX * factor, tumbleY * factor, tumbleZ * factor
        )

        apply(ragdoll.torso, 1.0f)
        apply(ragdoll.head, 1.1f)
        apply(ragdoll.leftArm, 0.9f)
        apply(ragdoll.rightArm, 0.9f)
        apply(ragdoll.leftLeg, 0.75f)
        apply(ragdoll.rightLeg, 0.75f)
    }

    override fun getPedestrianTransform(index: Int): Matrix4? = pedestrianPartTransform(index) { it.torso }
    override fun getPedestrianHeadTransform(index: Int): Matrix4? = pedestrianPartTransform(index) { it.head }
    override fun getPedestrianTorsoTransform(index: Int): Matrix4? = pedestrianPartTransform(index) { it.torso }
    override fun getPedestrianLeftArmTransform(index: Int): Matrix4? = pedestrianPartTransform(index) { it.leftArm }
    override fun getPedestrianRightArmTransform(index: Int): Matrix4? = pedestrianPartTransform(index) { it.rightArm }
    override fun getPedestrianLeftLegTransform(index: Int): Matrix4? = pedestrianPartTransform(index) { it.leftLeg }
    override fun getPedestrianRightLegTransform(index: Int): Matrix4? = pedestrianPartTransform(index) { it.rightLeg }

    /** Returns null (never a stale matrix) when out of range or the part was retired. */
    private inline fun pedestrianPartTransform(index: Int, part: (PedRagdoll) -> Part): Matrix4? {
        if (!ensureWorld()) return null
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        val id = part(pedestrianRagdolls[index]).id ?: return null
        return fillMatrix(id, tempMatrix)
    }

    /** Get shirt color for a pedestrian ragdoll. */
    override fun getPedestrianShirtColor(index: Int): Color? {
        if (index < 0 || index >= pedestrianRagdolls.size) return null
        return pedestrianRagdolls[index].shirtColor
    }

    /** Get number of active pedestrian ragdolls. */
    override fun getPedestrianCount(): Int = pedestrianRagdolls.size

    // ------------------------------------------------------------------ dynamic objects

    /**
     * Add a dynamic trash can that can be knocked over.
     * @return Index of the created dynamic object, or -1 while the natives are still loading.
     */
    override fun addTrashCanRagdoll(
        position: Vector3,
        playerVelocity: Float,
        playerDirection: Vector3,
        entityIndex: Int
    ): Int {
        if (!ensureWorld()) return -1

        val obj = DynObject(entityIndex)

        val scale = 1.6f
        val radius = 0.25f * scale
        val height = 1f * scale
        val halfHeight = height / 2
        val mass = 12f

        val id = createDynamicBody(
            cylinderShape(radius, halfHeight), mass,
            position.x, halfHeight, position.z, 0f,
            0.5f, 0.2f, 0.05f, 0.1f,
            null, nextGroupId++, 0,
            continuous = true   // Bullet enabled CCD on trash cans to prevent tunneling
        )
        obj.id = id

        // Impulse applied at the TOP of the can so it tips over instead of sliding.
        val impactSpeed = playerVelocity.coerceIn(5f, 15f)
        val impulseStrength = impactSpeed * mass * 0.3f

        val pointX = position.x + playerDirection.x * radius * 0.5f
        val pointY = halfHeight + halfHeight * 0.7f
        val pointZ = position.z + playerDirection.z * radius * 0.5f

        bodyInterface.ActivateBody(id)  // AddImpulse does nothing on a sleeping body
        bodyInterface.AddImpulse(
            id,
            vecA(
                playerDirection.x * impulseStrength,
                impulseStrength * 0.2f,
                playerDirection.z * impulseStrength
            ),
            vecB(pointX, pointY, pointZ)
        )

        dynamicObjects.add(obj)
        return dynamicObjects.size - 1
    }

    /** Get transform for a dynamic object (trash can, etc.), or null if invalid/retired. */
    override fun getDynamicObjectTransform(index: Int): Matrix4? {
        if (!ensureWorld()) return null
        if (index < 0 || index >= dynamicObjects.size) return null
        val id = dynamicObjects[index].id ?: return null
        return fillMatrix(id, tempMatrix2)
    }

    // ------------------------------------------------------------------ queries / impulses

    /**
     * Retire ragdolls (pedestrians and dynamic objects) that fell behind the player. Retired
     * slots stay in the lists so indices held by components remain valid, but their bodies are
     * removed and destroyed.
     */
    override fun retireRagdollsBehind(minZ: Float) {
        if (!ensureWorld()) return

        for (ragdoll in pedestrianRagdolls) {
            val id = ragdoll.torso.id ?: continue  // already retired
            bodyInterface.GetPositionAndRotation(id, outPos, outRot)
            retireCheckPos.set(outPos.GetX(), outPos.GetY(), outPos.GetZ())
            if (retireCheckPos.z < minZ) {
                removeConstraints(ragdoll.constraints)
                destroyPart(ragdoll.head)
                destroyPart(ragdoll.torso)
                destroyPart(ragdoll.leftArm)
                destroyPart(ragdoll.rightArm)
                destroyPart(ragdoll.leftLeg)
                destroyPart(ragdoll.rightLeg)
            }
        }

        for (obj in dynamicObjects) {
            val id = obj.id ?: continue  // already retired
            bodyInterface.GetPositionAndRotation(id, outPos, outRot)
            retireCheckPos.set(outPos.GetX(), outPos.GetY(), outPos.GetZ())
            if (retireCheckPos.z < minZ) {
                destroyBody(id)
                obj.id = null
            }
        }
    }

    /**
     * Get positions and velocities of all active ragdoll torsos.
     * Only returns bodies moving fast enough to knock someone down.
     */
    override fun getActiveRagdollBodies(minVelocity: Float): List<RagdollTypes.RagdollBodyInfo> {
        if (!ensureWorld()) return emptyList()
        val result = mutableListOf<RagdollTypes.RagdollBodyInfo>()

        torso.id?.let { id ->
            if (isActive) {
                bodyInterface.GetPositionAndRotation(id, outPos, outRot)
                bodyInterface.GetLinearAndAngularVelocity(id, outLin, outAng)
                if (length(outLin.GetX(), outLin.GetY(), outLin.GetZ()) >= minVelocity) {
                    result.add(
                        RagdollTypes.RagdollBodyInfo(
                            Vector3(outPos.GetX(), outPos.GetY(), outPos.GetZ()),
                            Vector3(outLin.GetX(), outLin.GetY(), outLin.GetZ()),
                            true
                        )
                    )
                }
            }
        }

        for (ragdoll in pedestrianRagdolls) {
            val id = ragdoll.torso.id ?: continue
            bodyInterface.GetPositionAndRotation(id, outPos, outRot)
            bodyInterface.GetLinearAndAngularVelocity(id, outLin, outAng)
            if (length(outLin.GetX(), outLin.GetY(), outLin.GetZ()) >= minVelocity) {
                result.add(
                    RagdollTypes.RagdollBodyInfo(
                        Vector3(outPos.GetX(), outPos.GetY(), outPos.GetZ()),
                        Vector3(outLin.GetX(), outLin.GetY(), outLin.GetZ()),
                        false
                    )
                )
            }
        }

        return result
    }

    /**
     * Apply external impulse to the player ragdoll (e.g. a car hitting the ragdoll).
     * @param impactPosition Position where the impact occurred
     * @param impactVelocity Velocity/direction of the impacting object
     */
    override fun applyExternalImpulse(impactPosition: Vector3, impactVelocity: Vector3) {
        if (!ensureWorld()) return
        if (!isActive) return

        val impactSpeed = impactVelocity.len()
        val impulseStrength = impactSpeed * 15f

        val nx: Float
        val ny: Float
        val nz: Float
        if (impactSpeed > 0.0001f) {
            nx = impactVelocity.x / impactSpeed
            ny = impactVelocity.y / impactSpeed
            nz = impactVelocity.z / impactSpeed
        } else {
            nx = 0f; ny = 0f; nz = 0f
        }

        // Torso: central impulse + tumbling torque impulse
        torso.id?.let { id ->
            bodyInterface.ActivateBody(id)
            bodyInterface.AddImpulse(
                id,
                vecA(
                    nx * impulseStrength,
                    ny * impulseStrength + impulseStrength * 0.3f,
                    nz * impulseStrength
                )
            )
            bodyInterface.AddAngularImpulse(
                id,
                vecB(impactVelocity.z * 2f, 0f, -impactVelocity.x * 2f)
            )
        }

        // Head: smaller impulse
        head.id?.let { id ->
            bodyInterface.ActivateBody(id)
            bodyInterface.AddImpulse(
                id,
                vecA(
                    nx * impulseStrength * 0.8f,
                    ny * impulseStrength * 0.8f + impulseStrength * 0.4f,
                    nz * impulseStrength * 0.8f
                )
            )
        }

        // Limbs: half strength
        val limbs = arrayOf(
            leftUpperArm, leftLowerArm, rightUpperArm, rightLowerArm,
            leftUpperLeg, leftLowerLeg, rightUpperLeg, rightLowerLeg
        )
        for (limb in limbs) {
            val id = limb.id ?: continue
            bodyInterface.ActivateBody(id)
            bodyInterface.AddImpulse(
                id,
                vecA(
                    nx * impulseStrength * 0.5f,
                    ny * impulseStrength * 0.5f + impulseStrength * 0.2f,
                    nz * impulseStrength * 0.5f
                )
            )
        }

        onRagdollGroundImpact?.invoke(impactPosition)
    }

    // ------------------------------------------------------------------ teardown

    /** Stop the simulation and cleanup bodies. */
    override fun stop() {
        if (worldReady) cleanup(clearPedestrians = true)
        isActive = false
        isFrozen = false
    }

    private fun cleanup(clearPedestrians: Boolean = false) {
        if (!worldReady) return

        removeConstraints(constraints)
        clearWorldColliders()

        if (clearPedestrians) {
            clearPedestrianBodies()
        }

        destroyPart(euc)
        destroyPart(head)
        destroyPart(torso)
        destroyPart(leftUpperArm)
        destroyPart(leftLowerArm)
        destroyPart(rightUpperArm)
        destroyPart(rightLowerArm)
        destroyPart(leftUpperLeg)
        destroyPart(leftLowerLeg)
        destroyPart(rightUpperLeg)
        destroyPart(rightLowerLeg)
    }

    private fun clearPedestrianBodies() {
        for (ragdoll in pedestrianRagdolls) {
            removeConstraints(ragdoll.constraints)
            destroyPart(ragdoll.head)
            destroyPart(ragdoll.torso)
            destroyPart(ragdoll.leftArm)
            destroyPart(ragdoll.rightArm)
            destroyPart(ragdoll.leftLeg)
            destroyPart(ragdoll.rightLeg)
        }
        pedestrianRagdolls.clear()
        clearDynamicObjects()
    }

    private fun clearDynamicObjects() {
        for (obj in dynamicObjects) {
            destroyBody(obj.id)
            obj.id = null
        }
        dynamicObjects.clear()
    }

    override fun dispose() {
        if (!worldReady) return
        worldReady = false

        cleanup(clearPedestrians = true)

        destroyBody(groundId)
        groundId = null

        // NOTE: ShapeSettings / ConstraintSettings / Constraint are Jolt RefTargets - they must
        // never be disposed here (BodyCreationSettings.dispose() already released the shapes).
        physicsSystem.dispose()
        jobSystem.dispose()
        tempAllocator.dispose()

        // Order matters: every body (and therefore every CollisionGroup holding a reference to
        // the tables) is gone by now, so dropping the shared group and then our own reference
        // takes the tables to refCount 0 and lets Jolt free them exactly once.
        scratchGroup.dispose()
        playerGroupFilter.Release()
        pedestrianGroupFilter.Release()

        vA.dispose(); vB.dispose(); vC.dispose(); vD.dispose()
        outPos.dispose(); outLin.dispose(); outAng.dispose()
        outRot.dispose(); qA.dispose()

        Gdx.app?.log("JoltRagdollPhysics", "Physics disposed")
    }
}
