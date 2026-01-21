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

    // === Wobble Sound Effect ===

    /**
     * Play wobble sound effect when wheel starts wobbling.
     * Low-frequency rumbling/rattling sound.
     * @param intensity Wobble intensity 0-1, affects volume and frequency
     */
    fun playWobbleSound(intensity: Float)

    /**
     * Stop wobble sound when wobbling ends.
     */
    fun stopWobbleSound()

    // === Obstacle Impact Sounds ===

    /**
     * Play manhole impact sound when riding over a manhole cover.
     */
    fun playManholeSound()

    /**
     * Play water splash sound when riding through a puddle.
     */
    fun playWaterSplashSound()

    /**
     * Play street light impact sound when crashing into a street light.
     * @param volume Volume multiplier (0.0 to 1.0), defaults to 1.0
     */
    fun playStreetLightImpactSound(volume: Float = 1f)

    /**
     * Play recycle bin impact sound when crashing into a recycle bin.
     * @param volume Volume multiplier (0.0 to 1.0), defaults to 1.0
     */
    fun playRecycleBinImpactSound(volume: Float = 1f)

    /**
     * Play person impact sound when crashing into a pedestrian.
     * @param volume Volume multiplier (0.0 to 1.0), defaults to 1.0
     */
    fun playPersonImpactSound(volume: Float = 1f)

    /**
     * Play generic hit sound for obstacles without specific sounds.
     * @param volume Volume multiplier (0.0 to 1.0), defaults to 1.0
     */
    fun playGenericHitSound(volume: Float = 1f)

    /**
     * Play car crash sound when colliding with a car.
     * @param volume Volume multiplier (0.0 to 1.0), defaults to 1.0
     */
    fun playCarCrashSound(volume: Float = 1f)

    /**
     * Play bench impact sound when colliding with a bench.
     * @param volume Volume multiplier (0.0 to 1.0), defaults to 1.0
     */
    fun playBenchImpactSound(volume: Float = 1f)

    // === Powerup Sound Effect ===

    /**
     * Play powerup pickup sound when collecting a battery powerup.
     */
    fun playPowerupSound()
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

    override fun playWobbleSound(intensity: Float) {
        // No-op on desktop/unsupported platforms
    }

    override fun stopWobbleSound() {
        // No-op on desktop/unsupported platforms
    }

    override fun playManholeSound() {
        // No-op on desktop/unsupported platforms
    }

    override fun playWaterSplashSound() {
        // No-op on desktop/unsupported platforms
    }

    override fun playStreetLightImpactSound(volume: Float) {
        // No-op on desktop/unsupported platforms
    }

    override fun playRecycleBinImpactSound(volume: Float) {
        // No-op on desktop/unsupported platforms
    }

    override fun playPersonImpactSound(volume: Float) {
        // No-op on desktop/unsupported platforms
    }

    override fun playGenericHitSound(volume: Float) {
        // No-op on desktop/unsupported platforms
    }

    override fun playCarCrashSound(volume: Float) {
        // No-op on desktop/unsupported platforms
    }

    override fun playBenchImpactSound(volume: Float) {
        // No-op on desktop/unsupported platforms
    }

    override fun playPowerupSound() {
        // No-op on desktop/unsupported platforms
    }
}
