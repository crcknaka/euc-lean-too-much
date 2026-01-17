package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Pool

class TransformComponent : Component, Pool.Poolable {
    val position = Vector3()
    val rotation = Quaternion()
    val scale = Vector3(1f, 1f, 1f)

    var yaw: Float = 0f  // Y-axis rotation in degrees (for simpler turning)

    override fun reset() {
        position.setZero()
        rotation.idt()
        scale.set(1f, 1f, 1f)
        yaw = 0f
    }

    fun setPosition(x: Float, y: Float, z: Float): TransformComponent {
        position.set(x, y, z)
        return this
    }

    fun updateRotationFromYaw() {
        rotation.setFromAxis(Vector3.Y, yaw)
    }
}
