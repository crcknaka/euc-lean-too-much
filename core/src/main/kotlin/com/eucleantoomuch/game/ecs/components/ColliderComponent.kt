package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Pool

class ColliderComponent : Component, Pool.Poolable {
    val bounds = BoundingBox()
    val halfExtents = Vector3()
    val offset = Vector3()  // Offset from entity position to collider center

    // Collision groups for filtering
    var collisionGroup: Int = 0
    var collisionMask: Int = -1  // Collide with everything by default

    override fun reset() {
        bounds.clr()
        halfExtents.setZero()
        offset.setZero()
        collisionGroup = 0
        collisionMask = -1
    }

    fun setSize(width: Float, height: Float, depth: Float): ColliderComponent {
        halfExtents.set(width / 2f, height / 2f, depth / 2f)
        // Default Y offset to center the collider above the position (entity at feet)
        offset.set(0f, halfExtents.y, 0f)
        return this
    }

    fun updateBounds(position: Vector3) {
        // Apply offset to center the collider correctly
        // Calculate center position (position + offset)
        val centerX = position.x + offset.x
        val centerY = position.y + offset.y
        val centerZ = position.z + offset.z

        bounds.min.set(centerX - halfExtents.x, centerY - halfExtents.y, centerZ - halfExtents.z)
        bounds.max.set(centerX + halfExtents.x, centerY + halfExtents.y, centerZ + halfExtents.z)
    }
}

object CollisionGroups {
    const val PLAYER = 1
    const val OBSTACLE = 2
    const val ENVIRONMENT = 4
}
