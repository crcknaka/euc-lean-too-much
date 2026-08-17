package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Pool

/**
 * Component for pigeon behavior - walking, pecking, and flying away when startled
 */
class PigeonComponent : Component, Pool.Poolable {

    enum class State {
        WALKING,
        PECKING,
        STARTLED,  // Brief pause before flying
        FLYING,
        LANDED     // After flying, landed somewhere far
    }

    var state: State = State.WALKING
    var stateTimer: Float = 0f

    // Walking behavior
    var walkSpeed: Float = 0.8f
    var walkDirection: Float = 0f  // Current walking direction (yaw)
    var timeToNextDirectionChange: Float = 2f

    // Pecking behavior
    var peckDuration: Float = 0.5f
    var timeBetweenPecks: Float = 3f

    // Flight behavior
    var flightSpeed: Float = 8f
    var flightAltitude: Float = 0f  // Current altitude above ground
    var maxFlightAltitude: Float = 15f
    var flightDirection = Vector3()  // Direction flying away
    var verticalSpeed: Float = 0f

    // Player detection
    var detectionRadius: Float = 4f  // Distance at which pigeon gets startled
    var isStartled: Boolean = false

    // Animation
    var animationTime: Float = 0f
    var bobOffset: Float = 0f  // For head bobbing while walking

    // Take-off shaping. Wingbeat frequency, downstroke amplitude and thrust are all highest on
    // the first beats and fall away with each one (Berg & Biewener, J Exp Biol 2010), so the
    // flight is driven from time-since-launch rather than from constant speeds.
    var flightTime: Float = 0f
    var wingPhase: Float = 0f
    var pitch: Float = 0f          // Body pitch in degrees; nose-down on the first downstroke
    var crouch: Float = 0f         // Pre-launch compression, 0..1

    // Group behavior - pigeons in same flock startle together
    var flockId: Int = 0

    // Spawn position (to check if flew far enough)
    val spawnPosition = Vector3()

    override fun reset() {
        state = State.WALKING
        stateTimer = 0f
        walkSpeed = 0.8f
        walkDirection = 0f
        timeToNextDirectionChange = 2f
        peckDuration = 0.5f
        timeBetweenPecks = 3f
        flightSpeed = 8f
        flightAltitude = 0f
        maxFlightAltitude = 15f
        flightDirection.setZero()
        verticalSpeed = 0f
        detectionRadius = 4f
        isStartled = false
        animationTime = 0f
        flightTime = 0f
        wingPhase = 0f
        pitch = 0f
        crouch = 0f
        bobOffset = 0f
        flockId = 0
        spawnPosition.setZero()
    }
}
