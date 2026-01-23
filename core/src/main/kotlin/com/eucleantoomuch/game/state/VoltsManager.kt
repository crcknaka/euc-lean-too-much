package com.eucleantoomuch.game.state

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Preferences

/**
 * Manages the Volts currency system.
 * Volts are earned through skilled gameplay and persist between sessions.
 * They can be spent on wheel upgrades, cosmetics, etc.
 */
class VoltsManager {
    private val prefs: Preferences by lazy {
        Gdx.app.getPreferences("EucLeanTooMuch")
    }

    companion object {
        private const val KEY_TOTAL_VOLTS = "totalVolts"

        // Reward amounts
        const val NEAR_MISS_PEDESTRIAN = 5
        const val NEAR_MISS_CAR = 10
        const val SURVIVE_MANHOLE = 3
        const val PWM_RISK_5SEC = 8
        const val BEAT_HIGH_SCORE = 10
        const val VOLTS_PICKUP = 15
        const val STARTLE_PIGEONS = 10

        // Streak multiplier
        const val STREAK_THRESHOLD = 3  // Near misses needed for x2
        const val STREAK_MULTIPLIER = 2

        // PWM risk tracking
        private const val PWM_RISK_THRESHOLD = 0.9f  // 90% PWM threshold
        private const val PWM_RISK_DURATION = 5f
    }

    // Persistent total
    var totalVolts: Int
        get() = prefs.getInteger(KEY_TOTAL_VOLTS, 0)
        private set(value) {
            prefs.putInteger(KEY_TOTAL_VOLTS, value)
            prefs.flush()
        }

    // Session tracking
    var sessionVolts: Int = 0
        private set

    // Near miss streak tracking
    private var nearMissStreakCount: Int = 0
    private var nearMissStreakTimer: Float = 0f
    private val nearMissStreakWindow: Float = 5f  // seconds to keep streak alive

    // PWM risk tracking
    private var pwmRiskTimer: Float = 0f
    private var pwmRiskRewarded: Boolean = false  // Only reward once per risk period

    // Manhole survival tracking (starts true = no pending reward until manhole is hit)
    private var inManholeSurvived: Boolean = true

    // Current multiplier (from streaks)
    val currentMultiplier: Int
        get() = if (nearMissStreakCount >= STREAK_THRESHOLD) STREAK_MULTIPLIER else 1

    /**
     * Award volts for a near miss (pedestrian or car).
     * Returns the amount awarded (after multiplier).
     */
    fun awardNearMiss(isCar: Boolean): Int {
        val baseAmount = if (isCar) NEAR_MISS_CAR else NEAR_MISS_PEDESTRIAN

        // Update streak
        nearMissStreakCount++
        nearMissStreakTimer = nearMissStreakWindow

        val amount = baseAmount * currentMultiplier
        addSessionVolts(amount)
        return amount
    }

    /**
     * Award volts for surviving a manhole wobble (called after wobble ends without dying).
     */
    fun awardManholeSurvival(): Int {
        if (!inManholeSurvived) {
            inManholeSurvived = true
            addSessionVolts(SURVIVE_MANHOLE)
            return SURVIVE_MANHOLE
        }
        return 0
    }

    /**
     * Called when player hits manhole (wobble starts).
     */
    fun onManholeHit() {
        inManholeSurvived = false
    }

    /**
     * Update PWM risk timer. Awards volts after holding PWM > 90% for 5 seconds.
     * Returns amount awarded (0 if no reward this frame).
     */
    fun updatePwmRisk(pwm: Float, deltaTime: Float): Int {
        if (pwm >= PWM_RISK_THRESHOLD) {
            pwmRiskTimer += deltaTime
            if (pwmRiskTimer >= PWM_RISK_DURATION && !pwmRiskRewarded) {
                pwmRiskRewarded = true
                addSessionVolts(PWM_RISK_5SEC)
                return PWM_RISK_5SEC
            }
        } else {
            // Reset timer when PWM drops below threshold
            pwmRiskTimer = 0f
            pwmRiskRewarded = false
        }
        return 0
    }

    /**
     * Award volts for beating high score.
     */
    fun awardHighScoreBeaten(): Int {
        addSessionVolts(BEAT_HIGH_SCORE)
        return BEAT_HIGH_SCORE
    }

    /**
     * Award volts for startling a pigeon flock.
     */
    fun awardPigeonStartle(): Int {
        addSessionVolts(STARTLE_PIGEONS)
        return STARTLE_PIGEONS
    }

    /**
     * Award volts for collecting a Volts pickup in the world.
     */
    fun awardPickup(): Int {
        val amount = VOLTS_PICKUP * currentMultiplier
        addSessionVolts(amount)
        return amount
    }

    /**
     * Update per-frame state (streak timer decay).
     */
    fun update(deltaTime: Float) {
        // Decay near miss streak timer
        if (nearMissStreakTimer > 0f) {
            nearMissStreakTimer -= deltaTime
            if (nearMissStreakTimer <= 0f) {
                nearMissStreakCount = 0
                nearMissStreakTimer = 0f
            }
        }
    }

    /**
     * Finalize session - add session volts to persistent total.
     * Write is deferred; call flushDeferred() on a later frame to persist.
     */
    fun finalizeSession() {
        prefs.putInteger(KEY_TOTAL_VOLTS, totalVolts + sessionVolts)
        hasDeferredFlush = true
    }

    private var hasDeferredFlush = false

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
     * Reset for new session.
     */
    fun resetSession() {
        sessionVolts = 0
        nearMissStreakCount = 0
        nearMissStreakTimer = 0f
        pwmRiskTimer = 0f
        pwmRiskRewarded = false
        inManholeSurvived = true  // No pending reward until manhole is actually hit
    }

    private fun addSessionVolts(amount: Int) {
        sessionVolts += amount
    }
}
