package com.eucleantoomuch.game.input

import com.eucleantoomuch.game.util.Constants
import kotlin.math.abs

/**
 * Tilt steering. Reads through a [TiltProvider] rather than libGDX directly, so the browser
 * build can feed it the DeviceMotion sensor - the values mean the same thing either way.
 */
class AccelerometerInput(
    private val tilt: TiltProvider = GdxTiltProvider()
) : GameInput {
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
        if (!tilt.isAvailable()) {
            return
        }

        // Raw accelerometer values
        // In LANDSCAPE mode (phone held horizontally):
        // - X axis = forward/back tilt (pitch)
        // - Y axis = left/right tilt (roll)
        val rawX = tilt.x()
        val rawY = tilt.y()

        // Apply exponential moving average for smoothing
        smoothedX = smoothedX + (rawX - smoothedX) * Constants.INPUT_SMOOTHING
        smoothedY = smoothedY + (rawY - smoothedY) * Constants.INPUT_SMOOTHING
    }

    override fun calibrate() {
        if (!tilt.isAvailable()) {
            calibrated = true
            return
        }

        calibrationX = tilt.x()
        calibrationY = tilt.y()
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
