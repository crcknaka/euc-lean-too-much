package com.eucleantoomuch.game.feedback

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.utils.Disposable

/**
 * Manages background music playback.
 * Supports loading music from assets folder.
 *
 * Place music files in: assets/music/
 * Supported formats: MP3, OGG, WAV
 *
 * Example files:
 * - assets/music/menu.ogg - Menu/title screen music
 * - assets/music/gameplay.ogg - In-game music
 */
class MusicManager : Disposable {

    private var menuMusic: Music? = null
    private var gameplayMusic: Music? = null
    private var currentMusic: Music? = null

    private var enabled = true
    private var volume = 0.5f  // Default 50% volume

    // Fade parameters
    private var fadeTarget = 1f
    private var fadeCurrent = 0f
    private var fadeSpeed = 2f

    companion object {
        private const val MENU_MUSIC_PATH = "music/menu.mp3"
        private const val GAMEPLAY_MUSIC_PATH = "music/gameplay.mp3"
    }

    /**
     * Initialize and load music files.
     * Call this after LibGDX is initialized.
     */
    fun initialize() {
        try {
            // Load menu music if exists
            if (Gdx.files.internal(MENU_MUSIC_PATH).exists()) {
                menuMusic = Gdx.audio.newMusic(Gdx.files.internal(MENU_MUSIC_PATH))
                menuMusic?.isLooping = true
            }
        } catch (e: Exception) {
            Gdx.app.log("MusicManager", "Could not load menu music: ${e.message}")
        }

        try {
            // Load gameplay music if exists
            if (Gdx.files.internal(GAMEPLAY_MUSIC_PATH).exists()) {
                gameplayMusic = Gdx.audio.newMusic(Gdx.files.internal(GAMEPLAY_MUSIC_PATH))
                gameplayMusic?.isLooping = true
            }
        } catch (e: Exception) {
            Gdx.app.log("MusicManager", "Could not load gameplay music: ${e.message}")
        }
    }

    /**
     * Set whether music is enabled.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            stopAll()
        }
    }

    /**
     * Set music volume (0.0 to 1.0).
     */
    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        currentMusic?.volume = this.volume * fadeCurrent
    }

    /**
     * Play menu music with fade in.
     */
    fun playMenuMusic() {
        if (!enabled) return
        playMusic(menuMusic)
    }

    /**
     * Play gameplay music with fade in.
     */
    fun playGameplayMusic() {
        if (!enabled) return
        playMusic(gameplayMusic)
    }

    private fun playMusic(music: Music?) {
        if (music == null) return

        // Stop current if different
        if (currentMusic != music) {
            currentMusic?.stop()
            currentMusic = music
            fadeCurrent = 0f
            fadeTarget = 1f
        }

        // Start playing if not already, or restart if was fading out
        if (!music.isPlaying) {
            fadeCurrent = 0f
            fadeTarget = 1f
            music.volume = 0f
            music.play()
        } else if (fadeTarget == 0f) {
            // Was fading out, restart fade in
            fadeTarget = 1f
        }
    }

    /**
     * Fade out and stop current music.
     */
    fun fadeOut() {
        fadeTarget = 0f
        fadeSpeed = 2f
    }

    /**
     * Stop all music immediately.
     */
    fun stopAll() {
        menuMusic?.stop()
        gameplayMusic?.stop()
        currentMusic = null
        fadeCurrent = 0f
    }

    /**
     * Pause current music.
     */
    fun pause() {
        currentMusic?.pause()
    }

    /**
     * Resume current music.
     */
    fun resume() {
        if (enabled) {
            currentMusic?.play()
        }
    }

    /**
     * Update music fading.
     * Call this every frame.
     */
    fun update(deltaTime: Float) {
        if (currentMusic == null) return

        // Update fade
        if (fadeCurrent < fadeTarget) {
            fadeCurrent = (fadeCurrent + fadeSpeed * deltaTime).coerceAtMost(fadeTarget)
        } else if (fadeCurrent > fadeTarget) {
            fadeCurrent = (fadeCurrent - fadeSpeed * deltaTime).coerceAtLeast(fadeTarget)
        }

        // Apply volume with fade
        currentMusic?.volume = volume * fadeCurrent

        // Stop music when fully faded out
        if (fadeTarget == 0f && fadeCurrent <= 0f) {
            currentMusic?.stop()
        }
    }

    /**
     * Check if any music is currently playing.
     */
    fun isPlaying(): Boolean = currentMusic?.isPlaying == true

    override fun dispose() {
        menuMusic?.dispose()
        gameplayMusic?.dispose()
        menuMusic = null
        gameplayMusic = null
        currentMusic = null
    }
}
