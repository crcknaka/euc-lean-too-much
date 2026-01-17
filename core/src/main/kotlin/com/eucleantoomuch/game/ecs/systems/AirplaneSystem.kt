package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.*
import com.eucleantoomuch.game.rendering.ProceduralModels

/**
 * System that manages airplane flight and contrail generation
 */
class AirplaneSystem(
    private val models: ProceduralModels
) : EntitySystem(3) {

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val airplaneMapper = ComponentMapper.getFor(AirplaneComponent::class.java)
    private val contrailMapper = ComponentMapper.getFor(ContrailSegmentComponent::class.java)
    private val modelMapper = ComponentMapper.getFor(ModelComponent::class.java)

    // Pre-created contrail model
    private var contrailModel: ModelInstance? = null

    private val entitiesToRemove = mutableListOf<Entity>()

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        // Create contrail model once
        contrailModel = ModelInstance(models.createContrailSegmentModel(0.6f))
    }

    override fun update(deltaTime: Float) {
        entitiesToRemove.clear()

        // Update airplanes
        val airplanes = engine.getEntitiesFor(Families.airplanes)
        for (entity in airplanes) {
            updateAirplane(entity, deltaTime)
        }

        // Update contrail segments (fade and remove old ones)
        val contrails = engine.getEntitiesFor(Families.contrailSegments)
        for (entity in contrails) {
            updateContrailSegment(entity, deltaTime)
        }

        // Remove marked entities
        for (entity in entitiesToRemove) {
            engine.removeEntity(entity)
        }
    }

    private fun updateAirplane(entity: Entity, deltaTime: Float) {
        val transform = transformMapper.get(entity)
        val airplane = airplaneMapper.get(entity)

        // Update lifetime
        airplane.lifetime += deltaTime
        if (airplane.lifetime >= airplane.maxLifetime) {
            entitiesToRemove.add(entity)
            return
        }

        // Move airplane along its direction
        val moveDistance = airplane.speed * deltaTime
        transform.position.x += airplane.direction.x * moveDistance
        transform.position.z += airplane.direction.z * moveDistance

        // Generate contrail segments
        if (airplane.contrailEnabled) {
            airplane.timeSinceLastSegment += deltaTime
            if (airplane.timeSinceLastSegment >= airplane.contrailSpacing) {
                airplane.timeSinceLastSegment = 0f
                spawnContrailSegment(transform.position.x, airplane.altitude, transform.position.z, transform.yaw)
            }
        }
    }

    private fun spawnContrailSegment(x: Float, y: Float, z: Float, yaw: Float) {
        val contrailModelInstance = contrailModel ?: return

        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, y, z)
            this.yaw = yaw
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(contrailModelInstance.model)
        })

        entity.add(ContrailSegmentComponent().apply {
            age = 0f
            maxAge = 12f + (Math.random() * 6f).toFloat()  // 12-18 seconds lifetime
            initialAlpha = 0.5f + (Math.random() * 0.2f).toFloat()
        })

        engine.addEntity(entity)
    }

    private fun updateContrailSegment(entity: Entity, deltaTime: Float) {
        val contrail = contrailMapper.get(entity)

        contrail.age += deltaTime

        // Remove if fully faded
        if (contrail.age >= contrail.maxAge) {
            entitiesToRemove.add(entity)
            return
        }

        // Update model opacity based on age
        val model = modelMapper.get(entity)
        val alpha = contrail.getCurrentAlpha()

        // Scale the contrail slightly as it ages (disperses)
        val transform = transformMapper.get(entity)
        val expansionFactor = 1f + (contrail.age / contrail.maxAge) * 0.5f
        transform.scale.set(expansionFactor, expansionFactor, 1f)
    }
}
