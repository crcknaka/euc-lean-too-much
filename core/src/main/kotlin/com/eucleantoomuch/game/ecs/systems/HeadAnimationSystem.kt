package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.systems.IteratingSystem
import com.badlogic.gdx.math.MathUtils
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.HeadComponent
import com.eucleantoomuch.game.ecs.components.EucComponent

/**
 * System that animates head movement at low speeds.
 * Below 30 km/h the rider occasionally moves their head as if dancing/looking around.
 */
class HeadAnimationSystem : IteratingSystem(Families.rider, 6) {
    private val headMapper = ComponentMapper.getFor(HeadComponent::class.java)
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val head = headMapper.get(entity) ?: return
        val euc = eucMapper.get(entity) ?: return

        head.animTime += deltaTime

        val speedKmh = euc.speed * 3.6f

        // Turn-based head lean - head tilts into the turn direction
        // sideLean: negative = turning left, positive = turning right
        // Head should tilt in the direction of the turn (lean into the turn)
        val turnLeanRoll = -euc.visualSideLean * 12f  // Tilt head into turn
        val turnLeanYaw = euc.visualSideLean * 8f     // Look slightly into turn

        // Head dance effect from 0 to 50 km/h (full intensity at 0, fades to 0 at 50)
        if (speedKmh < 50f) {
            // Full intensity at 0 km/h, linearly fades to 0 at 50 km/h
            val intensity = (50f - speedKmh) / 50f

            // Random-looking but deterministic head movement using multiple sine waves
            val t = head.animTime

            // Yaw: looking left/right occasionally - more noticeable
            val yawBase = MathUtils.sin(t * 1.3f) * 15f + MathUtils.sin(t * 2.1f) * 8f
            head.yaw = yawBase * intensity + turnLeanYaw

            // Pitch: nodding - more noticeable
            val pitchBase = MathUtils.sin(t * 1.7f) * 10f + MathUtils.sin(t * 0.8f) * 6f
            head.pitch = pitchBase * intensity

            // Roll: head tilt - combines dance with turn lean
            val rollBase = MathUtils.sin(t * 1.1f) * 8f
            head.roll = rollBase * intensity + turnLeanRoll
        } else {
            // At higher speed, head leans into turns more noticeably
            val targetYaw = turnLeanYaw
            val targetRoll = turnLeanRoll
            head.yaw = MathUtils.lerp(head.yaw, targetYaw, 5f * deltaTime)
            head.pitch = MathUtils.lerp(head.pitch, 0f, 5f * deltaTime)
            head.roll = MathUtils.lerp(head.roll, targetRoll, 5f * deltaTime)
        }
    }
}
