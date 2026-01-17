package com.eucleantoomuch.game.procedural

import com.eucleantoomuch.game.util.Constants

class DifficultyScaler {
    /**
     * Get normalized difficulty (0-1) based on distance traveled
     */
    fun getDifficulty(distance: Float): Float {
        return when {
            distance < Constants.EASY_DISTANCE -> 0f
            distance > Constants.HARD_DISTANCE -> 1f
            else -> (distance - Constants.EASY_DISTANCE) / (Constants.HARD_DISTANCE - Constants.EASY_DISTANCE)
        }
    }

    /**
     * Get obstacle spawn density (probability per chunk section)
     */
    fun getObstacleDensity(distance: Float): Float {
        return lerp(0.3f, 0.7f, getDifficulty(distance))
    }

    /**
     * Get pedestrian spawn probability
     */
    fun getPedestrianProbability(distance: Float): Float {
        return lerp(0.25f, 0.5f, getDifficulty(distance))  // More pedestrians in the city
    }

    /**
     * Get car spawn probability
     */
    fun getCarProbability(distance: Float): Float {
        return lerp(0.05f, 0.25f, getDifficulty(distance))
    }

    /**
     * Get pedestrian movement speed
     */
    fun getPedestrianSpeed(distance: Float): Float {
        return lerp(Constants.PEDESTRIAN_MIN_SPEED, Constants.PEDESTRIAN_MAX_SPEED, getDifficulty(distance))
    }

    /**
     * Get car speed
     */
    fun getCarSpeed(distance: Float): Float {
        return lerp(Constants.CAR_MIN_SPEED, Constants.CAR_MAX_SPEED, getDifficulty(distance))
    }

    /**
     * Get minimum spacing between obstacles
     */
    fun getMinObstacleSpacing(distance: Float): Float {
        return lerp(Constants.MAX_OBSTACLE_SPACING, Constants.MIN_OBSTACLE_SPACING, getDifficulty(distance))
    }

    private fun lerp(start: Float, end: Float, t: Float): Float {
        return start + (end - start) * t
    }
}
