package com.eucleantoomuch.game.ecs

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.eucleantoomuch.game.ecs.components.ArmComponent
import com.eucleantoomuch.game.ecs.components.ArmTagComponent
import com.eucleantoomuch.game.ecs.components.HeadComponent
import com.eucleantoomuch.game.ecs.components.ColliderComponent
import com.eucleantoomuch.game.ecs.components.CollisionGroups
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.ecs.components.ModelComponent
import com.eucleantoomuch.game.ecs.components.PlayerComponent
import com.eucleantoomuch.game.ecs.components.ShadowComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.components.VelocityComponent
import com.eucleantoomuch.game.model.WheelType
import com.eucleantoomuch.game.rendering.ProceduralModels
import com.eucleantoomuch.game.util.Constants
import net.mgsx.gltf.scene3d.scene.Scene

class EntityFactory(
    private val engine: Engine,
    private val models: ProceduralModels
) {
    private val riderModel by lazy { models.createRiderModel() }
    private val leftArmModel by lazy { models.createArmModel(isLeft = true) }
    private val rightArmModel by lazy { models.createArmModel(isLeft = false) }

    // Shadow model for player/rider (silhouette shape)
    private val playerShadowModel by lazy { models.createPlayerShadowModel() }

    fun createPlayer(wheelType: WheelType = WheelType.Standard): Entity {
        val entity = engine.createEntity()

        // Load EUC model for the selected wheel type
        val eucModel = models.createEucModel(wheelType.id)
        val eucModelInstance = ModelInstance(eucModel)

        // Now read the scale (after lazy init has set it)
        val modelScale = models.eucModelScale

        // Transform - start slightly above ground to prevent wheel clipping
        entity.add(TransformComponent().apply {
            position.set(0f, Constants.EUC_GROUND_OFFSET, 0f)
            scale.set(modelScale, modelScale, modelScale)
        })

        // Velocity
        entity.add(VelocityComponent())

        // Collider
        entity.add(ColliderComponent().apply {
            setSize(0.5f, 1.8f, 0.5f)  // Player hitbox
            collisionGroup = CollisionGroups.PLAYER
            collisionMask = CollisionGroups.OBSTACLE
        })

        // Model (EUC + Rider combined conceptually, we'll render separately)
        entity.add(ModelComponent().apply {
            if (models.isEucPbr && models.getEucSceneAsset() != null) {
                // Create Scene for PBR rendering with proper materials
                scene = Scene(models.getEucSceneAsset()!!.scene)
                modelInstance = scene!!.modelInstance
                isPbr = true
            } else {
                modelInstance = eucModelInstance
                isPbr = false
            }
            // Find brake light material for dynamic lighting
            findBrakeLightMaterial()
        })

        // Player marker
        entity.add(PlayerComponent())

        // EUC state with wheel-specific physics
        entity.add(EucComponent().apply {
            maxSpeed = wheelType.maxSpeed
            criticalLean = wheelType.criticalLean
            acceleration = wheelType.acceleration
            deceleration = wheelType.deceleration
            pwmSensitivity = wheelType.pwmSensitivity
            turnResponsiveness = wheelType.turnResponsiveness
        })

        // Add shadow for wheel
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(playerShadowModel)
            scale = 1.5f
        })

        engine.addEntity(entity)
        return entity
    }

    fun createRiderModel(): Entity {
        // Create a separate entity for the rider model that follows the player
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(0f, 0.2f, 0f)  // Slightly above EUC
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(riderModel)
        })

        // Add EUC component so rider leans with EUC
        entity.add(EucComponent())

        // Add ArmComponent for arm animation state
        // Pose is dynamically chosen during gameplay (20% chance for behind-back on each acceleration)
        entity.add(ArmComponent())

        // Add HeadComponent for head animation state
        entity.add(HeadComponent())

        engine.addEntity(entity)
        return entity
    }

    /**
     * Create an arm entity that will be attached to the rider.
     * @param isLeft true for left arm, false for right arm
     */
    fun createArmEntity(isLeft: Boolean): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            // Default scale (will be used for arm rendering)
            scale.set(1f, 1f, 1f)
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(if (isLeft) leftArmModel else rightArmModel)
        })

        // Tag to identify which arm this is
        entity.add(ArmTagComponent().apply {
            this.isLeft = isLeft
        })

        engine.addEntity(entity)
        return entity
    }

}
