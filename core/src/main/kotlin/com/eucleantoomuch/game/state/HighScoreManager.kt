package com.eucleantoomuch.game.state

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

class HighScoreManager {
    private val prefs: Preferences by lazy {
        Gdx.app.getPreferences("EucLeanTooMuch")
    }

    companion object {
        private const val KEY_HIGH_SCORE = "highScore"
        private const val KEY_HIGH_SCORE_HARDCORE = "highScoreHardcore"
        private const val KEY_HIGH_SCORE_NIGHT_HARDCORE = "highScoreNightHardcore"
        private const val KEY_MAX_DISTANCE = "maxDistance"
        private const val KEY_GAMES_PLAYED = "gamesPlayed"
        private const val KEY_MAX_NEAR_MISSES = "maxNearMisses"
        private const val KEY_CALIBRATION_X = "calibrationX"
        private const val KEY_CALIBRATION_Y = "calibrationY"
    }

    // Endless mode high score
    var highScore: Int
        get() = prefs.getInteger(KEY_HIGH_SCORE, 0)
        set(value) {
            if (value > highScore) {
                prefs.putInteger(KEY_HIGH_SCORE, value)
                prefs.flush()
            }
        }

    // Hardcore mode high score
    var hardcoreHighScore: Int
        get() = prefs.getInteger(KEY_HIGH_SCORE_HARDCORE, 0)
        set(value) {
            if (value > hardcoreHighScore) {
                prefs.putInteger(KEY_HIGH_SCORE_HARDCORE, value)
                prefs.flush()
            }
        }

    // Night Hardcore mode high score
    var nightHardcoreHighScore: Int
        get() = prefs.getInteger(KEY_HIGH_SCORE_NIGHT_HARDCORE, 0)
        set(value) {
            if (value > nightHardcoreHighScore) {
                prefs.putInteger(KEY_HIGH_SCORE_NIGHT_HARDCORE, value)
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

    private var hasDeferredFlush = false

    fun recordGame(session: GameSession): Boolean {
        // Determine which high score to compare against based on game mode
        val currentHighScore = when {
            session.isNightHardcoreMode -> prefs.getInteger(KEY_HIGH_SCORE_NIGHT_HARDCORE, 0)
            session.isHardcoreMode -> prefs.getInteger(KEY_HIGH_SCORE_HARDCORE, 0)
            else -> prefs.getInteger(KEY_HIGH_SCORE, 0)
        }
        val isNewHighScore = session.score > currentHighScore

        // Batch all writes - defer flush to avoid collision-frame lag
        // Save to the appropriate high score based on mode
        when {
            session.isNightHardcoreMode -> {
                if (session.score > prefs.getInteger(KEY_HIGH_SCORE_NIGHT_HARDCORE, 0)) {
                    prefs.putInteger(KEY_HIGH_SCORE_NIGHT_HARDCORE, session.score)
                }
            }
            session.isHardcoreMode -> {
                if (session.score > prefs.getInteger(KEY_HIGH_SCORE_HARDCORE, 0)) {
                    prefs.putInteger(KEY_HIGH_SCORE_HARDCORE, session.score)
                }
            }
            else -> {
                if (session.score > prefs.getInteger(KEY_HIGH_SCORE, 0)) {
                    prefs.putInteger(KEY_HIGH_SCORE, session.score)
                }
            }
        }

        if (session.distanceTraveled > prefs.getFloat(KEY_MAX_DISTANCE, 0f)) {
            prefs.putFloat(KEY_MAX_DISTANCE, session.distanceTraveled)
        }
        if (session.nearMisses > prefs.getInteger(KEY_MAX_NEAR_MISSES, 0)) {
            prefs.putInteger(KEY_MAX_NEAR_MISSES, session.nearMisses)
        }
        prefs.putInteger(KEY_GAMES_PLAYED, gamesPlayed + 1)
        hasDeferredFlush = true
        return isNewHighScore
    }

    fun flushDeferred() {
        if (hasDeferredFlush) {
            prefs.flush()
            hasDeferredFlush = false
        }
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
