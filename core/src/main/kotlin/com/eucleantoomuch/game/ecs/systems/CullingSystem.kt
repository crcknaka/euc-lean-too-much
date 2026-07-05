package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.ashley.utils.ImmutableArray
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.util.Constants

class CullingSystem : EntitySystem(7) {
    private lateinit var playerEntities: ImmutableArray<Entity>
    private lateinit var obstacleEntities: ImmutableArray<Entity>

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)

    // Flag to disable culling
    var enabled = true

    // Reused across frames to avoid per-frame list allocation
    private val toRemove = ArrayList<Entity>()

    // Entities that pull this far AHEAD of the player (e.g. same-direction cars faster than
    // the player) are past any render distance and would otherwise accumulate forever.
    private val forwardCullDistance = 450f

    override fun addedToEngine(engine: Engine) {
        playerEntities = engine.getEntitiesFor(Families.player)
        obstacleEntities = engine.getEntitiesFor(Families.obstacles)
    }

    override fun update(deltaTime: Float) {
        if (!enabled) return
        if (playerEntities.size() == 0) return

        val playerTransform = transformMapper.get(playerEntities.first())
        val playerZ = playerTransform.position.z
        val cullDistance = Constants.DESPAWN_DISTANCE

        // Find entities to remove (can't modify during iteration)
        toRemove.clear()

        for (entity in obstacleEntities) {
            val transform = transformMapper.get(entity)

            // Cull entities too far BEHIND the player (cullDistance is negative, e.g. -60)...
            // ...or too far AHEAD (faster same-direction cars that outran the player and are
            // now beyond any render distance - without this they never despawn).
            val relativeZ = transform.position.z - playerZ
            if (relativeZ < cullDistance || relativeZ > forwardCullDistance) {
                toRemove.add(entity)
            }
        }

        // Remove culled entities
        for (i in toRemove.indices) {
            engine.removeEntity(toRemove[i])
        }
    }
}
