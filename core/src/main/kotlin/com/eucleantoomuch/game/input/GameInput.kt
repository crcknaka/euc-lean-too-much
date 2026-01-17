package com.eucleantoomuch.game.input

interface GameInput {
    fun update(deltaTime: Float)
    fun getInput(): InputData
    fun calibrate()
    fun isCalibrated(): Boolean
}
