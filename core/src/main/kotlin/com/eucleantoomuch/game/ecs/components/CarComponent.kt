package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

class CarComponent : Component, Pool.Poolable {
    var speed: Float = 8f
    var lane: Int = 0            // 0 = left lane, 1 = right lane
    var direction: Int = 1       // 1 = same direction as player, -1 = oncoming

    override fun reset() {
        speed = 8f
        lane = 0
        direction = 1
    }
}
