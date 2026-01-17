package com.eucleantoomuch.game.feedback

import com.badlogic.gdx.math.MathUtils
import com.eucleantoomuch.game.platform.PlatformServices
import kotlin.math.sign

/**
 * Controls the visual fall animation sequence when player crashes.
 * Manages rider tumbling, EUC rolling away, camera shake, and crash sounds.
 */
class FallAnimationController(
    private val platformServices: PlatformServices
) {
    companion object {
        const val FALL_DURATION = 2.0f  // Total animation duration in seconds
        const val IMPACT_TIME = 0.15f   // When the "impact" happens
    }

    // Animation state
    private var timer = 0f
    private var isActive = false
    private var impactTriggered = false

    // Initial state captured at fall start
    private var initialSpeed = 0f
    private var initialForwardLean = 0f
    private var initialSideLean = 0f
    private var initialYaw = 0f

    // Rider animation state
    var riderPitch = 0f         // Forward tumble (increases as rider falls forward)
        private set
    var riderRoll = 0f          // Side roll
        private set
    var riderYOffset = 0f       // How much rider has fallen down
        private set
    var riderForwardOffset = 0f // How much rider has moved forward (thrown off)
        private set

    // Arm animation state
    var armSpread = 0f          // Arms swing outward on impact (0-1)
        private set
    var armForwardAngle = 0f    // Arms reach forward (degrees, for catching fall)
        private set

    // EUC animation state
    var eucRoll = 0f            // EUC tips over
        private set
    var eucYOffset = 0f         // EUC drops as it falls on its side
        private set
    var eucForwardOffset = 0f   // EUC slides forward slightly
        private set
    var eucSideOffset = 0f      // EUC veers off to side
        private set

    // Camera effects
    var cameraShake = 0f        // Screen shake intensity (0-1)
        private set
    var cameraDropOffset = 0f   // Camera drops down
        private set
    var cameraForwardOffset = 0f // Camera moves forward (zoom in)
        private set
    var cameraRoll = 0f         // Camera tilt/roll angle in degrees
        private set
    var fovPunch = 0f           // FOV spike on impact
        private set

    // Random roll direction for this fall (set on start)
    private var rollDirection = 1f

    // Animation progress (0 to 1)
    val progress: Float
        get() = (timer / FALL_DURATION).coerceIn(0f, 1f)

    val isComplete: Boolean
        get() = timer >= FALL_DURATION

    /**
     * Start the fall animation sequence.
     * @param speed Current speed in m/s at moment of fall
     * @param forwardLean Forward lean at moment of fall (-1 to 1)
     * @param sideLean Side lean at moment of fall (-1 to 1)
     * @param yaw Current yaw rotation
     */
    fun start(speed: Float, forwardLean: Float, sideLean: Float, yaw: Float) {
        timer = 0f
        isActive = true
        impactTriggered = false

        // Capture initial state
        initialSpeed = speed
        initialForwardLean = forwardLean
        initialSideLean = sideLean
        initialYaw = yaw

        // Random roll direction for camera
        rollDirection = if (MathUtils.randomBoolean()) 1f else -1f

        // Reset all animation values
        riderPitch = 0f
        riderRoll = 0f
        riderYOffset = 0f
        riderForwardOffset = 0f
        armSpread = 0f
        armForwardAngle = 0f
        eucRoll = 0f
        eucYOffset = 0f
        eucForwardOffset = 0f
        eucSideOffset = 0f
        cameraShake = 0f
        cameraDropOffset = 0f
        cameraRoll = 0f
        fovPunch = 0f
    }

    /**
     * Update the fall animation.
     * @param delta Time since last frame
     */
    fun update(delta: Float) {
        if (!isActive) return

        timer += delta
        val t = progress

        // Speed factor affects intensity of fall (faster = more dramatic)
        val speedFactor = (initialSpeed / 15f).coerceIn(0.3f, 1.5f)

        // Camera bounce effect - simulates camera hitting ground and bouncing
        // Bounce formula: multiple damped impacts
        val bounceOffset = calculateBounce(t, speedFactor)

        // === PHASE 1: Impact (0 - 0.15s) ===
        if (t < 0.15f) {
            val impactT = t / 0.15f  // 0-1 during impact phase

            // Trigger impact effects once
            if (!impactTriggered && t > 0.02f) {
                impactTriggered = true
                triggerImpactEffects(speedFactor)
            }

            // Camera shake peaks at impact
            cameraShake = impactT * speedFactor * 2.5f

            // Camera drops and bounces
            cameraDropOffset = easeInQuad(impactT) * -0.8f + bounceOffset

            // Camera roll - tilts on impact
            cameraRoll = easeOutQuad(impactT) * 12f * rollDirection * speedFactor

            // FOV punch (quick zoom)
            fovPunch = MathUtils.sin(impactT * MathUtils.PI) * 10f * speedFactor

            // Rider leans forward aggressively (like extreme forward lean)
            riderPitch = easeOutQuad(impactT) * 60f  // Strong forward lean
            riderYOffset = easeInQuad(impactT) * -0.3f  // Slight drop toward ground
            riderForwardOffset = easeOutQuad(impactT) * 0.5f * speedFactor

            // Arms go FORWARD (protective reflex - hands out to catch fall)
            armSpread = easeOutQuad(impactT) * 0.3f  // Slight spread
            armForwardAngle = easeOutQuad(impactT) * 60f  // Arms reach forward

            // EUC starts tipping to side
            val eucRollDir = if (initialSideLean != 0f) sign(initialSideLean) else rollDirection
            eucRoll = easeOutQuad(impactT) * 25f * eucRollDir
            eucYOffset = easeInQuad(impactT) * -0.05f
            eucForwardOffset = easeOutQuad(impactT) * 0.2f * speedFactor  // Starts sliding

        // === PHASE 2: Tumble (0.15 - 0.5s) ===
        } else if (t < 0.5f) {
            val tumbleT = (t - 0.15f) / 0.35f  // 0-1 during tumble phase

            // Camera shake decreases
            cameraShake = (1f - tumbleT * 0.6f) * speedFactor * 1.8f

            // Camera drops more + bounce effect
            cameraDropOffset = -0.8f + easeInQuad(tumbleT) * -0.5f + bounceOffset

            // Camera roll oscillates
            cameraRoll = 12f * rollDirection * speedFactor * (1f - tumbleT * 0.3f)

            // FOV returns to normal
            fovPunch = (1f - tumbleT) * 5f * speedFactor

            // Rider leans forward to ground (like falling face-first)
            riderPitch = 60f + easeInQuad(tumbleT) * 30f  // 60 -> 90 degrees (horizontal)
            riderYOffset = -0.3f + easeInQuad(tumbleT) * -0.5f  // Falls toward ground
            riderForwardOffset = 0.5f * speedFactor + easeOutQuad(tumbleT) * 1.5f * speedFactor

            // Rider gains some roll as they tumble
            val riderRollDir = if (initialSideLean != 0f) sign(initialSideLean) else rollDirection
            riderRoll = easeInOutQuad(tumbleT) * 15f * riderRollDir

            // Arms stay forward (catching fall)
            armSpread = 0.3f + tumbleT * 0.2f  // Slight spread
            armForwardAngle = 60f + easeOutQuad(tumbleT) * 30f  // Arms fully forward (90 degrees)

            // EUC falls over to its side and slides forward
            val eucRollDir = if (initialSideLean >= 0) 1f else -1f
            eucRoll = 25f * eucRollDir + easeInQuad(tumbleT) * 55f * eucRollDir  // 25 -> 80 degrees
            eucYOffset = -0.05f + easeInQuad(tumbleT) * -0.25f  // Drop as it tips
            eucForwardOffset = 0.2f * speedFactor + easeOutQuad(tumbleT) * 1.2f * speedFactor  // Slides forward
            eucSideOffset = easeOutQuad(tumbleT) * 0.3f * eucRollDir

        // === PHASE 3: Settle (0.5 - 1.0s) ===
        } else {
            val settleT = (t - 0.5f) / 0.5f  // 0-1 during settle phase

            // Camera shake fades out
            cameraShake = (1f - settleT) * 0.5f * speedFactor

            // Camera settles at low position + residual bounce
            cameraDropOffset = -1.3f + bounceOffset * (1f - settleT)

            // Camera roll settles back to normal
            cameraRoll = 12f * rollDirection * speedFactor * 0.7f * (1f - easeOutQuad(settleT))

            // FOV settles
            fovPunch = 0f

            // Rider on ground - leaned forward with hands out
            riderPitch = 90f  // Horizontal - face down toward ground
            riderYOffset = -0.8f  // Close to ground (fallen)
            riderForwardOffset = 2f * speedFactor + settleT * 0.2f

            // Roll settles
            val riderRollDir = if (initialSideLean >= 0) 1f else -1f
            riderRoll = 15f * riderRollDir

            // Arms forward (bracing on ground)
            armSpread = 0.5f
            armForwardAngle = 90f  // Fully forward

            // EUC lying on its side, sliding to a stop
            val eucRollDir = if (initialSideLean >= 0) 1f else -1f
            eucRoll = 80f * eucRollDir + easeOutQuad(settleT) * 10f * eucRollDir  // Settles to 90
            eucYOffset = -0.3f  // On the ground
            eucForwardOffset = 1.4f * speedFactor + easeOutQuad(settleT) * 0.3f * speedFactor  // Slides to stop
            eucSideOffset = 0.3f * eucRollDir
        }
    }

    /**
     * Calculate camera bounce effect - damped bounces like dropping phone/camera.
     * Returns vertical offset (positive = up from current drop position).
     */
    private fun calculateBounce(t: Float, speedFactor: Float): Float {
        // Bounce timing: first bounce at ~0.1s, second at ~0.25s, third at ~0.4s
        val bounce1Time = 0.1f
        val bounce2Time = 0.28f
        val bounce3Time = 0.45f

        val bounce1Height = 0.4f * speedFactor  // First bounce height
        val bounce2Height = 0.15f * speedFactor // Second bounce (smaller)
        val bounce3Height = 0.05f * speedFactor // Third bounce (tiny)

        val bounce1Duration = 0.15f
        val bounce2Duration = 0.12f
        val bounce3Duration = 0.08f

        return when {
            // First bounce
            t in bounce1Time..(bounce1Time + bounce1Duration) -> {
                val bt = (t - bounce1Time) / bounce1Duration
                MathUtils.sin(bt * MathUtils.PI) * bounce1Height
            }
            // Second bounce
            t in bounce2Time..(bounce2Time + bounce2Duration) -> {
                val bt = (t - bounce2Time) / bounce2Duration
                MathUtils.sin(bt * MathUtils.PI) * bounce2Height
            }
            // Third bounce
            t in bounce3Time..(bounce3Time + bounce3Duration) -> {
                val bt = (t - bounce3Time) / bounce3Duration
                MathUtils.sin(bt * MathUtils.PI) * bounce3Height
            }
            else -> 0f
        }
    }

    /**
     * Trigger one-shot impact effects (sound, vibration).
     */
    private fun triggerImpactEffects(speedFactor: Float) {
        // Vibration: short, sharp impact
        val vibrationDuration = (60L + (speedFactor * 40L).toLong()).coerceIn(50L, 150L)
        val vibrationAmplitude = (150 + (speedFactor * 100).toInt()).coerceIn(100, 255)
        platformServices.vibrate(vibrationDuration, vibrationAmplitude)

        // Crash sound: short "crack" with pitch down
        platformServices.playCrashSound(speedFactor)
    }

    fun reset() {
        timer = 0f
        isActive = false
        impactTriggered = false
        riderPitch = 0f
        riderRoll = 0f
        riderYOffset = 0f
        riderForwardOffset = 0f
        armSpread = 0f
        armForwardAngle = 0f
        eucRoll = 0f
        eucYOffset = 0f
        eucForwardOffset = 0f
        eucSideOffset = 0f
        cameraShake = 0f
        cameraDropOffset = 0f
        cameraForwardOffset = 0f
        cameraRoll = 0f
        fovPunch = 0f
    }

    // Easing functions
    private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)
    private fun easeInQuad(t: Float): Float = t * t
    private fun easeInOutQuad(t: Float): Float = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f
}
