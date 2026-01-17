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

    // EUC animation state
    var eucRoll = 0f            // EUC tips over
        private set
    var eucForwardOffset = 0f   // EUC continues forward momentum
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
    var fovPunch = 0f           // FOV spike on impact
        private set

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

        // Reset all animation values
        riderPitch = 0f
        riderRoll = 0f
        riderYOffset = 0f
        riderForwardOffset = 0f
        armSpread = 0f
        eucRoll = 0f
        eucForwardOffset = 0f
        eucSideOffset = 0f
        cameraShake = 0f
        cameraDropOffset = 0f
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

        // === PHASE 1: Impact (0 - 0.15s) ===
        if (t < 0.1f) {
            val impactT = t / 0.1f  // 0-1 during impact phase

            // Trigger impact effects once
            if (!impactTriggered && t > 0.02f) {
                impactTriggered = true
                triggerImpactEffects(speedFactor)
            }

            // Camera shake peaks at impact
            cameraShake = impactT * speedFactor * 2f

            // No forward offset for now
            cameraForwardOffset = 0f

            // FOV punch (quick zoom in/out)
            fovPunch = MathUtils.sin(impactT * MathUtils.PI) * 8f * speedFactor

            // Rider starts pitching forward immediately
            riderPitch = easeOutQuad(impactT) * 25f * speedFactor

            // Arms start spreading outward (protective reflex)
            armSpread = easeOutQuad(impactT) * 0.8f

            // EUC starts tipping based on initial lean direction
            val rollDirection = if (initialSideLean != 0f) sign(initialSideLean) else
                (if (MathUtils.randomBoolean()) 1f else -1f)
            eucRoll = easeOutQuad(impactT) * 15f * rollDirection

        // === PHASE 2: Tumble (0.1 - 0.6s) ===
        } else if (t < 0.4f) {
            val tumbleT = (t - 0.1f) / 0.3f  // 0-1 during tumble phase

            // Camera shake decreases
            cameraShake = (1f - tumbleT * 0.7f) * speedFactor * 1.5f

            // No forward offset
            cameraForwardOffset = 0f

            // FOV returns to normal
            fovPunch = (1f - tumbleT) * 4f * speedFactor

            // Rider continues tumbling forward and starts falling
            riderPitch = 25f * speedFactor + easeInQuad(tumbleT) * 55f * speedFactor
            riderYOffset = easeInQuad(tumbleT) * -0.5f * speedFactor  // Falls down
            riderForwardOffset = easeOutQuad(tumbleT) * 1.5f * speedFactor  // Thrown forward

            // Rider gains some roll as they tumble
            val rollDirection = if (initialSideLean != 0f) sign(initialSideLean) else
                (if (MathUtils.randomBoolean()) 1f else -1f)
            riderRoll = easeInOutQuad(tumbleT) * 20f * rollDirection

            // Arms fully spread
            armSpread = 0.8f + tumbleT * 0.2f

            // EUC continues rolling and moves away
            eucRoll = 15f + easeInQuad(tumbleT) * 60f * (if (initialSideLean >= 0) 1f else -1f)
            eucForwardOffset = easeOutQuad(tumbleT) * 2f * speedFactor  // Rolls forward
            eucSideOffset = easeOutQuad(tumbleT) * 0.8f * sign(eucRoll)

            // Camera starts dropping
            cameraDropOffset = easeInQuad(tumbleT) * -0.3f

        // === PHASE 3: Settle (0.4 - 1.0s) ===
        } else {
            val settleT = (t - 0.4f) / 0.6f  // 0-1 during settle phase

            // Camera shake fades out
            cameraShake = (1f - settleT) * 0.4f * speedFactor

            // No forward offset
            cameraForwardOffset = 0f

            // FOV settles
            fovPunch = 0f

            // Rider on ground
            riderPitch = 80f * speedFactor + settleT * 10f  // Almost face down
            riderYOffset = -0.5f * speedFactor - easeOutQuad(settleT) * 0.2f  // Fully down
            riderForwardOffset = 1.5f * speedFactor + settleT * 0.3f

            // Roll settles
            val rollDirection = if (initialSideLean >= 0) 1f else -1f
            riderRoll = 20f * rollDirection + easeInOutQuad(settleT) * 5f * rollDirection

            // Arms relax slightly
            armSpread = 1f - settleT * 0.1f

            // EUC has rolled over and slides to a stop
            val eucRollTarget = 85f * (if (initialSideLean >= 0) 1f else -1f)
            eucRoll = 75f * sign(eucRollTarget) + easeOutQuad(settleT) * 10f * sign(eucRollTarget)
            eucForwardOffset = 2f * speedFactor + easeOutQuad(settleT) * 0.5f
            eucSideOffset = 0.8f * sign(eucRoll) + easeOutQuad(settleT) * 0.3f * sign(eucRoll)

            // Camera settles at low position
            cameraDropOffset = -0.3f - easeOutQuad(settleT) * 0.15f
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
        eucRoll = 0f
        eucForwardOffset = 0f
        eucSideOffset = 0f
        cameraShake = 0f
        cameraDropOffset = 0f
        cameraForwardOffset = 0f
        fovPunch = 0f
    }

    // Easing functions
    private fun easeOutQuad(t: Float): Float = 1f - (1f - t) * (1f - t)
    private fun easeInQuad(t: Float): Float = t * t
    private fun easeInOutQuad(t: Float): Float = if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).let { it * it } / 2f
}
