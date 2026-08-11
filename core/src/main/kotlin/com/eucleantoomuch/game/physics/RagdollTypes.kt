package com.eucleantoomuch.game.physics

import com.badlogic.gdx.math.Vector3

/**
 * Shared ragdoll value types.
 *
 * These used to be nested inside the Bullet implementation. They live at package level now so
 * the physics backend can be swapped without touching the ~35 call sites that name them - and
 * so the Bullet dependency (which has no web support) could be dropped entirely.
 */
object RagdollTypes {

    /** What a ragdoll body hit - selects the impact sound. */
    enum class ColliderType {
        GROUND,
        STREET_LIGHT,
        RECYCLE_BIN,
        CAR,
        PEDESTRIAN,
        BENCH,
        BUILDING,
        TREE,
        GENERIC
    }

    /** Position/velocity snapshot of an active ragdoll body, used for chain-reaction hits. */
    data class RagdollBodyInfo(
        val position: Vector3,
        val velocity: Vector3,
        val isPlayerRagdoll: Boolean
    )
}
