package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.HeadComponent
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.util.Easing

/**
 * Head motion for the rider.
 *
 * The head is rendered inside the already-leaned body, so whatever the body does the head
 * inherits. That is the opposite of how a rider actually holds their head: the body throws
 * itself into a turn and folds forward under acceleration while the head stays level and
 * keeps looking up the road. Most of the work here is therefore taking the body's lean back
 * out again - without it the rider stares at the tarmac every time they accelerate.
 *
 * The rest is idle behaviour: an occasional glance rather than a permanent sway.
 */
class HeadAnimationSystem : IteratingSystem(Families.rider, 6) {
    private val headMapper = ComponentMapper.getFor(HeadComponent::class.java)
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val head = headMapper.get(entity) ?: return
        val euc = eucMapper.get(entity) ?: return

        head.animTime += deltaTime

        // === Gaze stabilisation ===
        // The renderer leans the body by these amounts before the head is placed, so cancelling
        // most of them is what keeps the eyes near the horizon. Not all of it: a head pinned
        // perfectly level looks mechanical, and a rider does drop their gaze a little when tucked.
        val bodySideLean = euc.visualSideLean * BODY_SIDE_LEAN_DEGREES
        val bodyForwardLean = euc.visualForwardLean * BODY_FORWARD_LEAN_DEGREES
        val levelRoll = -bodySideLean * SIDE_STABILISATION
        val levelPitch = -bodyForwardLean * FORWARD_STABILISATION

        // Riders look where they are going, into the turn rather than straight over the bars
        val turnYaw = euc.visualSideLean * TURN_LOOK_DEGREES

        // === Idle glances ===
        val speedKmh = euc.speed * 3.6f
        val calm = ((GLANCE_MAX_KMH - speedKmh) / GLANCE_MAX_KMH).coerceIn(0f, 1f)
        updateGlance(head, deltaTime, calm)

        val glance = Easing.outN(head.glanceProgress, 3)
        val glanceYaw = MathUtils.lerp(head.glanceFromYaw, head.glanceToYaw, glance) * calm
        val glancePitch = MathUtils.lerp(head.glanceFromPitch, head.glanceToPitch, glance) * calm

        // Wobble shakes the head loose - fast, small, and only while it lasts
        val shake = if (euc.wobbleIntensity > 0.01f) {
            MathUtils.sin(head.animTime * WOBBLE_SHAKE_HZ * MathUtils.PI2) * WOBBLE_SHAKE_DEGREES * euc.wobbleIntensity
        } else 0f

        // Approach the targets rather than snapping: necks have mass
        head.yaw = MathUtils.lerp(head.yaw, turnYaw + glanceYaw, FOLLOW_SPEED * deltaTime)
        head.pitch = MathUtils.lerp(head.pitch, levelPitch + glancePitch, FOLLOW_SPEED * deltaTime)
        head.roll = MathUtils.lerp(head.roll, levelRoll + shake, FOLLOW_SPEED * deltaTime)
    }

    /** Picks a new place to look every few seconds, then holds it. */
    private fun updateGlance(head: HeadComponent, deltaTime: Float, calm: Float) {
        if (head.glanceProgress < 1f) {
            head.glanceProgress = (head.glanceProgress + deltaTime / GLANCE_SECONDS).coerceAtMost(1f)
            return
        }

        head.glanceCountdown -= deltaTime
        if (head.glanceCountdown > 0f || calm <= 0.05f) return

        head.glanceFromYaw = head.glanceToYaw
        head.glanceFromPitch = head.glanceToPitch
        // Half the time the glance is a return to centre, which is what stops it wandering off
        if (MathUtils.randomBoolean()) {
            head.glanceToYaw = 0f
            head.glanceToPitch = 0f
        } else {
            head.glanceToYaw = MathUtils.random(-GLANCE_YAW, GLANCE_YAW)
            head.glanceToPitch = MathUtils.random(-GLANCE_PITCH, GLANCE_PITCH)
        }
        head.glanceProgress = 0f
        head.glanceCountdown = MathUtils.random(GLANCE_GAP_MIN, GLANCE_GAP_MAX)
    }

    private companion object {
        // Must match the lean the renderer applies to the rider before placing the head
        const val BODY_SIDE_LEAN_DEGREES = 25f
        const val BODY_FORWARD_LEAN_DEGREES = 60f

        const val SIDE_STABILISATION = 0.8f
        const val FORWARD_STABILISATION = 0.65f

        const val TURN_LOOK_DEGREES = 14f
        const val FOLLOW_SPEED = 8f

        const val GLANCE_MAX_KMH = 50f
        const val GLANCE_SECONDS = 0.35f
        const val GLANCE_GAP_MIN = 1.2f
        const val GLANCE_GAP_MAX = 3.5f
        const val GLANCE_YAW = 22f
        const val GLANCE_PITCH = 9f

        const val WOBBLE_SHAKE_HZ = 6f
        const val WOBBLE_SHAKE_DEGREES = 4f
    }
}
