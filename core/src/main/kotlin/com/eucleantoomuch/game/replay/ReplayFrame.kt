package com.eucleantoomuch.game.replay

import com.badlogic.gdx.math.Vector3

/**
 * Single frame of replay data.
 * Contains all information needed to reconstruct the visual state at this moment.
 */
data class ReplayFrame(
    val timestamp: Float,  // Time in seconds from start of recording

    // Player EUC state
    val playerPosition: Vector3,
    val playerYaw: Float,
    val eucForwardLean: Float,
    val eucSideLean: Float,
    val eucSpeed: Float,
    val eucRoll: Float,  // For fall animation

    // Rider state
    val riderVisualForwardLean: Float,
    val riderVisualSideLean: Float,

    // Head animation
    val headYaw: Float,
    val headPitch: Float,
    val headRoll: Float,

    // Arm positions
    val leftArmPitch: Float,
    val leftArmYaw: Float,
    val rightArmPitch: Float,
    val rightArmYaw: Float,

    // Camera state (for reference point)
    val cameraPosition: Vector3,
    val cameraYaw: Float
) {
    companion object {
        fun create(
            timestamp: Float,
            playerPos: Vector3,
            playerYaw: Float,
            eucForwardLean: Float,
            eucSideLean: Float,
            eucSpeed: Float,
            eucRoll: Float,
            riderForwardLean: Float,
            riderSideLean: Float,
            headYaw: Float,
            headPitch: Float,
            headRoll: Float,
            leftArmPitch: Float,
            leftArmYaw: Float,
            rightArmPitch: Float,
            rightArmYaw: Float,
            cameraPos: Vector3,
            cameraYaw: Float
        ): ReplayFrame {
            return ReplayFrame(
                timestamp = timestamp,
                playerPosition = Vector3(playerPos),
                playerYaw = playerYaw,
                eucForwardLean = eucForwardLean,
                eucSideLean = eucSideLean,
                eucSpeed = eucSpeed,
                eucRoll = eucRoll,
                riderVisualForwardLean = riderForwardLean,
                riderVisualSideLean = riderSideLean,
                headYaw = headYaw,
                headPitch = headPitch,
                headRoll = headRoll,
                leftArmPitch = leftArmPitch,
                leftArmYaw = leftArmYaw,
                rightArmPitch = rightArmPitch,
                rightArmYaw = rightArmYaw,
                cameraPosition = Vector3(cameraPos),
                cameraYaw = cameraYaw
            )
        }
    }
}
