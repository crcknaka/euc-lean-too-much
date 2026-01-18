package com.eucleantoomuch.game.physics

import com.eucleantoomuch.game.util.Constants
import kotlin.math.sqrt

object EucPhysics {
    /**
     * Calculate target speed based on forward lean
     * @param forwardLean Normalized lean (-1 to 1)
     * @param maxSpeed Maximum speed for this wheel type
     * @return Target speed in m/s
     */
    fun calculateTargetSpeed(forwardLean: Float, maxSpeed: Float = Constants.MAX_SPEED): Float {
        return when {
            forwardLean > 0 -> lerp(Constants.MIN_SPEED, maxSpeed, forwardLean)
            forwardLean < 0 -> lerp(Constants.MIN_SPEED, 0f, -forwardLean)
            else -> Constants.MIN_SPEED
        }
    }

    /**
     * Update speed with acceleration/deceleration
     * @param currentSpeed Current speed in m/s
     * @param targetSpeed Target speed in m/s
     * @param deltaTime Frame delta time
     * @param acceleration Acceleration rate for this wheel type
     * @param deceleration Deceleration rate for this wheel type
     * @param maxSpeed Maximum speed for this wheel type
     * @return New speed
     */
    fun updateSpeed(
        currentSpeed: Float,
        targetSpeed: Float,
        deltaTime: Float,
        acceleration: Float = Constants.ACCELERATION,
        deceleration: Float = Constants.DECELERATION,
        maxSpeed: Float = Constants.MAX_SPEED
    ): Float {
        val accel = if (targetSpeed > currentSpeed) acceleration else deceleration
        return moveTowards(currentSpeed, targetSpeed, accel * deltaTime)
            .coerceIn(0f, maxSpeed)
    }

    /**
     * Calculate turn rate based on side lean and current speed
     * Turning is slower at higher speeds (more realistic)
     * @param sideLean Normalized side lean (-1 to 1)
     * @param currentSpeed Current speed in m/s
     * @param turnResponsiveness Turn rate multiplier for this wheel type
     * @param maxSpeed Maximum speed for calculating speed factor
     * @return Turn rate in degrees/second
     */
    fun calculateTurnRate(
        sideLean: Float,
        currentSpeed: Float,
        turnResponsiveness: Float = Constants.TURN_RESPONSIVENESS,
        maxSpeed: Float = Constants.MAX_SPEED
    ): Float {
        // Speed factor: turning is harder at high speed
        val speedFactor = 1f - (currentSpeed / maxSpeed) * 0.5f
        // Turn responsiveness affects how agile the wheel is
        val responsivenessFactor = turnResponsiveness / Constants.TURN_RESPONSIVENESS
        return sideLean * Constants.MAX_TURN_RATE * speedFactor * responsivenessFactor
    }

    /**
     * Check if the combined lean angle causes a fall
     */
    fun checkFall(forwardLean: Float, sideLean: Float, criticalLean: Float = Constants.CRITICAL_LEAN): Boolean {
        val totalLean = sqrt(forwardLean * forwardLean + sideLean * sideLean)
        return totalLean >= criticalLean
    }

    /**
     * Calculate the danger level (0-1) based on lean
     * 0 = safe, 1 = about to fall
     */
    fun getDangerLevel(forwardLean: Float, sideLean: Float): Float {
        val totalLean = sqrt(forwardLean * forwardLean + sideLean * sideLean)
        return (totalLean / Constants.CRITICAL_LEAN).coerceIn(0f, 1f)
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t
    }

    private fun moveTowards(current: Float, target: Float, maxDelta: Float): Float {
        return when {
            target > current -> minOf(current + maxDelta, target)
            target < current -> maxOf(current - maxDelta, target)
            else -> current
        }
    }
}
