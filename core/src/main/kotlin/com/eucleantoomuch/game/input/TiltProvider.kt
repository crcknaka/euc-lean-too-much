package com.eucleantoomuch.game.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input

/**
 * Where tilt readings come from.
 *
 * Values are gravity-including acceleration in m/s², in the same screen-aligned landscape
 * frame libGDX reports on Android: [x] is the forward/back tilt, [y] the left/right one.
 * Keeping the units identical is what lets calibration, smoothing and [Constants.MAX_TILT]
 * work unchanged no matter which platform the numbers came from.
 *
 * [isAvailable] may start out false and become true later - in a browser the sensor only
 * starts after the player grants permission - so callers must re-check rather than decide
 * once at startup.
 */
interface TiltProvider {
    fun isAvailable(): Boolean
    fun x(): Float
    fun y(): Float
}

/** Reads libGDX's own accelerometer: the right answer on Android and on desktop (where it is simply absent). */
class GdxTiltProvider : TiltProvider {
    override fun isAvailable(): Boolean =
        Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer)

    override fun x(): Float = Gdx.input.accelerometerX
    override fun y(): Float = Gdx.input.accelerometerY
}
