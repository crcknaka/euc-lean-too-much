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
    private lateinit var powerupEntities: ImmutableArray<Entity>

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val colliderMapper = ComponentMapper.getFor(ColliderComponent::class.java)
    private val obstacleMapper = ComponentMapper.getFor(ObstacleComponent::class.java)
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)
    private val playerMapper = ComponentMapper.getFor(PlayerComponent::class.java)
    private val pedestrianMapper = ComponentMapper.getFor(PedestrianComponent::class.java)
    private val powerupMapper = ComponentMapper.getFor(PowerupComponent::class.java)

    var onCollision: ((ObstacleType, Boolean) -> Unit)? = null  // Type, causesGameOver
    var onNearMiss: (() -> Unit)? = null  // Called when player passes close to pedestrian
    var onPedestrianHit: ((Entity) -> Unit)? = null  // Called when player hits a pedestrian
    var onKnockableHit: ((Entity) -> Unit)? = null  // Called when player hits a knockable object (trash can)
    var onPowerupCollected: ((PowerupComponent) -> Unit)? = null  // Called when player collects a powerup

    // Near miss tracking - distance threshold for "close call"
    private val nearMissThresholdPedestrian = 1.2f  // Distance in meters for pedestrian near miss
    private val nearMissThresholdCar = 2.8f  // Distance in meters for car near miss (wider, cars are big)

    override fun addedToEngine(engine: Engine) {
        playerEntities = engine.getEntitiesFor(Families.player)
        obstacleEntities = engine.getEntitiesFor(Families.obstacles)
        powerupEntities = engine.getEntitiesFor(Families.powerups)
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

            // Quick Z distance check FIRST (before getting other components)
            val zDist = obstacleTransform.position.z - playerZ
            if (zDist > collisionCheckRange || zDist < -5f) {
                // Mark as passed if player is well ahead (only if needed)
                if (zDist < -2f) {
                    val obstacleComponent = obstacleMapper.get(obstacleEntity)
                    if (!obstacleComponent.hasBeenPassed) {
                        obstacleComponent.hasBeenPassed = true
                    }
                }
                continue
            }

            val obstacleComponent = obstacleMapper.get(obstacleEntity)
            if (obstacleComponent.hasBeenPassed) continue

            // Skip pedestrians that are already falling (ragdolling)
            // Only call pedestrianMapper if type is PEDESTRIAN (avoid unnecessary lookup)
            if (obstacleComponent.type == ObstacleType.PEDESTRIAN) {
                val pedestrianComponent = pedestrianMapper.get(obstacleEntity)
                if (pedestrianComponent?.isRagdolling == true) continue
            }

            val obstacleCollider = colliderMapper.get(obstacleEntity)

            // Update obstacle bounds
            obstacleCollider.updateBounds(obstacleTransform.position)

            // Simple AABB collision check
            if (checkAABBCollision(playerCollider, obstacleCollider)) {
                handleCollision(playerComponent, eucComponent, obstacleComponent, obstacleEntity)
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

        // Check collision with powerups
        checkPowerupCollisions(playerTransform, playerCollider)
    }

    private fun checkPowerupCollisions(
        playerTransform: TransformComponent,
        playerCollider: ColliderComponent
    ) {
        val playerZ = playerTransform.position.z
        val playerX = playerTransform.position.x
        val entitiesToRemove = mutableListOf<Entity>()

        for (powerupEntity in powerupEntities) {
            val powerupTransform = transformMapper.get(powerupEntity) ?: continue
            val powerupComponent = powerupMapper.get(powerupEntity) ?: continue

            // Skip already collected
            if (powerupComponent.isCollected) continue

            // Quick Z distance check
            val zDist = powerupTransform.position.z - playerZ
            if (zDist > 10f || zDist < -5f) continue

            val powerupCollider = colliderMapper.get(powerupEntity) ?: continue
            powerupCollider.updateBounds(powerupTransform.position)

            // Use simpler distance-based collision for powerups (more forgiving pickup radius)
            val xDist = abs(playerX - powerupTransform.position.x)
            val zDistAbs = abs(zDist)
            val pickupRadius = 1.5f  // Generous pickup radius

            if (xDist < pickupRadius && zDistAbs < pickupRadius) {
                powerupComponent.isCollected = true
                onPowerupCollected?.invoke(powerupComponent)
                entitiesToRemove.add(powerupEntity)
            }
        }

        // Remove collected powerups
        for (entity in entitiesToRemove) {
            engine.removeEntity(entity)
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
        obstacle: ObstacleComponent,
        obstacleEntity: Entity
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
            ObstacleType.PEDESTRIAN -> {
                // Pedestrian collision - pedestrian falls AND player falls too
                onPedestrianHit?.invoke(obstacleEntity)
                // Mark as passed so we don't collide again
                obstacle.hasBeenPassed = true
                // Player also falls when hitting pedestrian
                player.isAlive = false
                onCollision?.invoke(obstacle.type, true)
            }
            else -> {
                // Check if knockable (e.g., trash can)
                if (obstacle.isKnockable && !obstacle.isKnockedOver) {
                    obstacle.isKnockedOver = true
                    obstacle.hasBeenPassed = true
                    onKnockableHit?.invoke(obstacleEntity)
                    onCollision?.invoke(obstacle.type, false)
                } else if (obstacle.causesGameOver) {
                    // Non-knockable obstacles cause game over
                    player.isAlive = false
                    onCollision?.invoke(obstacle.type, true)
                }
            }
        }
    }
}
