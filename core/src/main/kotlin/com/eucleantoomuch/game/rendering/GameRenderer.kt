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
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.ArmComponent
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.ecs.components.ModelComponent
import com.eucleantoomuch.game.ecs.components.PlayerComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent

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
    private val armMapper = ComponentMapper.getFor(ArmComponent::class.java)

    private val tempMatrix = Matrix4()

    // Sky color
    private val skyR = 0.5f
    private val skyG = 0.7f
    private val skyB = 0.9f

    // LOD distance threshold - buildings further than this use simple model
    private val lodDistance = 80f
    private val lodDistanceSq = lodDistance * lodDistance

    init {
        camera.near = 0.5f  // Increased from 0.1f to reduce z-fighting
        camera.far = 300f
        camera.update()
    }

    fun setCameraFar(distance: Float) {
        camera.far = distance + 50f  // Add buffer beyond render distance
        camera.update()
    }

    fun render() {
        // Clear screen with sky color
        Gdx.gl.glClearColor(skyR, skyG, skyB, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_CULL_FACE)

        modelBatch.begin(camera)

        // Render all entities with ModelComponent
        for (entity in engine.getEntitiesFor(Families.renderable)) {
            val model = modelMapper.get(entity)
            val transform = transformMapper.get(entity)

            if (model.visible && model.modelInstance != null) {
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
                    // Check if this is an arm
                    val arm = armMapper.get(entity)
                    if (arm != null) {
                        // For arm: apply yaw rotation (negated to match visual direction)
                        tempMatrix.rotate(0f, 1f, 0f, -transform.yaw)

                        // Lean forward and side
                        val forwardLeanAngle = euc.visualForwardLean * 20f
                        val sideLeanAngle = euc.visualSideLean * 15f
                        tempMatrix.rotate(1f, 0f, 0f, forwardLeanAngle)
                        tempMatrix.rotate(0f, 0f, 1f, sideLeanAngle)

                        // Rotate arm around Z axis (spread out or down) + wave offset
                        val armRotation = if (arm.isLeftArm) -arm.armAngle else arm.armAngle
                        val totalRotation = armRotation + arm.waveOffset
                        tempMatrix.rotate(0f, 0f, 1f, totalRotation)
                    } else {
                        // For rider: apply yaw rotation (negated to match visual direction)
                        tempMatrix.rotate(0f, 1f, 0f, -transform.yaw)

                        // Lean forward and side
                        val forwardLeanAngle = euc.visualForwardLean * 20f
                        val sideLeanAngle = euc.visualSideLean * 15f
                        tempMatrix.rotate(1f, 0f, 0f, forwardLeanAngle)
                        tempMatrix.rotate(0f, 0f, 1f, sideLeanAngle)
                    }
                } else {
                    // For other entities: standard rotation
                    tempMatrix.rotate(transform.rotation)
                }

                tempMatrix.scale(transform.scale.x, transform.scale.y, transform.scale.z)

                activeModel.transform.set(tempMatrix)
                modelBatch.render(activeModel, environment)
            }
        }

        modelBatch.end()
    }

    fun resize(width: Int, height: Int) {
        camera.viewportWidth = width.toFloat()
        camera.viewportHeight = height.toFloat()
        camera.update()
    }

    override fun dispose() {
        modelBatch.dispose()
    }
}
