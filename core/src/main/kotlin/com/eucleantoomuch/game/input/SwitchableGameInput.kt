package com.eucleantoomuch.game.input

/**
 * Forwards to whichever input is currently in charge.
 *
 * The physics system is handed its input once, at construction, but the right source is not
 * always known by then: in a browser the tilt sensor only starts after the player grants
 * permission, which happens long after startup. Passing this wrapper instead lets the game
 * swap the real source underneath without rebuilding the ECS.
 */
class SwitchableGameInput(delegate: GameInput) : GameInput {

    var delegate: GameInput = delegate
        private set

    fun switchTo(newDelegate: GameInput) {
        if (newDelegate !== delegate) {
            delegate = newDelegate
        }
    }

    override fun update(deltaTime: Float) = delegate.update(deltaTime)
    override fun getInput(): InputData = delegate.getInput()
    override fun calibrate() = delegate.calibrate()
    override fun isCalibrated(): Boolean = delegate.isCalibrated()
}
