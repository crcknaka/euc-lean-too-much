package com.eucleantoomuch.game.lwjgl3

import com.eucleantoomuch.game.physics.JoltRagdollPhysics
import com.eucleantoomuch.game.physics.RagdollEngine
import com.eucleantoomuch.game.platform.GdxSoundPlatformServices

/**
 * Desktop services.
 *
 * Sound effects come from the shared .ogg files via [GdxSoundPlatformServices]; the beeps,
 * motor voice and wobble rumble, which have no file behind them, are synthesised in
 * [DesktopSynth]. Before this the desktop build inherited the shared no-ops and was silent
 * apart from the music - no impacts, no powerups, no overspeed warning.
 *
 * The ragdoll engine is chosen here rather than in shared code so the browser build never
 * references the Jolt classes at all (TeaVM cannot compile their native loader).
 */
class DesktopPlatformServices : GdxSoundPlatformServices() {

    private val synth = DesktopSynth()

    override fun createRagdollEngine(): RagdollEngine = JoltRagdollPhysics()

    /** A desktop GPU is not the constraint here, and the view is what the game is worth looking at for. */
    override fun defaultRenderDistance(): Float =
        com.eucleantoomuch.game.state.SettingsManager.RENDER_DISTANCE_ULTRA

    override fun playBeep(frequencyHz: Int, durationMs: Int) = synth.beep(frequencyHz, durationMs)

    override fun playCrashSound(intensity: Float) = synth.crash(intensity)

    override fun startMotorSound(mode: Int) = synth.startMotor(mode)

    override fun updateMotorSound(speed: Float, pwm: Float, acceleration: Float) =
        synth.updateMotor(speed, pwm)

    override fun stopMotorSound() = synth.stopMotor()

    override fun isMotorSoundPlaying(): Boolean = synth.isMotorPlaying()

    override fun playWobbleSound(intensity: Float) = synth.wobble(intensity)

    override fun stopWobbleSound() = synth.stopWobble()

    override fun releaseAudio() {
        synth.dispose()
        super.releaseAudio()
    }
}
