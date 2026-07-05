package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool
import com.eucleantoomuch.game.util.Constants

class EucComponent : Component, Pool.Poolable {
    var forwardLean: Float = 0f      // -1 (back) to +1 (forward)
    var sideLean: Float = 0f         // -1 (left) to +1 (right)
    var speed: Float = Constants.MIN_SPEED
    var maxSpeed: Float = Constants.MAX_SPEED

    // Wheel-specific physics parameters
    var acceleration: Float = Constants.ACCELERATION
    var deceleration: Float = Constants.DECELERATION
    var pwmSensitivity: Float = 1.0f
    var turnResponsiveness: Float = Constants.TURN_RESPONSIVENESS

    // Puddle effect
    var inPuddle: Boolean = false
    var puddleTimer: Float = 0f

    // Visual lean (smoothed for rendering)
    var visualForwardLean: Float = 0f
    var visualSideLean: Float = 0f

    // PWM (motor load) - 0 to 1, at 1.0 = cutout/fall
    var pwm: Float = 0f

    // Speed wobble (колебания при резком торможении)
    var wobbleIntensity: Float = 0f       // Current wobble strength (0-1)
    var wobblePhase: Float = 0f           // Current phase of oscillation
    var wobbleFrequency: Float = 12f      // Oscillations per second (Hz)
    var previousSpeed: Float = 0f         // For detecting deceleration
    var wobbleTimer: Float = 0f           // Time spent wobbling (fall after 3 seconds)
    var isWobbling: Boolean = false       // Currently in wobble state (for UI)

    override fun reset() {
        forwardLean = 0f
        sideLean = 0f
        speed = Constants.MIN_SPEED
        maxSpeed = Constants.MAX_SPEED
        acceleration = Constants.ACCELERATION
        deceleration = Constants.DECELERATION
        pwmSensitivity = 1.0f
        turnResponsiveness = Constants.TURN_RESPONSIVENESS
        inPuddle = false
        puddleTimer = 0f
        visualForwardLean = 0f
        visualSideLean = 0f
        pwm = 0f
        wobbleIntensity = 0f
        wobblePhase = 0f
        wobbleFrequency = 12f
        previousSpeed = 0f
        wobbleTimer = 0f
        isWobbling = false
    }

    fun getTotalLean(): Float {
        return kotlin.math.sqrt(forwardLean * forwardLean + sideLean * sideLean)
    }

    /**
     * Calculate PWM based on speed and lean.
     * PWM represents motor load - combination of:
     * - Current speed vs max speed (higher speed = more PWM needed to maintain)
     * - Forward lean (acceleration demand)
     * - Side lean (turning demand) - minimal impact, turning doesn't stress motor much
     * At PWM >= 1.0, the motor can't keep up = cutout
     */
    fun calculatePwm(): Float {
        val speedKmh = speed * 3.6f

        // Relative load: how close to THIS wheel's own top speed (its headroom).
        val relativeLoad = (speed / maxSpeed) * 0.5f

        // Absolute load: raw-speed stress (air drag / voltage sag), on the SAME scale for
        // every wheel. Without this, a high-top-speed wheel (Speed Demon) shows almost no
        // PWM at normal city speeds and the mechanic feels dead - this keeps PWM a visible,
        // live threat across the whole speed range on all wheels.
        val absoluteLoad = (speedKmh / 110f).coerceIn(0f, 1f) * 0.22f

        val speedFactor = relativeLoad + absoluteLoad

        // Additional PWM from forward lean (acceleration demand)
        // Positive lean = accelerating = more PWM needed
        val accelDemand = if (forwardLean > 0) forwardLean * 0.4f else forwardLean * 0.1f

        // Side lean has minimal impact on PWM - turning uses gyro, not motor torque
        // Only adds small amount at extreme turns
        val turnDemand = kotlin.math.abs(sideLean) * 0.05f

        // Total PWM
        pwm = (speedFactor + accelDemand + turnDemand).coerceIn(0f, 1.5f)
        return pwm
    }

    /**
     * Get PWM as percentage (0-100+)
     */
    fun getPwmPercent(): Int = (pwm * 100).toInt()

    /**
     * Check if PWM is in danger zone (>80%)
     */
    fun isPwmDanger(): Boolean = pwm >= 0.8f

    /**
     * Check if PWM caused cutout (>100% - you can ride at 100%, but going over = fall)
     */
    fun isPwmCutout(): Boolean = pwm > 1.0f

    fun isAboutToFall(): Boolean = pwm >= 0.9f
    fun hasFallen(): Boolean = pwm > 1.0f

    fun applyPuddleEffect(duration: Float) {
        inPuddle = true
        puddleTimer = duration
    }

    /**
     * Apply wobble effect from external source (e.g., manhole collision).
     * Sets wobble intensity immediately and starts the wobble timer.
     */
    fun applyWobbleEffect(intensity: Float = 0.6f) {
        wobbleIntensity = intensity.coerceIn(0f, 0.8f)
        wobbleTimer = 0f  // Reset timer - player has 3 seconds to stabilize
        isWobbling = true
    }
}
