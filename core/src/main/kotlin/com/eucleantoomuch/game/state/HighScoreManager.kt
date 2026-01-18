package com.eucleantoomuch.game.state

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

class HighScoreManager {
    private val prefs: Preferences by lazy {
        Gdx.app.getPreferences("EucLeanTooMuch")
    }

    companion object {
        private const val KEY_HIGH_SCORE = "highScore"
        private const val KEY_MAX_DISTANCE = "maxDistance"
        private const val KEY_GAMES_PLAYED = "gamesPlayed"
        private const val KEY_MAX_NEAR_MISSES = "maxNearMisses"
        private const val KEY_CALIBRATION_X = "calibrationX"
        private const val KEY_CALIBRATION_Y = "calibrationY"
    }

    var highScore: Int
        get() = prefs.getInteger(KEY_HIGH_SCORE, 0)
        set(value) {
            if (value > highScore) {
                prefs.putInteger(KEY_HIGH_SCORE, value)
                prefs.flush()
            }
        }

    var maxDistance: Float
        get() = prefs.getFloat(KEY_MAX_DISTANCE, 0f)
        set(value) {
            if (value > maxDistance) {
                prefs.putFloat(KEY_MAX_DISTANCE, value)
                prefs.flush()
            }
        }

    val gamesPlayed: Int
        get() = prefs.getInteger(KEY_GAMES_PLAYED, 0)

    var maxNearMisses: Int
        get() = prefs.getInteger(KEY_MAX_NEAR_MISSES, 0)
        set(value) {
            if (value > maxNearMisses) {
                prefs.putInteger(KEY_MAX_NEAR_MISSES, value)
                prefs.flush()
            }
        }

    fun recordGame(session: GameSession): Boolean {
        val isNewHighScore = session.score > highScore
        highScore = session.score
        maxDistance = session.distanceTraveled
        maxNearMisses = session.nearMisses
        prefs.putInteger(KEY_GAMES_PLAYED, gamesPlayed + 1)
        prefs.flush()
        return isNewHighScore
    }

    fun saveCalibration(x: Float, y: Float) {
        prefs.putFloat(KEY_CALIBRATION_X, x)
        prefs.putFloat(KEY_CALIBRATION_Y, y)
        prefs.flush()
    }

    fun getCalibrationX(): Float = prefs.getFloat(KEY_CALIBRATION_X, 0f)
    fun getCalibrationY(): Float = prefs.getFloat(KEY_CALIBRATION_Y, 0f)
    fun hasCalibration(): Boolean = prefs.contains(KEY_CALIBRATION_X)
}
