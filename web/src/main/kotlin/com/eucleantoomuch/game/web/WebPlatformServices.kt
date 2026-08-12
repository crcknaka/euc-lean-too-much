package com.eucleantoomuch.game.web

import com.eucleantoomuch.game.platform.DefaultPlatformServices

/**
 * Browser platform layer.
 *
 * Sound effects and music come from the shared code through libGDX's audio backend, so the
 * only things that need a browser-specific answer here are haptics (the Vibration API) and
 * the fact that a tab cannot quit itself.
 *
 * Ragdolls run on the same Jolt engine as desktop and Android, compiled to wasm. Its loader
 * is asynchronous, which JoltRagdollPhysics already accounts for: until the module is in, it
 * reports not-ready and the game plays the scripted fall animation instead.
 */
class WebPlatformServices : DefaultPlatformServices() {

    private val vibrationSupported: Boolean = WebVibration.isSupported()

    override fun hasVibrator(): Boolean = vibrationSupported

    override fun vibrate(durationMs: Long, amplitude: Int) {
        if (vibrationSupported) WebVibration.vibrate(durationMs.toInt())
    }

    override fun cancelVibration() {
        if (vibrationSupported) WebVibration.vibrate(0)
    }

    override fun canExit(): Boolean = false

    override fun createRagdollEngine(): com.eucleantoomuch.game.physics.RagdollEngine =
        com.eucleantoomuch.game.physics.JoltRagdollPhysics()

    override fun createTiltProvider(): com.eucleantoomuch.game.input.TiltProvider =
        WebTiltProvider()

    override fun hasTouchScreen(): Boolean = WebDeviceMotion.hasTouchScreen()
}
