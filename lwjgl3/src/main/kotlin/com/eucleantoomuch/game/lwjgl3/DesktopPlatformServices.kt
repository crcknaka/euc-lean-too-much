package com.eucleantoomuch.game.lwjgl3

import com.eucleantoomuch.game.physics.JoltRagdollPhysics
import com.eucleantoomuch.game.physics.RagdollEngine
import com.eucleantoomuch.game.platform.DefaultPlatformServices

/**
 * Desktop services: same no-op vibration/synth audio as the default, but with real Jolt
 * physics. The engine is selected here rather than in shared code so that the browser build
 * never references the Jolt classes at all (TeaVM cannot compile their native loader).
 */
class DesktopPlatformServices : DefaultPlatformServices() {
    override fun createRagdollEngine(): RagdollEngine = JoltRagdollPhysics()
}
