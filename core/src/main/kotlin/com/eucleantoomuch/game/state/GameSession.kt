package com.eucleantoomuch.game.state

import com.eucleantoomuch.game.util.Constants
import kotlin.math.max

class GameSession {
    var distanceTraveled: Float = 0f
        private set
    var currentSpeed: Float = 0f
        private set
    var maxSpeed: Float = 0f
        private set
    var obstaclesAvoided: Int = 0
    var nearMisses: Int = 0
    var startTime: Long = System.currentTimeMillis()
        private set

    // Time Trial mode
    var timeTrialLevel: TimeTrialLevel? = null

    val isTimeTrial: Boolean
        get() = timeTrialLevel != null

    val timeRemaining: Float
        get() = timeTrialLevel?.let { max(0f, it.timeLimit - playTimeSeconds) } ?: 0f

    val levelCompleted: Boolean
        get() = timeTrialLevel?.let { distanceTraveled >= it.targetDistance } ?: false

    val distanceProgress: Float
        get() = timeTrialLevel?.let { (distanceTraveled / it.targetDistance).coerceIn(0f, 1f) } ?: 0f

    // Battery system
    var batteryCapacity: Int = 2400  // mAh (set from WheelType)
        private set
    var batteryLevel: Float = 1f     // 0-1 (percentage)
        private set
    var isBatteryLow: Boolean = false
        private set
    var isBatteryDead: Boolean = false
        private set

    // Battery drain constants
    // Base drain rate is calibrated so that:
    // - Simple (1800mAh) at 30 km/h lasts ~1 minute
    // - Standard (2400mAh) at 50 km/h lasts ~1.5 minutes
    // - Performance (4000mAh) at 80 km/h lasts ~2 minutes
    // Formula: drain = baseDrain * (1 + speedFactor * normalizedSpeed^2)
    // Higher speed = exponentially more drain
    companion object {
        private const val BASE_DRAIN_PER_SECOND = 0.006f  // Base drain at 0 speed (3x faster)
        private const val SPEED_DRAIN_FACTOR = 4f        // How much speed multiplies drain
        private const val LOW_BATTERY_THRESHOLD = 0.2f   // 20% triggers warning
    }

    val score: Int
        get() = (distanceTraveled * Constants.POINTS_PER_METER).toInt()

    val playTimeSeconds: Float
        get() = (System.currentTimeMillis() - startTime) / 1000f

    val batteryPercent: Int
        get() = (batteryLevel * 100).toInt()

    fun setBatteryCapacity(capacity: Int) {
        batteryCapacity = capacity
        // Scale drain rate inversely with capacity (bigger battery = slower drain)
        // Reference: 2400mAh is baseline (factor 1.0)
    }

    fun restoreBattery(amount: Float) {
        batteryLevel = (batteryLevel + amount).coerceAtMost(1f)
        // Update flags after restore
        isBatteryLow = batteryLevel <= LOW_BATTERY_THRESHOLD && batteryLevel > 0f
        isBatteryDead = batteryLevel <= 0f
    }

    fun update(deltaTime: Float, speed: Float) {
        distanceTraveled += speed * deltaTime
        currentSpeed = speed
        if (speed > maxSpeed) {
            maxSpeed = speed
        }

        // Update battery drain
        updateBattery(deltaTime, speed)
    }

    private fun updateBattery(deltaTime: Float, speed: Float) {
        if (isBatteryDead) return

        // Normalize speed (0-1 based on typical max ~30 m/s = 108 km/h)
        val normalizedSpeed = (speed / 30f).coerceIn(0f, 1f)

        // Calculate drain rate - exponential with speed
        // drainRate = baseDrain * (1 + speedFactor * speed^2)
        val speedMultiplier = 1f + SPEED_DRAIN_FACTOR * normalizedSpeed * normalizedSpeed

        // Capacity factor: bigger battery drains slower
        // 2400 mAh = baseline (1.0), 1800 mAh = 1.33x faster, 4000 mAh = 0.6x slower
        val capacityFactor = 2400f / batteryCapacity

        val drainRate = BASE_DRAIN_PER_SECOND * speedMultiplier * capacityFactor
        batteryLevel = (batteryLevel - drainRate * deltaTime).coerceAtLeast(0f)

        // Update status flags
        isBatteryLow = batteryLevel <= LOW_BATTERY_THRESHOLD && batteryLevel > 0f
        isBatteryDead = batteryLevel <= 0f
    }

    fun reset() {
        distanceTraveled = 0f
        currentSpeed = 0f
        maxSpeed = 0f
        obstaclesAvoided = 0
        nearMisses = 0
        startTime = System.currentTimeMillis()
        batteryLevel = 1f
        isBatteryLow = false
        isBatteryDead = false
        timeTrialLevel = null
    }
}
