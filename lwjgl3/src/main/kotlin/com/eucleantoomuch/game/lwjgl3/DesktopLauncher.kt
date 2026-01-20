package com.eucleantoomuch.game.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration.GLEmulation
import com.eucleantoomuch.game.EucGame

fun main() {
    // Disable libGDX splash screen
    if (StartupHelper.startNewJvmIfRequired()) return

    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("EUC Rider - Lean too much")
        setWindowedMode(1280, 720)
        setForegroundFPS(60)
        useVsync(true)
        setResizable(true)
        setWindowIcon("icon128.png", "icon64.png", "icon32.png", "icon16.png")
        disableAudio(false)
        setOpenGLEmulation(GLEmulation.GL30, 3, 2)
    }

    Lwjgl3Application(EucGame(), config)
}
