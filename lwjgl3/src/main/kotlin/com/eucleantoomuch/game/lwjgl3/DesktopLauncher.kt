package com.eucleantoomuch.game.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
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
        // Default GL20 (compatibility profile). The previous GL 3.2 core-profile emulation
        // broke every legacy-GLSL shader on macOS (attribute/varying + no #version):
        // the game's post-processing shaders AND libGDX's own SpriteBatch/ShapeRenderer
        // defaults failed to compile, crashing at startup. GL20 matches Android's GLES2
        // path, so desktop renders exactly what the phone renders.
    }

    Lwjgl3Application(EucGame(DesktopPlatformServices()), config)
}
