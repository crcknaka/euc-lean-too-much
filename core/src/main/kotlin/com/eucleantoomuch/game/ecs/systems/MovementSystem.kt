package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.PlayerComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.components.VelocityComponent

class MovementSystem : IteratingSystem(Families.movable, 2) {
    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val velocityMapper = ComponentMapper.getFor(VelocityComponent::class.java)
    private val playerMapper = ComponentMapper.getFor(PlayerComponent::class.java)

    private val tempMovement = Vector3()

    companion object {
        // Maximum turn angle for player (can't turn more than 75 degrees left or right)
        const val MAX_PLAYER_YAW = 75f
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val transform = transformMapper.get(entity)
        val velocity = velocityMapper.get(entity)

        // Apply angular velocity (Y-axis rotation for turning)
        if (velocity.angular.y != 0f) {
            transform.yaw += velocity.angular.y * deltaTime

            // For player entity, limit yaw to prevent turning around
            val player = playerMapper.get(entity)
            if (player != null) {
                transform.yaw = transform.yaw.coerceIn(-MAX_PLAYER_YAW, MAX_PLAYER_YAW)
            } else {
                // Keep yaw in reasonable range for other entities
                if (transform.yaw > 360f) transform.yaw -= 360f
                if (transform.yaw < -360f) transform.yaw += 360f
            }
            transform.updateRotationFromYaw()
        }

        // Apply linear velocity in local space (rotated by yaw)
        if (!velocity.linear.isZero) {
            tempMovement.set(velocity.linear).scl(deltaTime)

            // Rotate movement by yaw angle
            val cos = MathUtils.cosDeg(transform.yaw)
            val sin = MathUtils.sinDeg(transform.yaw)

            val rotatedX = tempMovement.x * cos - tempMovement.z * sin
            val rotatedZ = tempMovement.x * sin + tempMovement.z * cos

            transform.position.x += rotatedX
            transform.position.y += tempMovement.y
            transform.position.z += rotatedZ
        }
    }
}
