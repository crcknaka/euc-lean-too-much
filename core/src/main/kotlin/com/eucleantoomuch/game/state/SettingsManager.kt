package com.eucleantoomuch.game.state

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

class SettingsManager {
    private val prefs: Preferences by lazy {
        Gdx.app.getPreferences("EucLeanTooMuch_Settings")
    }

    companion object {
        private const val KEY_RENDER_DISTANCE = "renderDistance"
        private const val KEY_SHOW_FPS = "showFps"
        private const val KEY_PWM_WARNING = "pwmWarning"
        private const val KEY_BEEPS_ENABLED = "beepsEnabled"
        private const val KEY_AVAS_MODE = "avasMode"
        private const val KEY_MAX_FPS = "maxFps"

        // Max FPS options (0 = unlimited/120+)
        val MAX_FPS_OPTIONS = listOf(
            "60" to 60,
            "90" to 90,
            "120+" to 0  // 0 means unlimited
        )

        // Render distance presets (in meters)
        const val RENDER_DISTANCE_LOW = 100f
        const val RENDER_DISTANCE_MEDIUM = 150f
        const val RENDER_DISTANCE_HIGH = 250f
        const val RENDER_DISTANCE_ULTRA = 400f

        val RENDER_DISTANCE_OPTIONS = listOf(
            "Low" to RENDER_DISTANCE_LOW,
            "Medium" to RENDER_DISTANCE_MEDIUM,
            "High" to RENDER_DISTANCE_HIGH,
            "Ultra" to RENDER_DISTANCE_ULTRA
        )

        // PWM warning threshold options (percentage)
        val PWM_WARNING_OPTIONS = listOf(
            "Off" to 0,
            "60%" to 60,
            "70%" to 70,
            "80%" to 80,
            "90%" to 90
        )

        // AVAS (Acoustic Vehicle Alerting System) sound options
        // 0 = Off, 1 = Electric (quiet EUC whine), 2 = Motorcycle, 3 = V8 Engine
        val AVAS_OPTIONS = listOf(
            "Off" to 0,
            "Electric" to 1,
            "Motorcycle" to 2,
            "V8 Engine" to 3
        )
    }

    var renderDistance: Float
        get() = prefs.getFloat(KEY_RENDER_DISTANCE, RENDER_DISTANCE_MEDIUM)
        set(value) {
            prefs.putFloat(KEY_RENDER_DISTANCE, value)
            prefs.flush()
        }

    var showFps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_FPS, false)
        set(value) {
            prefs.putBoolean(KEY_SHOW_FPS, value)
            prefs.flush()
        }

    // PWM warning threshold (0 = off, 60-90 = percentage)
    var pwmWarning: Int
        get() = prefs.getInteger(KEY_PWM_WARNING, 80)  // Default 80%
        set(value) {
            prefs.putInteger(KEY_PWM_WARNING, value)
            prefs.flush()
        }

    // Beeps on/off
    var beepsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BEEPS_ENABLED, true)  // Default on
        set(value) {
            prefs.putBoolean(KEY_BEEPS_ENABLED, value)
            prefs.flush()
        }

    // AVAS mode (0 = off, 1 = electric, 2 = motorcycle)
    var avasMode: Int
        get() = prefs.getInteger(KEY_AVAS_MODE, 1)  // Default to Electric
        set(value) {
            prefs.putInteger(KEY_AVAS_MODE, value)
            prefs.flush()
        }

    // Max FPS (0 = unlimited/120+, 60 = 60fps, 90 = 90fps)
    var maxFps: Int
        get() = prefs.getInteger(KEY_MAX_FPS, 0)  // Default to unlimited (120+)
        set(value) {
            prefs.putInteger(KEY_MAX_FPS, value)
            prefs.flush()
        }

    fun getMaxFpsIndex(): Int {
        val current = maxFps
        return MAX_FPS_OPTIONS.indexOfFirst { it.second == current }.takeIf { it >= 0 } ?: 2  // Default to 120+
    }

    fun setMaxFpsByIndex(index: Int) {
        if (index in MAX_FPS_OPTIONS.indices) {
            maxFps = MAX_FPS_OPTIONS[index].second
        }
    }

    fun getMaxFpsName(): String {
        return MAX_FPS_OPTIONS.getOrNull(getMaxFpsIndex())?.first ?: "120+"
    }

    fun getAvasModeIndex(): Int {
        val current = avasMode
        return AVAS_OPTIONS.indexOfFirst { it.second == current }.takeIf { it >= 0 } ?: 1  // Default to Electric
    }

    fun setAvasModeByIndex(index: Int) {
        if (index in AVAS_OPTIONS.indices) {
            avasMode = AVAS_OPTIONS[index].second
        }
    }

    fun getAvasModeName(): String {
        return AVAS_OPTIONS.getOrNull(getAvasModeIndex())?.first ?: "Electric"
    }

    fun getPwmWarningIndex(): Int {
        val current = pwmWarning
        return PWM_WARNING_OPTIONS.indexOfFirst { it.second == current }.takeIf { it >= 0 } ?: 3  // Default to 80%
    }

    fun setPwmWarningByIndex(index: Int) {
        if (index in PWM_WARNING_OPTIONS.indices) {
            pwmWarning = PWM_WARNING_OPTIONS[index].second
        }
    }

    fun getPwmWarningName(): String {
        return PWM_WARNING_OPTIONS.getOrNull(getPwmWarningIndex())?.first ?: "80%"
    }

    fun getRenderDistanceIndex(): Int {
        val current = renderDistance
        return RENDER_DISTANCE_OPTIONS.indexOfFirst { it.second == current }.takeIf { it >= 0 } ?: 1
    }

    fun setRenderDistanceByIndex(index: Int) {
        if (index in RENDER_DISTANCE_OPTIONS.indices) {
            renderDistance = RENDER_DISTANCE_OPTIONS[index].second
        }
    }

    fun getRenderDistanceName(): String {
        return RENDER_DISTANCE_OPTIONS.getOrNull(getRenderDistanceIndex())?.first ?: "Medium"
    }
}
