package com.eucleantoomuch.game.rendering

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.ecs.Families
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.eucleantoomuch.game.ecs.components.ArmComponent
import com.eucleantoomuch.game.ecs.components.ArmTagComponent
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.ecs.components.HeadComponent
import com.eucleantoomuch.game.ecs.components.ModelComponent
import com.eucleantoomuch.game.ecs.components.PlayerComponent
import com.eucleantoomuch.game.ecs.components.ShadowComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.components.PedestrianComponent
import com.eucleantoomuch.game.ecs.components.GroundComponent
import com.eucleantoomuch.game.ecs.components.GroundType
import com.eucleantoomuch.game.ecs.components.ObstacleComponent
import com.eucleantoomuch.game.ecs.components.ObstacleType
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.scene.SceneManager

class GameRenderer(
    private val engine: Engine,
    private val models: ProceduralModels
) : Disposable {
    private val modelBatch = ModelBatch()
    val camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    val cameraController = CameraController(camera)

    // Fog color matches sky for seamless blend
    private val fogColor = Color(0.5f, 0.7f, 0.9f, 1f)

    private val environment = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f))
        add(DirectionalLight().set(0.9f, 0.9f, 0.9f, -0.5f, -1f, -0.3f))
        // Add fog that matches sky color - objects fade into sky at distance
        set(ColorAttribute(ColorAttribute.Fog, fogColor))
    }

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val modelMapper = ComponentMapper.getFor(ModelComponent::class.java)
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)
    private val playerMapper = ComponentMapper.getFor(PlayerComponent::class.java)
    private val armTagMapper = ComponentMapper.getFor(ArmTagComponent::class.java)
    private val armMapper = ComponentMapper.getFor(ArmComponent::class.java)
    private val headMapper = ComponentMapper.getFor(HeadComponent::class.java)
    private val shadowMapper = ComponentMapper.getFor(ShadowComponent::class.java)
    private val pedestrianMapper = ComponentMapper.getFor(PedestrianComponent::class.java)
    private val groundMapper = ComponentMapper.getFor(GroundComponent::class.java)
    private val obstacleMapper = ComponentMapper.getFor(ObstacleComponent::class.java)

    private val tempMatrix = Matrix4()
    private val shadowMatrix = Matrix4()
    private val headMatrix = Matrix4()

    // Reference to rider entity for arm rendering (set externally)
    var riderEntity: com.badlogic.ashley.core.Entity? = null

    // Head model instance (rendered separately for animation)
    private var headInstance: ModelInstance? = null

    // Flag to hide head during ragdoll (head is rendered by ragdoll system instead)
    var hideHead = false

    // Flag to enable/disable shadow rendering
    var shadowsEnabled = true

    // Ragdoll renderer and physics (set when ragdoll is active)
    var activeRagdollRenderer: com.eucleantoomuch.game.physics.RagdollRenderer? = null
    var activeRagdollPhysics: com.eucleantoomuch.game.physics.RagdollPhysics? = null

    // Pedestrian ragdoll renderer (always active for fallen pedestrians during gameplay)
    var pedestrianRagdollRenderer: com.eucleantoomuch.game.physics.RagdollRenderer? = null
    var pedestrianRagdollPhysics: com.eucleantoomuch.game.physics.RagdollPhysics? = null

    // Articulated pedestrian renderer with walking animation
    val pedestrianRenderer: PedestrianRenderer

    // Sky color
    private val skyR = 0.5f
    private val skyG = 0.7f
    private val skyB = 0.9f

    // LOD distance threshold - buildings further than this use simple model
    private val lodDistance = 80f
    private val lodDistanceSq = lodDistance * lodDistance

    // Frustum culling - radius for bounding sphere checks (generous to avoid popping)
    private val defaultCullRadius = 8f  // Most objects fit in 8m sphere
    private val buildingCullRadius = 60f  // Tall buildings need larger radius
    private val shadowMaxDistance = 50f  // Don't render shadows beyond this distance
    private val shadowMaxDistanceSq = shadowMaxDistance * shadowMaxDistance

    // Post-processing effects
    val postProcessing = PostProcessing()

    // SceneManager for PBR/GLTF models
    val sceneManager: SceneManager
    private val pbrLight: DirectionalLightEx

    init {
        postProcessing.initialize()
        camera.near = 0.5f  // Increased from 0.1f to reduce z-fighting
        camera.far = 400f   // Larger far distance = weaker fog effect
        camera.update()

        // Create head model instance for animated rendering
        headInstance = ModelInstance(models.createHeadModel())

        // Create pedestrian renderer for articulated animation
        pedestrianRenderer = PedestrianRenderer(engine, models)

        // Setup SceneManager for PBR rendering
        sceneManager = SceneManager()
        sceneManager.setCamera(camera)

        // Setup flat lighting for GLB models (like Blender viewport without extra lights)
        pbrLight = DirectionalLightEx()
        pbrLight.direction.set(-0.5f, -1f, -0.3f).nor()
        pbrLight.intensity = 5.5f  // Soft directional light
        sceneManager.environment.add(pbrLight)

        // Moderate ambient for flat but not overexposed look
        sceneManager.setAmbientLight(0.020f)
    }

    fun setCameraFar(distance: Float) {
        camera.far = distance + 150f  // Larger buffer = weaker fog effect
        camera.update()
    }

    fun render() {
        // Begin post-processing (render to framebuffer)
        postProcessing.begin()

        // Clear screen with sky color
        Gdx.gl.glClearColor(skyR, skyG, skyB, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)

        modelBatch.begin(camera)

        // First pass: render shadows (with blending, no cull face for flat surfaces)
        if (shadowsEnabled) {
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            Gdx.gl.glDisable(GL20.GL_CULL_FACE)  // Shadows are flat, don't cull
            Gdx.gl.glDepthMask(false)  // Don't write to depth buffer

            for (entity in engine.getEntitiesFor(Families.renderable)) {
                val shadow = shadowMapper.get(entity) ?: continue
                val transform = transformMapper.get(entity) ?: continue

                if (shadow.visible && shadow.shadowInstance != null) {
                    // Distance culling for shadows - don't render far away shadows
                    val dx = transform.position.x - camera.position.x
                    val dz = transform.position.z - camera.position.z
                    val distSq = dx * dx + dz * dz
                    if (distSq > shadowMaxDistanceSq) continue

                    // Frustum culling for shadows
                    if (!camera.frustum.sphereInFrustum(transform.position.x, 0f, transform.position.z, shadow.scale * 5f)) {
                        continue
                    }

                    val shadowX = transform.position.x + shadow.xOffset

                    // All shadows render at sidewalk height (0.11f) so they're visible on all surfaces
                    val groundY = 0.11f

                    shadowMatrix.idt()
                    shadowMatrix.translate(shadowX, groundY, transform.position.z)
                    // Apply yaw rotation for directional shadows (buildings)
                    if (transform.yaw != 0f) {
                        shadowMatrix.rotate(0f, 1f, 0f, transform.yaw)
                    }
                    shadowMatrix.scale(shadow.scale, 1f, shadow.scale)

                    shadow.shadowInstance!!.transform.set(shadowMatrix)
                    modelBatch.render(shadow.shadowInstance!!)
                }
            }

            // Restore state for regular rendering
            Gdx.gl.glDepthMask(true)
            Gdx.gl.glEnable(GL20.GL_CULL_FACE)
        }

        // Second pass: render all entities with ModelComponent
        for (entity in engine.getEntitiesFor(Families.renderable)) {
            val model = modelMapper.get(entity)
            val transform = transformMapper.get(entity)

            // Skip pedestrians - they're rendered separately with articulated animation
            val pedestrian = pedestrianMapper.get(entity)
            if (pedestrian != null && !pedestrian.isRagdolling) {
                continue  // PedestrianRenderer handles these
            }

            if (model.visible && model.modelInstance != null) {
                // Frustum culling - skip objects outside camera view
                // Skip culling for: ground surfaces, arms (attached to rider), player-related entities
                val ground = groundMapper.get(entity)
                val isGroundSurface = ground != null && (ground.type == GroundType.ROAD || ground.type == GroundType.SIDEWALK)
                val isArm = armTagMapper.get(entity) != null
                val isPlayerRelated = eucMapper.get(entity) != null  // EUC, rider, arms

                // Check if this is a curb (long horizontal object)
                val obstacle = obstacleMapper.get(entity)
                val isCurb = obstacle != null && obstacle.type == ObstacleType.CURB

                if (!isGroundSurface && !isArm && !isPlayerRelated) {
                    // Use larger radius for buildings (tall) and curbs (long horizontal)
                    val cullRadius = when {
                        model.modelInstanceLod != null -> buildingCullRadius
                        isCurb -> 25f  // Curbs are long, need larger radius
                        else -> defaultCullRadius
                    }
                    // Check if bounding sphere is in frustum (Y offset for tall objects)
                    val centerY = if (model.modelInstanceLod != null) 30f else transform.position.y + 2f
                    if (!camera.frustum.sphereInFrustum(transform.position.x, centerY, transform.position.z, cullRadius)) {
                        continue  // Object is outside camera frustum, skip rendering
                    }
                }

                // Update LOD state based on distance to camera
                if (model.modelInstanceLod != null) {
                    val dx = transform.position.x - camera.position.x
                    val dz = transform.position.z - camera.position.z
                    val distSq = dx * dx + dz * dz
                    model.useLod = distSq > lodDistanceSq
                }

                val activeModel = model.getActiveModel() ?: continue

                // Build transform matrix
                tempMatrix.idt()
                tempMatrix.translate(transform.position)

                val euc = eucMapper.get(entity)
                val isPlayer = playerMapper.get(entity) != null

                if (euc != null && isPlayer) {
                    // For EUC model: apply yaw (turn left/right), then model fix rotation, then lean
                    // Negate yaw to match visual direction with input direction
                    tempMatrix.rotate(0f, 1f, 0f, -transform.yaw)

                    // Model orientation fix (flip upside down model)
                    if (models.eucModelRotationX != 0f) {
                        tempMatrix.rotate(1f, 0f, 0f, models.eucModelRotationX)
                    }
                    if (models.eucModelRotationY != 0f) {
                        tempMatrix.rotate(0f, 1f, 0f, models.eucModelRotationY)
                    }

                    // Lean forward (around X axis) and side (around Z axis)
                    val forwardLeanAngle = euc.visualForwardLean * 20f
                    // Side lean: normal riding uses values -1 to 1 (mapped to ±15°)
                    // During fall, visualSideLean can exceed ±1 (eucRoll/90 gets added)
                    // For values > 1 or < -1, we need the full angle for fall animation (up to 90°)
                    val sideLeanAngle = if (kotlin.math.abs(euc.visualSideLean) > 1f) {
                        // Falling: interpret as direct angle contribution
                        // visualSideLean = baseLean + eucRoll/90, so eucRoll/90 part needs *90 to get degrees
                        val baseLean = euc.visualSideLean.coerceIn(-1f, 1f)
                        val fallContribution = euc.visualSideLean - baseLean
                        baseLean * 15f + fallContribution * 90f
                    } else {
                        euc.visualSideLean * 15f
                    }
                    tempMatrix.rotate(1f, 0f, 0f, forwardLeanAngle)
                    tempMatrix.rotate(0f, 0f, 1f, -sideLeanAngle)
                } else if (euc != null) {
                    // Check if this is an arm entity
                    val armTag = armTagMapper.get(entity)
                    if (armTag != null) {
                        // Arm rendering - attach to rider's shoulder with proper transforms
                        renderArm(tempMatrix, transform, armTag.isLeft)
                    } else {
                        // For rider: apply yaw rotation (negated to match visual direction)
                        tempMatrix.rotate(0f, 1f, 0f, -transform.yaw)

                        // Lean forward and side
                        // Rider leans dramatically (60°) at full gas/brake
                        val forwardLeanAngle = euc.visualForwardLean * 60f
                        // Rider leans into turns - more dramatic lean (25° at full turn)
                        val sideLeanAngle = euc.visualSideLean * 25f
                        tempMatrix.rotate(1f, 0f, 0f, forwardLeanAngle)
                        tempMatrix.rotate(0f, 0f, 1f, sideLeanAngle)
                    }
                } else if (armTagMapper.get(entity) != null) {
                    // Arm entity without EucComponent - still need to render
                    val armTag = armTagMapper.get(entity)
                    renderArm(tempMatrix, transform, armTag.isLeft)
                } else {
                    // For other entities: standard rotation
                    tempMatrix.rotate(transform.rotation)
                }

                tempMatrix.scale(transform.scale.x, transform.scale.y, transform.scale.z)

                activeModel.transform.set(tempMatrix)

                // Use SceneManager for PBR models with Scene, regular ModelBatch for others
                if (model.isPbr && model.scene != null) {
                    // Update scene's model instance transform
                    model.scene!!.modelInstance.transform.set(tempMatrix)
                    sceneManager.getRenderableProviders().add(model.scene!!)
                } else {
                    modelBatch.render(activeModel, environment)
                }
            }
        }

        // Render head separately with animation
        renderHead()

        // Render articulated pedestrians with walking animation (with frustum culling)
        pedestrianRenderer.render(modelBatch, environment, camera)

        // Render ragdoll if active (inside main render pass for correct lighting/post-processing)
        if (activeRagdollPhysics != null && activeRagdollRenderer != null && activeRagdollPhysics!!.isActive()) {
            // Make sure blending is disabled for opaque ragdoll rendering
            Gdx.gl.glDisable(GL20.GL_BLEND)
            activeRagdollRenderer!!.render(modelBatch, activeRagdollPhysics!!, environment)
        }

        // Render pedestrian ragdolls (separate from player ragdoll, active during gameplay)
        if (pedestrianRagdollPhysics != null && pedestrianRagdollRenderer != null) {
            val pedCount = pedestrianRagdollPhysics!!.getPedestrianCount()
            if (pedCount > 0) {
                Gdx.gl.glDisable(GL20.GL_BLEND)
                pedestrianRagdollRenderer!!.renderPedestrians(modelBatch, pedestrianRagdollPhysics!!, environment)
            }
        }

        modelBatch.end()

        // Render PBR models with SceneManager
        sceneManager.update(Gdx.graphics.deltaTime)
        sceneManager.render()
        // Clear renderableProviders for next frame
        sceneManager.getRenderableProviders().clear()

        // End post-processing (apply effects and render to screen)
        postProcessing.end()
    }

    /**
     * Render the rider's head with animation.
     * Head is attached to the rider's neck and can rotate independently.
     */
    private fun renderHead() {
        if (hideHead) return  // Skip during ragdoll
        val rider = riderEntity ?: return
        val head = headInstance ?: return
        val riderTransform = transformMapper.get(rider) ?: return
        val riderEuc = eucMapper.get(rider) ?: return
        val headComponent = headMapper.get(rider) ?: return

        // Neck position (top of torso)
        val neckY = headComponent.offsetY

        headMatrix.idt()

        // Start with rider's base position
        headMatrix.translate(riderTransform.position)

        // Apply rider's yaw (body facing direction)
        headMatrix.rotate(0f, 1f, 0f, -riderTransform.yaw)

        // Apply rider's lean (forward and side)
        val forwardLeanAngle = riderEuc.visualForwardLean * 60f
        // Rider leans into turns - more dramatic lean (25° at full turn)
        val sideLeanAngle = riderEuc.visualSideLean * 25f
        headMatrix.rotate(1f, 0f, 0f, forwardLeanAngle)
        headMatrix.rotate(0f, 0f, 1f, sideLeanAngle)

        // Translate to neck position (in leaned body space)
        headMatrix.translate(0f, neckY, 0f)

        // Apply head rotation (independent movement)
        // Yaw: turn head left/right
        headMatrix.rotate(0f, 1f, 0f, headComponent.yaw)
        // Pitch: nod up/down
        headMatrix.rotate(1f, 0f, 0f, headComponent.pitch)
        // Roll: tilt head side to side
        headMatrix.rotate(0f, 0f, 1f, headComponent.roll)

        head.transform.set(headMatrix)
        modelBatch.render(head, environment)
    }

    /**
     * Build transform matrix for arm rendering.
     * Arms are attached to rider's shoulders and follow rider's lean.
     * Note: matrix has already been reset with idt() but NOT translated yet.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun renderArm(matrix: Matrix4, armTransform: TransformComponent, isLeft: Boolean) {
        val rider = riderEntity ?: return
        val riderTransform = transformMapper.get(rider) ?: return
        val riderEuc = eucMapper.get(rider) ?: return
        val armComponent = armMapper.get(rider) ?: return

        // Shoulder offset in local rider space
        val shoulderX = armComponent.shoulderOffsetX * (if (isLeft) -1f else 1f)
        val shoulderY = armComponent.shoulderOffsetY
        val shoulderZ = armComponent.shoulderOffsetZ

        // Get arm angles
        val armPitch = if (isLeft) armComponent.leftArmPitch else armComponent.rightArmPitch
        val armYaw = if (isLeft) armComponent.leftArmYaw else armComponent.rightArmYaw

        // Reset matrix (idt already called, but let's be safe)
        matrix.idt()

        // Start with rider's base position
        matrix.translate(riderTransform.position)

        // Apply rider's yaw (body facing direction)
        matrix.rotate(0f, 1f, 0f, -riderTransform.yaw)

        // Apply rider's lean (forward and side)
        // Rider leans dramatically (60°) at full gas/brake
        val forwardLeanAngle = riderEuc.visualForwardLean * 60f
        // Rider leans into turns - more dramatic lean (25° at full turn)
        val sideLeanAngle = riderEuc.visualSideLean * 25f
        matrix.rotate(1f, 0f, 0f, forwardLeanAngle)
        matrix.rotate(0f, 0f, 1f, sideLeanAngle)

        // Translate to shoulder position (in leaned body space)
        matrix.translate(shoulderX, shoulderY, shoulderZ)

        // Apply arm rotation (relative to shoulder)
        // The arm model points DOWN (-Y) by default
        // Coordinate system after rider transforms:
        // X = left/right, Y = up/down, Z = forward/back

        // armPitch: swing forward/back (rotate around X axis)
        // Positive = arm swings forward, negative = backward
        // This is visible from behind camera
        matrix.rotate(1f, 0f, 0f, armPitch)

        // armYaw: lift arm outward to side (rotate around Z axis)
        // armYaw = 0 means arm pointing down, 90 = horizontal to side
        val liftDirection = if (isLeft) -1f else 1f
        matrix.rotate(0f, 0f, 1f, armYaw * liftDirection)
    }

    fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    /**
     * Render ragdoll physics bodies.
     * Call this after render() when ragdoll is active.
     */
    fun renderRagdoll(ragdollRenderer: com.eucleantoomuch.game.physics.RagdollRenderer,
                      ragdollPhysics: com.eucleantoomuch.game.physics.RagdollPhysics) {
        if (!ragdollPhysics.isActive()) return

        // Render ragdoll bodies with same environment as other models
        modelBatch.begin(camera)
        ragdollRenderer.render(modelBatch, ragdollPhysics, environment)
        modelBatch.end()
    }

    /**
     * Render ragdoll from recorded transforms (for replay).
     * Call this after render() when playing back recorded ragdoll frames.
     */
    fun renderRagdollFromTransforms(
        ragdollRenderer: com.eucleantoomuch.game.physics.RagdollRenderer,
        transforms: com.eucleantoomuch.game.replay.ReplayFrame.RagdollTransforms
    ) {
        modelBatch.begin(camera)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        ragdollRenderer.renderFromTransforms(modelBatch, transforms, environment)
        modelBatch.end()
    }

    override fun dispose() {
        modelBatch.dispose()
        sceneManager.dispose()
        postProcessing.dispose()
        pedestrianRenderer.dispose()
    }
}
