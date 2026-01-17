package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

class ArmComponent : Component, Pool.Poolable {
    var isLeftArm: Boolean = true
    var armAngle: Float = 0f  // 0 = down (by side), 90 = horizontal (spread)
    var targetArmAngle: Float = 0f

    override fun reset() {
        isLeftArm = true
        armAngle = 0f
        targetArmAngle = 0f
    }
}
