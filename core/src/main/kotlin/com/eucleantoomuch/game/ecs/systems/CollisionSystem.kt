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
    var onNearMiss: (() -> Unit)? = null  // Called when player passes close to pedestrian

    // Near miss tracking - distance threshold for "close call"
    private val nearMissThresholdPedestrian = 1.2f  // Distance in meters for pedestrian near miss
    private val nearMissThresholdCar = 2.8f  // Distance in meters for car near miss (wider, cars are big)

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

        // Check collision with nearby obstacles only
        val playerZ = playerTransform.position.z
        val collisionCheckRange = 10f  // Only check obstacles within this Z distance

        for (obstacleEntity in obstacleEntities) {
            val obstacleTransform = transformMapper.get(obstacleEntity)
            val obstacleComponent = obstacleMapper.get(obstacleEntity)

            if (obstacleComponent.hasBeenPassed) continue

            // Skip obstacles too far away (quick Z distance check)
            val zDist = obstacleTransform.position.z - playerZ
            if (zDist > collisionCheckRange || zDist < -5f) {
                // Mark as passed if player is well ahead
                if (zDist < -2f) {
                    obstacleComponent.hasBeenPassed = true
                }
                continue
            }

            val obstacleCollider = colliderMapper.get(obstacleEntity)

            // Update obstacle bounds
            obstacleCollider.updateBounds(obstacleTransform.position)

            // Simple AABB collision check
            if (checkAABBCollision(playerCollider, obstacleCollider)) {
                handleCollision(playerComponent, eucComponent, obstacleComponent)
            } else if (!obstacleComponent.nearMissTriggered) {
                // Near miss detection for pedestrians and cars
                val nearMissThreshold = when (obstacleComponent.type) {
                    ObstacleType.PEDESTRIAN -> nearMissThresholdPedestrian
                    ObstacleType.CAR -> nearMissThresholdCar
                    else -> null
                }

                if (nearMissThreshold != null) {
                    // Check if player just passed the obstacle (Z behind player) and was close in X
                    if (zDist < 0f && zDist > -2f) {
                        val xDist = abs(playerTransform.position.x - obstacleTransform.position.x)
                        if (xDist < nearMissThreshold) {
                            obstacleComponent.nearMissTriggered = true
                            onNearMiss?.invoke()
                        }
                    }
                }
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
            ObstacleType.MANHOLE -> {
                // Manhole causes wobble effect instead of instant game over
                if (!euc.isWobbling) {
                    euc.applyWobbleEffect(0.6f)
                    onCollision?.invoke(ObstacleType.MANHOLE, false)
                }
            }
            ObstacleType.POTHOLE -> {
                // Pothole causes full wobble - need to slow down to recover
                if (!euc.isWobbling) {
                    euc.applyWobbleEffect(0.7f)
                    onCollision?.invoke(ObstacleType.POTHOLE, false)
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
