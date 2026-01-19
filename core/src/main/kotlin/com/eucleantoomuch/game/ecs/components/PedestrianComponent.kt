package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Pool

enum class PedestrianState {
    WALKING,
    STANDING,
    WALKING_TO_CROSSING,  // Walking along sidewalk towards zebra crossing
    CROSSING,             // Crossing the road on zebra
    CHATTING,             // Standing and talking to another pedestrian
    FALLING               // Hit by player, now ragdolling
}

class PedestrianComponent : Component, Pool.Poolable {
    var walkSpeed: Float = 1.5f
    val direction = Vector3(1f, 0f, 0f)  // Walking direction
    var state: PedestrianState = PedestrianState.WALKING

    // Patrol bounds (for sidewalk pedestrians)
    var minX: Float = -6f
    var maxX: Float = 6f
    var minZ: Float = -100f
    var maxZ: Float = 100f

    // Behavior timers
    var stateTimer: Float = 0f           // Time in current state
    var nextStateChange: Float = 5f       // When to consider changing state
    var standDuration: Float = 0f         // How long to stand still

    // For sidewalk pedestrians - walk along Z axis
    var isSidewalkPedestrian: Boolean = false
    var walkDirectionZ: Float = 1f        // 1 = forward, -1 = backward

    // For crossing pedestrians - target Z position of zebra crossing
    var targetCrossingZ: Float = 0f       // Z coordinate of the zebra crossing
    var crossingDirectionX: Float = 1f    // 1 = crossing to right, -1 = crossing to left

    // Chatting behavior
    var chatPartnerId: Int = -1           // Entity ID of chat partner (-1 = none)
    var chatDuration: Float = 0f          // How long to chat

    // Ragdoll physics state (when hit by player)
    var isRagdolling: Boolean = false     // True when ragdoll physics is active
    var ragdollBodyIndex: Int = -1        // Index in RagdollPhysics pedestrian bodies list

    override fun reset() {
        walkSpeed = 1.5f
        direction.set(1f, 0f, 0f)
        state = PedestrianState.WALKING
        minX = -6f
        maxX = 6f
        minZ = -100f
        maxZ = 100f
        stateTimer = 0f
        nextStateChange = 5f
        standDuration = 0f
        isSidewalkPedestrian = false
        walkDirectionZ = 1f
        targetCrossingZ = 0f
        crossingDirectionX = 1f
        chatPartnerId = -1
        chatDuration = 0f
        isRagdolling = false
        ragdollBodyIndex = -1
    }
}
