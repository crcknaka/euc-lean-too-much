package com.eucleantoomuch.game.web

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.eucleantoomuch.game.platform.DefaultPlatformServices

/**
 * Browser platform layer.
 *
 * Sound effects come from the same .ogg files Android plays through SoundPool - they live in
 * assets/sounds so both platforms can reach them - while the beeps, motor whine and wobble
 * rumble, which Android synthesises sample-by-sample, are rebuilt with Web Audio (see
 * [WebAudio]). Without this the browser build was silent apart from the music: every one of
 * these calls falls back to a no-op in the shared defaults.
 *
 * Ragdolls run on the same Jolt engine as desktop and Android, compiled to wasm. Its loader
 * is asynchronous, which JoltRagdollPhysics already accounts for: until the module is in, it
 * reports not-ready and the game plays the scripted fall animation instead.
 */
class WebPlatformServices : DefaultPlatformServices() {

    private val vibrationSupported: Boolean = WebVibration.isSupported()

    /**
     * Loaded on first use, not up front: this object is built before WebApplication has
     * installed Gdx.audio, so touching it in the constructor would fail. A name that failed
     * to load is remembered as absent so a missing file cannot retry every frame.
     */
    private val sounds = HashMap<String, Sound?>()

    private var motorPlaying = false
    private var wobblePlaying = false

    private fun play(name: String, volume: Float = 1f) {
        val sound = sounds.getOrPut(name) {
            try {
                Gdx.audio?.newSound(Gdx.files.internal("sounds/$name.ogg"))
            } catch (t: Throwable) {
                Gdx.app?.error("WebPlatformServices", "sound $name failed to load: ${t.message}")
                null
            }
        } ?: return

        // Browsers keep the audio context suspended until the page has been interacted with;
        // by the time anything in-game fires, the player has tapped through the menu.
        WebAudio.resume()
        sound.play(volume.coerceIn(0f, 1f))
    }

    override fun hasVibrator(): Boolean = vibrationSupported

    override fun vibrate(durationMs: Long, amplitude: Int) {
        if (vibrationSupported) WebVibration.vibrate(durationMs.toInt())
    }

    override fun cancelVibration() {
        if (vibrationSupported) WebVibration.vibrate(0)
    }

    override fun playBeep(frequencyHz: Int, durationMs: Int) {
        WebAudio.init()
        WebAudio.resume()
        WebAudio.beep(frequencyHz, durationMs)
    }

    override fun playCrashSound(intensity: Float) {
        WebAudio.init()
        WebAudio.resume()
        WebAudio.crash(intensity)
    }

    override fun startMotorSound(mode: Int) {
        if (mode == 0) return
        WebAudio.init()
        WebAudio.resume()
        WebAudio.motorStart(mode)
        motorPlaying = true
    }

    override fun updateMotorSound(speed: Float, pwm: Float, acceleration: Float) {
        if (motorPlaying) WebAudio.motorUpdate(speed, pwm)
    }

    override fun stopMotorSound() {
        if (!motorPlaying) return
        WebAudio.motorStop()
        motorPlaying = false
    }

    override fun isMotorSoundPlaying(): Boolean = motorPlaying

    override fun playWobbleSound(intensity: Float) {
        WebAudio.init()
        WebAudio.resume()
        WebAudio.wobble(intensity)
        wobblePlaying = true
    }

    override fun stopWobbleSound() {
        if (!wobblePlaying) return
        WebAudio.wobbleStop()
        wobblePlaying = false
    }

    override fun playPowerupSound() = play("powerup")
    override fun playWhooshSound() = play("swoosh")
    override fun playPigeonFlyOffSound() = play("pigeon_wings")
    override fun playManholeSound() = play("manhole")
    override fun playWaterSplashSound() = play("water_splash")
    override fun playStreetLightImpactSound(volume: Float) = play("impact_street_light", volume)
    override fun playRecycleBinImpactSound(volume: Float) = play("impact_recycle", volume)
    override fun playPersonImpactSound(volume: Float) = play("personimpact", volume)
    override fun playGenericHitSound(volume: Float) = play("hit1", volume)
    override fun playCarCrashSound(volume: Float) = play("carcrash", volume)
    override fun playBenchImpactSound(volume: Float) = play("bench", volume)

    override fun releaseAudio() {
        stopMotorSound()
        stopWobbleSound()
        sounds.values.forEach { it?.dispose() }
        sounds.clear()
    }

    override fun canExit(): Boolean = false

    override fun createRagdollEngine(): com.eucleantoomuch.game.physics.RagdollEngine =
        com.eucleantoomuch.game.physics.JoltRagdollPhysics()

    override fun createTiltProvider(): com.eucleantoomuch.game.input.TiltProvider =
        WebTiltProvider()

    override fun hasTouchScreen(): Boolean = WebDeviceMotion.hasTouchScreen()
}
