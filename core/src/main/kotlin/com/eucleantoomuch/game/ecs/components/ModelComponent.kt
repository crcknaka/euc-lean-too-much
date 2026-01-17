package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.utils.Pool

class ModelComponent : Component, Pool.Poolable {
    var modelInstance: ModelInstance? = null
    var visible: Boolean = true

    // LOD support - simple model for far distances
    var modelInstanceLod: ModelInstance? = null
    var useLod: Boolean = false  // Currently using LOD model

    override fun reset() {
        modelInstance = null
        modelInstanceLod = null
        visible = true
        useLod = false
    }

    /**
     * Get the current active model instance based on LOD state
     */
    fun getActiveModel(): ModelInstance? {
        return if (useLod && modelInstanceLod != null) modelInstanceLod else modelInstance
    }
}
