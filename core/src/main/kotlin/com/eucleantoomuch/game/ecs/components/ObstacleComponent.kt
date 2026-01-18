package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

enum class ObstacleType {
    MANHOLE,
    PUDDLE,
    PEDESTRIAN,
    CAR,
    CURB,
    POTHOLE
}

class ObstacleComponent : Component, Pool.Poolable {
    var type: ObstacleType = ObstacleType.MANHOLE
    var causesGameOver: Boolean = true   // True = instant death, False = effect only
    var hasBeenPassed: Boolean = false   // For scoring
    var nearMissTriggered: Boolean = false  // For near miss detection (only trigger once per obstacle)

    override fun reset() {
        type = ObstacleType.MANHOLE
        causesGameOver = true
        hasBeenPassed = false
        nearMissTriggered = false
    }
}
