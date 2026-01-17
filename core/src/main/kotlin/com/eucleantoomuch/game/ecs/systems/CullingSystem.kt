package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.utils.ImmutableArray
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.CarComponent
import com.eucleantoomuch.game.ecs.components.ObstacleComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.util.Constants

class CullingSystem : EntitySystem(7) {
    private lateinit var playerEntities: ImmutableArray<Entity>
    private lateinit var obstacleEntities: ImmutableArray<Entity>

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val carMapper = ComponentMapper.getFor(CarComponent::class.java)

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
            val car = carMapper.get(entity)

            // Cars coming towards player (direction = -1) should also be culled when far behind
            // Cars going same direction (direction = 1) cull normally
            // For oncoming cars, also cull if they've gone too far behind the player
            val shouldCull = transform.position.z < playerZ + cullDistance

            // Also cull cars that have gone too far ahead (oncoming cars that passed)
            val tooFarAhead = car != null && car.direction == -1 &&
                              transform.position.z < playerZ + cullDistance

            if (shouldCull || tooFarAhead) {
                toRemove.add(entity)
            }
        }

        // Remove culled entities
        toRemove.forEach { engine.removeEntity(it) }
    }
}
