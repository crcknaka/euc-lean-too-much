package com.eucleantoomuch.game.state

import com.eucleantoomuch.game.util.Constants

class GameSession {
    var distanceTraveled: Float = 0f
        private set
    var currentSpeed: Float = 0f
        private set
    var maxSpeed: Float = 0f
        private set
    var obstaclesAvoided: Int = 0
    var startTime: Long = System.currentTimeMillis()
        private set

    val score: Int
        get() = (distanceTraveled * Constants.POINTS_PER_METER).toInt()

    val playTimeSeconds: Float
        get() = (System.currentTimeMillis() - startTime) / 1000f

    fun update(deltaTime: Float, speed: Float) {
        distanceTraveled += speed * deltaTime
        currentSpeed = speed
        if (speed > maxSpeed) {
            maxSpeed = speed
        }
    }

    fun reset() {
        distanceTraveled = 0f
        currentSpeed = 0f
        maxSpeed = 0f
        obstaclesAvoided = 0
        startTime = System.currentTimeMillis()
    }
}
