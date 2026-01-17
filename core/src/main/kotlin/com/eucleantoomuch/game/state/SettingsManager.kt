package com.eucleantoomuch.game.state

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

class SettingsManager {
    private val prefs: Preferences by lazy {
        Gdx.app.getPreferences("EucLeanTooMuch_Settings")
    }

    companion object {
        private const val KEY_RENDER_DISTANCE = "renderDistance"

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
    }

    var renderDistance: Float
        get() = prefs.getFloat(KEY_RENDER_DISTANCE, RENDER_DISTANCE_MEDIUM)
        set(value) {
            prefs.putFloat(KEY_RENDER_DISTANCE, value)
            prefs.flush()
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
