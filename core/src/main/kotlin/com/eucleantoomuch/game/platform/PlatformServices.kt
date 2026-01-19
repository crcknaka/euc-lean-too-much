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

    // === Motor/Tire Sound Synthesis (AVAS) ===

    /**
     * Start continuous motor and tire sound synthesis.
     * Call updateMotorSound() each frame to modulate parameters.
     * @param mode AVAS mode: 0 = off, 1 = electric (quiet), 2 = motorcycle (loud)
     */
    fun startMotorSound(mode: Int)

    /**
     * Stop motor and tire sounds.
     */
    fun stopMotorSound()

    /**
     * Update motor sound parameters based on current physics state.
     * @param speed current speed in m/s (0 to ~24)
     * @param pwm motor load 0-1.5 (higher = more strain sound)
     * @param acceleration rate of speed change (for motor strain)
     */
    fun updateMotorSound(speed: Float, pwm: Float, acceleration: Float)

    /**
     * Check if motor sound is currently playing.
     */
    fun isMotorSoundPlaying(): Boolean

    // === Crash Sound Effect ===

    /**
     * Play a crash/impact sound effect.
     * Short "crack" noise with descending pitch.
     * @param intensity Impact intensity (0.3 to 1.5), affects volume and pitch
     */
    fun playCrashSound(intensity: Float)

    // === Whoosh Sound Effect ===

    /**
     * Play a whoosh/swoosh sound effect for near misses.
     * Quick frequency sweep simulating air rushing past.
     */
    fun playWhooshSound()

    // === Pigeon Sound Effect ===

    /**
     * Play pigeon wing flapping sound when pigeons fly off.
     * Short fluttering/cooing sound.
     */
    fun playPigeonFlyOffSound()
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

    override fun startMotorSound(mode: Int) {
        // No-op on desktop/unsupported platforms
    }

    override fun stopMotorSound() {
        // No-op
    }

    override fun updateMotorSound(speed: Float, pwm: Float, acceleration: Float) {
        // No-op
    }

    override fun isMotorSoundPlaying(): Boolean = false

    override fun playCrashSound(intensity: Float) {
        // No-op on desktop/unsupported platforms
    }

    override fun playWhooshSound() {
        // No-op on desktop/unsupported platforms
    }

    override fun playPigeonFlyOffSound() {
        // No-op on desktop/unsupported platforms
    }
}
