package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.util.Constants

class CullingSystem : EntitySystem(7) {
    private lateinit var playerEntities: ImmutableArray<Entity>
    private lateinit var obstacleEntities: ImmutableArray<Entity>

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)

    override fun addedToEngine(engine: Engine) {
        playerEntities = engine.getEntitiesFor(Families.player)
        obstacleEntities = engine.getEntitiesFor(Families.obstacles)
    }

    override fun update(deltaTime: Float) {
        if (playerEntities.size() == 0) return

        val playerTransform = transformMapper.get(playerEntities.first())
        val playerZ = playerTransform.position.z
        val cullDistance = Constants.DESPAWN_DISTANCE

        // Find entities to remove (can't modify during iteration)
        val toRemove = mutableListOf<Entity>()

        for (entity in obstacleEntities) {
            val transform = transformMapper.get(entity)

            // Cull entities that have gone too far behind the player
            // cullDistance is negative (-30), so this checks if entity Z < playerZ - 30
            // This works for all entities including oncoming cars (they pass player then go behind)
            if (transform.position.z < playerZ + cullDistance) {
                toRemove.add(entity)
            }
        }

        // Remove culled entities
        toRemove.forEach { engine.removeEntity(it) }
    }
}
