package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.PedestrianComponent
import com.eucleantoomuch.game.ecs.components.PedestrianState
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.components.VelocityComponent

class PedestrianAISystem : IteratingSystem(Families.pedestrians, 3) {
    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val velocityMapper = ComponentMapper.getFor(VelocityComponent::class.java)
    private val pedestrianMapper = ComponentMapper.getFor(PedestrianComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val transform = transformMapper.get(entity)
        val velocity = velocityMapper.get(entity)
        val pedestrian = pedestrianMapper.get(entity)

        when (pedestrian.state) {
            PedestrianState.WALKING, PedestrianState.CROSSING -> {
                // Move in direction
                velocity.linear.set(pedestrian.direction).scl(pedestrian.walkSpeed)

                // Check bounds and reverse direction
                if (transform.position.x < pedestrian.minX) {
                    pedestrian.direction.x = 1f
                    transform.yaw = 90f
                } else if (transform.position.x > pedestrian.maxX) {
                    pedestrian.direction.x = -1f
                    transform.yaw = -90f
                }

                transform.updateRotationFromYaw()
            }
            PedestrianState.STANDING -> {
                velocity.linear.setZero()
            }
        }
    }
}
