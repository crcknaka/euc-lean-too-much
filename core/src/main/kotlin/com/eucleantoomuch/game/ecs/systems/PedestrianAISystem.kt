package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.PedestrianComponent
import com.eucleantoomuch.game.ecs.components.PedestrianState
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.components.VelocityComponent
import com.eucleantoomuch.game.util.Constants

class PedestrianAISystem : IteratingSystem(Families.pedestrians, 3) {
    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val velocityMapper = ComponentMapper.getFor(VelocityComponent::class.java)
    private val pedestrianMapper = ComponentMapper.getFor(PedestrianComponent::class.java)

    /** Debug flag to freeze all AI movement */
    var frozen = false

    override fun processEntity(entity: Entity, deltaTime: Float) {
        if (frozen) return  // Skip AI processing when frozen
        val transform = transformMapper.get(entity)
        val velocity = velocityMapper.get(entity)
        val pedestrian = pedestrianMapper.get(entity)

        // Update state timer
        pedestrian.stateTimer += deltaTime

        // Update walking animation phase when moving
        val isWalking = pedestrian.state == PedestrianState.WALKING ||
                        pedestrian.state == PedestrianState.CROSSING ||
                        pedestrian.state == PedestrianState.WALKING_TO_CROSSING
        if (isWalking && !pedestrian.isRagdolling) {
            // Animation speed scales with walk speed
            pedestrian.walkAnimPhase += deltaTime * pedestrian.walkAnimSpeed * (pedestrian.walkSpeed / 1.5f)
            if (pedestrian.walkAnimPhase > 6.28318f) {  // 2 * PI
                pedestrian.walkAnimPhase -= 6.28318f
            }
        }

        if (pedestrian.isSidewalkPedestrian) {
            processSidewalkPedestrian(entity, transform, velocity, pedestrian, deltaTime)
        } else {
            processCrossingPedestrian(transform, velocity, pedestrian)
        }
    }

    private fun processCrossingPedestrian(
        transform: TransformComponent,
        velocity: VelocityComponent,
        pedestrian: PedestrianComponent
    ) {
        when (pedestrian.state) {
            PedestrianState.WALKING_TO_CROSSING -> {
                // Walk along sidewalk (Z axis) towards the zebra crossing
                val distanceToZebra = pedestrian.targetCrossingZ - transform.position.z
                val reachedZebra = kotlin.math.abs(distanceToZebra) < 0.5f

                if (reachedZebra) {
                    // Start crossing the road - turn to face crossing direction
                    pedestrian.state = PedestrianState.CROSSING
                    pedestrian.stateTimer = 0f
                    // Turn 90 degrees to face across the road
                    // yaw -90 = moving +X (right), yaw 90 = moving -X (left)
                    transform.yaw = if (pedestrian.crossingDirectionX > 0) -90f else 90f
                    transform.updateRotationFromYaw()
                    return
                }

                // Walk towards zebra crossing along Z
                val walkDirZ = if (distanceToZebra > 0) 1f else -1f
                velocity.linear.set(0f, 0f, pedestrian.walkSpeed)

                // Face walking direction along Z
                transform.yaw = if (walkDirZ > 0) 0f else 180f
                transform.updateRotationFromYaw()
            }

            PedestrianState.CROSSING -> {
                // Check if pedestrian has reached the other side of the road
                val sidewalkEdge = Constants.ROAD_WIDTH / 2 + Constants.SIDEWALK_WIDTH * 0.5f
                val reachedOtherSide = if (pedestrian.crossingDirectionX > 0) {
                    transform.position.x >= sidewalkEdge
                } else {
                    transform.position.x <= -sidewalkEdge
                }

                if (reachedOtherSide) {
                    // Convert to sidewalk pedestrian - walk along sidewalk
                    pedestrian.isSidewalkPedestrian = true
                    pedestrian.state = PedestrianState.WALKING
                    pedestrian.walkDirectionZ = if (MathUtils.randomBoolean()) 1f else -1f
                    pedestrian.stateTimer = 0f
                    pedestrian.nextStateChange = MathUtils.random(5f, 15f)
                    return
                }

                // Cross the road (move along X axis)
                // MovementSystem rotates velocity by yaw: yaw -90 moves +X, yaw 90 moves -X
                velocity.linear.set(0f, 0f, pedestrian.walkSpeed)

                // Face crossing direction: yaw -90 = moving +X (right), yaw 90 = moving -X (left)
                transform.yaw = if (pedestrian.crossingDirectionX > 0) -90f else 90f
                transform.updateRotationFromYaw()
            }

            PedestrianState.WALKING -> {
                // Legacy case - treat as crossing
                pedestrian.state = PedestrianState.CROSSING
            }

            PedestrianState.STANDING, PedestrianState.CHATTING -> {
                velocity.linear.setZero()
            }

            PedestrianState.FALLING -> {
                // Ragdoll physics controls this pedestrian, don't update AI
                velocity.linear.setZero()
            }
        }
    }

    private fun processSidewalkPedestrian(
        entity: Entity,
        transform: TransformComponent,
        velocity: VelocityComponent,
        pedestrian: PedestrianComponent,
        deltaTime: Float
    ) {
        when (pedestrian.state) {
            PedestrianState.WALKING -> {
                // Walk along Z axis (along the sidewalk)
                velocity.linear.set(0f, 0f, pedestrian.walkDirectionZ * pedestrian.walkSpeed)

                // Face walking direction
                transform.yaw = if (pedestrian.walkDirectionZ > 0) 0f else 180f
                transform.updateRotationFromYaw()

                // Random behavior changes
                if (pedestrian.stateTimer > pedestrian.nextStateChange) {
                    val roll = MathUtils.random()
                    when {
                        // 15% chance to stop and stand
                        roll < 0.15f -> {
                            pedestrian.state = PedestrianState.STANDING
                            pedestrian.standDuration = MathUtils.random(2f, 6f)
                            pedestrian.stateTimer = 0f
                        }
                        // 10% chance to turn around
                        roll < 0.25f -> {
                            pedestrian.walkDirectionZ *= -1f
                            pedestrian.stateTimer = 0f
                            pedestrian.nextStateChange = MathUtils.random(4f, 10f)
                        }
                        // 8% chance to change speed
                        roll < 0.33f -> {
                            pedestrian.walkSpeed = MathUtils.random(0.8f, 2.2f)
                            pedestrian.stateTimer = 0f
                            pedestrian.nextStateChange = MathUtils.random(3f, 8f)
                        }
                        else -> {
                            // Keep walking, reset timer
                            pedestrian.stateTimer = 0f
                            pedestrian.nextStateChange = MathUtils.random(3f, 8f)
                        }
                    }
                }
            }

            PedestrianState.STANDING -> {
                velocity.linear.setZero()

                // After standing, start walking again
                if (pedestrian.stateTimer > pedestrian.standDuration) {
                    pedestrian.state = PedestrianState.WALKING
                    pedestrian.stateTimer = 0f
                    pedestrian.nextStateChange = MathUtils.random(4f, 10f)

                    // Maybe change direction after standing
                    if (MathUtils.random() < 0.3f) {
                        pedestrian.walkDirectionZ *= -1f
                    }
                    // Maybe change speed
                    pedestrian.walkSpeed = MathUtils.random(0.8f, 2.0f)
                }
            }

            PedestrianState.CHATTING -> {
                velocity.linear.setZero()

                // After chatting, start walking again
                if (pedestrian.stateTimer > pedestrian.chatDuration) {
                    pedestrian.state = PedestrianState.WALKING
                    pedestrian.stateTimer = 0f
                    pedestrian.nextStateChange = MathUtils.random(5f, 12f)
                    pedestrian.chatPartnerId = -1

                    // Random direction after chat
                    if (MathUtils.random() < 0.5f) {
                        pedestrian.walkDirectionZ *= -1f
                    }
                }
            }

            PedestrianState.CROSSING, PedestrianState.WALKING_TO_CROSSING -> {
                // Sidewalk pedestrians don't cross, treat as walking
                pedestrian.state = PedestrianState.WALKING
            }

            PedestrianState.FALLING -> {
                // Ragdoll physics controls this pedestrian, don't update AI
                velocity.linear.setZero()
            }
        }
    }
}
