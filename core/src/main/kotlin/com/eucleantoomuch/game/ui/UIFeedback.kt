package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable

/**
 * Provides audio and haptic feedback for UI interactions.
 * Singleton pattern for easy access from all UI renderers.
 */
object UIFeedback : Disposable {
    private var clickSound: Sound? = null
    private var initialized = false
    private var enabled = true

    // Haptic feedback interface - set from platform
    var hapticProvider: HapticProvider? = null

    // Beep provider for synthesized click sound fallback
    var beepProvider: BeepProvider? = null

    interface HapticProvider {
        fun vibrate(durationMs: Long, amplitude: Int = -1)
        fun hasVibrator(): Boolean
    }

    interface BeepProvider {
        fun playBeep(frequencyHz: Int, durationMs: Int)
    }

    /**
     * Initialize sound assets. Safe to call multiple times.
     */
    fun initialize() {
        if (initialized) return

        try {
            // Try to load click sound from various formats
            val formats = listOf("sounds/click.ogg", "sounds/click.mp3", "sounds/click.wav")
            for (path in formats) {
                val soundFile = Gdx.files.internal(path)
                if (soundFile.exists()) {
                    clickSound = Gdx.audio.newSound(soundFile)
                    Gdx.app.log("UIFeedback", "Loaded click sound from $path")
                    break
                }
            }

            if (clickSound == null) {
                Gdx.app.log("UIFeedback", "No click sound file found, will use synthesized beep")
            }
        } catch (e: Exception) {
            Gdx.app.log("UIFeedback", "Could not load click sound: ${e.message}")
        }

        initialized = true
    }

    /**
     * Enable or disable UI feedback.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    /**
     * Play button click feedback (sound + haptic).
     */
    fun click() {
        if (!enabled) return

        // Play click sound or fallback to beep
        if (clickSound != null) {
            clickSound?.play(0.5f)
        } else {
            // Synthesized click - short high-frequency beep
            beepProvider?.playBeep(1200, 15)
        }

        // Haptic feedback - short light tap
        hapticProvider?.let {
            if (it.hasVibrator()) {
                it.vibrate(15, 80) // 15ms, light intensity
            }
        }
    }

    /**
     * Play heavier click for important actions (like PLAY button).
     */
    fun clickHeavy() {
        if (!enabled) return

        // Play click sound louder or fallback to beep
        if (clickSound != null) {
            clickSound?.play(0.7f)
        } else {
            // Synthesized heavier click
            beepProvider?.playBeep(800, 25)
        }

        // Stronger haptic
        hapticProvider?.let {
            if (it.hasVibrator()) {
                it.vibrate(25, 150) // 25ms, medium intensity
            }
        }
    }

    /**
     * Light haptic tap without sound (for toggles, selections).
     */
    fun tap() {
        if (!enabled) return

        hapticProvider?.let {
            if (it.hasVibrator()) {
                it.vibrate(10, 50) // 10ms, very light
            }
        }
    }

    /**
     * Selection swipe sound - for changing wheel selection, carousel navigation.
     * Distinct "swoosh" feel with medium haptic.
     */
    fun swipe() {
        if (!enabled) return

        // Rising tone for "swipe" feel
        beepProvider?.playBeep(600, 20)
        beepProvider?.playBeep(900, 15)

        hapticProvider?.let {
            if (it.hasVibrator()) {
                it.vibrate(20, 100) // Medium haptic pulse
            }
        }
    }

    /**
     * Pause/menu open sound - for two-finger tap to pause.
     * Distinctive "pause" feel with descending tone.
     */
    fun pauseOpen() {
        if (!enabled) return

        // Descending tone for "pause/stop" feel
        beepProvider?.playBeep(1000, 30)
        beepProvider?.playBeep(700, 40)

        hapticProvider?.let {
            if (it.hasVibrator()) {
                it.vibrate(35, 120) // Firm haptic feedback
            }
        }
    }

    /**
     * Near miss sound - quick "whoosh" feeling when passing close to pedestrian.
     * Rising tone with haptic for dramatic effect.
     */
    fun nearMiss() {
        if (!enabled) return

        // Quick rising tone for "close call" feel
        beepProvider?.playBeep(400, 30)
        beepProvider?.playBeep(800, 40)
        beepProvider?.playBeep(1200, 30)

        hapticProvider?.let {
            if (it.hasVibrator()) {
                it.vibrate(50, 180) // Strong haptic pulse
            }
        }
    }

    /**
     * Wobble haptic feedback - continuous vibration while wobbling.
     * Intensity based on wobble strength (0-1).
     * Should be called every frame while wobbling.
     */
    fun wobble(intensity: Float) {
        if (!enabled) return
        if (intensity < 0.05f) return

        hapticProvider?.let {
            if (it.hasVibrator()) {
                // Short pulse proportional to intensity
                val amplitude = (40 + intensity * 100).toInt().coerceIn(40, 140)
                it.vibrate(16, amplitude) // ~60Hz vibration pulses
            }
        }
    }

    override fun dispose() {
        clickSound?.dispose()
        clickSound = null
        initialized = false
    }
}
