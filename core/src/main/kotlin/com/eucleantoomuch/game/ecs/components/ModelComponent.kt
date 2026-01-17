package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.utils.Pool

class ModelComponent : Component, Pool.Poolable {
    var modelInstance: ModelInstance? = null
    var visible: Boolean = true

    override fun reset() {
        modelInstance = null
        visible = true
    }
}
