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
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.ecs.components.ModelComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent

class GameRenderer(private val engine: Engine) : Disposable {
    private val modelBatch = ModelBatch()
    val camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    val cameraController = CameraController(camera)

    private val environment = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f))
        add(DirectionalLight().set(0.9f, 0.9f, 0.9f, -0.5f, -1f, -0.3f))
    }

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val modelMapper = ComponentMapper.getFor(ModelComponent::class.java)
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)

    private val tempMatrix = Matrix4()

    // Sky color
    private val skyR = 0.5f
    private val skyG = 0.7f
    private val skyB = 0.9f

    init {
        camera.near = 0.1f
        camera.far = 300f
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
                // Build transform matrix
                tempMatrix.idt()
                tempMatrix.translate(transform.position)
                tempMatrix.rotate(transform.rotation)

                // Apply visual lean for EUC and rider
                val euc = eucMapper.get(entity)
                if (euc != null) {
                    // Lean forward (around X axis) and side (around Z axis)
                    val forwardLeanAngle = euc.visualForwardLean * 20f  // Max 20 degrees visual
                    val sideLeanAngle = euc.visualSideLean * 15f        // Max 15 degrees visual

                    tempMatrix.rotate(1f, 0f, 0f, forwardLeanAngle)
                    tempMatrix.rotate(0f, 0f, 1f, -sideLeanAngle)
                }

                tempMatrix.scale(transform.scale.x, transform.scale.y, transform.scale.z)

                model.modelInstance!!.transform.set(tempMatrix)
                modelBatch.render(model.modelInstance, environment)
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
