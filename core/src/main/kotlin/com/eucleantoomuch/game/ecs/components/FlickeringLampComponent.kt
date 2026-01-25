package com.eucleantoomuch.game.ecs.components

import com.badlogic.ashley.core.Component
import com.badlogic.gdx.utils.Pool

/**
 * Component for lamp posts that flicker in night hardcore mode.
 * Tracks flickering state and timing for dynamic light effects.
 */
class FlickeringLampComponent : Component, Pool.Poolable {
    /** Whether the lamp is currently lit (false = flickered off) */
    var isLit: Boolean = true

    /** Timer until next flicker state change */
    var flickerTimer: Float = 0f

    /** Duration to stay in current state */
    var stateDuration: Float = 0f

    /** Base intensity when lit (0-1) */
    var baseIntensity: Float = 1f

    /** Current intensity (affected by flickering) */
    var currentIntensity: Float = 1f

    /** Whether this lamp is actively flickering (or just turned off) */
    var isActivelyFlickering: Boolean = false

    /** Flicker speed multiplier (higher = faster flickering) */
    var flickerSpeed: Float = 1f

    override fun reset() {
        isLit = true
        flickerTimer = 0f
        stateDuration = 0f
        baseIntensity = 1f
        currentIntensity = 1f
        isActivelyFlickering = false
        flickerSpeed = 1f
    }
}
