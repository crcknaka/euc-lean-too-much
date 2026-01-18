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

            // Default cull: entities that have gone too far behind the player
            val tooFarBehind = transform.position.z < playerZ + cullDistance

            // Oncoming cars (direction = -1) that have passed the player and gone too far behind
            // should also be culled - they move in negative Z direction so check if they're far ahead
            val oncomingCarPassedPlayer = car != null && car.direction == -1 &&
                              transform.position.z > playerZ - cullDistance

            if (tooFarBehind || oncomingCarPassedPlayer) {
                toRemove.add(entity)
            }
        }

        // Remove culled entities
        toRemove.forEach { engine.removeEntity(it) }
    }
}
