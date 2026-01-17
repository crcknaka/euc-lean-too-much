package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

class ArmComponent : Component, Pool.Poolable {
    var isLeftArm: Boolean = true
    var armAngle: Float = 0f  // 0 = down (by side), 90 = horizontal (spread)
    var targetArmAngle: Float = 0f
    var waveTime: Float = 0f  // Timer for waving animation
    var waveOffset: Float = 0f  // Current wave offset

    override fun reset() {
        isLeftArm = true
        armAngle = 0f
        targetArmAngle = 0f
        waveTime = 0f
        waveOffset = 0f
    }
}
