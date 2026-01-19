package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.utils.Pool
import net.mgsx.gltf.scene3d.scene.Scene

class ModelComponent : Component, Pool.Poolable {
    var modelInstance: ModelInstance? = null
    var visible: Boolean = true

    // LOD support - simple model for far distances
    var modelInstanceLod: ModelInstance? = null
    var useLod: Boolean = false  // Currently using LOD model

    // PBR flag - true for GLTF/GLB models that need SceneManager rendering
    var isPbr: Boolean = false

    // Scene for gdx-gltf PBR models (contains proper materials)
    var scene: Scene? = null

    override fun reset() {
        modelInstance = null
        modelInstanceLod = null
        visible = true
        useLod = false
        isPbr = false
        scene = null
    }

    /**
     * Get the current active model instance based on LOD state
     */
    fun getActiveModel(): ModelInstance? {
        return if (useLod && modelInstanceLod != null) modelInstanceLod else modelInstance
    }
}
