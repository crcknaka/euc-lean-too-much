package com.eucleantoomuch.game.feedback

import com.eucleantoomuch.game.platform.PlatformServices

/**
 * Manages PWM warning feedback (beeps and vibration) similar to real EUC wheels.
 * - PWM warning: activates when PWM exceeds user-configured threshold (continuous beeping)
 */
class SpeedWarningManager(
    private val platformServices: PlatformServices
) {
    companion object {
        // Beep timing - faster beeps at higher PWM
        const val MIN_BEEP_INTERVAL = 0.15f  // seconds (at 100% PWM)
        const val MAX_BEEP_INTERVAL = 0.5f   // seconds (at warning threshold)

        // Beep sound parameters
        const val BEEP_FREQUENCY = 2000  // Hz - high pitched warning beep
        const val BEEP_DURATION = 50     // ms - short beep

        // Vibration parameters
        const val VIBRATION_DURATION = 30L  // ms - light vibration pulse
        const val VIBRATION_AMPLITUDE = 80  // 0-255, light vibration
    }

    // PWM warning threshold (0 = off, 0.6-0.9 = active)
    var pwmWarningThreshold: Float = 0.8f

    private var isWarningActive = false
    private var beepTimer = 0f
    private var currentBeepInterval = MAX_BEEP_INTERVAL

    /**
     * Update the warning system based on current PWM.
     * @param pwm current PWM value (0 to 1+)
     * @param delta time since last update in seconds
     */
    fun update(pwm: Float, delta: Float) {
        // Warning disabled if threshold is 0
        if (pwmWarningThreshold <= 0f) {
            if (isWarningActive) {
                isWarningActive = false
                platformServices.cancelVibration()
            }
            return
        }

        val shouldWarn = pwm >= pwmWarningThreshold

        if (shouldWarn) {
            if (!isWarningActive) {
                // Just crossed threshold - start warning
                isWarningActive = true
                beepTimer = 0f  // Immediate first beep
            }

            // Calculate beep interval based on PWM (higher = more urgent)
            // At threshold = MAX_BEEP_INTERVAL
            // At 100% PWM = MIN_BEEP_INTERVAL
            val pwmFactor = ((pwm - pwmWarningThreshold) / (1f - pwmWarningThreshold)).coerceIn(0f, 1f)
            currentBeepInterval = MAX_BEEP_INTERVAL - (MAX_BEEP_INTERVAL - MIN_BEEP_INTERVAL) * pwmFactor

            // Update beep timer
            beepTimer -= delta
            if (beepTimer <= 0f) {
                triggerWarning()
                beepTimer = currentBeepInterval
            }
        } else {
            if (isWarningActive) {
                // PWM dropped below threshold - stop warning
                isWarningActive = false
                platformServices.cancelVibration()
            }
        }
    }

    private fun triggerWarning() {
        // Play beep sound
        platformServices.playBeep(BEEP_FREQUENCY, BEEP_DURATION)

        // Light vibration pulse
        if (platformServices.hasVibrator()) {
            platformServices.vibrate(VIBRATION_DURATION, VIBRATION_AMPLITUDE)
        }
    }

    /**
     * Check if PWM warning is currently active.
     */
    fun isActive(): Boolean = isWarningActive

    /**
     * Get current beep interval for UI feedback.
     */
    fun getCurrentBeepInterval(): Float = currentBeepInterval

    /**
     * Stop all warnings (e.g., when game pauses or ends).
     */
    fun stop() {
        isWarningActive = false
        platformServices.cancelVibration()
    }
}
