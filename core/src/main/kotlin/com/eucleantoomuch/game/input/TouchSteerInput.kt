package com.eucleantoomuch.game.input

import com.badlogic.gdx.Gdx
import com.eucleantoomuch.game.util.Constants
import kotlin.math.abs

/**
 * Drag-to-steer, for touch devices with no usable tilt sensor - which in practice means a
 * phone browser, where the sensor needs HTTPS and, on iOS, a permission the player may refuse.
 *
 * The finger acts as a virtual stick anchored wherever it first lands, so there is no on-screen
 * pad to miss and it works the same in either hand: drag up to lean forward, down to brake,
 * sideways to turn. Full deflection is [DEFLECTION_FRACTION] of the screen height, which keeps
 * the gesture the same physical size on any display.
 *
 * A plain tap produces no steering at all, on purpose: the game uses taps to cycle the camera,
 * and [wasDrag] lets it tell the two apart.
 */
class TouchSteerInput : GameInput {

    private var anchorX = 0f
    private var anchorY = 0f
    private var tracking = false
    private var dragged = false

    private var forward = 0f
    private var side = 0f

    /** True once the current (or last) touch moved far enough to count as steering, not a tap. */
    fun wasDrag(): Boolean = dragged

    override fun update(deltaTime: Float) {
        // Steering is one-finger; a second finger is the pause gesture and must not steer.
        val touching = Gdx.input.isTouched(0) && !Gdx.input.isTouched(1)

        if (!touching) {
            tracking = false
            // Release the stick immediately rather than easing out: a rider who lifts their
            // finger wants the wheel to level off now.
            forward = 0f
            side = 0f
            return
        }

        val touchX = Gdx.input.getX(0).toFloat()
        val touchY = Gdx.input.getY(0).toFloat()

        if (!tracking) {
            tracking = true
            dragged = false
            anchorX = touchX
            anchorY = touchY
            return
        }

        val radius = Gdx.graphics.height * DEFLECTION_FRACTION
        if (radius <= 0f) return

        // Screen Y grows downwards, so dragging up has to become positive forward lean.
        var newForward = ((anchorY - touchY) / radius).coerceIn(-1f, 1f)
        var newSide = ((touchX - anchorX) / radius).coerceIn(-1f, 1f)

        if (!dragged && (abs(newForward) > TAP_TOLERANCE || abs(newSide) > TAP_TOLERANCE)) {
            dragged = true
        }

        if (abs(newForward) < Constants.DEAD_ZONE) newForward = 0f
        if (abs(newSide) < Constants.DEAD_ZONE) newSide = 0f

        forward = newForward
        side = newSide
    }

    override fun getInput(): InputData = InputData(forward, side)

    override fun calibrate() {
        // Nothing to calibrate - the stick is anchored per touch.
    }

    override fun isCalibrated(): Boolean = true

    private companion object {
        const val DEFLECTION_FRACTION = 0.18f
        const val TAP_TOLERANCE = 0.08f
    }
}
