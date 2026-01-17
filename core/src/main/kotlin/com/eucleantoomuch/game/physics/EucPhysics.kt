package com.eucleantoomuch.game.physics

import com.eucleantoomuch.game.util.Constants
import kotlin.math.sqrt

object EucPhysics {
    /**
     * Calculate target speed based on forward lean
     * @param forwardLean Normalized lean (-1 to 1)
     * @return Target speed in m/s
     */
    fun calculateTargetSpeed(forwardLean: Float): Float {
        return when {
            forwardLean > 0 -> lerp(Constants.MIN_SPEED, Constants.MAX_SPEED, forwardLean)
            forwardLean < 0 -> lerp(Constants.MIN_SPEED, 0f, -forwardLean)
            else -> Constants.MIN_SPEED
        }
    }

    /**
     * Update speed with acceleration/deceleration
     * @param currentSpeed Current speed in m/s
     * @param targetSpeed Target speed in m/s
     * @param deltaTime Frame delta time
     * @return New speed
     */
    fun updateSpeed(currentSpeed: Float, targetSpeed: Float, deltaTime: Float): Float {
        val accel = if (targetSpeed > currentSpeed) Constants.ACCELERATION else Constants.DECELERATION
        return moveTowards(currentSpeed, targetSpeed, accel * deltaTime)
            .coerceIn(0f, Constants.MAX_SPEED)
    }

    /**
     * Calculate turn rate based on side lean and current speed
     * Turning is slower at higher speeds (more realistic)
     */
    fun calculateTurnRate(sideLean: Float, currentSpeed: Float): Float {
        // Speed factor: turning is harder at high speed
        val speedFactor = 1f - (currentSpeed / Constants.MAX_SPEED) * 0.5f
        return sideLean * Constants.MAX_TURN_RATE * speedFactor
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
