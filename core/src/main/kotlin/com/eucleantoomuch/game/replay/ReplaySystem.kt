package com.eucleantoomuch.game.replay

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3

/**
 * System for recording and playing back game replays.
 * Records the last N seconds of gameplay for instant replay on death.
 */
class ReplaySystem {
    companion object {
        const val BUFFER_DURATION = 10f  // Record last 10 seconds
        const val RECORD_INTERVAL = 1f / 30f  // Record at 30fps to save memory
    }

    private val frameBuffer = mutableListOf<ReplayFrame>()
    private var recordTimer = 0f
    private var totalRecordTime = 0f

    // Playback state
    private var playbackTime = 0f
    private var isPlaying = false
    private var isPaused = false
    private var playbackSpeed = 1f  // 1.0 = normal, 0.25 = slow-mo

    // Current interpolated frame for rendering
    private var currentFrame: ReplayFrame? = null

    // Flag to detect when replay loops (reached crash point)
    private var justLooped = false

    /**
     * Record a frame if enough time has passed.
     * Call this every game update during gameplay.
     */
    fun recordFrame(
        delta: Float,
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
    ) {
        totalRecordTime += delta
        recordTimer += delta

        if (recordTimer >= RECORD_INTERVAL) {
            recordTimer = 0f

            val frame = ReplayFrame.create(
                timestamp = totalRecordTime,
                playerPos = playerPos,
                playerYaw = playerYaw,
                eucForwardLean = eucForwardLean,
                eucSideLean = eucSideLean,
                eucSpeed = eucSpeed,
                eucRoll = eucRoll,
                riderForwardLean = riderForwardLean,
                riderSideLean = riderSideLean,
                headYaw = headYaw,
                headPitch = headPitch,
                headRoll = headRoll,
                leftArmPitch = leftArmPitch,
                leftArmYaw = leftArmYaw,
                rightArmPitch = rightArmPitch,
                rightArmYaw = rightArmYaw,
                cameraPos = cameraPos,
                cameraYaw = cameraYaw
            )

            frameBuffer.add(frame)

            // Remove old frames beyond buffer duration
            val cutoffTime = totalRecordTime - BUFFER_DURATION
            frameBuffer.removeAll { it.timestamp < cutoffTime }
        }
    }

    /**
     * Start playback from the beginning of the buffer.
     */
    fun startPlayback() {
        if (frameBuffer.isEmpty()) return

        isPlaying = true
        isPaused = false
        playbackSpeed = 1f
        playbackTime = frameBuffer.first().timestamp
        updateCurrentFrame()
    }

    /**
     * Update playback state.
     * Call this every frame during replay mode.
     */
    fun updatePlayback(delta: Float) {
        justLooped = false
        if (!isPlaying || isPaused || frameBuffer.isEmpty()) return

        playbackTime += delta * playbackSpeed

        // Loop back to start when reaching end
        val endTime = frameBuffer.last().timestamp
        if (playbackTime > endTime) {
            playbackTime = frameBuffer.first().timestamp
            justLooped = true  // Signal that we reached the crash point
        }

        updateCurrentFrame()
    }

    /**
     * Returns true if replay just looped back to start (reached the crash moment).
     * Use this to trigger crash sounds.
     */
    fun justReachedEnd(): Boolean = justLooped

    /**
     * Interpolate between frames to get smooth playback.
     */
    private fun updateCurrentFrame() {
        if (frameBuffer.size < 2) {
            currentFrame = frameBuffer.firstOrNull()
            return
        }

        // Find the two frames surrounding current playback time
        var prevFrame: ReplayFrame? = null
        var nextFrame: ReplayFrame? = null

        for (i in 0 until frameBuffer.size - 1) {
            if (frameBuffer[i].timestamp <= playbackTime && frameBuffer[i + 1].timestamp > playbackTime) {
                prevFrame = frameBuffer[i]
                nextFrame = frameBuffer[i + 1]
                break
            }
        }

        // If no interpolation needed, use closest frame
        if (prevFrame == null || nextFrame == null) {
            currentFrame = frameBuffer.lastOrNull { it.timestamp <= playbackTime } ?: frameBuffer.first()
            return
        }

        // Calculate interpolation factor
        val t = (playbackTime - prevFrame.timestamp) / (nextFrame.timestamp - prevFrame.timestamp)

        // Interpolate all values
        currentFrame = ReplayFrame(
            timestamp = playbackTime,
            playerPosition = lerp(prevFrame.playerPosition, nextFrame.playerPosition, t),
            playerYaw = MathUtils.lerp(prevFrame.playerYaw, nextFrame.playerYaw, t),
            eucForwardLean = MathUtils.lerp(prevFrame.eucForwardLean, nextFrame.eucForwardLean, t),
            eucSideLean = MathUtils.lerp(prevFrame.eucSideLean, nextFrame.eucSideLean, t),
            eucSpeed = MathUtils.lerp(prevFrame.eucSpeed, nextFrame.eucSpeed, t),
            eucRoll = MathUtils.lerp(prevFrame.eucRoll, nextFrame.eucRoll, t),
            riderVisualForwardLean = MathUtils.lerp(prevFrame.riderVisualForwardLean, nextFrame.riderVisualForwardLean, t),
            riderVisualSideLean = MathUtils.lerp(prevFrame.riderVisualSideLean, nextFrame.riderVisualSideLean, t),
            headYaw = MathUtils.lerp(prevFrame.headYaw, nextFrame.headYaw, t),
            headPitch = MathUtils.lerp(prevFrame.headPitch, nextFrame.headPitch, t),
            headRoll = MathUtils.lerp(prevFrame.headRoll, nextFrame.headRoll, t),
            leftArmPitch = MathUtils.lerp(prevFrame.leftArmPitch, nextFrame.leftArmPitch, t),
            leftArmYaw = MathUtils.lerp(prevFrame.leftArmYaw, nextFrame.leftArmYaw, t),
            rightArmPitch = MathUtils.lerp(prevFrame.rightArmPitch, nextFrame.rightArmPitch, t),
            rightArmYaw = MathUtils.lerp(prevFrame.rightArmYaw, nextFrame.rightArmYaw, t),
            cameraPosition = lerp(prevFrame.cameraPosition, nextFrame.cameraPosition, t),
            cameraYaw = MathUtils.lerp(prevFrame.cameraYaw, nextFrame.cameraYaw, t)
        )
    }

    private fun lerp(a: Vector3, b: Vector3, t: Float): Vector3 {
        return Vector3(
            MathUtils.lerp(a.x, b.x, t),
            MathUtils.lerp(a.y, b.y, t),
            MathUtils.lerp(a.z, b.z, t)
        )
    }

    // Playback controls
    fun togglePause() {
        isPaused = !isPaused
    }

    fun toggleSlowMo() {
        playbackSpeed = if (playbackSpeed == 1f) 0.25f else 1f
    }

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
    }

    fun seekTo(normalizedPosition: Float) {
        if (frameBuffer.isEmpty()) return
        val startTime = frameBuffer.first().timestamp
        val endTime = frameBuffer.last().timestamp
        playbackTime = MathUtils.lerp(startTime, endTime, normalizedPosition.coerceIn(0f, 1f))
        updateCurrentFrame()
    }

    fun stopPlayback() {
        isPlaying = false
        isPaused = false
    }

    /**
     * Reset recording buffer for new game.
     */
    fun reset() {
        frameBuffer.clear()
        recordTimer = 0f
        totalRecordTime = 0f
        isPlaying = false
        isPaused = false
        playbackSpeed = 1f
        currentFrame = null
    }

    // Getters
    fun getCurrentFrame(): ReplayFrame? = currentFrame
    fun isPlaying(): Boolean = isPlaying
    fun isPaused(): Boolean = isPaused
    fun isSlowMo(): Boolean = playbackSpeed < 1f
    fun getPlaybackSpeed(): Float = playbackSpeed
    fun hasFrames(): Boolean = frameBuffer.isNotEmpty()

    fun getPlaybackProgress(): Float {
        if (frameBuffer.size < 2) return 0f
        val startTime = frameBuffer.first().timestamp
        val endTime = frameBuffer.last().timestamp
        val duration = endTime - startTime
        if (duration <= 0f) return 0f
        return ((playbackTime - startTime) / duration).coerceIn(0f, 1f)
    }

    fun getDuration(): Float {
        if (frameBuffer.size < 2) return 0f
        return frameBuffer.last().timestamp - frameBuffer.first().timestamp
    }
}
