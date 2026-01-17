package com.eucleantoomuch.game.feedback

import com.eucleantoomuch.game.platform.PlatformServices

/**
 * Manages speed warning feedback (beeps and vibration) similar to real EUC wheels.
 * - Speed warning: activates when speed exceeds 55 km/h (continuous beeping)
 * - Overpower warning: activates on sudden hard acceleration (single beep + vibration)
 */
class SpeedWarningManager(
    private val platformServices: PlatformServices
) {
    companion object {
        // Speed threshold in m/s (70 km/h = 19.44 m/s)
        const val WARNING_SPEED_MS = 19.44f
        const val WARNING_SPEED_KMH = 70f

        // Beep timing - faster beeps at higher speeds
        const val MIN_BEEP_INTERVAL = 0.15f  // seconds (at max speed)
        const val MAX_BEEP_INTERVAL = 0.5f   // seconds (at warning threshold)

        // Beep sound parameters
        const val BEEP_FREQUENCY = 2000  // Hz - high pitched warning beep
        const val BEEP_DURATION = 50     // ms - short beep

        // Vibration parameters
        const val VIBRATION_DURATION = 30L  // ms - light vibration pulse
        const val VIBRATION_AMPLITUDE = 80  // 0-255, light vibration

        // Overpower warning thresholds (very powerful wheel - very hard to trigger)
        const val OVERPOWER_LEAN_THRESHOLD = 0.95f  // Forward lean > 95% triggers warning (almost max)
        const val OVERPOWER_LEAN_RATE_THRESHOLD = 4.0f  // Extremely rapid lean change rate needed
        const val OVERPOWER_COOLDOWN = 0.3f  // Seconds between overpower warnings

        // Overpower beep (lower pitch, longer)
        const val OVERPOWER_BEEP_FREQUENCY = 1500  // Hz - lower pitch
        const val OVERPOWER_BEEP_DURATION = 100    // ms - longer beep
        const val OVERPOWER_VIBRATION_DURATION = 80L  // ms - stronger vibration
        const val OVERPOWER_VIBRATION_AMPLITUDE = 150  // 0-255
    }

    private var isWarningActive = false
    private var beepTimer = 0f
    private var currentBeepInterval = MAX_BEEP_INTERVAL

    // Overpower tracking
    private var lastForwardLean = 0f
    private var overpowerCooldown = 0f
    private var isOverpowerActive = false

    /**
     * Update the warning system based on current speed and lean.
     * @param speedMs current speed in m/s
     * @param delta time since last update in seconds
     * @param forwardLean current forward lean value (-1 to +1)
     */
    fun update(speedMs: Float, delta: Float, forwardLean: Float = 0f) {
        // === Speed warning (55+ km/h) ===
        val shouldWarn = speedMs >= WARNING_SPEED_MS

        if (shouldWarn) {
            if (!isWarningActive) {
                // Just crossed threshold - start warning
                isWarningActive = true
                beepTimer = 0f  // Immediate first beep
            }

            // Calculate beep interval based on speed (faster = more urgent)
            // At 70 km/h (19.44 m/s) = MAX_BEEP_INTERVAL
            // At 85 km/h (23.6 m/s) = MIN_BEEP_INTERVAL
            val speedFactor = ((speedMs - WARNING_SPEED_MS) / (23.6f - WARNING_SPEED_MS)).coerceIn(0f, 1f)
            currentBeepInterval = MAX_BEEP_INTERVAL - (MAX_BEEP_INTERVAL - MIN_BEEP_INTERVAL) * speedFactor

            // Update beep timer
            beepTimer -= delta
            if (beepTimer <= 0f) {
                triggerWarning()
                beepTimer = currentBeepInterval
            }
        } else {
            if (isWarningActive) {
                // Speed dropped below threshold - stop warning
                isWarningActive = false
                platformServices.cancelVibration()
            }
        }

        // === Overpower warning (sudden hard acceleration) ===
        overpowerCooldown -= delta

        // Detect rapid forward lean increase (user pushing too hard)
        val leanRate = (forwardLean - lastForwardLean) / delta.coerceAtLeast(0.001f)
        val isOverpowering = forwardLean > OVERPOWER_LEAN_THRESHOLD &&
                leanRate > OVERPOWER_LEAN_RATE_THRESHOLD &&
                overpowerCooldown <= 0f

        if (isOverpowering) {
            triggerOverpowerWarning()
            overpowerCooldown = OVERPOWER_COOLDOWN
            isOverpowerActive = true
        } else if (forwardLean < OVERPOWER_LEAN_THRESHOLD * 0.8f) {
            isOverpowerActive = false
        }

        lastForwardLean = forwardLean
    }

    private fun triggerWarning() {
        // Play beep sound
        platformServices.playBeep(BEEP_FREQUENCY, BEEP_DURATION)

        // Light vibration pulse
        if (platformServices.hasVibrator()) {
            platformServices.vibrate(VIBRATION_DURATION, VIBRATION_AMPLITUDE)
        }
    }

    private fun triggerOverpowerWarning() {
        // Lower pitch, longer beep for overpower
        platformServices.playBeep(OVERPOWER_BEEP_FREQUENCY, OVERPOWER_BEEP_DURATION)

        // Stronger vibration for overpower warning
        if (platformServices.hasVibrator()) {
            platformServices.vibrate(OVERPOWER_VIBRATION_DURATION, OVERPOWER_VIBRATION_AMPLITUDE)
        }
    }

    /**
     * Check if speed warning is currently active (55+ km/h).
     */
    fun isActive(): Boolean = isWarningActive

    /**
     * Check if overpower warning is currently active (hard acceleration).
     */
    fun isOverpowerWarning(): Boolean = isOverpowerActive

    /**
     * Get current beep interval for UI feedback.
     */
    fun getCurrentBeepInterval(): Float = currentBeepInterval

    /**
     * Stop all warnings (e.g., when game pauses or ends).
     */
    fun stop() {
        isWarningActive = false
        isOverpowerActive = false
        overpowerCooldown = 0f
        platformServices.cancelVibration()
    }
}
