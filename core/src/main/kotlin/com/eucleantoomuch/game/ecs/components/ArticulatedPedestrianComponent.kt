package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Pool

/**
 * Component for articulated pedestrian rendering with walking animation.
 * Stores separate model instances for each body part that can be animated independently.
 */
class ArticulatedPedestrianComponent : Component, Pool.Poolable {
    // Individual body part model instances (created from the main pedestrian model's nodes)
    var headInstance: ModelInstance? = null
    var torsoInstance: ModelInstance? = null
    var leftUpperArmInstance: ModelInstance? = null
    var leftLowerArmInstance: ModelInstance? = null
    var rightUpperArmInstance: ModelInstance? = null
    var rightLowerArmInstance: ModelInstance? = null
    var leftUpperLegInstance: ModelInstance? = null
    var leftLowerLegInstance: ModelInstance? = null
    var rightUpperLegInstance: ModelInstance? = null
    var rightLowerLegInstance: ModelInstance? = null
    var hairInstance: ModelInstance? = null

    // Animation state
    var animPhase: Float = 0f           // Current animation phase (0 to 2*PI)
    var isAnimating: Boolean = true     // Whether to animate (false when standing/chatting)

    // Body part dimensions for animation calculations
    val upperArmLength = 0.28f
    val lowerArmLength = 0.25f
    val upperLegLength = 0.4f
    val lowerLegLength = 0.4f
    val shoulderOffset = 0.17f  // torsoWidth + 0.02
    val hipOffset = 0.08f
    val hipY = 0.9f
    val shoulderY = 1.35f  // hipY + torsoHeight - 0.05

    // Temp matrices for animation (to avoid allocations)
    val tempMatrix = Matrix4()

    override fun reset() {
        headInstance = null
        torsoInstance = null
        leftUpperArmInstance = null
        leftLowerArmInstance = null
        rightUpperArmInstance = null
        rightLowerArmInstance = null
        leftUpperLegInstance = null
        leftLowerLegInstance = null
        rightUpperLegInstance = null
        rightLowerLegInstance = null
        hairInstance = null
        animPhase = 0f
        isAnimating = true
    }
}
