package com.eucleantoomuch.game.web

import com.eucleantoomuch.game.input.TiltProvider

/**
 * Tilt readings from the browser's DeviceMotion sensor.
 *
 * Reports unavailable until real readings arrive, which is what lets the game fall back to
 * drag-steering on a phone where the sensor is blocked (plain http) or declined (iOS), and
 * switch over by itself the moment permission is granted.
 */
class WebTiltProvider : TiltProvider {

    init {
        // Harmless on a desktop browser: with no sensor the events never fire and this stays
        // unavailable, so the game keeps the keyboard.
        WebDeviceMotion.enable()
    }

    override fun isAvailable(): Boolean = WebDeviceMotion.isAvailable()
    override fun x(): Float = WebDeviceMotion.x()
    override fun y(): Float = WebDeviceMotion.y()
}
