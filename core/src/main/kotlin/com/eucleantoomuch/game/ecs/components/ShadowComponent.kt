package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.utils.Pool

/**
 * Component for blob shadow rendering.
 * Entities with this component will have a shadow rendered beneath them.
 */
class ShadowComponent : Component, Pool.Poolable {
    var shadowInstance: ModelInstance? = null

    /** Shadow size multiplier (1.0 = default size) */
    var scale: Float = 1f

    /** Offset from entity position (usually slightly above ground) */
    var yOffset: Float = 0.01f

    /** X offset for shadow (used for building shadows towards road) */
    var xOffset: Float = 0f

    /** Whether shadow should be visible */
    var visible: Boolean = true

    override fun reset() {
        shadowInstance = null
        scale = 1f
        yOffset = 0.01f
        xOffset = 0f
        visible = true
    }
}
