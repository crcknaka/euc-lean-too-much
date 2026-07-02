package com.eucleantoomuch.game.physics

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.math.Vector3
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.CarComponent
import com.eucleantoomuch.game.ecs.components.ColliderComponent
import com.eucleantoomuch.game.ecs.components.ModelComponent
import com.eucleantoomuch.game.ecs.components.ObstacleComponent
import com.eucleantoomuch.game.ecs.components.PedestrianComponent
import com.eucleantoomuch.game.ecs.components.PedestrianState
import com.eucleantoomuch.game.ecs.components.ShadowComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.systems.PigeonSystem
import com.eucleantoomuch.game.platform.PlatformServices

/**
 * Handles all ragdoll interactions between the world and entities:
 * knocking down pedestrians, tipping trash cans, chain-reaction impacts,
 * and syncing entity transforms with ragdoll physics.
 *
 * Ragdolling entities are tracked in explicit lists so per-frame updates
 * only touch active ragdolls instead of iterating every pedestrian/obstacle.
 */
class RagdollInteractions(
    private val engine: Engine,
    private val platformServices: PlatformServices,
    private val pigeonSystem: PigeonSystem,
    private val ragdollPhysics: () -> RagdollPhysics?,
    private val playerEntity: () -> Entity?,
    private val useRagdollPhysics: () -> Boolean
) {
    // Entities currently driven by ragdoll physics - bounded per-frame updates
    private val ragdollingPedestrians = mutableListOf<Entity>()
    private val knockedOverObstacles = mutableListOf<Entity>()

    // Track which cars already hit the player ragdoll (impulse applied once per car)
    private val carsHitRagdoll = mutableSetOf<Entity>()

    // Temp vectors (reused to avoid per-frame allocations)
    private val impactDir = Vector3()
    private val ragdollCheckPos = Vector3()
    private val pedestrianCheckPos = Vector3()
    private val pedestrianTempPos = Vector3()
    private val trashCanTempPos = Vector3()
    private val carCheckPos = Vector3()
    private val carVelocity = Vector3()

    private val ragdollCollisionRadius = 0.8f  // Collision radius for ragdoll body

    // Ragdolls left this far behind the player get their physics bodies retired
    private val retireDistanceBehind = 60f
    private val retireCheckInterval = 1f
    private var retireTimer = 0f

    /** Clear car-hit tracking when a new player ragdoll starts. */
    fun resetCarHits() {
        carsHitRagdoll.clear()
    }

    /** Full reset when a new session starts (world entities are recreated). */
    fun reset() {
        carsHitRagdoll.clear()
        ragdollingPedestrians.clear()
        knockedOverObstacles.clear()
    }

    /** Start ragdoll physics for a pedestrian hit directly by the player. */
    fun startPedestrianRagdoll(pedestrianEntity: Entity) {
        if (!useRagdollPhysics() || ragdollPhysics() == null) return

        val pedestrianComponent = pedestrianEntity.getComponent(PedestrianComponent::class.java) ?: return
        if (pedestrianComponent.isRagdolling) return

        val pedestrianTransform = pedestrianEntity.getComponent(TransformComponent::class.java) ?: return
        val playerTransform = playerEntity()?.getComponent(TransformComponent::class.java) ?: return
        val eucComponent = playerEntity()?.getComponent(com.eucleantoomuch.game.ecs.components.EucComponent::class.java) ?: return

        // Impact direction: forward along the player's heading
        val yawRad = Math.toRadians(playerTransform.yaw.toDouble()).toFloat()
        impactDir.set(kotlin.math.sin(yawRad), 0f, kotlin.math.cos(yawRad))

        beginPedestrianRagdoll(pedestrianEntity, pedestrianComponent, pedestrianTransform, eucComponent.speed, impactDir)
    }

    /** Start pedestrian ragdoll from being hit by another ragdoll body (chain reaction). */
    private fun startRagdollFromImpact(pedestrianEntity: Entity, impactVelocity: Vector3) {
        if (!useRagdollPhysics() || ragdollPhysics() == null) return

        val pedestrianComponent = pedestrianEntity.getComponent(PedestrianComponent::class.java) ?: return
        if (pedestrianComponent.isRagdolling) return

        val pedestrianTransform = pedestrianEntity.getComponent(TransformComponent::class.java) ?: return

        // Impact direction from the incoming body's velocity, kept horizontal
        impactDir.set(impactVelocity).nor()
        impactDir.y = 0f

        // Secondary impacts carry reduced speed
        val impactSpeed = impactVelocity.len() * 0.6f

        beginPedestrianRagdoll(pedestrianEntity, pedestrianComponent, pedestrianTransform, impactSpeed, impactDir)
    }

    /** Shared pedestrian-ragdoll setup: hide models, spawn physics body, mark component. */
    private fun beginPedestrianRagdoll(
        pedestrianEntity: Entity,
        pedestrianComponent: PedestrianComponent,
        pedestrianTransform: TransformComponent,
        impactSpeed: Float,
        impactDirection: Vector3
    ) {
        // Hide models first to avoid any visual glitch
        val modelComponent = pedestrianEntity.getComponent(ModelComponent::class.java)
        modelComponent?.visible = false
        pedestrianEntity.getComponent(ShadowComponent::class.java)?.visible = false

        // Extract shirt color from pedestrian model for ragdoll rendering
        val shirtColor = extractPedestrianShirtColor(modelComponent?.modelInstance) ?: Color.GREEN

        // Add pedestrian ragdoll body (this is the heavy operation)
        val bodyIndex = ragdollPhysics()!!.addPedestrianRagdoll(
            position = pedestrianTransform.position,
            yaw = pedestrianTransform.yaw,
            playerVelocity = impactSpeed,
            playerDirection = impactDirection,
            entityIndex = pedestrianEntity.hashCode(),
            shirtColor = shirtColor
        )

        pedestrianComponent.isRagdolling = true
        pedestrianComponent.ragdollBodyIndex = bodyIndex
        pedestrianComponent.state = PedestrianState.FALLING

        ragdollingPedestrians.add(pedestrianEntity)

        // Startle nearby pigeons when pedestrian is hit
        pigeonSystem.addStartleSource(pedestrianTransform.position)
    }

    /** Start ragdoll physics for a trash can (knockable object). */
    fun startTrashCanRagdoll(obstacleEntity: Entity) {
        val physics = ragdollPhysics() ?: return

        val obstacleComponent = obstacleEntity.getComponent(ObstacleComponent::class.java) ?: return
        val obstacleTransform = obstacleEntity.getComponent(TransformComponent::class.java) ?: return
        val playerTransform = playerEntity()?.getComponent(TransformComponent::class.java) ?: return
        val eucComponent = playerEntity()?.getComponent(com.eucleantoomuch.game.ecs.components.EucComponent::class.java) ?: return

        // Impact direction: forward along the player's heading
        val yawRad = Math.toRadians(playerTransform.yaw.toDouble()).toFloat()
        impactDir.set(kotlin.math.sin(yawRad), 0f, kotlin.math.cos(yawRad))

        // Hide original model and shadow
        obstacleEntity.getComponent(ModelComponent::class.java)?.visible = false
        obstacleEntity.getComponent(ShadowComponent::class.java)?.visible = false

        val bodyIndex = physics.addTrashCanRagdoll(
            position = obstacleTransform.position,
            playerVelocity = eucComponent.speed,
            playerDirection = impactDir,
            entityIndex = obstacleEntity.hashCode()
        )

        obstacleComponent.ragdollBodyIndex = bodyIndex
        knockedOverObstacles.add(obstacleEntity)
    }

    /** Extract shirt color from pedestrian model instance for ragdoll rendering. */
    private fun extractPedestrianShirtColor(modelInstance: ModelInstance?): Color? {
        if (modelInstance == null) return null

        // Find the torso material and extract its color
        for (material in modelInstance.materials) {
            val id = material.id?.lowercase() ?: ""
            if (id.contains("torso") || id.contains("shirt") || id.contains("upper")) {
                val colorAttr = material.get(ColorAttribute.Diffuse) as? ColorAttribute
                if (colorAttr != null) return colorAttr.color
            }
        }

        // Fallback: return first material color that's not skin/pants/hair
        for (material in modelInstance.materials) {
            val colorAttr = material.get(ColorAttribute.Diffuse) as? ColorAttribute
            if (colorAttr != null) {
                val c = colorAttr.color
                // Skip skin-like colors (high R, medium G, low-medium B)
                if (c.r > 0.7f && c.g > 0.5f && c.b > 0.4f) continue
                // Skip pants-like colors (dark)
                if (c.r < 0.35f && c.g < 0.35f && c.b < 0.45f) continue
                // Skip hair-like colors (brown)
                if (c.r < 0.4f && c.g < 0.3f && c.b < 0.2f) continue
                return c
            }
        }
        return null
    }

    /**
     * Check if any ragdoll body (player or pedestrian) collides with standing pedestrians.
     * If collision detected, knock down the standing pedestrian.
     */
    fun checkRagdollPedestrianCollisions() {
        val physics = ragdollPhysics() ?: return
        if (!useRagdollPhysics()) return

        // Get all active ragdoll bodies with significant velocity
        val ragdollBodies = physics.getActiveRagdollBodies(minVelocity = 3f)
        if (ragdollBodies.isEmpty()) return

        // Get all pedestrians
        val pedestrians = engine.getEntitiesFor(Families.pedestrians)
        if (pedestrians.size() == 0) return

        for (ragdollBody in ragdollBodies) {
            ragdollCheckPos.set(ragdollBody.position)

            for (i in 0 until pedestrians.size()) {
                val pedestrianEntity = pedestrians[i]
                val pedestrianComponent = pedestrianEntity.getComponent(PedestrianComponent::class.java) ?: continue

                // Skip if already ragdolling
                if (pedestrianComponent.isRagdolling) continue

                val pedestrianTransform = pedestrianEntity.getComponent(TransformComponent::class.java) ?: continue
                pedestrianCheckPos.set(pedestrianTransform.position)
                pedestrianCheckPos.y += 0.8f  // Check at torso height

                // Simple distance check
                val dx = ragdollCheckPos.x - pedestrianCheckPos.x
                val dy = ragdollCheckPos.y - pedestrianCheckPos.y
                val dz = ragdollCheckPos.z - pedestrianCheckPos.z
                val distSq = dx * dx + dy * dy + dz * dz

                if (distSq < ragdollCollisionRadius * ragdollCollisionRadius) {
                    // Collision detected! Start ragdoll for this pedestrian
                    startRagdollFromImpact(pedestrianEntity, ragdollBody.velocity)

                    // Play quieter impact sound (chain reaction)
                    platformServices.playPersonImpactSound(0.4f)
                }
            }
        }
    }

    /**
     * Check if moving cars collide with the player ragdoll.
     * If collision detected, apply impulse to ragdoll and startle pigeons.
     */
    fun checkRagdollCarCollisions() {
        val physics = ragdollPhysics() ?: return
        if (!useRagdollPhysics() || !physics.isActive()) return

        // Get player ragdoll torso position
        physics.getTorsoPosition(ragdollCheckPos)
        if (ragdollCheckPos.isZero) return

        // Get all cars
        val cars = engine.getEntitiesFor(Families.cars)
        if (cars.size() == 0) return

        for (i in 0 until cars.size()) {
            val carEntity = cars[i]

            // Skip if already hit this car
            if (carEntity in carsHitRagdoll) continue

            val carTransform = carEntity.getComponent(TransformComponent::class.java) ?: continue
            val carComponent = carEntity.getComponent(CarComponent::class.java) ?: continue
            val carCollider = carEntity.getComponent(ColliderComponent::class.java)

            // Car dimensions
            val carHalfWidth = carCollider?.halfExtents?.x ?: 1.0f
            val carHalfHeight = carCollider?.halfExtents?.y ?: 0.7f
            val carHalfLength = carCollider?.halfExtents?.z ?: 2.2f

            carCheckPos.set(carTransform.position)
            carCheckPos.y += carHalfHeight  // Center of car

            // Simple AABB check (car is axis-aligned or rotated 180)
            val yawRad = Math.toRadians(carTransform.yaw.toDouble()).toFloat()
            val cosYaw = kotlin.math.cos(yawRad)
            val sinYaw = kotlin.math.sin(yawRad)

            // Transform ragdoll position to car's local space
            val localX = (ragdollCheckPos.x - carCheckPos.x) * cosYaw + (ragdollCheckPos.z - carCheckPos.z) * sinYaw
            val localY = ragdollCheckPos.y - carCheckPos.y
            val localZ = -(ragdollCheckPos.x - carCheckPos.x) * sinYaw + (ragdollCheckPos.z - carCheckPos.z) * cosYaw

            // Check if ragdoll is inside car AABB (with some margin for ragdoll radius)
            val margin = 0.5f
            if (kotlin.math.abs(localX) < carHalfWidth + margin &&
                kotlin.math.abs(localY) < carHalfHeight + margin &&
                kotlin.math.abs(localZ) < carHalfLength + margin) {

                // Collision detected!
                carsHitRagdoll.add(carEntity)

                // Calculate car velocity direction
                carVelocity.set(0f, 0f, carComponent.speed)
                carVelocity.rotate(Vector3.Y, carTransform.yaw)

                // Apply impulse to ragdoll
                physics.applyExternalImpulse(ragdollCheckPos, carVelocity)

                // Startle nearby pigeons
                pigeonSystem.addStartleSource(ragdollCheckPos)

                // Play car hit sound
                platformServices.playGenericHitSound(0.8f)
            }
        }
    }

    /**
     * Update positions of falling pedestrians from ragdoll physics.
     * Only iterates pedestrians that are actually ragdolling.
     */
    fun updateFallingPedestrians() {
        // Periodically retire ragdoll bodies left far behind the player - their
        // Bullet bodies never sleep, so they'd keep simulating until session end
        retireTimer += Gdx.graphics.deltaTime
        if (retireTimer >= retireCheckInterval) {
            retireTimer = 0f
            val playerZ = playerEntity()?.getComponent(TransformComponent::class.java)?.position?.z
            if (playerZ != null) {
                ragdollPhysics()?.retireRagdollsBehind(playerZ - retireDistanceBehind)
            }
        }

        if (ragdollingPedestrians.isEmpty()) return
        val physics = ragdollPhysics() ?: return

        val iterator = ragdollingPedestrians.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            val pedestrianComponent = entity.getComponent(PedestrianComponent::class.java)
            val transform = entity.getComponent(TransformComponent::class.java)
            val modelComponent = entity.getComponent(ModelComponent::class.java)

            // Entity was removed from the engine (world cleanup) - drop it from tracking
            if (pedestrianComponent == null || transform == null || modelComponent == null) {
                iterator.remove()
                continue
            }
            if (!pedestrianComponent.isRagdolling) {
                iterator.remove()
                continue
            }

            // Hide original pedestrian model - ragdoll renderer will draw the articulated body
            modelComponent.visible = false

            // Get physics transform (torso position)
            val physicsTransform = physics.getPedestrianTransform(pedestrianComponent.ragdollBodyIndex)
            if (physicsTransform != null) {
                // Extract position from physics
                physicsTransform.getTranslation(pedestrianTempPos)

                // Update entity transform position (for culling/tracking)
                transform.position.set(pedestrianTempPos)
                // Offset Y down by half torso height since physics center is at torso
                transform.position.y = pedestrianTempPos.y - 0.25f

                // Continuously startle pigeons near falling pedestrians
                pigeonSystem.addStartleSource(pedestrianTempPos.x, pedestrianTempPos.z)
            }
        }
    }

    /**
     * Update positions of knocked over trash cans using ragdoll physics transforms.
     * Only iterates obstacles that were actually knocked over.
     */
    fun updateKnockedOverTrashCans() {
        if (knockedOverObstacles.isEmpty()) return
        val physics = ragdollPhysics() ?: return

        val iterator = knockedOverObstacles.iterator()
        while (iterator.hasNext()) {
            val entity = iterator.next()
            val obstacleComponent = entity.getComponent(ObstacleComponent::class.java)
            val transform = entity.getComponent(TransformComponent::class.java)
            val modelComponent = entity.getComponent(ModelComponent::class.java)

            // Entity was removed from the engine (world cleanup) - drop it from tracking
            if (obstacleComponent == null || transform == null || modelComponent == null) {
                iterator.remove()
                continue
            }
            if (!obstacleComponent.isKnockedOver || obstacleComponent.ragdollBodyIndex < 0) continue

            // Get physics transform
            val physicsTransform = physics.getDynamicObjectTransform(obstacleComponent.ragdollBodyIndex)
            if (physicsTransform != null) {
                // Update model instance transform directly from physics
                modelComponent.modelInstance?.transform?.set(physicsTransform)

                // Also update entity position for culling
                physicsTransform.getTranslation(trashCanTempPos)
                transform.position.set(trashCanTempPos)

                // Make model visible again (it's now controlled by physics)
                modelComponent.visible = true
            }
        }
    }
}
