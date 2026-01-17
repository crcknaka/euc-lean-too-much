package com.eucleantoomuch.game.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input.Keys

class KeyboardInput : GameInput {
    private var forward: Float = 0f
    private var side: Float = 0f

    // Smoothing for keyboard input
    private val acceleration = 3f  // How fast input ramps up
    private val deceleration = 5f  // How fast input returns to zero

    override fun update(deltaTime: Float) {
        // Target values based on key presses
        var targetForward = 0f
        var targetSide = 0f

        if (Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP)) {
            targetForward = 0.6f  // Not max to leave room for more
        }
        if (Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN)) {
            targetForward = -0.5f
        }
        if (Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT)) {
            targetSide = -0.7f
        }
        if (Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT)) {
            targetSide = 0.7f
        }

        // Shift = lean more (for testing critical lean)
        if (Gdx.input.isKeyPressed(Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Keys.SHIFT_RIGHT)) {
            targetForward *= 1.5f
            targetSide *= 1.3f
        }

        // Smooth interpolation
        val forwardRate = if (targetForward != 0f) acceleration else deceleration
        val sideRate = if (targetSide != 0f) acceleration else deceleration

        forward = moveTowards(forward, targetForward, forwardRate * deltaTime)
        side = moveTowards(side, targetSide, sideRate * deltaTime)
    }

    private fun moveTowards(current: Float, target: Float, maxDelta: Float): Float {
        return when {
            target > current -> minOf(current + maxDelta, target)
            target < current -> maxOf(current - maxDelta, target)
            else -> current
        }
    }

    override fun getInput(): InputData {
        return InputData(
            forward.coerceIn(-1f, 1f),
            side.coerceIn(-1f, 1f)
        )
    }

    override fun calibrate() {
        // No calibration needed for keyboard
    }

    override fun isCalibrated(): Boolean = true
}
