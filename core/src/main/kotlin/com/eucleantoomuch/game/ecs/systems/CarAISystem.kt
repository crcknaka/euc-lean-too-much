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

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val velocity = velocityMapper.get(entity)
        val car = carMapper.get(entity)

        // Cars move in Z direction based on their direction
        // direction = 1: same as player (positive Z)
        // direction = -1: opposite (negative Z, but we handle this in world gen)
        velocity.linear.set(0f, 0f, car.speed * car.direction)
    }
}
