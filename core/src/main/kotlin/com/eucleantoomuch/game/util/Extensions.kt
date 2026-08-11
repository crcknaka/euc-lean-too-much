package com.eucleantoomuch.game.util

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3

fun Float.lerp(target: Float, alpha: Float): Float {
    return this + (target - this) * alpha
}

fun Float.moveTowards(target: Float, maxDelta: Float): Float {
    return when {
        target > this -> minOf(this + maxDelta, target)
        target < this -> maxOf(this - maxDelta, target)
        else -> this
    }
}

fun Float.clamp(min: Float, max: Float): Float {
    return MathUtils.clamp(this, min, max)
}

fun Float.toDegrees(): Float = this * MathUtils.radiansToDegrees
fun Float.toRadians(): Float = this * MathUtils.degreesToRadians

fun Vector3.setFromSpherical(theta: Float, phi: Float, radius: Float): Vector3 {
    val sinPhi = MathUtils.sin(phi)
    this.x = radius * sinPhi * MathUtils.cos(theta)
    this.y = radius * MathUtils.cos(phi)
    this.z = radius * sinPhi * MathUtils.sin(theta)
    return this
}

fun randomFloat(min: Float, max: Float): Float {
    return MathUtils.random(min, max)
}

fun randomInt(min: Int, max: Int): Int {
    return MathUtils.random(min, max)
}

/**
 * Pick a random element using libGDX's RNG.
 *
 * Must be used instead of Kotlin's stdlib `Collection.random()`: that one goes through
 * `kotlin.random.Random.Default`, which on TeaVM resolves to `ThreadLocalRandom.current()` -
 * unimplemented there, so it throws UnsupportedOperationException (and leaves a half-built
 * ModelBuilder behind, cascading into "Call end() first"). This killed the whole web build.
 */
fun <T> List<T>.randomItem(): T = this[com.badlogic.gdx.math.MathUtils.random(size - 1)]
