package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

enum class PowerupType {
    BATTERY  // Restores battery charge
}

class PowerupComponent : Component, Pool.Poolable {
    var type: PowerupType = PowerupType.BATTERY
    var isCollected: Boolean = false

    // Animation
    var bobOffset: Float = 0f      // Vertical bobbing
    var rotationAngle: Float = 0f  // Spinning animation

    // Battery powerup specifics
    var batteryRestoreAmount: Float = 0.15f  // Restores 15% battery

    override fun reset() {
        type = PowerupType.BATTERY
        isCollected = false
        bobOffset = 0f
        rotationAngle = 0f
        batteryRestoreAmount = 0.15f
    }
}
