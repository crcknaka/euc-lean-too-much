package com.eucleantoomuch.game.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.eucleantoomuch.game.util.Constants
import kotlin.math.abs

class AccelerometerInput : GameInput {
    // Calibration values (neutral position)
    private var calibrationX: Float = 0f
    private var calibrationY: Float = 0f
    private var calibrated: Boolean = false

    // Smoothed values
    private var smoothedX: Float = 0f
    private var smoothedY: Float = 0f

    // Sensitivity multipliers
    var forwardSensitivity: Float = 1.0f
    var sideSensitivity: Float = 1.0f

    override fun update(deltaTime: Float) {
        if (!Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer)) {
            return
        }

        // Raw accelerometer values
        // In LANDSCAPE mode (phone held horizontally):
        // - X axis = forward/back tilt (pitch)
        // - Y axis = left/right tilt (roll)
        val rawX = Gdx.input.accelerometerX
        val rawY = Gdx.input.accelerometerY

        // Apply exponential moving average for smoothing
        smoothedX = smoothedX + (rawX - smoothedX) * Constants.INPUT_SMOOTHING
        smoothedY = smoothedY + (rawY - smoothedY) * Constants.INPUT_SMOOTHING
    }

    override fun calibrate() {
        if (!Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer)) {
            calibrated = true
            return
        }

        calibrationX = Gdx.input.accelerometerX
        calibrationY = Gdx.input.accelerometerY
        smoothedX = calibrationX
        smoothedY = calibrationY
        calibrated = true
    }

    fun setCalibration(x: Float, y: Float) {
        calibrationX = x
        calibrationY = y
        smoothedX = x
        smoothedY = y
        calibrated = true
    }

    fun getCalibrationX(): Float = calibrationX
    fun getCalibrationY(): Float = calibrationY

    override fun isCalibrated(): Boolean = calibrated

    override fun getInput(): InputData {
        if (!calibrated) {
            return InputData.ZERO
        }

        // Calculate offset from calibrated neutral
        val offsetX = smoothedX - calibrationX
        val offsetY = smoothedY - calibrationY

        // Normalize to -1 to +1 range based on max expected tilt
        // INVERTED forward: negative offsetX = lean forward = positive speed
        var forward = -(offsetX / Constants.MAX_TILT).coerceIn(-1f, 1f) * forwardSensitivity
        var side = (offsetY / Constants.MAX_TILT).coerceIn(-1f, 1f) * sideSensitivity

        // Apply dead zone
        if (abs(forward) < Constants.DEAD_ZONE) forward = 0f
        if (abs(side) < Constants.DEAD_ZONE) side = 0f

        return InputData(forward, side)
    }

    fun getRawValues(): Pair<Float, Float> {
        return Pair(smoothedX, smoothedY)
    }
}
