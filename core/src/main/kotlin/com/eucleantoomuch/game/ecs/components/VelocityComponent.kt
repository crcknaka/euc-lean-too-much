package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Pool

class VelocityComponent : Component, Pool.Poolable {
    val linear = Vector3()   // m/s
    val angular = Vector3()  // deg/s (Y = turn rate)

    override fun reset() {
        linear.setZero()
        angular.setZero()
    }
}
