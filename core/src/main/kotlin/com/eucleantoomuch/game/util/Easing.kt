package com.eucleantoomuch.game.util

import com.badlogic.gdx.math.MathUtils
import kotlin.math.exp
import kotlin.math.pow

/**
 * Shaping curves for movement that should not look like it was driven by a clock.
 *
 * Two habits make animation read as flat, and both were all over this game: constant velocity
 * (a value moved by `speed * delta` every frame), and a plain sine, which spends equal time
 * accelerating and decelerating and so gives every phase the same weight.
 *
 * The vocabulary:
 * - [inN]  accelerates, peak speed at the END of the span - a launch, a strike, a fall.
 * - [outN] leaves fast and arrives softly - a recovery, a settle, a wind-up.
 * - [smooth] eases at both ends; the safe default, and the reason things brake to a stop at
 *   every keyframe when applied everywhere without thought.
 *
 * Real motion is usually asymmetric: the recovery from an action takes considerably longer
 * than the action itself, and fades rather than stopping.
 */
object Easing {

    /** Accelerating. `power` 2 is gentle, 4 is a whip. */
    fun inN(t: Float, power: Int = 2): Float = t.coerceIn(0f, 1f).pow(power)

    /** Fast start, soft arrival. */
    fun outN(t: Float, power: Int = 2): Float = 1f - (1f - t.coerceIn(0f, 1f)).pow(power)

    /** Eased at both ends (smoothstep). */
    fun smooth(t: Float): Float {
        val k = t.coerceIn(0f, 1f)
        return k * k * (3f - 2f * k)
    }

    /**
     * Exponential fade from 1 towards 0 with time constant [tau] seconds.
     *
     * The honest shape for anything that is spent rather than switched off: a burst of thrust,
     * a settling wobble, a sound dying away. Unlike a linear ramp it never has a corner at the
     * end, which is what makes a linear fade so easy to notice.
     */
    fun decay(seconds: Float, tau: Float): Float =
        if (tau <= 0f) 0f else exp(-seconds / tau)

    /**
     * Bends a 0..1 phase so the first [firstShare] of it covers half the cycle.
     *
     * Walking is the case this exists for: the foot is on the ground for about 60% of the gait
     * cycle and swinging for the other 40%, so driving both halves off one sine - equal time
     * each - is what makes a walk look like a metronome rather than a person.
     */
    fun warpPhase(t: Float, firstShare: Float): Float {
        val k = t.coerceIn(0f, 1f)
        val share = firstShare.coerceIn(0.05f, 0.95f)
        return if (k < share) 0.5f * (k / share) else 0.5f + 0.5f * ((k - share) / (1f - share))
    }

    /** Sine of a 0..1 phase, saving the caller a PI2 every time. */
    fun sinPhase(t: Float): Float = MathUtils.sin(t * MathUtils.PI2)
}
