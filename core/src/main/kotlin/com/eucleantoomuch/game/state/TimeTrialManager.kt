package com.eucleantoomuch.game.state

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Manages Time Trial mode progression and persistence.
 * Tracks unlocked levels and best times.
 */
class TimeTrialManager {
    private val prefs: Preferences by lazy {
        Gdx.app.getPreferences("EucLeanTooMuch")
    }

    companion object {
        private const val KEY_PREFIX_UNLOCKED = "timeTrial_unlocked_"
        private const val KEY_PREFIX_BEST_TIME = "timeTrial_bestTime_"
        private const val KEY_PREFIX_COMPLETED = "timeTrial_completed_"
    }

    // Currently selected level for session
    var selectedLevel: TimeTrialLevel? = null
        private set

    private var hasDeferredFlush = false

    /**
     * Check if a level is unlocked.
     * Level 1 is always unlocked.
     */
    fun isUnlocked(level: TimeTrialLevel): Boolean {
        if (level == TimeTrialLevel.LEVEL_1) return true
        return prefs.getBoolean(KEY_PREFIX_UNLOCKED + level.levelId, false)
    }

    /**
     * Check if a level has been completed at least once.
     */
    fun isCompleted(level: TimeTrialLevel): Boolean {
        return prefs.getBoolean(KEY_PREFIX_COMPLETED + level.levelId, false)
    }

    /**
     * Get best time for a level (or null if never completed).
     */
    fun getBestTime(level: TimeTrialLevel): Float? {
        val time = prefs.getFloat(KEY_PREFIX_BEST_TIME + level.levelId, -1f)
        return if (time < 0) null else time
    }

    /**
     * Select a level for the upcoming session.
     */
    fun selectLevel(level: TimeTrialLevel) {
        selectedLevel = level
    }

    /**
     * Clear selected level (when going back to menu).
     */
    fun clearSelection() {
        selectedLevel = null
    }

    /**
     * Record a level completion. Returns true if this is a new best time.
     * Also unlocks the next level if applicable.
     */
    fun recordCompletion(level: TimeTrialLevel, completionTime: Float): Boolean {
        // Mark as completed
        prefs.putBoolean(KEY_PREFIX_COMPLETED + level.levelId, true)

        // Check for new best time
        val previousBest = getBestTime(level)
        val isNewBest = previousBest == null || completionTime < previousBest

        if (isNewBest) {
            prefs.putFloat(KEY_PREFIX_BEST_TIME + level.levelId, completionTime)
        }

        // Unlock next level
        val nextLevel = level.nextLevel()
        if (nextLevel != null) {
            prefs.putBoolean(KEY_PREFIX_UNLOCKED + nextLevel.levelId, true)
        }

        hasDeferredFlush = true
        return isNewBest
    }

    /**
     * Flush any deferred writes to disk. Call this on a non-critical frame.
     */
    fun flushDeferred() {
        if (hasDeferredFlush) {
            prefs.flush()
            hasDeferredFlush = false
        }
    }

    /**
     * Get the highest unlocked level.
     */
    fun getHighestUnlockedLevel(): TimeTrialLevel {
        return TimeTrialLevel.entries.lastOrNull { isUnlocked(it) } ?: TimeTrialLevel.LEVEL_1
    }

    /**
     * Get count of completed levels.
     */
    fun getCompletedCount(): Int {
        return TimeTrialLevel.entries.count { isCompleted(it) }
    }
}
