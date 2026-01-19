package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.ArmComponent
import com.eucleantoomuch.game.ecs.components.EucComponent

/**
 * System that animates arm positions based on rider speed.
 * - At low speed (< 15 km/h): arms move in balancing motion (like tightrope walker)
 * - At high speed (>= 15 km/h): arms forward pose (80%) or behind back pose (20%)
 */
class ArmAnimationSystem : IteratingSystem(Families.rider, 5) {
    private val armMapper = ComponentMapper.getFor(ArmComponent::class.java)
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val arm = armMapper.get(entity) ?: return
        val euc = eucMapper.get(entity) ?: return

        // Update balance animation time
        arm.balanceTime += deltaTime

        // Detect start of acceleration (forward lean becoming positive)
        // Re-roll pose choice with 20% chance for behind-back
        val isAccelerating = euc.visualForwardLean > 0.15f
        if (isAccelerating && !arm.wasAccelerating) {
            // Just started accelerating - roll for pose
            arm.useBehindBack = MathUtils.random() < 0.2f
        }
        arm.wasAccelerating = isAccelerating

        // Determine target pose based on speed (2 poses: balance vs arms forward/behind back)
        // poseBlend: 0 = balance, 1 = arms forward or behind back (based on useBehindBack)
        val targetPoseBlend = if (euc.speed < ArmComponent.BALANCE_SPEED_THRESHOLD) 0f else 1f

        // Smoothly transition between poses
        arm.poseBlend = MathUtils.lerp(
            arm.poseBlend,
            targetPoseBlend,
            ArmComponent.TRANSITION_SPEED * deltaTime
        )

        // Calculate and blend arm angles based on pose blend
        calculateBlendedPose(arm, euc)
    }

    private fun calculateBalancePose(arm: ArmComponent, euc: EucComponent) {
        // Balance pose: arms stretched out horizontally to the sides (like a tightrope walker)
        // Yaw controls how high the arm is raised (90 = horizontal, >90 = above horizontal, <90 = below)
        val baseYaw = 90f  // Arms straight out to sides (horizontal)

        // Add up/down swaying motion for balance effect by varying the lift angle
        val swaySpeed = 2.5f  // Oscillation speed
        val swayAmount = 20f  // Max degrees of vertical sway

        // Use sine waves for smooth balancing motion
        // Left and right arms move in opposite directions (seesaw motion)
        val sway = MathUtils.sin(arm.balanceTime * swaySpeed) * swayAmount

        // Also add response to side lean - arms counter the lean (one goes up, other down)
        val leanResponse = -euc.visualSideLean * 25f

        // Left arm - when leaning right, left arm goes up (higher yaw = arm up)
        arm.leftArmYaw = baseYaw + sway + leanResponse
        arm.leftArmPitch = 0f
        arm.leftArmRoll = 0f
        arm.leftForearmBend = 0f  // Straight arm for balance

        // Right arm - opposite motion (seesaw)
        arm.rightArmYaw = baseYaw - sway - leanResponse
        arm.rightArmPitch = 0f
        arm.rightArmRoll = 0f
        arm.rightForearmBend = 0f  // Straight arm for balance
    }

    private fun calculateArmsForwardPose(arm: ArmComponent, euc: EucComponent) {
        // Arms forward pose: arms extend forward based on acceleration (forward lean)
        // When accelerating hard, arms stretch forward for balance
        // When cruising (no acceleration), arms hang down naturally
        // During sharp turns, inner arm drops more than outer arm

        // Forward lean goes from -1 (braking) to +1 (accelerating)
        // Only extend arms forward when accelerating (positive lean)
        val accelFactor = euc.visualForwardLean.coerceIn(0f, 1f)

        // Turn detection - sideLean: negative = left turn, positive = right turn
        val turnIntensity = kotlin.math.abs(euc.visualSideLean)

        // Inner arm drops more during turns (the arm on the turn side)
        // Left turn (negative sideLean) -> left arm drops more
        // Right turn (positive sideLean) -> right arm drops more
        val leftTurnDrop = (-euc.visualSideLean).coerceIn(0f, 1f)   // Higher in left turns
        val rightTurnDrop = euc.visualSideLean.coerceIn(0f, 1f)     // Higher in right turns

        // Base dampen for both arms during any turn
        val baseTurnDampen = (1f - turnIntensity * 0.8f).coerceIn(0f, 1f)

        // Per-arm dampen: inner arm drops more
        val leftDampen = baseTurnDampen * (1f - leftTurnDrop * 0.7f)
        val rightDampen = baseTurnDampen * (1f - rightTurnDrop * 0.7f)

        // Effective acceleration factor per arm
        val leftEffectiveAccel = accelFactor * leftDampen
        val rightEffectiveAccel = accelFactor * rightDampen

        // Yaw: how far out to side (0 = down at sides, 90 = horizontal out)
        val turnSpread = turnIntensity * 15f
        val leftTargetYaw = MathUtils.lerp(15f, 5f, leftEffectiveAccel) + turnSpread
        val rightTargetYaw = MathUtils.lerp(15f, 5f, rightEffectiveAccel) + turnSpread

        // Pitch: forward/backward (negative = forward, positive = backward)
        val leftTargetPitch = MathUtils.lerp(0f, -180f, leftEffectiveAccel)
        val rightTargetPitch = MathUtils.lerp(0f, -180f, rightEffectiveAccel)

        // Minimal idle sway - very subtle
        val swingTime = arm.balanceTime * 1.2f
        val idleSway = MathUtils.sin(swingTime) * 3f * (1f - accelFactor)

        // Turn response - arms swing to opposite side of turn for balance
        val turnSwing = -euc.visualSideLean * 25f

        // Forearm bend: straight when reaching forward, slightly bent when relaxed
        val leftForearmBend = MathUtils.lerp(10f, 0f, leftEffectiveAccel)
        val rightForearmBend = MathUtils.lerp(10f, 0f, rightEffectiveAccel)

        // Left arm - drops more in left turns
        arm.leftArmYaw = leftTargetYaw + idleSway + turnSwing
        arm.leftArmPitch = leftTargetPitch
        arm.leftArmRoll = 0f
        arm.leftForearmBend = leftForearmBend

        // Right arm - drops more in right turns
        arm.rightArmYaw = rightTargetYaw + idleSway + turnSwing
        arm.rightArmPitch = rightTargetPitch
        arm.rightArmRoll = 0f
        arm.rightForearmBend = rightForearmBend
    }

    private fun calculateBehindBackPose(arm: ArmComponent, euc: EucComponent) {
        // Arms behind the back - common EUC riding pose at speed

        // Minimal idle sway
        val swingTime = arm.balanceTime * 1f
        val idleSway = MathUtils.sin(swingTime) * 2f  // Very subtle

        // Turn response - both arms shift together
        val turnResponse = euc.visualSideLean * 15f

        // Left arm - closer to body
        arm.leftArmYaw = 10f + idleSway + turnResponse
        arm.leftArmPitch = 50f
        arm.leftArmRoll = -10f
        arm.leftForearmBend = 90f

        // Right arm - same turn direction
        arm.rightArmYaw = 10f + idleSway + turnResponse
        arm.rightArmPitch = 50f
        arm.rightArmRoll = 10f
        arm.rightForearmBend = 90f
    }

    /**
     * Calculate blended pose between balance and high-speed pose.
     * poseBlend: 0 = balance, 1 = relaxed or behind back (based on prefersBehindBack)
     */
    private fun calculateBlendedPose(arm: ArmComponent, euc: EucComponent) {
        val blendFactor = arm.poseBlend.coerceIn(0f, 1f)

        // Calculate balance pose first
        calculateBalancePose(arm, euc)

        // If we need to blend toward high-speed pose
        if (blendFactor > 0.01f) {
            val balanceLeftYaw = arm.leftArmYaw
            val balanceLeftPitch = arm.leftArmPitch
            val balanceLeftRoll = arm.leftArmRoll
            val balanceLeftBend = arm.leftForearmBend
            val balanceRightYaw = arm.rightArmYaw
            val balanceRightPitch = arm.rightArmPitch
            val balanceRightRoll = arm.rightArmRoll
            val balanceRightBend = arm.rightForearmBend

            // Calculate high-speed pose (arms forward or behind back based on current roll)
            if (arm.useBehindBack) {
                calculateBehindBackPose(arm, euc)
            } else {
                calculateArmsForwardPose(arm, euc)
            }

            // Lerp between balance and high-speed pose
            arm.leftArmYaw = MathUtils.lerp(balanceLeftYaw, arm.leftArmYaw, blendFactor)
            arm.leftArmPitch = MathUtils.lerp(balanceLeftPitch, arm.leftArmPitch, blendFactor)
            arm.leftArmRoll = MathUtils.lerp(balanceLeftRoll, arm.leftArmRoll, blendFactor)
            arm.leftForearmBend = MathUtils.lerp(balanceLeftBend, arm.leftForearmBend, blendFactor)
            arm.rightArmYaw = MathUtils.lerp(balanceRightYaw, arm.rightArmYaw, blendFactor)
            arm.rightArmPitch = MathUtils.lerp(balanceRightPitch, arm.rightArmPitch, blendFactor)
            arm.rightArmRoll = MathUtils.lerp(balanceRightRoll, arm.rightArmRoll, blendFactor)
            arm.rightForearmBend = MathUtils.lerp(balanceRightBend, arm.rightForearmBend, blendFactor)
        }
    }
}
