package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool
import com.eucleantoomuch.game.util.Constants

class EucComponent : Component, Pool.Poolable {
    var forwardLean: Float = 0f      // -1 (back) to +1 (forward)
    var sideLean: Float = 0f         // -1 (left) to +1 (right)
    var speed: Float = Constants.MIN_SPEED
    var maxSpeed: Float = Constants.MAX_SPEED
    var criticalLean: Float = Constants.CRITICAL_LEAN

    // Puddle effect
    var inPuddle: Boolean = false
    var puddleTimer: Float = 0f

    // Visual lean (smoothed for rendering)
    var visualForwardLean: Float = 0f
    var visualSideLean: Float = 0f

    override fun reset() {
        forwardLean = 0f
        sideLean = 0f
        speed = Constants.MIN_SPEED
        maxSpeed = Constants.MAX_SPEED
        criticalLean = Constants.CRITICAL_LEAN
        inPuddle = false
        puddleTimer = 0f
        visualForwardLean = 0f
        visualSideLean = 0f
    }

    fun getTotalLean(): Float {
        return kotlin.math.sqrt(forwardLean * forwardLean + sideLean * sideLean)
    }

    fun isAboutToFall(): Boolean = getTotalLean() >= criticalLean * 0.8f
    fun hasFallen(): Boolean = getTotalLean() >= criticalLean

    fun applyPuddleEffect(duration: Float) {
        inPuddle = true
        puddleTimer = duration
    }
}
