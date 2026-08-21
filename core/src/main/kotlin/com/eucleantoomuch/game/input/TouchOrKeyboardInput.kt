package com.eucleantoomuch.game.input

/**
 * Touch steering with the keyboard still live underneath it.
 *
 * Choosing touch steering outright whenever a touch screen exists locked the keyboard out on
 * every touch-screen laptop - a Windows machine with a touch display reported itself as a
 * touch device and W/A/S/D stopped working in the browser. Here a finger on the screen takes
 * over while it is down and the keyboard has the wheel the rest of the time.
 */
class TouchOrKeyboardInput(
    private val touch: TouchSteerInput,
    private val keyboard: KeyboardInput
) : GameInput {

    override fun update(deltaTime: Float) {
        touch.update(deltaTime)
        keyboard.update(deltaTime)
    }

    override fun getInput(): InputData =
        if (touch.isTouching()) touch.getInput() else keyboard.getInput()

    override fun calibrate() {
        // Neither source needs it
    }

    override fun isCalibrated(): Boolean = true
}
