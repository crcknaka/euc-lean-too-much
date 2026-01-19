package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.CarComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.components.VelocityComponent

class CarAISystem : IteratingSystem(Families.cars, 4) {
    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val velocityMapper = ComponentMapper.getFor(VelocityComponent::class.java)
    private val carMapper = ComponentMapper.getFor(CarComponent::class.java)

    /** Debug flag to freeze all AI movement */
    var frozen = false

    override fun processEntity(entity: Entity, deltaTime: Float) {
        if (frozen) return  // Skip AI processing when frozen

        val velocity = velocityMapper.get(entity)
        val car = carMapper.get(entity)

        // Cars always move forward in their local space (positive Z)
        // The yaw rotation (0 or 180) determines world direction
        // MovementSystem will rotate this by yaw to get world movement
        velocity.linear.set(0f, 0f, car.speed)
    }
}
