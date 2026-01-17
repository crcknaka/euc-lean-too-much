package com.eucleantoomuch.game.platform

/**
 * Platform-specific services interface for features like vibration and sound
 * that require native implementation on each platform.
 */
interface PlatformServices {
    /**
     * Vibrate the device for the specified duration in milliseconds.
     * @param durationMs vibration duration in milliseconds
     * @param amplitude vibration strength 0-255 (if supported), -1 for default
     */
    fun vibrate(durationMs: Long, amplitude: Int = -1)

    /**
     * Play a short beep sound at the specified frequency.
     * @param frequencyHz frequency in Hz (e.g., 1000 for 1kHz beep)
     * @param durationMs duration in milliseconds
     */
    fun playBeep(frequencyHz: Int, durationMs: Int)

    /**
     * Check if vibration is available on this device.
     */
    fun hasVibrator(): Boolean

    /**
     * Stop any ongoing vibration.
     */
    fun cancelVibration()
}

/**
 * Default implementation that does nothing - used on platforms without these features.
 */
class DefaultPlatformServices : PlatformServices {
    override fun vibrate(durationMs: Long, amplitude: Int) {
        // No-op on desktop/unsupported platforms
    }

    override fun playBeep(frequencyHz: Int, durationMs: Int) {
        // No-op on desktop/unsupported platforms
    }

    override fun hasVibrator(): Boolean = false

    override fun cancelVibration() {
        // No-op
    }
}
