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
import com.eucleantoomuch.game.util.Constants

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
    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val velocityMapper = ComponentMapper.getFor(VelocityComponent::class.java)
    private val playerMapper = ComponentMapper.getFor(PlayerComponent::class.java)

    var onPlayerFall: (() -> Unit)? = null

    override fun processEntity(entity: Entity, deltaTime: Float) {
        val euc = eucMapper.get(entity)
        val transform = transformMapper.get(entity)
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

        // Check for fall condition
        if (EucPhysics.checkFall(euc.forwardLean, euc.sideLean)) {
            player.isAlive = false
            player.hasFallen = true
            velocity.linear.setZero()
            velocity.angular.setZero()
            onPlayerFall?.invoke()
            return
        }

        // Calculate speed based on forward lean
        val targetSpeed = EucPhysics.calculateTargetSpeed(euc.forwardLean)
        euc.speed = EucPhysics.updateSpeed(euc.speed, targetSpeed, deltaTime)

        // Calculate turn rate based on side lean
        val turnRate = EucPhysics.calculateTurnRate(euc.sideLean, euc.speed)

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
    }

    private fun lerp(start: Float, end: Float, alpha: Float): Float {
        return start + (end - start) * alpha
    }
}
