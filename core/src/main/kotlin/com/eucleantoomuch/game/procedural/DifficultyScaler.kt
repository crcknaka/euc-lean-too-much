package com.eucleantoomuch.game.procedural

import com.eucleantoomuch.game.util.Constants

class DifficultyScaler {
    // Hardcore mode multipliers - more intense than normal mode
    private var isHardcoreMode = false
    private var hardcoreDifficulty = 0f  // 0-1 based on time played

    /**
     * Set hardcore mode and its time-based difficulty (0-1)
     */
    fun setHardcoreMode(enabled: Boolean, difficulty: Float = 0f) {
        isHardcoreMode = enabled
        hardcoreDifficulty = difficulty.coerceIn(0f, 1f)
    }

    /**
     * Get normalized difficulty (0-1) based on distance traveled
     * In hardcore mode, also considers time-based difficulty
     */
    fun getDifficulty(distance: Float): Float {
        val distanceDifficulty = when {
            distance < Constants.EASY_DISTANCE -> 0f
            distance > Constants.HARD_DISTANCE -> 1f
            else -> (distance - Constants.EASY_DISTANCE) / (Constants.HARD_DISTANCE - Constants.EASY_DISTANCE)
        }

        // In hardcore, take the max of distance and time-based difficulty
        return if (isHardcoreMode) {
            maxOf(distanceDifficulty, hardcoreDifficulty)
        } else {
            distanceDifficulty
        }
    }

    /**
     * Get obstacle spawn density (probability per chunk section)
     * Hardcore: higher density, faster scaling
     */
    fun getObstacleDensity(distance: Float): Float {
        val difficulty = getDifficulty(distance)
        return if (isHardcoreMode) {
            lerp(0.4f, 0.9f, difficulty)  // 40-90% density
        } else {
            lerp(0.3f, 0.7f, difficulty)  // 30-70% density
        }
    }

    /**
     * Get pedestrian spawn probability
     * Hardcore: more pedestrians
     */
    fun getPedestrianProbability(distance: Float): Float {
        val difficulty = getDifficulty(distance)
        return if (isHardcoreMode) {
            lerp(0.4f, 0.7f, difficulty)  // 40-70% chance
        } else {
            lerp(0.25f, 0.5f, difficulty)  // 25-50% chance
        }
    }

    /**
     * Get car spawn probability
     * Hardcore: many more cars
     */
    fun getCarProbability(distance: Float): Float {
        val difficulty = getDifficulty(distance)
        return if (isHardcoreMode) {
            lerp(0.3f, 0.6f, difficulty)  // 30-60% chance
        } else {
            lerp(0.15f, 0.35f, difficulty)  // 15-35% chance
        }
    }

    /**
     * Get pedestrian movement speed
     * Hardcore: faster pedestrians
     */
    fun getPedestrianSpeed(distance: Float): Float {
        val difficulty = getDifficulty(distance)
        return if (isHardcoreMode) {
            lerp(Constants.PEDESTRIAN_MIN_SPEED * 1.2f, Constants.PEDESTRIAN_MAX_SPEED * 1.5f, difficulty)
        } else {
            lerp(Constants.PEDESTRIAN_MIN_SPEED, Constants.PEDESTRIAN_MAX_SPEED, difficulty)
        }
    }

    /**
     * Get car speed
     * Hardcore: faster cars
     */
    fun getCarSpeed(distance: Float): Float {
        val difficulty = getDifficulty(distance)
        return if (isHardcoreMode) {
            lerp(Constants.CAR_MIN_SPEED * 1.3f, Constants.CAR_MAX_SPEED * 1.5f, difficulty)
        } else {
            lerp(Constants.CAR_MIN_SPEED, Constants.CAR_MAX_SPEED, difficulty)
        }
    }

    /**
     * Get minimum spacing between obstacles
     * Hardcore: closer obstacles
     */
    fun getMinObstacleSpacing(distance: Float): Float {
        val difficulty = getDifficulty(distance)
        return if (isHardcoreMode) {
            lerp(Constants.MAX_OBSTACLE_SPACING * 0.8f, Constants.MIN_OBSTACLE_SPACING * 0.7f, difficulty)
        } else {
            lerp(Constants.MAX_OBSTACLE_SPACING, Constants.MIN_OBSTACLE_SPACING, difficulty)
        }
    }

    /**
     * Get jaywalking pedestrian probability (pedestrians walking on road, not at crossings)
     * Only active in hardcore mode
     */
    fun getJaywalkingProbability(distance: Float): Float {
        if (!isHardcoreMode) return 0f
        val difficulty = getDifficulty(distance)
        return lerp(0.4f, 0.8f, difficulty)  // 40-80% chance in hardcore (high!)
    }

    /**
     * Get volts pickup spawn multiplier
     * Hardcore: more rewards to compensate for difficulty
     */
    fun getVoltsMultiplier(): Float {
        return if (isHardcoreMode) 2f else 1f
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t
    }
}
