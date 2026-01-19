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

    // Camera view modes
    enum class ViewMode {
        NORMAL,      // Default third-person view
        CLOSE,       // Closer third-person view
        CINEMATIC    // Side angle cinematic view
    }

    private var viewMode = ViewMode.NORMAL

    // Offset from player in local space (will be modified based on view mode)
    private val offset = Vector3(0f, Constants.CAMERA_OFFSET_Y, Constants.CAMERA_OFFSET_Z)
    private val lookAheadOffset = Vector3(0f, 1f, Constants.CAMERA_LOOK_AHEAD)

    // View mode settings
    private val normalOffsetY = Constants.CAMERA_OFFSET_Y
    private val normalOffsetZ = Constants.CAMERA_OFFSET_Z
    private val normalOffsetX = 0f
    private val normalLookAheadY = 1f

    private val closeOffsetY = 2.5f
    private val closeOffsetZ = -2.5f
    private val closeOffsetX = 0f
    private val closeLookAheadY = 1.2f

    // Cinematic sub-positions (cycles automatically between left and right)
    private enum class CinematicPosition {
        LEFT,   // View from left side
        RIGHT   // View from right side
    }

    private var cinematicPosition = CinematicPosition.LEFT
    private var cinematicTimer = 0f
    private val cinematicSwitchDuration = 5f  // Switch every 5 seconds

    // Cinematic position settings (left is slightly higher, right slightly lower for variety)
    private val cinematicLeftOffsetX = 1.8f
    private val cinematicLeftOffsetY = 1.8f   // Slightly higher
    private val cinematicLeftOffsetZ = -1.8f
    private val cinematicLeftLookAheadY = 1.5f

    private val cinematicRightOffsetX = -1.8f
    private val cinematicRightOffsetY = 1.4f  // Slightly lower
    private val cinematicRightOffsetZ = -1.8f
    private val cinematicRightLookAheadY = 1.3f

    // FOV settings - very dramatic effect for intense speed sensation
    private val baseFov = 60f
    private val maxFov = 110f     // Very wide FOV for extreme speed sensation
    private val maxSpeed = 15f    // Speed at which FOV reaches max (m/s, ~54 km/h)
    private var currentFov = baseFov
    private var currentSpeed = 0f

    // Camera distance compensation - move much closer as FOV increases to keep player same size
    private var baseOffsetZ = Constants.CAMERA_OFFSET_Z
    private var minOffsetZ = Constants.CAMERA_OFFSET_Z + 3.5f  // Move 3.5m closer at max speed

    // Target offsets for smooth transitions between view modes
    private var targetOffsetY = normalOffsetY
    private var targetBaseOffsetZ = normalOffsetZ
    private var targetOffsetX = normalOffsetX
    private var targetLookAheadY = normalLookAheadY

    // Fall animation effects
    private var shakeIntensity = 0f
    private var fovPunch = 0f
    private var dropOffset = 0f
    private var forwardOffset = 0f  // Moves camera forward (toward player)
    private var rollAngle = 0f      // Camera tilt/roll effect

    // Wobble shake effect (continuous during wobble)
    private var wobbleShakeX = 0f
    private var wobbleShakeY = 0f

    fun initialize(playerPosition: Vector3) {
        currentPosition.set(playerPosition).add(offset)
        currentLookAt.set(playerPosition).add(lookAheadOffset)
        camera.position.set(currentPosition)
        camera.lookAt(currentLookAt)
        camera.up.set(Vector3.Y)
        camera.update()
    }

    fun update(playerPosition: Vector3, playerYaw: Float, deltaTime: Float, speed: Float = 0f) {
        // Update cinematic auto-cycling (left <-> right only)
        if (viewMode == ViewMode.CINEMATIC) {
            cinematicTimer += deltaTime

            if (cinematicTimer >= cinematicSwitchDuration) {
                cinematicTimer = 0f
                // Toggle between left and right
                cinematicPosition = when (cinematicPosition) {
                    CinematicPosition.LEFT -> CinematicPosition.RIGHT
                    CinematicPosition.RIGHT -> CinematicPosition.LEFT
                }
                // Update target offsets for new cinematic position
                applyCinematicPosition()
            }
        }

        // Smoothly transition camera offsets for view mode changes
        val viewTransitionSpeed = deltaTime * 4f
        offset.x = MathUtils.lerp(offset.x, targetOffsetX, viewTransitionSpeed)
        offset.y = MathUtils.lerp(offset.y, targetOffsetY, viewTransitionSpeed)
        baseOffsetZ = MathUtils.lerp(baseOffsetZ, targetBaseOffsetZ, viewTransitionSpeed)
        minOffsetZ = targetBaseOffsetZ + 3.5f  // Keep relative distance compensation
        lookAheadOffset.y = MathUtils.lerp(lookAheadOffset.y, targetLookAheadY, viewTransitionSpeed)

        // Calculate rotated offset based on player yaw (negated to match visual direction)
        val cos = MathUtils.cosDeg(-playerYaw)
        val sin = MathUtils.sinDeg(-playerYaw)

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

        // Smooth interpolation - cinematic mode is slightly more responsive
        val smoothFactor = deltaTime * (if (viewMode == ViewMode.CINEMATIC) 8f else Constants.CAMERA_SMOOTHNESS)
        currentPosition.lerp(targetPosition, smoothFactor)
        currentLookAt.lerp(targetLookAt, smoothFactor)

        // Update FOV based on speed + FOV punch effect from fall
        // Cinematic mode has slightly wider FOV for dramatic effect
        currentSpeed = speed
        val speedRatio = (currentSpeed / maxSpeed).coerceIn(0f, 1f)
        val modeBaseFov = if (viewMode == ViewMode.CINEMATIC) 70f else baseFov
        val modeMaxFov = if (viewMode == ViewMode.CINEMATIC) 115f else maxFov
        val targetFov = MathUtils.lerp(modeBaseFov, modeMaxFov, speedRatio) + fovPunch
        currentFov = MathUtils.lerp(currentFov, targetFov, deltaTime * 5f)
        camera.fieldOfView = currentFov

        // Compensate camera distance - move closer as FOV increases
        // Cinematic mode has less compensation to keep the dramatic side angle
        val speedOffsetZ = if (viewMode == ViewMode.CINEMATIC) {
            MathUtils.lerp(baseOffsetZ, baseOffsetZ + 1.5f, speedRatio)  // Less compensation
        } else {
            MathUtils.lerp(baseOffsetZ, minOffsetZ, speedRatio)
        }
        offset.z = MathUtils.lerp(offset.z, speedOffsetZ, deltaTime * 5f)

        // Apply to camera with fall effects
        camera.position.set(currentPosition)
        camera.position.y += dropOffset

        // Apply forward offset (move camera toward where player is looking)
        if (forwardOffset != 0f) {
            val forwardX = sin * forwardOffset
            val forwardZ = cos * forwardOffset
            camera.position.add(forwardX, 0f, forwardZ)
        }

        // Apply screen shake - stronger shake effect
        if (shakeIntensity > 0.01f) {
            val shakeX = MathUtils.random(-shakeIntensity, shakeIntensity) * 0.25f
            val shakeY = MathUtils.random(-shakeIntensity, shakeIntensity) * 0.25f
            val shakeZ = MathUtils.random(-shakeIntensity, shakeIntensity) * 0.1f
            camera.position.add(shakeX, shakeY, shakeZ)
        }

        // Apply wobble shake (smoother than fall shake)
        if (wobbleShakeX != 0f || wobbleShakeY != 0f) {
            camera.position.add(wobbleShakeX * 0.02f, wobbleShakeY * 0.02f, 0f)
        }

        camera.lookAt(currentLookAt)

        // Apply roll (tilt) effect - rotate the up vector
        if (rollAngle != 0f) {
            val rollRad = rollAngle * MathUtils.degreesToRadians
            camera.up.set(MathUtils.sin(rollRad), MathUtils.cos(rollRad), 0f)
        } else {
            camera.up.set(Vector3.Y)
        }

        camera.update()
    }

    fun shake(intensity: Float) {
        val shakeX = MathUtils.random(-intensity, intensity)
        val shakeY = MathUtils.random(-intensity, intensity)
        camera.position.add(shakeX, shakeY, 0f)
        camera.update()
    }

    // Fall animation effect setters
    fun setShake(intensity: Float) {
        shakeIntensity = intensity
    }

    fun setFovPunch(punch: Float) {
        fovPunch = punch
    }

    fun setDropOffset(offset: Float) {
        dropOffset = offset
    }

    fun setForwardOffset(offset: Float) {
        forwardOffset = offset
    }

    fun setRoll(angle: Float) {
        rollAngle = angle
    }

    fun setWobbleShake(x: Float, y: Float) {
        wobbleShakeX = x
        wobbleShakeY = y
    }

    /**
     * Apply cinematic position offsets based on current cinematicPosition.
     */
    private fun applyCinematicPosition() {
        when (cinematicPosition) {
            CinematicPosition.LEFT -> {
                targetOffsetX = cinematicLeftOffsetX
                targetOffsetY = cinematicLeftOffsetY
                targetBaseOffsetZ = cinematicLeftOffsetZ
                targetLookAheadY = cinematicLeftLookAheadY
            }
            CinematicPosition.RIGHT -> {
                targetOffsetX = cinematicRightOffsetX
                targetOffsetY = cinematicRightOffsetY
                targetBaseOffsetZ = cinematicRightOffsetZ
                targetLookAheadY = cinematicRightLookAheadY
            }
        }
    }

    /**
     * Cycle to the next camera view mode.
     * Returns the new mode name for UI feedback.
     */
    fun cycleViewMode(): String {
        viewMode = when (viewMode) {
            ViewMode.NORMAL -> ViewMode.CLOSE
            ViewMode.CLOSE -> ViewMode.CINEMATIC
            ViewMode.CINEMATIC -> ViewMode.NORMAL
        }

        // Set target offsets based on new mode
        when (viewMode) {
            ViewMode.NORMAL -> {
                targetOffsetX = normalOffsetX
                targetOffsetY = normalOffsetY
                targetBaseOffsetZ = normalOffsetZ
                targetLookAheadY = normalLookAheadY
            }
            ViewMode.CLOSE -> {
                targetOffsetX = closeOffsetX
                targetOffsetY = closeOffsetY
                targetBaseOffsetZ = closeOffsetZ
                targetLookAheadY = closeLookAheadY
            }
            ViewMode.CINEMATIC -> {
                // Reset cinematic to start from left
                cinematicPosition = CinematicPosition.LEFT
                cinematicTimer = 0f
                applyCinematicPosition()
            }
        }

        return when (viewMode) {
            ViewMode.NORMAL -> "Normal"
            ViewMode.CLOSE -> "Close"
            ViewMode.CINEMATIC -> "Cinematic"
        }
    }

    fun getViewMode(): ViewMode = viewMode

    fun isCinematic(): Boolean = viewMode == ViewMode.CINEMATIC

    /**
     * Reset camera to default view mode.
     */
    fun resetViewMode() {
        viewMode = ViewMode.NORMAL
        targetOffsetX = normalOffsetX
        targetOffsetY = normalOffsetY
        targetBaseOffsetZ = normalOffsetZ
        targetLookAheadY = normalLookAheadY
        offset.x = normalOffsetX
        offset.y = normalOffsetY
        baseOffsetZ = normalOffsetZ
        offset.z = normalOffsetZ
        lookAheadOffset.y = normalLookAheadY
    }
}
