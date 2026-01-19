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
 * - At medium speed (15-35 km/h): arms relaxed down, swaying with turns
 * - At high speed (>= 35 km/h): arms held behind the back
 */
class ArmAnimationSystem : IteratingSystem(Families.rider, 5) {
    private val armMapper = ComponentMapper.getFor(ArmComponent::class.java)
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)

    // Pose indices for blending
    private companion object {
        const val POSE_BALANCE = 0
        const val POSE_RELAXED = 1
        const val POSE_BEHIND_BACK = 2
    }

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val arm = armMapper.get(entity) ?: return
        val euc = eucMapper.get(entity) ?: return

        // Update balance animation time
        arm.balanceTime += deltaTime

        // Determine target pose based on speed (3 poses now)
        val targetPose = when {
            euc.speed < ArmComponent.BALANCE_SPEED_THRESHOLD -> POSE_BALANCE
            euc.speed < ArmComponent.RELAXED_SPEED_THRESHOLD -> POSE_RELAXED
            else -> POSE_BEHIND_BACK
        }

        // poseBlend now goes 0..2 for 3 poses
        val targetPoseBlend = targetPose.toFloat()

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

    private fun calculateRelaxedPose(arm: ArmComponent, euc: EucComponent) {
        // Relaxed pose: arms extend forward based on acceleration (forward lean)
        // When accelerating hard, arms stretch forward for balance
        // When cruising (no acceleration), arms hang down naturally

        // Forward lean goes from -1 (braking) to +1 (accelerating)
        // Only extend arms forward when accelerating (positive lean)
        val accelFactor = euc.visualForwardLean.coerceIn(0f, 1f)

        // Yaw: how far out to side (0 = down at sides, 90 = horizontal out)
        // Keep arms close to body
        val targetYaw = MathUtils.lerp(15f, 5f, accelFactor)  // Arms stay close to body

        // Pitch: forward/backward (negative = forward, positive = backward)
        // When accelerating hard, arms stretch fully forward
        val targetPitch = MathUtils.lerp(0f, -180f, accelFactor)  // Arms reach forward (negative pitch)

        // Minimal idle sway - very subtle
        val swingTime = arm.balanceTime * 1.2f
        val idleSway = MathUtils.sin(swingTime) * 3f * (1f - accelFactor)  // Less sway when accelerating

        // Turn response - BOTH arms swing in SAME direction (inertia effect)
        val turnSwing = -euc.visualSideLean * 40f * (1f - accelFactor * 0.7f)  // Less turn response when arms forward

        // Forearm bend: straight when reaching forward, slightly bent when relaxed
        val forearmBend = MathUtils.lerp(10f, 0f, accelFactor)

        // Both arms move together
        arm.leftArmYaw = targetYaw + idleSway + turnSwing
        arm.leftArmPitch = targetPitch
        arm.leftArmRoll = 0f
        arm.leftForearmBend = forearmBend

        arm.rightArmYaw = targetYaw + idleSway + turnSwing
        arm.rightArmPitch = targetPitch
        arm.rightArmRoll = 0f
        arm.rightForearmBend = forearmBend
    }

    private fun calculateBehindBackPose(arm: ArmComponent, euc: EucComponent) {
        // Arms behind the back - common EUC riding pose at speed

        // Minimal idle sway
        val swingTime = arm.balanceTime * 1f
        val idleSway = MathUtils.sin(swingTime) * 2f  // Very subtle

        // Turn response - both arms shift together
        val turnResponse = euc.visualSideLean * 15f

        // Left arm
        arm.leftArmYaw = 25f + idleSway + turnResponse
        arm.leftArmPitch = 50f
        arm.leftArmRoll = -15f
        arm.leftForearmBend = 90f

        // Right arm - same turn direction
        arm.rightArmYaw = 25f + idleSway + turnResponse
        arm.rightArmPitch = 50f
        arm.rightArmRoll = 15f
        arm.rightForearmBend = 90f
    }

    /**
     * Calculate blended pose between all 3 arm positions.
     * poseBlend: 0 = balance, 1 = relaxed, 2 = behind back
     */
    private fun calculateBlendedPose(arm: ArmComponent, euc: EucComponent) {
        // Determine which two poses to blend between
        val lowerPose = arm.poseBlend.toInt().coerceIn(0, 1)
        val upperPose = (lowerPose + 1).coerceIn(0, 2)
        val blendFactor = arm.poseBlend - lowerPose

        // Calculate lower pose
        when (lowerPose) {
            POSE_BALANCE -> calculateBalancePose(arm, euc)
            POSE_RELAXED -> calculateRelaxedPose(arm, euc)
            else -> calculateBehindBackPose(arm, euc)
        }

        // If we need to blend, store values and calculate upper pose
        if (blendFactor > 0.01f && upperPose != lowerPose) {
            val lowerLeftYaw = arm.leftArmYaw
            val lowerLeftPitch = arm.leftArmPitch
            val lowerLeftRoll = arm.leftArmRoll
            val lowerLeftBend = arm.leftForearmBend
            val lowerRightYaw = arm.rightArmYaw
            val lowerRightPitch = arm.rightArmPitch
            val lowerRightRoll = arm.rightArmRoll
            val lowerRightBend = arm.rightForearmBend

            // Calculate upper pose
            when (upperPose) {
                POSE_RELAXED -> calculateRelaxedPose(arm, euc)
                POSE_BEHIND_BACK -> calculateBehindBackPose(arm, euc)
                else -> calculateBalancePose(arm, euc)
            }

            // Lerp between lower and upper pose
            arm.leftArmYaw = MathUtils.lerp(lowerLeftYaw, arm.leftArmYaw, blendFactor)
            arm.leftArmPitch = MathUtils.lerp(lowerLeftPitch, arm.leftArmPitch, blendFactor)
            arm.leftArmRoll = MathUtils.lerp(lowerLeftRoll, arm.leftArmRoll, blendFactor)
            arm.leftForearmBend = MathUtils.lerp(lowerLeftBend, arm.leftForearmBend, blendFactor)
            arm.rightArmYaw = MathUtils.lerp(lowerRightYaw, arm.rightArmYaw, blendFactor)
            arm.rightArmPitch = MathUtils.lerp(lowerRightPitch, arm.rightArmPitch, blendFactor)
            arm.rightArmRoll = MathUtils.lerp(lowerRightRoll, arm.rightArmRoll, blendFactor)
            arm.rightForearmBend = MathUtils.lerp(lowerRightBend, arm.rightForearmBend, blendFactor)
        }
    }
}
