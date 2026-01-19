package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

enum class ObstacleType {
    MANHOLE,
    PUDDLE,
    PEDESTRIAN,
    CAR,
    CURB,
    POTHOLE,
    STREET_LIGHT,
    RECYCLE_BIN
}

class ObstacleComponent : Component, Pool.Poolable {
    var type: ObstacleType = ObstacleType.MANHOLE
    var causesGameOver: Boolean = true   // True = instant death, False = effect only
    var hasBeenPassed: Boolean = false   // For scoring
    var nearMissTriggered: Boolean = false  // For near miss detection (only trigger once per obstacle)

    // For knockable objects (trash cans)
    var isKnockable: Boolean = false      // Can be knocked over instead of causing game over
    var isKnockedOver: Boolean = false    // Has been knocked over by player
    var ragdollBodyIndex: Int = -1        // Index in RagdollPhysics for knocked objects

    override fun reset() {
        type = ObstacleType.MANHOLE
        causesGameOver = true
        hasBeenPassed = false
        nearMissTriggered = false
        isKnockable = false
        isKnockedOver = false
        ragdollBodyIndex = -1
    }
}
