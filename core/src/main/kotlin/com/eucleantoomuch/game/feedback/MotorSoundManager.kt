package com.eucleantoomuch.game.feedback

import com.eucleantoomuch.game.platform.PlatformServices

/**
 * Manages motor and tire sound synthesis for realistic EUC audio feedback.
 *
 * AVAS modes:
 * - 0 = Off: No sound
 * - 1 = Electric: Quiet EUC-like motor whine
 * - 2 = Motorcycle: Louder with more harmonics
 */
class MotorSoundManager(
    private val platformServices: PlatformServices
) {
    // AVAS mode: 0 = off, 1 = electric, 2 = motorcycle
    var avasMode: Int = 1

    private var isPlaying = false
    private var lastSpeed = 0f

    /**
     * Start motor sound playback.
     * Call this when gameplay begins.
     */
    fun start() {
        if (avasMode == 0) return  // AVAS off
        if (isPlaying) return

        platformServices.startMotorSound(avasMode)
        isPlaying = true
        lastSpeed = 0f
    }

    /**
     * Stop motor sound playback.
     * Call this when game pauses or ends.
     */
    fun stop() {
        if (!isPlaying) return

        platformServices.stopMotorSound()
        isPlaying = false
    }

    /**
     * Update motor sound based on current EUC state.
     * Call this every frame during gameplay.
     *
     * @param speed current speed in m/s
     * @param pwm motor load (0-1.5)
     * @param delta time since last frame
     */
    fun update(speed: Float, pwm: Float, delta: Float) {
        if (avasMode == 0 || !isPlaying) return

        // Calculate acceleration from speed change
        val acceleration = (speed - lastSpeed) / delta.coerceAtLeast(0.001f)
        lastSpeed = speed

        platformServices.updateMotorSound(speed, pwm, acceleration)
    }

    /**
     * Check if sound is currently playing.
     */
    fun isPlaying(): Boolean = isPlaying && platformServices.isMotorSoundPlaying()
}
