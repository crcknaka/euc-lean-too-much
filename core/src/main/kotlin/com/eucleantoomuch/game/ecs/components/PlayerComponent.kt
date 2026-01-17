package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

class PlayerComponent : Component, Pool.Poolable {
    var isAlive: Boolean = true
    var hasFallen: Boolean = false

    override fun reset() {
        isAlive = true
        hasFallen = false
    }
}
