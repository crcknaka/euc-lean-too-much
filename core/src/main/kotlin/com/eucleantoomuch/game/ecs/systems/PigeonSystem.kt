package com.eucleantoomuch.game.ecs.systems

import com.badlogic.ashley.core.ComponentMapper
import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.ashley.core.EntitySystem
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import kotlin.math.sqrt
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.*
import com.eucleantoomuch.game.util.Easing
import com.eucleantoomuch.game.platform.PlatformServices
import com.eucleantoomuch.game.rendering.ProceduralModels

/**
 * System that manages pigeon AI behavior:
 * - Walking around and pecking at ground
 * - Getting startled when player approaches
 * - Flying away and being removed
 */
class PigeonSystem(
    private val models: ProceduralModels,
    private val platformServices: PlatformServices
) : EntitySystem(5) {

    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val pigeonMapper = ComponentMapper.getFor(PigeonComponent::class.java)
    private val modelMapper = ComponentMapper.getFor(ModelComponent::class.java)

    // Pre-created models for walking and flying states
    private var walkingModel: ModelInstance? = null
    private var flyingModel: ModelInstance? = null

    private val entitiesToRemove = mutableListOf<Entity>()
    private val tempVec = Vector3()

    // Player position cache
    private var playerX: Float = 0f
    private var playerZ: Float = 0f

    // External startle sources (ragdoll impacts, falling pedestrians)
    private val startleSources = mutableListOf<Vector3>()
    private val startleRadius = 6f  // Radius for startle from external sources (increased for ragdoll impacts)

    // Callback when player startles a flock
    var onFlockStartled: (() -> Unit)? = null
    private val startledFlockIds = mutableSetOf<Int>()  // Track which flocks already triggered callback

    /**
     * Clear per-session startle tracking. Must be called on new-game reset, otherwise flock
     * IDs from the previous session stay "already startled" and the Volts reward never fires again.
     */
    fun clearStartleTracking() {
        startledFlockIds.clear()
    }

    override fun addedToEngine(engine: Engine) {
        super.addedToEngine(engine)
        walkingModel = ModelInstance(models.createPigeonModel(isFlying = false))
        flyingModel = ModelInstance(models.createPigeonModel(isFlying = true))
    }

    override fun update(deltaTime: Float) {
        entitiesToRemove.clear()

        // Get player position
        val players = engine.getEntitiesFor(Families.player)
        if (players.size() > 0) {
            val playerTransform = transformMapper.get(players.first())
            playerX = playerTransform.position.x
            playerZ = playerTransform.position.z
        }

        // Update all pigeons in a single pass
        // Flock startle propagation works because we mark isStartled flag on flock members,
        // which they check at the start of their own checkForStartle call
        val pigeons = engine.getEntitiesFor(Families.pigeons)
        for (entity in pigeons) {
            checkForStartle(entity)
            checkForExternalStartle(entity)
            updatePigeon(entity, deltaTime)
        }

        // Clear startle sources after processing
        startleSources.clear()

        // Remove marked entities
        for (entity in entitiesToRemove) {
            engine.removeEntity(entity)
        }
    }

    /**
     * Add an external startle source (e.g., player ragdoll impact, falling pedestrian).
     * Pigeons near this position will fly away.
     */
    fun addStartleSource(position: Vector3) {
        startleSources.add(Vector3(position))
    }

    /**
     * Add startle source from x, z coordinates (y is ignored for ground-level events).
     */
    fun addStartleSource(x: Float, z: Float) {
        startleSources.add(Vector3(x, 0f, z))
    }

    /**
     * Check if pigeon should be startled by external sources (ragdolls, falling pedestrians).
     */
    private fun checkForExternalStartle(entity: Entity) {
        val transform = transformMapper.get(entity)
        val pigeon = pigeonMapper.get(entity)

        // Skip if already flying or startled
        if (pigeon.state == PigeonComponent.State.FLYING ||
            pigeon.state == PigeonComponent.State.LANDED ||
            pigeon.state == PigeonComponent.State.STARTLED ||
            pigeon.isStartled) {
            return
        }

        // Check distance to each startle source
        for (source in startleSources) {
            val dx = transform.position.x - source.x
            val dz = transform.position.z - source.z
            val distSq = dx * dx + dz * dz

            if (distSq < startleRadius * startleRadius) {
                // Startle this pigeon, fleeing from the source
                startlePigeonFromSource(entity, pigeon, transform, source)
                return
            }
        }
    }

    /**
     * Startle a pigeon from a specific source position.
     */
    private fun startlePigeonFromSource(
        entity: Entity,
        pigeon: PigeonComponent,
        transform: TransformComponent,
        source: Vector3
    ) {
        pigeon.isStartled = true
        pigeon.state = PigeonComponent.State.STARTLED
        pigeon.stateTimer = 0f

        // Calculate flight direction - away from source with some randomness
        val dx = transform.position.x - source.x
        val dz = transform.position.z - source.z
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)

        pigeon.flightDirection.set(
            dx / dist + MathUtils.random(-0.3f, 0.3f),
            0f,
            dz / dist + MathUtils.random(-0.3f, 0.3f)
        ).nor()

        // Mark flock members as startled
        val pigeons = engine.getEntitiesFor(Families.pigeons)
        for (i in 0 until pigeons.size()) {
            val other = pigeons[i]
            if (other == entity) continue
            val otherPigeon = pigeonMapper.get(other)
            if (otherPigeon.flockId == pigeon.flockId && !otherPigeon.isStartled) {
                otherPigeon.isStartled = true
            }
        }
    }

    private fun checkForStartle(entity: Entity) {
        val transform = transformMapper.get(entity)
        val pigeon = pigeonMapper.get(entity)

        // Skip if already flying or landed
        if (pigeon.state == PigeonComponent.State.FLYING ||
            pigeon.state == PigeonComponent.State.LANDED ||
            pigeon.state == PigeonComponent.State.STARTLED) {
            return
        }

        // Check if marked as startled by flock member
        if (pigeon.isStartled) {
            // Set up flight for this pigeon too
            pigeon.state = PigeonComponent.State.STARTLED
            pigeon.stateTimer = 0f

            // Calculate flight direction - away from player
            val dx = transform.position.x - playerX
            val dz = transform.position.z - playerZ
            val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)
            pigeon.flightDirection.set(
                dx / dist + MathUtils.random(-0.3f, 0.3f),
                0f,
                dz / dist + MathUtils.random(-0.3f, 0.3f)
            ).nor()
            return
        }

        // Check distance to player
        val dx = transform.position.x - playerX
        val dz = transform.position.z - playerZ
        val distSq = dx * dx + dz * dz

        if (distSq < pigeon.detectionRadius * pigeon.detectionRadius) {
            startlePigeon(entity, pigeon, transform)
        }
    }

    private fun startlePigeon(entity: Entity, pigeon: PigeonComponent, transform: TransformComponent) {
        pigeon.isStartled = true
        pigeon.state = PigeonComponent.State.STARTLED
        pigeon.stateTimer = 0f

        // Fire callback once per flock
        if (pigeon.flockId !in startledFlockIds) {
            startledFlockIds.add(pigeon.flockId)
            onFlockStartled?.invoke()
        }

        // Calculate flight direction - away from player with some randomness
        val dx = transform.position.x - playerX
        val dz = transform.position.z - playerZ
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1f)

        // Normalize and add random offset
        pigeon.flightDirection.set(
            dx / dist + MathUtils.random(-0.3f, 0.3f),
            0f,
            dz / dist + MathUtils.random(-0.3f, 0.3f)
        ).nor()

        // Mark flock members as startled (they will be processed in checkForStartle)
        // Use index-based iteration to avoid nested iterator issue
        val pigeons = engine.getEntitiesFor(Families.pigeons)
        for (i in 0 until pigeons.size()) {
            val other = pigeons[i]
            if (other == entity) continue
            val otherPigeon = pigeonMapper.get(other)
            if (otherPigeon.flockId == pigeon.flockId && !otherPigeon.isStartled) {
                // Just mark as startled, don't recurse
                otherPigeon.isStartled = true
            }
        }
    }

    private fun updatePigeon(entity: Entity, deltaTime: Float) {
        val transform = transformMapper.get(entity)
        val pigeon = pigeonMapper.get(entity)
        val model = modelMapper.get(entity)

        pigeon.stateTimer += deltaTime
        pigeon.animationTime += deltaTime

        when (pigeon.state) {
            PigeonComponent.State.WALKING -> updateWalking(entity, transform, pigeon, model, deltaTime)
            PigeonComponent.State.PECKING -> updatePecking(entity, transform, pigeon, model, deltaTime)
            PigeonComponent.State.STARTLED -> updateStartled(entity, transform, pigeon, model, deltaTime)
            PigeonComponent.State.FLYING -> updateFlying(entity, transform, pigeon, model, deltaTime)
            PigeonComponent.State.LANDED -> {
                // Remove pigeons that have landed far away
                entitiesToRemove.add(entity)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateWalking(
        entity: Entity,
        transform: TransformComponent,
        pigeon: PigeonComponent,
        model: ModelComponent,
        deltaTime: Float
    ) {
        // Head bobbing animation
        pigeon.bobOffset = MathUtils.sin(pigeon.animationTime * 8f) * 0.02f

        // Change direction periodically
        pigeon.timeToNextDirectionChange -= deltaTime
        if (pigeon.timeToNextDirectionChange <= 0f) {
            pigeon.walkDirection += MathUtils.random(-90f, 90f)
            pigeon.timeToNextDirectionChange = MathUtils.random(1f, 4f)

            // Sometimes start pecking instead
            if (MathUtils.random() < 0.3f) {
                pigeon.state = PigeonComponent.State.PECKING
                pigeon.stateTimer = 0f
                return
            }
        }

        // Move in walk direction
        val rad = pigeon.walkDirection * MathUtils.degreesToRadians
        val moveX = MathUtils.sin(rad) * pigeon.walkSpeed * deltaTime
        val moveZ = MathUtils.cos(rad) * pigeon.walkSpeed * deltaTime

        transform.position.x += moveX
        transform.position.z += moveZ
        transform.yaw = pigeon.walkDirection
        transform.updateRotationFromYaw()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updatePecking(
        entity: Entity,
        transform: TransformComponent,
        pigeon: PigeonComponent,
        model: ModelComponent,
        deltaTime: Float
    ) {
        // Simple pecking animation via bobOffset
        val peckProgress = pigeon.stateTimer / pigeon.peckDuration
        if (peckProgress < 0.5f) {
            // Head going down
            pigeon.bobOffset = -peckProgress * 0.1f
        } else {
            // Head coming back up
            pigeon.bobOffset = -(1f - peckProgress) * 0.1f
        }

        // Return to walking after peck
        if (pigeon.stateTimer >= pigeon.peckDuration) {
            pigeon.state = PigeonComponent.State.WALKING
            pigeon.stateTimer = 0f
            pigeon.timeToNextDirectionChange = MathUtils.random(2f, 5f)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateStartled(
        entity: Entity,
        transform: TransformComponent,
        pigeon: PigeonComponent,
        model: ModelComponent,
        deltaTime: Float
    ) {
        // Anticipation: the bird sinks onto its legs before it goes. The legs supply about a
        // quarter of the take-off acceleration (Berg & Biewener, J Exp Biol 2010), and without
        // a visible crouch the launch reads as the bird being teleported upwards.
        pigeon.crouch = Easing.outN(pigeon.stateTimer / CROUCH_SECONDS, 2)
        transform.position.y = pigeon.spawnPosition.y - pigeon.crouch * 0.045f

        if (pigeon.stateTimer >= CROUCH_SECONDS) {
            pigeon.state = PigeonComponent.State.FLYING
            pigeon.stateTimer = 0f
            pigeon.flightTime = 0f
            pigeon.crouch = 0f
            pigeon.verticalSpeed = 5f  // Initial upward velocity

            // Switch to flying model
            flyingModel?.let { flying ->
                model.modelInstance = ModelInstance(flying.model)
            }

            // Play wing flapping sound
            platformServices.playPigeonFlyOffSound()

            // Face flight direction
            transform.yaw = MathUtils.atan2(pigeon.flightDirection.x, pigeon.flightDirection.z) * MathUtils.radiansToDegrees
            transform.updateRotationFromYaw()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun updateFlying(
        entity: Entity,
        transform: TransformComponent,
        pigeon: PigeonComponent,
        model: ModelComponent,
        deltaTime: Float
    ) {
        pigeon.flightTime += deltaTime
        val t = pigeon.flightTime

        // Thrust profile. A bird does not leave at cruising speed: the strongest acceleration
        // comes around the second wingbeat and every beat after that produces less, so the
        // burst is ramped in over the first couple of beats and then allowed to decay towards
        // a steady cruise. Constant speed from frame one was what made this look weightless.
        val rampIn = Easing.outN(t / BURST_PEAK_SECONDS, 2)
        val burst = rampIn * Easing.decay((t - BURST_PEAK_SECONDS).coerceAtLeast(0f), BURST_TAU)
        val speed = pigeon.flightSpeed * (1f + BURST_GAIN * burst)

        transform.position.x += pigeon.flightDirection.x * speed * deltaTime
        transform.position.z += pigeon.flightDirection.z * speed * deltaTime

        // The climb is spent in the same way: steep while the burst lasts, easing into a
        // shallow rise rather than holding a fixed climb rate for ever.
        pigeon.verticalSpeed -= 2f * deltaTime
        pigeon.verticalSpeed = pigeon.verticalSpeed.coerceAtLeast(1f)
        pigeon.flightAltitude += (pigeon.verticalSpeed + CLIMB_BURST * burst) * deltaTime

        // Wingbeat: fastest on take-off, settling towards the ~5.5 Hz of level flight. The
        // models have no articulated wings, so the beat is carried by the body - a small
        // vertical surge on each downstroke, which is what the eye actually reads as effort.
        val wingHz = CRUISE_HZ + (TAKEOFF_HZ - CRUISE_HZ) * Easing.decay(t, WINGBEAT_TAU)
        pigeon.wingPhase += wingHz * deltaTime
        val surge = Easing.sinPhase(pigeon.wingPhase) * WING_SURGE * (0.35f + 0.65f * burst)

        transform.position.y = pigeon.spawnPosition.y + pigeon.flightAltitude + surge

        // Body angle swings from nose-down on the first downstroke to nose-up as it settles
        pigeon.pitch = PITCH_LAUNCH + (PITCH_CRUISE - PITCH_LAUNCH) * Easing.smooth(t / PITCH_SECONDS)
        // Set directly rather than through updateRotationFromYaw(), which only knows about yaw.
        // Safe to do here: the flight direction is fixed at launch, so nothing rewrites this.
        transform.rotation.setEulerAngles(transform.yaw, pigeon.pitch, 0f)

        // Remove when high enough and far enough
        val dx = transform.position.x - pigeon.spawnPosition.x
        val dz = transform.position.z - pigeon.spawnPosition.z
        val horizontalDist = sqrt(dx * dx + dz * dz)

        if (pigeon.flightAltitude > pigeon.maxFlightAltitude || horizontalDist > 30f) {
            pigeon.state = PigeonComponent.State.LANDED
        }
    }

    private companion object {
        /** Sink onto the legs before launching. */
        const val CROUCH_SECONDS = 0.15f

        // Take-off thrust: ramps in over roughly the first two wingbeats, then is spent.
        const val BURST_PEAK_SECONDS = 0.22f
        const val BURST_TAU = 0.55f
        const val BURST_GAIN = 1.35f
        const val CLIMB_BURST = 4.5f

        // Wingbeat rate, take-off down to level flight (~5.5 Hz measured in pigeons)
        const val TAKEOFF_HZ = 9.5f
        const val CRUISE_HZ = 5.5f
        const val WINGBEAT_TAU = 0.9f
        const val WING_SURGE = 0.05f

        // Body angle: wing root below the rump on the first beat, nose-up once settled
        const val PITCH_LAUNCH = -13f
        const val PITCH_CRUISE = 19f
        const val PITCH_SECONDS = 1.2f
    }
}
