package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

enum class GroundType {
    ROAD,
    SIDEWALK,
    BUILDING
}

class GroundComponent : Component, Pool.Poolable {
    var type: GroundType = GroundType.ROAD
    var chunkIndex: Int = 0

    override fun reset() {
        type = GroundType.ROAD
        chunkIndex = 0
    }
}
