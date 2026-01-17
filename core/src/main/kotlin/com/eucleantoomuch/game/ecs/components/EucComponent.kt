package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool
import com.eucleantoomuch.game.util.Constants

class EucComponent : Component, Pool.Poolable {
    var forwardLean: Float = 0f      // -1 (back) to +1 (forward)
    var sideLean: Float = 0f         // -1 (left) to +1 (right)
    var speed: Float = Constants.MIN_SPEED
    var maxSpeed: Float = Constants.MAX_SPEED
    var criticalLean: Float = Constants.CRITICAL_LEAN

    // Puddle effect
    var inPuddle: Boolean = false
    var puddleTimer: Float = 0f

    // Visual lean (smoothed for rendering)
    var visualForwardLean: Float = 0f
    var visualSideLean: Float = 0f

    // PWM (motor load) - 0 to 1, at 1.0 = cutout/fall
    var pwm: Float = 0f

    override fun reset() {
        forwardLean = 0f
        sideLean = 0f
        speed = Constants.MIN_SPEED
        maxSpeed = Constants.MAX_SPEED
        criticalLean = Constants.CRITICAL_LEAN
        inPuddle = false
        puddleTimer = 0f
        visualForwardLean = 0f
        visualSideLean = 0f
        pwm = 0f
    }

    fun getTotalLean(): Float {
        return kotlin.math.sqrt(forwardLean * forwardLean + sideLean * sideLean)
    }

    /**
     * Calculate PWM based on speed and lean.
     * PWM represents motor load - combination of:
     * - Current speed vs max speed (higher speed = more PWM needed to maintain)
     * - Forward lean (acceleration demand)
     * - Side lean (turning demand)
     * At PWM >= 1.0, the motor can't keep up = cutout
     */
    fun calculatePwm(): Float {
        // Base PWM from current speed (cruising at max speed = ~70% PWM)
        val speedFactor = (speed / maxSpeed) * 0.7f

        // Additional PWM from forward lean (acceleration demand)
        // Positive lean = accelerating = more PWM needed
        val accelDemand = if (forwardLean > 0) forwardLean * 0.4f else forwardLean * 0.1f

        // Side lean also uses PWM for turning torque
        val turnDemand = kotlin.math.abs(sideLean) * 0.15f

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
}
