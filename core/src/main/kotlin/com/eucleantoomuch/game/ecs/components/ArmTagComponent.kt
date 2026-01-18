package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

/**
 * Tag component to mark arm entities and identify left vs right.
 */
class ArmTagComponent : Component, Pool.Poolable {
    var isLeft: Boolean = true

    override fun reset() {
        isLeft = true
    }
}
