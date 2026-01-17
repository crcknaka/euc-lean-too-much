package com.eucleantoomuch.game.procedural

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
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
    private var grassModel = models.createGrassAreaModel(Constants.CHUNK_LENGTH)
    private var manholeModel = models.createManholeModel()
    private var puddleModel = models.createPuddleModel()
    private var curbModel = models.createCurbModel()
    private var potholeModel = models.createPotholeModel()

    // Pedestrian models with different shirt colors
    private val pedestrianModels = mutableListOf<Model>()

    // Pre-create some building models with different heights
    // Triple: height, detailed model, simple model (LOD)
    private val buildingModels = mutableListOf<Triple<Float, ModelInstance, ModelInstance>>()
    private val skyscraperModels = mutableListOf<Triple<Float, ModelInstance, ModelInstance>>()  // Rare tall buildings
    private val carModels = mutableListOf<ModelInstance>()

    // Track skyscraper cluster state
    private var skyscraperClusterRemaining = 0  // How many more skyscrapers in current cluster
    private var lastSkyscraperChunk = -100      // Last chunk with a skyscraper

    // Scenery models
    private val treeModels = mutableListOf<ModelInstance>()
    private var lampPostModel: ModelInstance
    private var benchModel: ModelInstance
    private var trashCanModel: ModelInstance
    private var bushModel: ModelInstance
    private var flowerBedModel: ModelInstance

    // Cloud models
    private val cloudModels = mutableListOf<ModelInstance>()

    // Tower crane models
    private val towerCraneModels = mutableListOf<ModelInstance>()

    // Airplane model
    private lateinit var airplaneModel: ModelInstance

    // Pigeon models (walking and flying)
    private lateinit var pigeonWalkingModel: ModelInstance
    private lateinit var pigeonFlyingModel: ModelInstance

    // Track airplane spawning (very rare, one at a time in sky)
    private var lastAirplaneChunk = -100
    private var activeAirplaneEntity: Entity? = null

    // Track pigeon flock ID for group behavior
    private var nextFlockId = 0

    // Zebra crossing model
    private var zebraCrossingModel: ModelInstance

    // Background building models (silhouettes on horizon) - multiple fog layers
    private val backgroundBuildingModelsNear = mutableListOf<ModelInstance>()  // Less fog
    private val backgroundBuildingModelsMid = mutableListOf<ModelInstance>()   // Medium fog
    private val backgroundBuildingModelsFar = mutableListOf<ModelInstance>()   // Heavy fog

    // Fog wall model for front boundary
    private var fogWallModel: ModelInstance

    // Track which chunks have zebra crossings
    private var lastZebraCrossingChunk = -10  // Start with no recent crossing

    // Track tower crane spawning (rare, one at a time)
    private var lastCraneChunk = -50  // Start with no recent crane

    init {
        // Create zebra crossing model
        zebraCrossingModel = ModelInstance(models.createZebraCrossingModel())

        // Create pedestrians with different shirt colors
        for (i in 0..9) {
            pedestrianModels.add(models.createPedestrianModel(models.getRandomShirtColor()))
        }

        // Create variety of buildings with LOD versions
        for (i in 0..5) {
            val height = MathUtils.random(Constants.BUILDING_MIN_HEIGHT, Constants.BUILDING_MAX_HEIGHT)
            val color = models.getRandomBuildingColor()
            val detailedModel = models.createBuildingModel(height, color)
            val simpleModel = models.createBuildingModelSimple(height, color)
            buildingModels.add(Triple(height, ModelInstance(detailedModel), ModelInstance(simpleModel)))
        }
        // Add rare tall skyscrapers (for skyscraper clusters)
        for (i in 0..2) {
            val height = MathUtils.random(70f, 100f)  // Very tall buildings
            val color = models.getRandomBuildingColor()
            val detailedModel = models.createBuildingModel(height, color)
            val simpleModel = models.createBuildingModelSimple(height, color)
            skyscraperModels.add(Triple(height, ModelInstance(detailedModel), ModelInstance(simpleModel)))
        }

        // Create variety of cars
        for (i in 0..3) {
            val model = models.createCarModel(models.getRandomCarColor())
            carModels.add(ModelInstance(model))
        }

        // Create tree variants with height variation
        // Most trees are normal height, but occasionally a tall one
        for (i in 0..5) {
            val height = MathUtils.random(6f, 10f)  // Normal trees
            treeModels.add(ModelInstance(models.createTreeModel(height)))
        }
        // Add a couple of tall trees (rare)
        treeModels.add(ModelInstance(models.createTreeModel(MathUtils.random(14f, 18f))))

        for (i in 0..5) {
            val height = MathUtils.random(5f, 8f)  // Normal round trees
            treeModels.add(ModelInstance(models.createRoundTreeModel(height)))
        }
        // Add a tall round tree (rare)
        treeModels.add(ModelInstance(models.createRoundTreeModel(MathUtils.random(12f, 15f))))

        // Create other scenery models
        lampPostModel = ModelInstance(models.createLampPostModel())
        benchModel = ModelInstance(models.createBenchModel())
        trashCanModel = ModelInstance(models.createTrashCanModel())
        bushModel = ModelInstance(models.createBushModel())
        flowerBedModel = ModelInstance(models.createFlowerBedModel())

        // Create cloud variants
        for (i in 0..4) {
            val scaleX = MathUtils.random(0.8f, 1.5f)
            val scaleZ = MathUtils.random(0.8f, 1.2f)
            cloudModels.add(ModelInstance(models.createCloudModel(scaleX, scaleZ)))
        }

        // Create tower crane variants with different heights
        for (i in 0..2) {
            val height = MathUtils.random(40f, 55f)
            towerCraneModels.add(ModelInstance(models.createTowerCraneModel(height)))
        }

        // Create airplane model
        airplaneModel = ModelInstance(models.createAirplaneModel())

        // Create pigeon models
        pigeonWalkingModel = ModelInstance(models.createPigeonModel(isFlying = false))
        pigeonFlyingModel = ModelInstance(models.createPigeonModel(isFlying = true))

        // Create background building variants for each fog layer
        // Near layer (less fog)
        for (i in 0..5) {
            val height = MathUtils.random(25f, 60f)
            backgroundBuildingModelsNear.add(ModelInstance(models.createBackgroundBuildingModel(height, 0)))
        }
        // Mid layer (medium fog)
        for (i in 0..5) {
            val height = MathUtils.random(35f, 80f)
            backgroundBuildingModelsMid.add(ModelInstance(models.createBackgroundBuildingModel(height, 1)))
        }
        // Far layer (heavy fog)
        for (i in 0..5) {
            val height = MathUtils.random(40f, 100f)
            backgroundBuildingModelsFar.add(ModelInstance(models.createBackgroundBuildingModel(height, 2)))
        }

        // Create fog wall for front boundary
        fogWallModel = ModelInstance(models.createFogWallModel(300f, 120f))
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

        // Check if this chunk should have a zebra crossing
        // Crossings appear every 2-4 chunks, starting from chunk 1
        val hasZebraCrossing = chunkIndex > 0 &&
                chunkIndex - lastZebraCrossingChunk >= 2 &&
                MathUtils.random() < 0.5f  // 50% chance when conditions met

        if (hasZebraCrossing) {
            lastZebraCrossingChunk = chunkIndex
        }

        // Ground segment (road + sidewalks)
        entities.add(createGroundEntity(chunkIndex, chunkStartZ))

        // Grass areas on both sides
        entities.add(createGrassEntity(chunkIndex, chunkStartZ))

        // Add zebra crossing if this chunk has one
        val crossingZ = chunkStartZ + Constants.CHUNK_LENGTH / 2
        if (hasZebraCrossing) {
            entities.add(createZebraCrossingEntity(chunkIndex, crossingZ))
            // Add pedestrians crossing the road
            entities.addAll(generateCrossingPedestrians(chunkIndex, crossingZ, totalDistance))
        }

        // Buildings on both sides
        entities.addAll(generateBuildings(chunkIndex, chunkStartZ))

        // Tower cranes (rare, appear near construction/skyscraper areas)
        entities.addAll(generateTowerCranes(chunkIndex, chunkStartZ))

        // Scenery between buildings (trees, benches, etc.)
        entities.addAll(generateScenery(chunkIndex, chunkStartZ))

        // Sidewalk pedestrians (not crossing, just walking around)
        entities.addAll(generateSidewalkPedestrians(chunkIndex, chunkStartZ))

        // Clouds in the sky
        entities.addAll(generateClouds(chunkIndex, chunkStartZ))

        // Pigeons on sidewalks
        entities.addAll(generatePigeons(chunkIndex, chunkStartZ))

        // Airplanes in the sky (very rare)
        trySpawnAirplane(chunkIndex, chunkStartZ)

        // Background buildings on horizon (fill gaps between main buildings)
        entities.addAll(generateBackgroundBuildings(chunkIndex, chunkStartZ))

        // Skip obstacles in the very first chunk (give player time to start)
        if (chunkIndex > 0) {
            // Generate obstacles (skip area near zebra crossing)
            entities.addAll(generateObstacles(chunkStartZ, totalDistance, hasZebraCrossing))
        }

        activeChunks[chunkIndex] = entities
    }

    private fun createZebraCrossingEntity(chunkIndex: Int, z: Float): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(0f, 0f, z)
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(zebraCrossingModel.model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.ROAD
            this.chunkIndex = chunkIndex
        })

        engine.addEntity(entity)
        return entity
    }

    private fun generateCrossingPedestrians(chunkIndex: Int, crossingZ: Float, totalDistance: Float): List<Entity> {
        val entities = mutableListOf<Entity>()

        // More pedestrians at crossings based on difficulty
        val difficultyFactor = (totalDistance / Constants.HARD_DISTANCE).coerceIn(0f, 1f)
        val minPedestrians = 3
        val maxPedestrians = 5 + (difficultyFactor * 5).toInt()  // 3-10 pedestrians based on difficulty

        val numPedestrians = MathUtils.random(minPedestrians, maxPedestrians)

        // Sidewalk center positions
        val roadHalfWidth = Constants.ROAD_WIDTH / 2
        val leftSidewalkX = -roadHalfWidth - Constants.SIDEWALK_WIDTH / 2
        val rightSidewalkX = roadHalfWidth + Constants.SIDEWALK_WIDTH / 2

        for (i in 0 until numPedestrians) {
            // Choose which side pedestrian starts from
            val fromLeft = MathUtils.randomBoolean()
            val crossingDirectionX = if (fromLeft) 1f else -1f

            // Sidewalk X position with small variation
            val sidewalkX = if (fromLeft) {
                leftSidewalkX + MathUtils.random(-0.5f, 0.5f)
            } else {
                rightSidewalkX + MathUtils.random(-0.5f, 0.5f)
            }

            // Spawn pedestrians at various Z positions - some already at crossing, some walking towards it
            val progressRoll = MathUtils.random()
            val startZ: Float
            val state: PedestrianState

            when {
                // 30% - already at zebra, crossing
                progressRoll < 0.3f -> {
                    startZ = crossingZ + MathUtils.random(-1.5f, 1.5f)
                    state = PedestrianState.CROSSING
                }
                // 40% - walking towards zebra on sidewalk (behind the crossing)
                progressRoll < 0.7f -> {
                    // Spawn behind the crossing (negative Z offset = closer to player)
                    startZ = crossingZ - MathUtils.random(3f, 12f)
                    state = PedestrianState.WALKING_TO_CROSSING
                }
                // 30% - walking towards zebra from ahead of it
                else -> {
                    // Spawn ahead of the crossing
                    startZ = crossingZ + MathUtils.random(3f, 12f)
                    state = PedestrianState.WALKING_TO_CROSSING
                }
            }

            val entity = createCrossingPedestrianEntity(
                sidewalkX, startZ, crossingZ, chunkIndex, crossingDirectionX, state
            )
            entities.add(entity)
        }

        return entities
    }

    private fun createCrossingPedestrianEntity(
        x: Float,
        z: Float,
        crossingZ: Float,
        chunkIndex: Int,
        crossingDirectionX: Float,
        state: PedestrianState
    ): Entity {
        val entity = engine.createEntity()

        // Determine initial yaw based on state
        // MovementSystem rotates velocity by yaw: yaw -90 moves +X, yaw 90 moves -X
        val initialYaw = when (state) {
            PedestrianState.WALKING_TO_CROSSING -> {
                // Face towards the crossing (along Z axis)
                if (z < crossingZ) 0f else 180f  // 0 = forward (+Z), 180 = backward (-Z)
            }
            PedestrianState.CROSSING -> {
                // Face the direction of crossing (along X axis)
                // yaw -90 = moving +X (right), yaw 90 = moving -X (left)
                if (crossingDirectionX > 0) -90f else 90f
            }
            else -> 0f
        }

        entity.add(TransformComponent().apply {
            position.set(x, 0.01f, z)  // Slightly above ground to prevent z-fighting
            yaw = initialYaw
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(pedestrianModels.random())
        })

        entity.add(ColliderComponent().apply {
            setSize(Constants.PEDESTRIAN_WIDTH, Constants.PEDESTRIAN_HEIGHT, Constants.PEDESTRIAN_WIDTH)
            collisionGroup = CollisionGroups.OBSTACLE
        })

        entity.add(ObstacleComponent().apply {
            type = ObstacleType.PEDESTRIAN
            causesGameOver = true
        })

        // Vary speed for natural look
        val speed = MathUtils.random(Constants.PEDESTRIAN_MIN_SPEED * 0.8f, Constants.PEDESTRIAN_MAX_SPEED * 1.2f)
        entity.add(VelocityComponent())

        // Add PedestrianComponent so PedestrianAISystem can process them
        entity.add(PedestrianComponent().apply {
            isSidewalkPedestrian = false
            this.state = state
            targetCrossingZ = crossingZ
            this.crossingDirectionX = crossingDirectionX
            walkSpeed = speed
            // Set bounds
            minX = -Constants.ROAD_WIDTH / 2 - Constants.SIDEWALK_WIDTH
            maxX = Constants.ROAD_WIDTH / 2 + Constants.SIDEWALK_WIDTH
        })

        entity.add(GroundComponent().apply {
            type = GroundType.ROAD
            this.chunkIndex = chunkIndex
        })

        engine.addEntity(entity)
        return entity
    }

    private fun generateSidewalkPedestrians(chunkIndex: Int, chunkStartZ: Float): List<Entity> {
        val entities = mutableListOf<Entity>()

        // Sidewalk boundaries
        val roadHalfWidth = Constants.ROAD_WIDTH / 2
        val sidewalkLeftX = -roadHalfWidth - Constants.SIDEWALK_WIDTH / 2
        val sidewalkRightX = roadHalfWidth + Constants.SIDEWALK_WIDTH / 2

        // 0-2 pedestrians per chunk on each side (reduced)
        val numPedestriansPerSide = MathUtils.random(0, 2)

        // Left sidewalk
        for (i in 0 until numPedestriansPerSide) {
            val z = chunkStartZ + MathUtils.random(5f, Constants.CHUNK_LENGTH - 5f)
            val x = sidewalkLeftX + MathUtils.random(-1f, 1f)
            entities.add(createSidewalkPedestrianEntity(x, z, chunkIndex))
        }

        // Right sidewalk
        for (i in 0 until numPedestriansPerSide) {
            val z = chunkStartZ + MathUtils.random(5f, Constants.CHUNK_LENGTH - 5f)
            val x = sidewalkRightX + MathUtils.random(-1f, 1f)
            entities.add(createSidewalkPedestrianEntity(x, z, chunkIndex))
        }

        // Sometimes add a pair of chatting pedestrians (10% chance)
        if (MathUtils.random() < 0.1f) {
            val side = if (MathUtils.randomBoolean()) sidewalkLeftX else sidewalkRightX
            val z = chunkStartZ + MathUtils.random(10f, Constants.CHUNK_LENGTH - 10f)
            entities.addAll(createChattingPedestrianPair(side, z, chunkIndex))
        }

        return entities
    }

    private fun createSidewalkPedestrianEntity(x: Float, z: Float, chunkIndex: Int): Entity {
        val entity = engine.createEntity()

        // Random initial direction
        val walkDirection = if (MathUtils.randomBoolean()) 1f else -1f

        entity.add(TransformComponent().apply {
            position.set(x, 0.01f, z)  // Slightly above ground to prevent z-fighting
            yaw = if (walkDirection > 0) 0f else 180f
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(pedestrianModels.random())
        })

        entity.add(ColliderComponent().apply {
            setSize(Constants.PEDESTRIAN_WIDTH, Constants.PEDESTRIAN_HEIGHT, Constants.PEDESTRIAN_WIDTH)
            collisionGroup = CollisionGroups.OBSTACLE
        })

        entity.add(ObstacleComponent().apply {
            type = ObstacleType.PEDESTRIAN
            causesGameOver = true
        })

        entity.add(VelocityComponent())

        entity.add(PedestrianComponent().apply {
            isSidewalkPedestrian = true
            walkDirectionZ = walkDirection
            walkSpeed = MathUtils.random(0.8f, 2.0f)
            state = if (MathUtils.random() < 0.1f) PedestrianState.STANDING else PedestrianState.WALKING
            stateTimer = MathUtils.random(0f, 3f)  // Random start offset
            nextStateChange = MathUtils.random(3f, 8f)
            standDuration = MathUtils.random(2f, 5f)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.ROAD
            this.chunkIndex = chunkIndex
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createChattingPedestrianPair(x: Float, z: Float, chunkIndex: Int): List<Entity> {
        val entities = mutableListOf<Entity>()
        val chatTime = MathUtils.random(5f, 15f)

        // First pedestrian
        val entity1 = engine.createEntity()
        entity1.add(TransformComponent().apply {
            position.set(x - 0.4f, 0.01f, z)  // Slightly above ground to prevent z-fighting
            yaw = 90f  // Face each other
            updateRotationFromYaw()
        })
        entity1.add(ModelComponent().apply { modelInstance = ModelInstance(pedestrianModels.random()) })
        entity1.add(ColliderComponent().apply {
            setSize(Constants.PEDESTRIAN_WIDTH, Constants.PEDESTRIAN_HEIGHT, Constants.PEDESTRIAN_WIDTH)
            collisionGroup = CollisionGroups.OBSTACLE
        })
        entity1.add(ObstacleComponent().apply { type = ObstacleType.PEDESTRIAN; causesGameOver = true })
        entity1.add(VelocityComponent())
        entity1.add(PedestrianComponent().apply {
            isSidewalkPedestrian = true
            state = PedestrianState.CHATTING
            chatDuration = chatTime
            stateTimer = 0f
        })
        entity1.add(GroundComponent().apply { type = GroundType.ROAD; this.chunkIndex = chunkIndex })
        engine.addEntity(entity1)
        entities.add(entity1)

        // Second pedestrian
        val entity2 = engine.createEntity()
        entity2.add(TransformComponent().apply {
            position.set(x + 0.4f, 0.01f, z)  // Slightly above ground to prevent z-fighting
            yaw = -90f  // Face first pedestrian
            updateRotationFromYaw()
        })
        entity2.add(ModelComponent().apply { modelInstance = ModelInstance(pedestrianModels.random()) })
        entity2.add(ColliderComponent().apply {
            setSize(Constants.PEDESTRIAN_WIDTH, Constants.PEDESTRIAN_HEIGHT, Constants.PEDESTRIAN_WIDTH)
            collisionGroup = CollisionGroups.OBSTACLE
        })
        entity2.add(ObstacleComponent().apply { type = ObstacleType.PEDESTRIAN; causesGameOver = true })
        entity2.add(VelocityComponent())
        entity2.add(PedestrianComponent().apply {
            isSidewalkPedestrian = true
            state = PedestrianState.CHATTING
            chatDuration = chatTime
            stateTimer = 0f
        })
        entity2.add(GroundComponent().apply { type = GroundType.ROAD; this.chunkIndex = chunkIndex })
        engine.addEntity(entity2)
        entities.add(entity2)

        return entities
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

    private fun createGrassEntity(chunkIndex: Int, startZ: Float): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(0f, 0f, startZ)
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(grassModel)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.ROAD  // Just for chunk tracking
            this.chunkIndex = chunkIndex
        })

        engine.addEntity(entity)
        return entity
    }

    private fun generateBuildings(chunkIndex: Int, chunkStartZ: Float): List<Entity> {
        val entities = mutableListOf<Entity>()
        val buildingSpacing = 12f

        // Check if we should start a skyscraper cluster (rare, ~5% chance per chunk)
        // But not too close to the last cluster
        if (skyscraperClusterRemaining == 0 &&
            chunkIndex - lastSkyscraperChunk > 5 &&
            MathUtils.random() < 0.05f) {
            // Start a cluster of 2-3 skyscrapers
            skyscraperClusterRemaining = MathUtils.random(2, 3)
            lastSkyscraperChunk = chunkIndex
        }

        var z = chunkStartZ
        while (z < chunkStartZ + Constants.CHUNK_LENGTH) {
            // Random X offset for variety (-2 to +3 meters from base position)
            // Positive = further from road, negative = closer to road
            val leftXOffset = MathUtils.random(-2f, 3f)
            val rightXOffset = MathUtils.random(-2f, 3f)

            // Decide if this building slot should be a skyscraper
            val useSkyscraperLeft = skyscraperClusterRemaining > 0 && MathUtils.randomBoolean()
            val useSkyscraperRight = skyscraperClusterRemaining > 0 && !useSkyscraperLeft

            // Left side building
            val (heightL, detailedL, simpleL) = if (useSkyscraperLeft && skyscraperModels.isNotEmpty()) {
                skyscraperClusterRemaining--
                skyscraperModels.random()
            } else {
                buildingModels.random()
            }
            entities.add(createBuildingEntity(
                -Constants.BUILDING_OFFSET_X - leftXOffset,
                z + MathUtils.random(-2f, 2f),
                heightL,
                ModelInstance(detailedL.model),
                ModelInstance(simpleL.model),
                chunkIndex
            ))

            // Right side building
            val (heightR, detailedR, simpleR) = if (useSkyscraperRight && skyscraperModels.isNotEmpty()) {
                skyscraperClusterRemaining--
                skyscraperModels.random()
            } else {
                buildingModels.random()
            }
            entities.add(createBuildingEntity(
                Constants.BUILDING_OFFSET_X + rightXOffset,
                z + MathUtils.random(-2f, 2f),
                heightR,
                ModelInstance(detailedR.model),
                ModelInstance(simpleR.model),
                chunkIndex
            ))

            z += buildingSpacing + MathUtils.random(-3f, 3f)
        }

        return entities
    }

    private fun createBuildingEntity(
        x: Float,
        z: Float,
        height: Float,
        detailedModel: ModelInstance,
        simpleModel: ModelInstance,
        chunkIndex: Int
    ): Entity {
        val entity = engine.createEntity()

        val transform = TransformComponent().apply {
            position.set(x, 0f, z)
        }
        entity.add(transform)

        val model = ModelComponent().apply {
            this.modelInstance = detailedModel
            this.modelInstanceLod = simpleModel
        }
        entity.add(model)

        val ground = GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        }
        entity.add(ground)

        // Add collider for building
        entity.add(ColliderComponent().apply {
            setSize(Constants.BUILDING_WIDTH, height, Constants.BUILDING_DEPTH)
            collisionGroup = CollisionGroups.OBSTACLE
        })

        entity.add(ObstacleComponent().apply {
            type = ObstacleType.CURB  // Reuse type for buildings
            causesGameOver = true
        })

        engine.addEntity(entity)
        return entity
    }

    private fun generateScenery(chunkIndex: Int, chunkStartZ: Float): List<Entity> {
        val entities = mutableListOf<Entity>()

        // Grass zone: from sidewalk edge to building edge
        // Road edge: ROAD_WIDTH/2 = 4f
        // Sidewalk edge: ROAD_WIDTH/2 + SIDEWALK_WIDTH = 4 + 3 = 7f
        // Buildings at: BUILDING_OFFSET_X = 14f (but can be offset by -2 to +3)
        // Minimum building X = 14 - 2 = 12f, near edge = 12 - 3 = 9f
        // Grass area: from 7.5f to 8.5f (safe zone that never overlaps with buildings)
        val grassStartX = Constants.ROAD_WIDTH / 2 + Constants.SIDEWALK_WIDTH + 0.5f  // 0.5m into grass (at 7.5f)
        val grassEndX = Constants.ROAD_WIDTH / 2 + Constants.SIDEWALK_WIDTH + 1.5f    // 1.5m into grass (at 8.5f)

        var z = chunkStartZ + 2f
        while (z < chunkStartZ + Constants.CHUNK_LENGTH - 2f) {
            // Left side scenery - only on grass
            if (MathUtils.random() < 0.6f) {
                val xOffset = MathUtils.random(grassStartX, grassEndX)
                entities.add(createSceneryItem(-xOffset, z, chunkIndex, isLeftSide = true))
            }

            // Right side scenery - only on grass
            if (MathUtils.random() < 0.6f) {
                val xOffset = MathUtils.random(grassStartX, grassEndX)
                entities.add(createSceneryItem(xOffset, z, chunkIndex, isLeftSide = false))
            }

            z += MathUtils.random(5f, 10f)
        }

        // Add lamp posts with variety - sometimes none, sometimes one side, sometimes both
        // Lamp pattern varies per chunk: 0 = no lamps, 1 = left only, 2 = right only, 3 = both sides
        val lampPattern = when {
            MathUtils.random() < 0.15f -> 0  // 15% - no lamps this chunk
            MathUtils.random() < 0.25f -> 1  // ~21% - left side only
            MathUtils.random() < 0.35f -> 2  // ~22% - right side only
            else -> 3                         // ~42% - both sides
        }

        if (lampPattern > 0) {
            z = chunkStartZ + 5f
            while (z < chunkStartZ + Constants.CHUNK_LENGTH - 5f) {
                val lampOffsetX = Constants.ROAD_WIDTH / 2 + 0.5f  // Just 0.5m from road edge, on sidewalk

                // Left side lamp
                if (lampPattern == 1 || lampPattern == 3) {
                    entities.add(createLampPostEntity(-lampOffsetX, z, chunkIndex, isLeftSide = true))
                }

                // Right side lamp
                if (lampPattern == 2 || lampPattern == 3) {
                    entities.add(createLampPostEntity(lampOffsetX, z, chunkIndex, isLeftSide = false))
                }

                z += 25f
            }
        }

        return entities
    }

    private fun generateClouds(chunkIndex: Int, chunkStartZ: Float): List<Entity> {
        val entities = mutableListOf<Entity>()

        // Generate 2-4 clouds per chunk at random positions in the sky
        val numClouds = MathUtils.random(2, 4)
        for (i in 0 until numClouds) {
            val x = MathUtils.random(-50f, 50f)  // Wide range across the sky
            val y = MathUtils.random(50f, 80f)   // Higher in the sky (above buildings)
            val z = chunkStartZ + MathUtils.random(0f, Constants.CHUNK_LENGTH)

            entities.add(createCloudEntity(x, y, z, chunkIndex))
        }

        return entities
    }

    private fun generateBackgroundBuildings(chunkIndex: Int, chunkStartZ: Float): List<Entity> {
        val entities = mutableListOf<Entity>()

        // Multiple layers of background buildings for depth effect
        // Layer 1 (near): 25-45m from road center
        // Layer 2 (mid): 50-70m from road center
        // Layer 3 (far): 75-100m from road center

        val layer1DistanceX = 30f
        val layer2DistanceX = 55f
        val layer3DistanceX = 80f

        var z = chunkStartZ
        while (z < chunkStartZ + Constants.CHUNK_LENGTH) {
            // === LAYER 1 (near, less fog) ===
            // Left side
            val leftX1 = -layer1DistanceX - MathUtils.random(0f, 15f)
            entities.add(createBackgroundBuildingEntity(leftX1, z + MathUtils.random(-5f, 5f), chunkIndex, 0))

            // Right side
            val rightX1 = layer1DistanceX + MathUtils.random(0f, 15f)
            entities.add(createBackgroundBuildingEntity(rightX1, z + MathUtils.random(-5f, 5f), chunkIndex, 0))

            // === LAYER 2 (mid, medium fog) ===
            if (MathUtils.random() < 0.7f) {
                val leftX2 = -layer2DistanceX - MathUtils.random(0f, 15f)
                entities.add(createBackgroundBuildingEntity(leftX2, z + MathUtils.random(-3f, 8f), chunkIndex, 1))
            }
            if (MathUtils.random() < 0.7f) {
                val rightX2 = layer2DistanceX + MathUtils.random(0f, 15f)
                entities.add(createBackgroundBuildingEntity(rightX2, z + MathUtils.random(-3f, 8f), chunkIndex, 1))
            }

            // === LAYER 3 (far, heavy fog) ===
            if (MathUtils.random() < 0.5f) {
                val leftX3 = -layer3DistanceX - MathUtils.random(0f, 20f)
                entities.add(createBackgroundBuildingEntity(leftX3, z + MathUtils.random(0f, 10f), chunkIndex, 2))
            }
            if (MathUtils.random() < 0.5f) {
                val rightX3 = layer3DistanceX + MathUtils.random(0f, 20f)
                entities.add(createBackgroundBuildingEntity(rightX3, z + MathUtils.random(0f, 10f), chunkIndex, 2))
            }

            z += 15f + MathUtils.random(-2f, 2f)
        }

        // Add fog wall at front of chunk (only for chunks far ahead)
        entities.add(createFogWallEntity(chunkStartZ + Constants.CHUNK_LENGTH, chunkIndex))

        return entities
    }

    private fun createBackgroundBuildingEntity(x: Float, z: Float, chunkIndex: Int, fogLevel: Int): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
            // Random rotation for variety
            yaw = MathUtils.random(0f, 360f)
            updateRotationFromYaw()
        })

        // Select model based on fog level
        val modelList = when (fogLevel) {
            0 -> backgroundBuildingModelsNear
            1 -> backgroundBuildingModelsMid
            else -> backgroundBuildingModelsFar
        }

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(modelList.random().model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        // No collider - these are just visual background elements

        engine.addEntity(entity)
        return entity
    }

    private fun generateTowerCranes(chunkIndex: Int, chunkStartZ: Float): List<Entity> {
        val entities = mutableListOf<Entity>()

        // Tower cranes are rare - only spawn if:
        // 1. Enough distance from last crane (at least 8 chunks apart)
        // 2. Random chance (10% when conditions met)
        // 3. More likely near skyscraper clusters
        val distanceFromLastCrane = chunkIndex - lastCraneChunk
        val nearSkyscraperCluster = skyscraperClusterRemaining > 0 || chunkIndex - lastSkyscraperChunk < 3

        val spawnChance = if (nearSkyscraperCluster) 0.25f else 0.08f

        if (distanceFromLastCrane >= 8 && MathUtils.random() < spawnChance) {
            lastCraneChunk = chunkIndex

            // Decide which side (slightly more likely on right side)
            val isLeftSide = MathUtils.random() < 0.4f

            // Position crane behind buildings (further from road)
            val baseX = if (isLeftSide) {
                -Constants.BUILDING_OFFSET_X - MathUtils.random(8f, 15f)
            } else {
                Constants.BUILDING_OFFSET_X + MathUtils.random(8f, 15f)
            }

            val z = chunkStartZ + MathUtils.random(10f, Constants.CHUNK_LENGTH - 10f)

            entities.add(createTowerCraneEntity(baseX, z, chunkIndex, isLeftSide))
        }

        return entities
    }

    private fun createTowerCraneEntity(x: Float, z: Float, chunkIndex: Int, isLeftSide: Boolean): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
            // Rotate crane so jib points in interesting direction
            // If on left side, jib can point towards road or away
            // If on right side, same logic
            yaw = if (isLeftSide) {
                MathUtils.random(-30f, 120f)  // Mostly pointing towards road or forward
            } else {
                MathUtils.random(60f, 210f)   // Mostly pointing towards road or forward
            }
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(towerCraneModels.random().model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        // No collider - cranes are background decoration, too far from road to hit

        engine.addEntity(entity)
        return entity
    }

    private fun generatePigeons(chunkIndex: Int, chunkStartZ: Float): List<Entity> {
        val entities = mutableListOf<Entity>()

        // Pigeons appear in small flocks on sidewalks
        // 40% chance to have pigeons in this chunk
        if (MathUtils.random() > 0.4f) return entities

        // Sidewalk positions
        val roadHalfWidth = Constants.ROAD_WIDTH / 2
        val sidewalkLeftX = -roadHalfWidth - Constants.SIDEWALK_WIDTH / 2
        val sidewalkRightX = roadHalfWidth + Constants.SIDEWALK_WIDTH / 2

        // Choose a side for the flock
        val isLeftSide = MathUtils.randomBoolean()
        val baseX = if (isLeftSide) sidewalkLeftX else sidewalkRightX

        // Flock center position
        val flockCenterZ = chunkStartZ + MathUtils.random(10f, Constants.CHUNK_LENGTH - 10f)

        // Assign a unique flock ID so they startle together
        val flockId = nextFlockId++

        // 2-5 pigeons per flock
        val numPigeons = MathUtils.random(2, 5)

        for (i in 0 until numPigeons) {
            // Spread pigeons around the flock center
            val x = baseX + MathUtils.random(-1.5f, 1.5f)
            val z = flockCenterZ + MathUtils.random(-2f, 2f)

            entities.add(createPigeonEntity(x, z, chunkIndex, flockId))
        }

        return entities
    }

    private fun createPigeonEntity(x: Float, z: Float, chunkIndex: Int, flockId: Int): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
            yaw = MathUtils.random(0f, 360f)  // Random facing direction
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(pigeonWalkingModel.model)
        })

        entity.add(PigeonComponent().apply {
            this.flockId = flockId
            spawnPosition.set(x, 0f, z)

            // Randomize initial state
            state = if (MathUtils.random() < 0.3f) {
                PigeonComponent.State.PECKING
            } else {
                PigeonComponent.State.WALKING
            }

            // Vary detection radius slightly
            detectionRadius = MathUtils.random(3.5f, 5f)

            // Random walk direction
            walkDirection = MathUtils.random(0f, 360f)
            timeToNextDirectionChange = MathUtils.random(1f, 3f)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.ROAD
            this.chunkIndex = chunkIndex
        })

        // No collider - pigeons are small and fly away, not an obstacle

        engine.addEntity(entity)
        return entity
    }

    private fun createFogWallEntity(z: Float, chunkIndex: Int): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(0f, 0f, z)
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(fogWallModel.model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createCloudEntity(x: Float, y: Float, z: Float, chunkIndex: Int): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, y, z)
            // Random rotation for variety
            yaw = MathUtils.random(0f, 360f)
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(cloudModels.random().model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING  // Just for chunk tracking
            this.chunkIndex = chunkIndex
        })

        engine.addEntity(entity)
        return entity
    }

    /**
     * Try to spawn an airplane in the sky
     * Airplanes are very rare - only one can exist at a time
     */
    private fun trySpawnAirplane(chunkIndex: Int, chunkStartZ: Float) {
        // Check if there's already an active airplane
        if (activeAirplaneEntity != null) {
            // Check if airplane still exists in engine
            val airplane = activeAirplaneEntity
            if (airplane != null) {
                val airplaneComp = airplane.getComponent(AirplaneComponent::class.java)
                if (airplaneComp != null && airplaneComp.lifetime < airplaneComp.maxLifetime) {
                    return  // Airplane still active, don't spawn new one
                }
            }
            activeAirplaneEntity = null
        }

        // Check spawn conditions:
        // 1. At least 20 chunks since last airplane
        // 2. Random chance (3%)
        val distanceFromLastAirplane = chunkIndex - lastAirplaneChunk
        if (distanceFromLastAirplane < 20) return
        if (MathUtils.random() > 0.03f) return

        lastAirplaneChunk = chunkIndex

        // Spawn airplane
        val entity = engine.createEntity()

        // Random starting position - off to the side, high in the sky
        val startSide = if (MathUtils.randomBoolean()) -1f else 1f
        val startX = startSide * MathUtils.random(80f, 150f)  // Far to the side
        val altitude = MathUtils.random(100f, 140f)           // High in sky
        val startZ = chunkStartZ + MathUtils.random(-50f, 100f)

        // Flight direction - generally crossing the screen
        val dirX = -startSide * MathUtils.random(0.3f, 0.7f)  // Cross towards other side
        val dirZ = MathUtils.random(0.5f, 0.9f)               // Moving forward
        val direction = Vector3(dirX, 0f, dirZ).nor()

        // Calculate yaw from direction (plane nose points in flight direction)
        val yaw = MathUtils.atan2(direction.x, direction.z) * MathUtils.radiansToDegrees

        entity.add(TransformComponent().apply {
            position.set(startX, altitude, startZ)
            this.yaw = yaw
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(airplaneModel.model)
        })

        entity.add(AirplaneComponent().apply {
            speed = MathUtils.random(60f, 90f)  // Fast but visible
            this.direction.set(direction)
            this.altitude = altitude
            contrailEnabled = true
            maxLifetime = MathUtils.random(40f, 70f)  // 40-70 seconds flight
        })

        engine.addEntity(entity)
        activeAirplaneEntity = entity
    }

    private fun createSceneryItem(x: Float, z: Float, chunkIndex: Int, isLeftSide: Boolean): Entity {
        val roll = MathUtils.random()

        return when {
            roll < 0.35f -> createTreeEntity(x, z, chunkIndex, isLeftSide)
            roll < 0.50f -> createBushEntity(x, z, chunkIndex)
            roll < 0.65f -> createBenchEntity(x, z, chunkIndex, isLeftSide)
            roll < 0.80f -> createTrashCanEntity(x, z, chunkIndex)
            else -> createFlowerBedEntity(x, z, chunkIndex)
        }
    }

    private fun createTreeEntity(x: Float, z: Float, chunkIndex: Int, isLeftSide: Boolean): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(treeModels.random().model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        // Add collider - tree trunk
        entity.add(ColliderComponent().apply {
            setSize(0.4f, 4f, 0.4f)
            collisionGroup = CollisionGroups.OBSTACLE
        })

        entity.add(ObstacleComponent().apply {
            type = ObstacleType.CURB  // Reuse type for scenery
            causesGameOver = true
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createBushEntity(x: Float, z: Float, chunkIndex: Int): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(bushModel.model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createBenchEntity(x: Float, z: Float, chunkIndex: Int, isLeftSide: Boolean): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
            // Rotate bench to face the road
            yaw = if (isLeftSide) 90f else -90f
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(benchModel.model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        // Add collider - bench (scaled 1.6x)
        entity.add(ColliderComponent().apply {
            setSize(1.2f * 1.6f, 1f * 1.6f, 0.5f * 1.6f)
            collisionGroup = CollisionGroups.OBSTACLE
        })

        entity.add(ObstacleComponent().apply {
            type = ObstacleType.CURB
            causesGameOver = true
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createTrashCanEntity(x: Float, z: Float, chunkIndex: Int): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(trashCanModel.model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        // Add collider - trash can (scaled 1.6x)
        entity.add(ColliderComponent().apply {
            setSize(0.5f * 1.6f, 1f * 1.6f, 0.5f * 1.6f)
            collisionGroup = CollisionGroups.OBSTACLE
        })

        entity.add(ObstacleComponent().apply {
            type = ObstacleType.CURB
            causesGameOver = true
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createFlowerBedEntity(x: Float, z: Float, chunkIndex: Int): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(flowerBedModel.model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createLampPostEntity(x: Float, z: Float, chunkIndex: Int, isLeftSide: Boolean): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
            // Lamp arm extends along +Z in model space
            // For left side lamp: rotate so arm points towards road center (+X direction)
            // For right side lamp: rotate so arm points towards road center (-X direction)
            yaw = if (isLeftSide) 90f else -90f
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(lampPostModel.model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        // Add collider - lamp post
        entity.add(ColliderComponent().apply {
            setSize(0.3f, 5.5f, 0.3f)
            collisionGroup = CollisionGroups.OBSTACLE
        })

        entity.add(ObstacleComponent().apply {
            type = ObstacleType.CURB
            causesGameOver = true
        })

        engine.addEntity(entity)
        return entity
    }

    private fun generateObstacles(chunkStartZ: Float, totalDistance: Float, hasZebraCrossing: Boolean = false): List<Entity> {
        val entities = mutableListOf<Entity>()
        val minSpacing = difficultyScaler.getMinObstacleSpacing(totalDistance)
        val density = difficultyScaler.getObstacleDensity(totalDistance)

        // Zebra crossing zone - fewer obstacles near crossings (pedestrians are the main hazard there)
        val crossingCenterZ = chunkStartZ + Constants.CHUNK_LENGTH / 2
        val crossingClearance = 8f  // Reduced obstacles within 8m of zebra crossing

        var z = chunkStartZ + MathUtils.random(2f, 5f)
        while (z < chunkStartZ + Constants.CHUNK_LENGTH - 2f) {
            // Skip some obstacles near zebra crossing (pedestrians crossing are the main challenge)
            val effectiveDensity = if (hasZebraCrossing && kotlin.math.abs(z - crossingCenterZ) < crossingClearance) {
                density * 0.2f  // Only 20% of normal density near zebra crossings
            } else {
                density
            }

            if (MathUtils.random() < effectiveDensity) {
                val obstacle = generateRandomObstacle(z, totalDistance)
                obstacle?.let { entities.add(it) }
            }

            z += minSpacing + MathUtils.random(0f, 3f)
        }

        return entities
    }

    private fun generateRandomObstacle(z: Float, totalDistance: Float): Entity? {
        // Pedestrians only cross at zebra crossings now
        val carProb = difficultyScaler.getCarProbability(totalDistance)

        val roll = MathUtils.random()

        return when {
            roll < carProb -> {
                // Cars are created but NOT returned - they are managed by CullingSystem
                // not by chunk removal (so they don't disappear when chunk is removed)
                createCarEntity(z, totalDistance)
                null
            }
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
        entity.add(ModelComponent().apply { modelInstance = ModelInstance(pedestrianModels.random()) })
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
        // 70% chance for same direction (right lane), 30% for oncoming (left lane)
        val sameDirection = MathUtils.random() < 0.7f
        val lane = if (sameDirection) 1 else -1
        val x = lane * 1.5f  // Lane position
        val direction = if (lane == 1) 1 else -1  // Right lane = same direction, left = opposite

        // If same direction, start ahead of the obstacle position so player can catch up
        // If opposite direction, start further ahead so player can see them coming
        val startZ = if (direction == 1) z + 30f else z + 80f

        entity.add(TransformComponent().apply {
            position.set(x, 0f, startZ)
            yaw = if (direction == -1) 180f else 0f
            updateRotationFromYaw()
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
        // Reset zebra crossing tracking
        lastZebraCrossingChunk = -10
        // Reset skyscraper cluster tracking
        skyscraperClusterRemaining = 0
        lastSkyscraperChunk = -100
        // Reset tower crane tracking
        lastCraneChunk = -50
        // Reset airplane tracking
        lastAirplaneChunk = -100
        activeAirplaneEntity = null
        // Reset pigeon flock tracking
        nextFlockId = 0
    }
}
