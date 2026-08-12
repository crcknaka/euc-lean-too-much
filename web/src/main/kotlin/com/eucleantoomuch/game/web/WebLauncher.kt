@file:JvmName("WebLauncher")

package com.eucleantoomuch.game.web

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration
import com.eucleantoomuch.game.EucGame

/**
 * Browser entry point (TeaVM).
 *
 * Input note: EucGame picks KeyboardInput automatically when no accelerometer
 * peripheral is available, which is the case in a browser - no extra wiring needed.
 */
fun main() {
    val config = WebApplicationConfiguration("canvas").apply {
        // 0/0 means "fill the space the canvas container reports", which our index.html
        // stretches to the whole viewport - so the game resizes with the browser window.
        // (Leaving the defaults gives the canvas' own attributes, i.e. 0x0 and a blank page.)
        width = 0
        height = 0
        // Draw at the display's real pixel density. Without this the backing store is sized
        // in CSS pixels and the browser upscales it, which on any Retina/HiDPI screen makes
        // the whole game - text most visibly - look soft and blocky.
        usePhysicalPixels = true
        antialiasing = true
        powerPreference = "high-performance"
    }
    WebApplication(WebCrashGuard(EucGame(WebPlatformServices())), config)
}
