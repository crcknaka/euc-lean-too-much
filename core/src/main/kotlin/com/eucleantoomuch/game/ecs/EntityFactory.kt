package com.eucleantoomuch.game.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.eucleantoomuch.game.ecs.components.*
import com.eucleantoomuch.game.rendering.ProceduralModels
import com.eucleantoomuch.game.util.Constants

class EntityFactory(
    private val engine: Engine,
    private val models: ProceduralModels
) {
    private val eucModel by lazy { models.createEucModel() }
    private val riderModel by lazy { models.createRiderModel() }

    fun createPlayer(): Entity {
        val entity = engine.createEntity()

        // Transform - start at origin
        entity.add(TransformComponent().apply {
            position.set(0f, 0f, 0f)
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
            modelInstance = ModelInstance(eucModel)
        })

        // Player marker
        entity.add(PlayerComponent())

        // EUC state
        entity.add(EucComponent())

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
}
