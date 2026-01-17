package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

class ContrailSegmentComponent : Component, Pool.Poolable {
    var age: Float = 0f                 // Time since creation
    var maxAge: Float = 15f             // Time until fully faded and removed
    var initialAlpha: Float = 0.6f      // Starting opacity

    // Calculate current alpha based on age
    fun getCurrentAlpha(): Float {
        val fadeProgress = age / maxAge
        return initialAlpha * (1f - fadeProgress).coerceIn(0f, 1f)
    }

    override fun reset() {
        age = 0f
        maxAge = 15f
        initialAlpha = 0.6f
    }
}
