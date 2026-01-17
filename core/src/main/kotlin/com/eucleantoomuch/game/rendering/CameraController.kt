package com.eucleantoomuch.game.rendering

import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.eucleantoomuch.game.util.Constants

class CameraController(private val camera: PerspectiveCamera) {
    private val currentPosition = Vector3()
    private val currentLookAt = Vector3()
    private val targetPosition = Vector3()
    private val targetLookAt = Vector3()

    // Offset from player in local space
    private val offset = Vector3(0f, Constants.CAMERA_OFFSET_Y, Constants.CAMERA_OFFSET_Z)
    private val lookAheadOffset = Vector3(0f, 1f, Constants.CAMERA_LOOK_AHEAD)

    fun initialize(playerPosition: Vector3) {
        currentPosition.set(playerPosition).add(offset)
        currentLookAt.set(playerPosition).add(lookAheadOffset)
        camera.position.set(currentPosition)
        camera.lookAt(currentLookAt)
        camera.up.set(Vector3.Y)
        camera.update()
    }

    fun update(playerPosition: Vector3, playerYaw: Float, deltaTime: Float) {
        // Calculate rotated offset based on player yaw
        val cos = MathUtils.cosDeg(playerYaw)
        val sin = MathUtils.sinDeg(playerYaw)

        // Rotate offset by yaw
        val rotatedOffsetX = offset.x * cos - offset.z * sin
        val rotatedOffsetZ = offset.x * sin + offset.z * cos

        targetPosition.set(
            playerPosition.x + rotatedOffsetX,
            playerPosition.y + offset.y,
            playerPosition.z + rotatedOffsetZ
        )

        // Rotate look-ahead by yaw
        val rotatedLookX = lookAheadOffset.x * cos - lookAheadOffset.z * sin
        val rotatedLookZ = lookAheadOffset.x * sin + lookAheadOffset.z * cos

        targetLookAt.set(
            playerPosition.x + rotatedLookX,
            playerPosition.y + lookAheadOffset.y,
            playerPosition.z + rotatedLookZ
        )

        // Smooth interpolation
        val smoothFactor = deltaTime * Constants.CAMERA_SMOOTHNESS
        currentPosition.lerp(targetPosition, smoothFactor)
        currentLookAt.lerp(targetLookAt, smoothFactor)

        // Apply to camera
        camera.position.set(currentPosition)
        camera.lookAt(currentLookAt)
        camera.up.set(Vector3.Y)
        camera.update()
    }

    fun shake(intensity: Float) {
        val shakeX = MathUtils.random(-intensity, intensity)
        val shakeY = MathUtils.random(-intensity, intensity)
        camera.position.add(shakeX, shakeY, 0f)
        camera.update()
    }
}
