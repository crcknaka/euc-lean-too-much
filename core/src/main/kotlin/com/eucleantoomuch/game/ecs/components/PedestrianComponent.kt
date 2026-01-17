package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Pool

enum class PedestrianState {
    WALKING,
    STANDING,
    CROSSING
}

class PedestrianComponent : Component, Pool.Poolable {
    var walkSpeed: Float = 1.5f
    val direction = Vector3(1f, 0f, 0f)  // Walking direction
    var state: PedestrianState = PedestrianState.WALKING

    // Patrol bounds
    var minX: Float = -6f
    var maxX: Float = 6f

    override fun reset() {
        walkSpeed = 1.5f
        direction.set(1f, 0f, 0f)
        state = PedestrianState.WALKING
        minX = -6f
        maxX = 6f
    }
}
