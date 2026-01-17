package com.eucleantoomuch.game.procedural

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.eucleantoomuch.game.ecs.components.*
import com.eucleantoomuch.game.rendering.ProceduralModels
import com.eucleantoomuch.game.util.Constants

class WorldGenerator(
    private val engine: Engine,
    private val models: ProceduralModels
) {
    private val difficultyScaler = DifficultyScaler()
    private val activeChunks = mutableMapOf<Int, MutableList<Entity>>()

    // Configurable render distance
    private var renderDistance = Constants.RENDER_DISTANCE

    private var groundModel = models.createGroundChunkModel(Constants.CHUNK_LENGTH)
    private var manholeModel = models.createManholeModel()
    private var puddleModel = models.createPuddleModel()
    private var curbModel = models.createCurbModel()
    private var potholeModel = models.createPotholeModel()
    private var pedestrianModel = models.createPedestrianModel()

    // Pre-create some building models with different heights
    private val buildingModels = mutableListOf<Pair<Float, ModelInstance>>()
    private val carModels = mutableListOf<ModelInstance>()

    init {
        // Create variety of buildings
        for (i in 0..5) {
            val height = MathUtils.random(Constants.BUILDING_MIN_HEIGHT, Constants.BUILDING_MAX_HEIGHT)
            val model = models.createBuildingModel(height, models.getRandomBuildingColor())
            buildingModels.add(Pair(height, ModelInstance(model)))
        }

        // Create variety of cars
        for (i in 0..3) {
            val model = models.createCarModel(models.getRandomCarColor())
            carModels.add(ModelInstance(model))
        }
    }

    fun setRenderDistance(distance: Float) {
        renderDistance = distance
    }

    fun update(playerZ: Float, totalDistance: Float) {
        val currentChunk = (playerZ / Constants.CHUNK_LENGTH).toInt()
        // Start from behind the camera (which is at -8 from player), add extra buffer
        val startChunk = ((playerZ - 50f) / Constants.CHUNK_LENGTH).toInt()
        val endChunk = ((playerZ + renderDistance) / Constants.CHUNK_LENGTH).toInt()

        // Generate new chunks
        for (chunkIndex in startChunk..endChunk) {
            if (!activeChunks.containsKey(chunkIndex)) {
                generateChunk(chunkIndex, totalDistance)
            }
        }

        // Remove old chunks (behind the camera)
        val despawnChunk = ((playerZ + Constants.DESPAWN_DISTANCE) / Constants.CHUNK_LENGTH).toInt()
        val chunksToRemove = activeChunks.keys.filter { it < despawnChunk - 1 }
        for (chunkIndex in chunksToRemove) {
            removeChunk(chunkIndex)
        }
    }

    private fun generateChunk(chunkIndex: Int, totalDistance: Float) {
        val entities = mutableListOf<Entity>()
        val chunkStartZ = chunkIndex * Constants.CHUNK_LENGTH

        // Ground segment
        entities.add(createGroundEntity(chunkIndex, chunkStartZ))

        // Buildings on both sides
        entities.addAll(generateBuildings(chunkIndex, chunkStartZ))

        // Skip obstacles in the very first chunk (give player time to start)
        if (chunkIndex > 0) {
            // Generate obstacles
            entities.addAll(generateObstacles(chunkStartZ, totalDistance))
        }

        activeChunks[chunkIndex] = entities
    }

    private fun createGroundEntity(chunkIndex: Int, startZ: Float): Entity {
        val entity = engine.createEntity()

        val transform = TransformComponent().apply {
            position.set(0f, 0f, startZ)
        }
        entity.add(transform)

        val model = ModelComponent().apply {
            modelInstance = ModelInstance(groundModel)
        }
        entity.add(model)

        val ground = GroundComponent().apply {
            type = GroundType.ROAD
            this.chunkIndex = chunkIndex
        }
        entity.add(ground)

        engine.addEntity(entity)
        return entity
    }

    private fun generateBuildings(chunkIndex: Int, chunkStartZ: Float): List<Entity> {
        val entities = mutableListOf<Entity>()
        val buildingSpacing = 12f

        var z = chunkStartZ
        while (z < chunkStartZ + Constants.CHUNK_LENGTH) {
            // Left side building
            val (heightL, instanceL) = buildingModels.random()
            entities.add(createBuildingEntity(
                -Constants.BUILDING_OFFSET_X,
                z + MathUtils.random(-2f, 2f),
                heightL,
                ModelInstance(instanceL.model),
                chunkIndex
            ))

            // Right side building
            val (heightR, instanceR) = buildingModels.random()
            entities.add(createBuildingEntity(
                Constants.BUILDING_OFFSET_X,
                z + MathUtils.random(-2f, 2f),
                heightR,
                ModelInstance(instanceR.model),
                chunkIndex
            ))

            z += buildingSpacing + MathUtils.random(-3f, 3f)
        }

        return entities
    }

    private fun createBuildingEntity(x: Float, z: Float, height: Float, modelInstance: ModelInstance, chunkIndex: Int): Entity {
        val entity = engine.createEntity()

        val transform = TransformComponent().apply {
            position.set(x, 0f, z)
        }
        entity.add(transform)

        val model = ModelComponent().apply {
            this.modelInstance = modelInstance
        }
        entity.add(model)

        val ground = GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        }
        entity.add(ground)

        engine.addEntity(entity)
        return entity
    }

    private fun generateObstacles(chunkStartZ: Float, totalDistance: Float): List<Entity> {
        val entities = mutableListOf<Entity>()
        val minSpacing = difficultyScaler.getMinObstacleSpacing(totalDistance)
        val density = difficultyScaler.getObstacleDensity(totalDistance)

        var z = chunkStartZ + MathUtils.random(2f, 5f)
        while (z < chunkStartZ + Constants.CHUNK_LENGTH - 2f) {
            if (MathUtils.random() < density) {
                val obstacle = generateRandomObstacle(z, totalDistance)
                obstacle?.let { entities.add(it) }
            }

            z += minSpacing + MathUtils.random(0f, 3f)
        }

        return entities
    }

    private fun generateRandomObstacle(z: Float, totalDistance: Float): Entity? {
        val pedProb = difficultyScaler.getPedestrianProbability(totalDistance)
        val carProb = difficultyScaler.getCarProbability(totalDistance)

        val roll = MathUtils.random()

        return when {
            roll < pedProb -> createPedestrianEntity(z, totalDistance)
            roll < pedProb + carProb -> createCarEntity(z, totalDistance)
            else -> {
                // Static obstacle
                val staticRoll = MathUtils.random()
                val x = getRandomLaneX()
                when {
                    staticRoll < 0.25f -> createManholeEntity(x, z)
                    staticRoll < 0.45f -> createPuddleEntity(x, z)
                    staticRoll < 0.65f -> createPotholeEntity(x, z)
                    staticRoll < 0.85f -> createCurbEntity(z)
                    else -> createManholeEntity(x, z)  // Default to manhole
                }
            }
        }
    }

    private fun getRandomLaneX(): Float {
        // Random position on road or sidewalk
        return MathUtils.random(-Constants.ROAD_WIDTH / 2 + 0.5f, Constants.ROAD_WIDTH / 2 - 0.5f)
    }

    private fun createManholeEntity(x: Float, z: Float): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply { position.set(x, 0f, z) })
        entity.add(ModelComponent().apply { modelInstance = ModelInstance(manholeModel) })
        entity.add(ColliderComponent().apply {
            setSize(Constants.MANHOLE_RADIUS * 2, 0.1f, Constants.MANHOLE_RADIUS * 2)
            collisionGroup = CollisionGroups.OBSTACLE
        })
        entity.add(ObstacleComponent().apply {
            type = ObstacleType.MANHOLE
            causesGameOver = true
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createPuddleEntity(x: Float, z: Float): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply { position.set(x, 0f, z) })
        entity.add(ModelComponent().apply { modelInstance = ModelInstance(puddleModel) })
        entity.add(ColliderComponent().apply {
            setSize(Constants.PUDDLE_WIDTH, 0.1f, Constants.PUDDLE_LENGTH)
            collisionGroup = CollisionGroups.OBSTACLE
        })
        entity.add(ObstacleComponent().apply {
            type = ObstacleType.PUDDLE
            causesGameOver = false  // Puddle doesn't kill, just affects control
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createPotholeEntity(x: Float, z: Float): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply { position.set(x, 0f, z) })
        entity.add(ModelComponent().apply { modelInstance = ModelInstance(potholeModel) })
        entity.add(ColliderComponent().apply {
            setSize(Constants.POTHOLE_RADIUS * 2, 0.1f, Constants.POTHOLE_RADIUS * 2)
            collisionGroup = CollisionGroups.OBSTACLE
        })
        entity.add(ObstacleComponent().apply {
            type = ObstacleType.POTHOLE
            causesGameOver = true
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createCurbEntity(z: Float): Entity {
        val entity = engine.createEntity()

        // Place curb at edge of road
        val side = if (MathUtils.randomBoolean()) -1 else 1
        val x = side * (Constants.ROAD_WIDTH / 2 - 0.3f)

        entity.add(TransformComponent().apply { position.set(x, 0f, z) })
        entity.add(ModelComponent().apply { modelInstance = ModelInstance(curbModel) })
        entity.add(ColliderComponent().apply {
            setSize(0.3f, Constants.CURB_HEIGHT, 1f)
            collisionGroup = CollisionGroups.OBSTACLE
        })
        entity.add(ObstacleComponent().apply {
            type = ObstacleType.CURB
            causesGameOver = true
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createPedestrianEntity(z: Float, totalDistance: Float): Entity {
        val entity = engine.createEntity()

        // Start pedestrian at edge of road, walking across
        val startSide = if (MathUtils.randomBoolean()) -1 else 1
        val x = startSide * (Constants.ROAD_WIDTH / 2 + 1f)

        entity.add(TransformComponent().apply { position.set(x, 0f, z) })
        entity.add(ModelComponent().apply { modelInstance = ModelInstance(pedestrianModel) })
        entity.add(VelocityComponent())
        entity.add(ColliderComponent().apply {
            setSize(Constants.PEDESTRIAN_WIDTH, Constants.PEDESTRIAN_HEIGHT, 0.3f)
            collisionGroup = CollisionGroups.OBSTACLE
        })
        entity.add(ObstacleComponent().apply {
            type = ObstacleType.PEDESTRIAN
            causesGameOver = true
        })
        entity.add(PedestrianComponent().apply {
            walkSpeed = difficultyScaler.getPedestrianSpeed(totalDistance)
            direction.set(-startSide.toFloat(), 0f, 0f)  // Walk towards opposite side
            minX = -Constants.ROAD_WIDTH / 2 - 2f
            maxX = Constants.ROAD_WIDTH / 2 + 2f
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createCarEntity(z: Float, totalDistance: Float): Entity {
        val entity = engine.createEntity()

        // Car in a lane, moving in same or opposite direction
        val lane = if (MathUtils.randomBoolean()) -1 else 1
        val x = lane * 1.5f  // Lane position
        val direction = if (lane == 1) 1 else -1  // Right lane = same direction, left = opposite

        // If same direction, start behind player; if opposite, start ahead
        val startZ = if (direction == 1) z - 20f else z + 50f

        entity.add(TransformComponent().apply {
            position.set(x, 0f, startZ)
            if (direction == -1) {
                yaw = 180f
                updateRotationFromYaw()
            }
        })
        entity.add(ModelComponent().apply { modelInstance = ModelInstance(carModels.random().model) })
        entity.add(VelocityComponent())
        entity.add(ColliderComponent().apply {
            setSize(Constants.CAR_WIDTH, Constants.CAR_HEIGHT, Constants.CAR_LENGTH)
            collisionGroup = CollisionGroups.OBSTACLE
        })
        entity.add(ObstacleComponent().apply {
            type = ObstacleType.CAR
            causesGameOver = true
        })
        entity.add(CarComponent().apply {
            speed = difficultyScaler.getCarSpeed(totalDistance)
            this.lane = if (lane == 1) 1 else 0
            this.direction = direction
        })

        engine.addEntity(entity)
        return entity
    }

    private fun removeChunk(chunkIndex: Int) {
        activeChunks[chunkIndex]?.forEach { entity ->
            engine.removeEntity(entity)
        }
        activeChunks.remove(chunkIndex)
    }

    fun reset() {
        // Remove all chunks
        activeChunks.keys.toList().forEach { removeChunk(it) }
    }
}
