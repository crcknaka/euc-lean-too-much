package com.eucleantoomuch.game.input

data class InputData(
    val forward: Float,  // -1 (back) to +1 (forward)
    val side: Float      // -1 (left) to +1 (right)
) {
    companion object {
        val ZERO = InputData(0f, 0f)
    }
}
