package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.*
import com.eucleantoomuch.game.util.Constants
import kotlin.math.abs

class CollisionSystem : EntitySystem(5) {
    private lateinit var playerEntities: ImmutableArray<Entity>
    private lateinit var obstacleEntities: ImmutableArray<Entity>

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val colliderMapper = ComponentMapper.getFor(ColliderComponent::class.java)
    private val obstacleMapper = ComponentMapper.getFor(ObstacleComponent::class.java)
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)
    private val playerMapper = ComponentMapper.getFor(PlayerComponent::class.java)

    var onCollision: ((ObstacleType, Boolean) -> Unit)? = null  // Type, causesGameOver

    override fun addedToEngine(engine: Engine) {
        playerEntities = engine.getEntitiesFor(Families.player)
        obstacleEntities = engine.getEntitiesFor(Families.obstacles)
    }

    override fun update(deltaTime: Float) {
        if (playerEntities.size() == 0) return

        val playerEntity = playerEntities.first()
        val playerTransform = transformMapper.get(playerEntity)
        val playerCollider = colliderMapper.get(playerEntity) ?: return
        val playerComponent = playerMapper.get(playerEntity)
        val eucComponent = eucMapper.get(playerEntity)

        if (!playerComponent.isAlive) return

        // Update player collider bounds
        playerCollider.updateBounds(playerTransform.position)

        // Check collision with all obstacles
        for (obstacleEntity in obstacleEntities) {
            val obstacleTransform = transformMapper.get(obstacleEntity)
            val obstacleCollider = colliderMapper.get(obstacleEntity)
            val obstacleComponent = obstacleMapper.get(obstacleEntity)

            if (obstacleComponent.hasBeenPassed) continue

            // Update obstacle bounds
            obstacleCollider.updateBounds(obstacleTransform.position)

            // Simple AABB collision check
            if (checkAABBCollision(playerCollider, obstacleCollider)) {
                handleCollision(playerComponent, eucComponent, obstacleComponent)
            }

            // Mark as passed if player is ahead (for scoring)
            if (playerTransform.position.z > obstacleTransform.position.z + 2f) {
                obstacleComponent.hasBeenPassed = true
            }
        }
    }

    private fun checkAABBCollision(a: ColliderComponent, b: ColliderComponent): Boolean {
        return a.bounds.min.x <= b.bounds.max.x && a.bounds.max.x >= b.bounds.min.x &&
               a.bounds.min.y <= b.bounds.max.y && a.bounds.max.y >= b.bounds.min.y &&
               a.bounds.min.z <= b.bounds.max.z && a.bounds.max.z >= b.bounds.min.z
    }

    private fun handleCollision(
        player: PlayerComponent,
        euc: EucComponent,
        obstacle: ObstacleComponent
    ) {
        when (obstacle.type) {
            ObstacleType.PUDDLE -> {
                // Puddle causes control loss but not game over
                if (!euc.inPuddle) {
                    euc.applyPuddleEffect(Constants.PUDDLE_DURATION)
                    onCollision?.invoke(ObstacleType.PUDDLE, false)
                }
            }
            else -> {
                // All other obstacles cause game over
                if (obstacle.causesGameOver) {
                    player.isAlive = false
                    onCollision?.invoke(obstacle.type, true)
                }
            }
        }
    }
}
