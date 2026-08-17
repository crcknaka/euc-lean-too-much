package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

/**
 * Component to track head animation state for the rider.
 * Head moves slightly at low speeds as if dancing/looking around.
 */
class HeadComponent : Component, Pool.Poolable {
    // Head rotation angles (degrees)
    var pitch: Float = 0f   // Nod up/down
    var yaw: Float = 0f     // Turn left/right
    var roll: Float = 0f    // Tilt side to side

    // Animation time for head movement
    var animTime: Float = 0f

    // Glancing about. Held still between looks rather than swept continuously: a head that
    // never stops moving reads as a bobblehead, while real idle motion is a quick look, a
    // pause, and a look back.
    var glanceCountdown: Float = 1.5f
    var glanceProgress: Float = 1f
    var glanceFromYaw: Float = 0f
    var glanceToYaw: Float = 0f
    var glanceFromPitch: Float = 0f
    var glanceToPitch: Float = 0f

    // Head attachment offset from rider body (neck position)
    val riderScale = 1.55f
    var offsetX: Float = 0f
    var offsetY: Float = 1.38f * riderScale  // Just below where head was in model
    var offsetZ: Float = 0f

    companion object {
        // Speed threshold - head bobs only below this speed (m/s)
        const val DANCE_SPEED_THRESHOLD = 8.33f  // ~30 km/h
    }

    override fun reset() {
        pitch = 0f
        yaw = 0f
        roll = 0f
        animTime = 0f
        glanceCountdown = 1.5f
        glanceProgress = 1f
        glanceFromYaw = 0f
        glanceToYaw = 0f
        glanceFromPitch = 0f
        glanceToPitch = 0f
    }
}
