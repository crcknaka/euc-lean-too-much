package com.eucleantoomuch.game.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.eucleantoomuch.game.EucGame

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("EUC Rider - Lean too much")
        setWindowedMode(1280, 720)
        setForegroundFPS(60)
        useVsync(true)
        setResizable(true)
    }

    Lwjgl3Application(EucGame(), config)
}
