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

    // FOV settings - very dramatic effect for intense speed sensation
    private val baseFov = 60f
    private val maxFov = 110f     // Very wide FOV for extreme speed sensation
    private val maxSpeed = 15f    // Speed at which FOV reaches max (m/s, ~54 km/h)
    private var currentFov = baseFov
    private var currentSpeed = 0f

    // Camera distance compensation - move much closer as FOV increases to keep player same size
    private val baseOffsetZ = Constants.CAMERA_OFFSET_Z
    private val minOffsetZ = Constants.CAMERA_OFFSET_Z + 3.5f  // Move 3.5m closer at max speed

    fun initialize(playerPosition: Vector3) {
        currentPosition.set(playerPosition).add(offset)
        currentLookAt.set(playerPosition).add(lookAheadOffset)
        camera.position.set(currentPosition)
        camera.lookAt(currentLookAt)
        camera.up.set(Vector3.Y)
        camera.update()
    }

    fun update(playerPosition: Vector3, playerYaw: Float, deltaTime: Float, speed: Float = 0f) {
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

        // Update FOV based on speed
        currentSpeed = speed
        val speedRatio = (currentSpeed / maxSpeed).coerceIn(0f, 1f)
        val targetFov = MathUtils.lerp(baseFov, maxFov, speedRatio)
        currentFov = MathUtils.lerp(currentFov, targetFov, deltaTime * 5f)  // Smooth FOV transition
        camera.fieldOfView = currentFov

        // Compensate camera distance - move closer as FOV increases to keep player same size
        val targetOffsetZ = MathUtils.lerp(baseOffsetZ, minOffsetZ, speedRatio)
        offset.z = MathUtils.lerp(offset.z, targetOffsetZ, deltaTime * 5f)

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
