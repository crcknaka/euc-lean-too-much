package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Pool

class AirplaneComponent : Component, Pool.Poolable {
    // Flight parameters
    var speed: Float = 80f              // Speed in units per second (fast, high altitude)
    val direction = Vector3(0f, 0f, 1f) // Normalized flight direction
    var altitude: Float = 120f          // Height above ground

    // Contrail management
    var contrailEnabled: Boolean = true
    var timeSinceLastSegment: Float = 0f
    var contrailSpacing: Float = 0.15f  // Time between contrail segments

    // Lifetime management
    var lifetime: Float = 0f            // How long the plane has been flying
    var maxLifetime: Float = 60f        // Despawn after this many seconds

    override fun reset() {
        speed = 80f
        direction.set(0f, 0f, 1f)
        altitude = 120f
        contrailEnabled = true
        timeSinceLastSegment = 0f
        contrailSpacing = 0.15f
        lifetime = 0f
        maxLifetime = 60f
    }
}
