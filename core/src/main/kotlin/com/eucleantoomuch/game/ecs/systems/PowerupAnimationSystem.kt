package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.badlogic.gdx.math.Quaternion
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.PowerupComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent

/**
 * System that animates powerups - spinning effect.
 * Powerups are tilted 45 degrees and rotate around their axis.
 */
class PowerupAnimationSystem : EntitySystem(4) {
    private lateinit var powerupEntities: ImmutableArray<Entity>

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val powerupMapper = ComponentMapper.getFor(PowerupComponent::class.java)

    // Rotation speed in degrees per second
    private val rotationSpeed = 90f  // 90 degrees per second = full rotation in 4 seconds

    // 45 degree tilt
    private val tiltAngle = 45f

    // Temp quaternion for rotation calculations
    private val tempQuat = Quaternion()

    override fun addedToEngine(engine: Engine) {
        powerupEntities = engine.getEntitiesFor(Families.powerups)
    }

    override fun update(deltaTime: Float) {
        for (entity in powerupEntities) {
            val transform = transformMapper.get(entity) ?: continue
            val powerup = powerupMapper.get(entity) ?: continue

            if (powerup.isCollected) continue

            // Update rotation angle
            powerup.rotationAngle += rotationSpeed * deltaTime
            if (powerup.rotationAngle >= 360f) {
                powerup.rotationAngle -= 360f
            }

            // Apply rotation: first spin around Y, then tilt on Z axis
            // This makes it spin while tilted (like a coin spinning)
            tempQuat.setFromAxis(0f, 1f, 0f, powerup.rotationAngle)  // Spin around Y
            tempQuat.mul(Quaternion().setFromAxis(0f, 0f, 1f, tiltAngle))  // Then tilt sideways

            transform.rotation.set(tempQuat)
        }
    }
}
