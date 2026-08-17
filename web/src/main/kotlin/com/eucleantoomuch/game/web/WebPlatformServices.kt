package com.eucleantoomuch.game.web

import com.eucleantoomuch.game.platform.GdxSoundPlatformServices

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
class WebPlatformServices : GdxSoundPlatformServices() {

    private val vibrationSupported: Boolean = WebVibration.isSupported()

    private var motorPlaying = false
    private var wobblePlaying = false

    /** Browsers keep the audio context suspended until the page has been interacted with. */
    override fun onBeforePlay() {
        WebAudio.resume()
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

    override fun releaseAudio() {
        stopMotorSound()
        stopWobbleSound()
        super.releaseAudio()
    }

    override fun canExit(): Boolean = false

    override fun createRagdollEngine(): com.eucleantoomuch.game.physics.RagdollEngine =
        com.eucleantoomuch.game.physics.JoltRagdollPhysics()

    override fun createTiltProvider(): com.eucleantoomuch.game.input.TiltProvider =
        WebTiltProvider()

    override fun hasTouchScreen(): Boolean = WebDeviceMotion.hasTouchScreen()
}
