package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

/**
 * Component to track arm animation state for the rider.
 * Arms animate based on speed:
 * - At low speed (< 15 km/h): arms move to balance (like a tightrope walker)
 * - At high speed (>= 15 km/h): arms held behind the back
 */
class ArmComponent : Component, Pool.Poolable {
    // Arm pose blend: 0 = balance pose (arms out), 1 = behind back pose
    var poseBlend: Float = 0f

    // Left arm angles (degrees, relative to shoulder)
    var leftArmPitch: Float = 0f   // Forward/backward rotation
    var leftArmYaw: Float = 0f     // Side rotation (outward)
    var leftArmRoll: Float = 0f    // Twist

    // Right arm angles (degrees, relative to shoulder)
    var rightArmPitch: Float = 0f
    var rightArmYaw: Float = 0f
    var rightArmRoll: Float = 0f

    // Forearm bend (for behind-back pose)
    var leftForearmBend: Float = 0f   // 0 = straight, positive = bent
    var rightForearmBend: Float = 0f

    // Balance animation time (for swaying motion at low speeds)
    var balanceTime: Float = 0f

    // Shoulder attachment offsets from rider body center (set once based on model)
    // Torso is at Y=1.0 with height 0.6, so top of torso (shoulders) is at Y=1.3
    // Torso width is 0.35, half = 0.175. Add arm thickness (0.09) to place arms outside body
    var shoulderOffsetX: Float = (0.175f + 0.05f) * 1.4f  // Half torso width + gap, * rider scale
    var shoulderOffsetY: Float = 1.25f * 1.4f    // Slightly below top of torso (shoulder joint)
    var shoulderOffsetZ: Float = 0f

    companion object {
        // Speed thresholds in m/s (game uses m/s internally)
        const val BALANCE_SPEED_THRESHOLD = 4.17f   // ~15 km/h - below this, arms balance
        const val RELAXED_SPEED_THRESHOLD = 19.44f  // ~70 km/h - above this, arms go behind back
        const val TRANSITION_SPEED = 3f             // How fast to blend between poses (per second)
    }

    override fun reset() {
        poseBlend = 0f
        leftArmPitch = 0f
        leftArmYaw = 0f
        leftArmRoll = 0f
        rightArmPitch = 0f
        rightArmYaw = 0f
        rightArmRoll = 0f
        leftForearmBend = 0f
        rightForearmBend = 0f
        balanceTime = 0f
    }
}
