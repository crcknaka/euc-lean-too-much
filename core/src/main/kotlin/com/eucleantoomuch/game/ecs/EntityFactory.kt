package com.eucleantoomuch.game.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.eucleantoomuch.game.ecs.components.*
import com.eucleantoomuch.game.model.WheelType
import com.eucleantoomuch.game.rendering.ProceduralModels
import com.eucleantoomuch.game.util.Constants

class EntityFactory(
    private val engine: Engine,
    private val models: ProceduralModels
) {
    private val eucModel by lazy { models.createEucModel() }
    private val riderModel by lazy { models.createRiderModel() }
    private val armModel by lazy { models.createArmModel() }

    fun createPlayer(wheelType: WheelType = WheelType.Standard): Entity {
        val entity = engine.createEntity()

        // Create model instance first - this triggers lazy init and sets eucModelScale
        val eucModelInstance = ModelInstance(eucModel)

        // Now read the scale (after lazy init has set it)
        val modelScale = models.eucModelScale

        // Transform - start at origin with model scale
        entity.add(TransformComponent().apply {
            position.set(0f, 0f, 0f)
            scale.set(modelScale, modelScale, modelScale)
        })

        // Velocity
        entity.add(VelocityComponent())

        // Collider
        entity.add(ColliderComponent().apply {
            setSize(0.5f, 1.8f, 0.5f)  // Player hitbox
            collisionGroup = CollisionGroups.PLAYER
            collisionMask = CollisionGroups.OBSTACLE
        })

        // Model (EUC + Rider combined conceptually, we'll render separately)
        entity.add(ModelComponent().apply {
            modelInstance = eucModelInstance
        })

        // Player marker
        entity.add(PlayerComponent())

        // EUC state with wheel-specific physics
        entity.add(EucComponent().apply {
            maxSpeed = wheelType.maxSpeed
            criticalLean = wheelType.criticalLean
            acceleration = wheelType.acceleration
            deceleration = wheelType.deceleration
            pwmSensitivity = wheelType.pwmSensitivity
            turnResponsiveness = wheelType.turnResponsiveness
        })

        engine.addEntity(entity)
        return entity
    }

    fun createRiderModel(): Entity {
        // Create a separate entity for the rider model that follows the player
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(0f, 0.2f, 0f)  // Slightly above EUC
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(riderModel)
        })

        // Add EUC component so rider leans with EUC
        entity.add(EucComponent())

        engine.addEntity(entity)
        return entity
    }

    fun createArm(isLeft: Boolean): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent())

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(armModel)
        })

        entity.add(ArmComponent().apply {
            isLeftArm = isLeft
        })

        // Add EUC component so arm leans with rider
        entity.add(EucComponent())

        engine.addEntity(entity)
        return entity
    }
}
