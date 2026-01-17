package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Pool

class ColliderComponent : Component, Pool.Poolable {
    val bounds = BoundingBox()
    val halfExtents = Vector3()

    // Collision groups for filtering
    var collisionGroup: Int = 0
    var collisionMask: Int = -1  // Collide with everything by default

    override fun reset() {
        bounds.clr()
        halfExtents.setZero()
        collisionGroup = 0
        collisionMask = -1
    }

    fun setSize(width: Float, height: Float, depth: Float): ColliderComponent {
        halfExtents.set(width / 2f, height / 2f, depth / 2f)
        return this
    }

    fun updateBounds(position: Vector3) {
        bounds.min.set(position).sub(halfExtents)
        bounds.max.set(position).add(halfExtents)
    }
}

object CollisionGroups {
    const val PLAYER = 1
    const val OBSTACLE = 2
    const val ENVIRONMENT = 4
}
