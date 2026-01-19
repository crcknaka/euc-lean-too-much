package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.Family
import com.badlogic.ashley.systems.IteratingSystem
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.ecs.components.PlayerComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.components.VelocityComponent
import com.eucleantoomuch.game.input.GameInput
import com.eucleantoomuch.game.physics.EucPhysics
import com.eucleantoomuch.game.ui.UIFeedback
import com.eucleantoomuch.game.util.Constants
import com.badlogic.gdx.Gdx
import kotlin.math.sin
import kotlin.random.Random

class EucPhysicsSystem(
    private val gameInput: GameInput
) : IteratingSystem(
    Family.all(
        EucComponent::class.java,
        TransformComponent::class.java,
        VelocityComponent::class.java,
        PlayerComponent::class.java
    ).get(),
    1  // Priority
) {
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)
    private val velocityMapper = ComponentMapper.getFor(VelocityComponent::class.java)
    private val playerMapper = ComponentMapper.getFor(PlayerComponent::class.java)

    var onPlayerFall: (() -> Unit)? = null

    // Wobble constants
    private val wobbleGasThreshold = 0.85f         // Forward lean threshold for acceleration - must be flooring it
    private val wobbleBrakeThreshold = 0.10f       // Backward lean threshold for braking (actual values reach -0.03 to -0.13)
    private val wobbleSpeedThresholdGas = 12f      // m/s - minimum speed for gas wobble (~43 km/h)
    private val wobbleSpeedThresholdBrake = 6f     // m/s - minimum speed for brake wobble (~22 km/h)
    private val wobbleMaxAmplitude = 0.1f          // Maximum side lean amplitude from wobble (~1.5° at 15°/unit)
    private val wobbleDecayRate = 8f               // Fast decay when input released
    private val wobbleChance = 0.3f                // 30% chance wobble will trigger when conditions are met
    private val wobbleFallTime = 3f                // Fall after 3 seconds of wobbling

    // Wobble state - tracks if wobble is "active" for this session (random roll happened)
    private var wobbleActive = false               // Whether wobble triggered this time
    private var wasFlooringIt = false              // Track previous frame's flooring state

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val euc = eucMapper.get(entity)
        val velocity = velocityMapper.get(entity)
        val player = playerMapper.get(entity)

        if (!player.isAlive) return

        // Get calibrated input
        val input = gameInput.getInput()

        // Apply puddle effect (reduced control)
        val controlFactor = if (euc.inPuddle) Constants.PUDDLE_CONTROL_FACTOR else 1f

        // Smoothly update lean values
        val targetForwardLean = input.forward * controlFactor
        val targetSideLean = input.side * controlFactor

        euc.forwardLean = lerp(euc.forwardLean, targetForwardLean, deltaTime * 5f)
        euc.sideLean = lerp(euc.sideLean, targetSideLean, deltaTime * 5f)

        // Update visual lean (even smoother for rendering)
        euc.visualForwardLean = lerp(euc.visualForwardLean, euc.forwardLean, deltaTime * 8f)
        euc.visualSideLean = lerp(euc.visualSideLean, euc.sideLean, deltaTime * 8f)

        // Calculate PWM (motor load) - this determines cutout
        // Apply wheel-specific PWM sensitivity
        val basePwm = euc.calculatePwm()
        euc.pwm = basePwm * euc.pwmSensitivity

        // Check for fall condition (PWM cutout at 100%)
        if (euc.isPwmCutout()) {
            player.isAlive = false
            player.hasFallen = true
            velocity.linear.setZero()
            velocity.angular.setZero()
            onPlayerFall?.invoke()
            return
        }

        // Calculate speed based on forward lean (using wheel-specific maxSpeed)
        val targetSpeed = EucPhysics.calculateTargetSpeed(euc.forwardLean, euc.maxSpeed)
        euc.speed = EucPhysics.updateSpeed(
            euc.speed,
            targetSpeed,
            deltaTime,
            euc.acceleration,
            euc.deceleration,
            euc.maxSpeed
        )

        // Calculate turn rate based on side lean (using wheel-specific responsiveness)
        val turnRate = EucPhysics.calculateTurnRate(
            euc.sideLean,
            euc.speed,
            euc.turnResponsiveness,
            euc.maxSpeed
        )

        // Update velocity - forward is +Z in our coordinate system
        velocity.linear.set(0f, 0f, euc.speed)
        velocity.angular.set(0f, turnRate, 0f)

        // Update puddle timer
        if (euc.inPuddle) {
            euc.puddleTimer -= deltaTime
            if (euc.puddleTimer <= 0) {
                euc.inPuddle = false
            }
        }

        // Speed wobble detection and update (after speed is calculated)
        updateWobble(euc, deltaTime)

        // Check if wobble caused a fall (wobbled for too long)
        if (checkWobbleFall(euc)) {
            player.isAlive = false
            player.hasFallen = true
            velocity.linear.setZero()
            velocity.angular.setZero()
            onPlayerFall?.invoke()
        }
    }

    private fun  lerp(start: Float, end: Float, alpha: Float): Float {
        return start + (end - start) * alpha
    }

    /**
     * Update speed wobble effect.
     * Wobble triggers RANDOMLY when flooring gas or brake at speed.
     * Gas: forwardLean > 85% at 43+ km/h
     * Brake: forwardLean < -10% at 22+ km/h (brake input has limited range ~0.13 max)
     * Has 30% chance to trigger each time conditions are met.
     * Stops immediately when input is released.
     *
     * External wobble (from pothole/manhole) decays when slowing down.
     */
    private fun updateWobble(euc: EucComponent, deltaTime: Float) {
        // Check if flooring gas (>85% forward lean at high speed)
        val isFlooringGas = euc.forwardLean > wobbleGasThreshold && euc.speed > wobbleSpeedThresholdGas

        // Check if flooring brake (< -10% backward lean at speed)
        val isFlooringBrake = euc.forwardLean < -wobbleBrakeThreshold && euc.speed > wobbleSpeedThresholdBrake

        val isFlooringIt = isFlooringGas || isFlooringBrake

        // When we START flooring it (transition from not flooring to flooring), roll for wobble
        if (isFlooringIt && !wasFlooringIt) {
            wobbleActive = Random.nextFloat() < wobbleChance
            if (wobbleActive) {
                Gdx.app.log("Wobble", "TRIGGERED! lean=${euc.forwardLean}, speed=${euc.speed}")
            }
        }

        // When we STOP flooring it, reset wobble state
        if (!isFlooringIt && wasFlooringIt) {
            wobbleActive = false
        }

        wasFlooringIt = isFlooringIt

        // Check if wobble was triggered externally (pothole/manhole)
        val hasExternalWobble = euc.isWobbling && euc.wobbleIntensity > 0.01f

        if (isFlooringIt && wobbleActive) {
            // Build up wobble while flooring it (only if wobble was triggered)
            val leanFactor = if (isFlooringGas) {
                ((euc.forwardLean - wobbleGasThreshold) / (1f - wobbleGasThreshold)).coerceIn(0f, 1f)
            } else {
                // Brake input only reaches ~0.13 max due to lerp smoothing
                val brakeIntensity = -euc.forwardLean - wobbleBrakeThreshold
                (brakeIntensity / 0.05f).coerceIn(0f, 1f)
            }
            val speedFactor = (euc.speed / euc.maxSpeed).coerceIn(0.3f, 1f)
            val intensityIncrease = leanFactor * speedFactor * deltaTime * 6f
            euc.wobbleIntensity = (euc.wobbleIntensity + intensityIncrease).coerceAtMost(0.8f)

            // Update wobble timer - fall after 3 seconds
            euc.wobbleTimer += deltaTime
            euc.isWobbling = true
        } else if (hasExternalWobble) {
            // External wobble (pothole/manhole) - decays when slowing down
            // Wobble persists at high speed, decays faster when braking/slowing
            val speedKmh = euc.speed * 3.6f
            val slowdownThreshold = 60f  // km/h - wobble starts decaying below this speed

            if (speedKmh < slowdownThreshold) {
                // Decay rate increases as speed decreases
                val decayMultiplier = 1f + (slowdownThreshold - speedKmh) / 10f
                euc.wobbleIntensity = (euc.wobbleIntensity - wobbleDecayRate * decayMultiplier * deltaTime).coerceAtLeast(0f)
            }

            // Still count wobble timer while wobbling
            if (euc.wobbleIntensity > 0.01f) {
                euc.wobbleTimer += deltaTime
            } else {
                // Wobble fully decayed - reset state
                euc.wobbleTimer = 0f
                euc.isWobbling = false
            }
        } else {
            // No active wobble - fast decay
            euc.wobbleIntensity = (euc.wobbleIntensity - wobbleDecayRate * deltaTime).coerceAtLeast(0f)

            // Reset wobble timer when not actively wobbling
            euc.wobbleTimer = 0f
            euc.isWobbling = false
        }

        // Update wobble phase (oscillation) and apply to visual
        if (euc.wobbleIntensity > 0.01f) {
            euc.wobblePhase += deltaTime * euc.wobbleFrequency * 2f * Math.PI.toFloat()
            // Keep phase in reasonable range
            if (euc.wobblePhase > Math.PI.toFloat() * 4f) {
                euc.wobblePhase -= Math.PI.toFloat() * 4f
            }

            // Apply wobble directly to visual side lean
            val wobbleOffset = sin(euc.wobblePhase) * euc.wobbleIntensity * wobbleMaxAmplitude
            euc.visualSideLean += wobbleOffset

            // Haptic feedback while wobbling
            UIFeedback.wobble(euc.wobbleIntensity)
        } else {
            euc.wobblePhase = 0f
        }
    }

    /**
     * Check if wobble caused a fall (wobbled for too long)
     */
    fun checkWobbleFall(euc: EucComponent): Boolean {
        return euc.wobbleTimer >= wobbleFallTime
    }
}
