package com.eucleantoomuch.game.procedural

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.eucleantoomuch.game.ecs.components.*
import com.eucleantoomuch.game.rendering.ProceduralModels
import com.eucleantoomuch.game.util.Constants
import net.mgsx.gltf.loaders.glb.GLBLoader
import net.mgsx.gltf.scene3d.scene.Scene
import net.mgsx.gltf.scene3d.scene.SceneAsset

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
    private var potholeModel = models.createPotholeModel()

    // Curb models of varying lengths (3-8 segments, each segment is ~2 meters)
    private val curbModels = mutableMapOf<Int, Model>()

    // Pedestrian models with different shirt colors
    private val pedestrianModels = mutableListOf<Model>()

    // Pre-create some building models with different heights
    // Triple: height, detailed model, simple model (LOD)
    private val buildingModels = mutableListOf<Triple<Float, ModelInstance, ModelInstance>>()
    private val skyscraperModels = mutableListOf<Triple<Float, ModelInstance, ModelInstance>>()  // Rare tall buildings
    private val carModels = mutableListOf<ModelInstance>()
    private var carGlbAsset: SceneAsset? = null
    private var taxiGlbAsset: SceneAsset? = null
    private var sportscarGlbAsset: SceneAsset? = null
    private var carGlbScale = 1f  // Scale factor for GLB model to match game units
    private var taxiGlbScale = 1f  // Scale factor for taxi GLB model
    private var sportscarGlbScale = 1f  // Scale factor for sportscar GLB model
    private var useGlbCars = false  // Flag to track if GLB cars are being used

    // Track skyscraper cluster state
    private var skyscraperClusterRemaining = 0  // How many more skyscrapers in current cluster
    private var lastSkyscraperChunk = -100      // Last chunk with a skyscraper

    // Scenery models
    private val treeModels = mutableListOf<ModelInstance>()
    private var lampPostModel: ModelInstance
    private var benchModel: ModelInstance
    private var trashCanModel: ModelInstance
    private var bushModel: ModelInstance
    private val flowerBedModels = mutableListOf<ModelInstance>()  // Multiple lengths (3-6 segments)

    // Cloud models
    private val cloudModels = mutableListOf<ModelInstance>()

    // Pigeon models (walking and flying)
    private var pigeonWalkingModel: ModelInstance
    private var pigeonFlyingModel: ModelInstance

    // Shadow models for different entity sizes (eager initialization to avoid first-frame hitches)
    private val pedestrianShadowModel: Model
    private val carShadowModel: Model
    private val pigeonShadowModel: Model
    private val treeShadowModel: Model
    private val lampPostShadowModel: Model
    private val benchShadowModel: Model
    private val trashCanShadowModel: Model
    // Building shadow - slightly larger than building (width=6, depth=8)
    private val buildingShadowModel: Model

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

    // Tower crane model
    private var towerCraneModel: ModelInstance

    // Track which chunks have zebra crossings
    private var lastZebraCrossingChunk = -10  // Start with no recent crossing

    // Track tower crane placement to avoid clustering
    private var lastCraneChunk = -20  // Start with no recent crane

    init {
        // Initialize shadow models eagerly to avoid first-frame hitches
        pedestrianShadowModel = models.createBlobShadowModel(0.5f, 0.4f)
        carShadowModel = models.createBlobShadowModel(1.2f, 2.5f)
        pigeonShadowModel = models.createBlobShadowModel(0.18f, 0.15f)
        treeShadowModel = models.createBlobShadowModel(1.5f, 1.5f)
        lampPostShadowModel = models.createBlobShadowModel(0.6f, 0.6f)
        benchShadowModel = models.createBlobShadowModel(0.4f, 0.9f)
        trashCanShadowModel = models.createBlobShadowModel(0.4f, 0.4f)
        buildingShadowModel = models.createBuildingShadowModel(Constants.BUILDING_WIDTH + 2f, Constants.BUILDING_DEPTH + 2f)

        // Create zebra crossing model
        zebraCrossingModel = ModelInstance(models.createZebraCrossingModel())

        // Create curb models of varying lengths (3-20 segments, each ~2m)
        for (segments in 3..20) {
            val length = segments * 2f
            curbModels[segments] = models.createCurbModel(length)
        }

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

        // Load GLB car models
        try {
            val glbFile = Gdx.files.internal("car1.glb")
            carGlbAsset = GLBLoader().load(glbFile)
            val boundingBox = carGlbAsset!!.scene.model.calculateBoundingBox(com.badlogic.gdx.math.collision.BoundingBox())
            carGlbScale = Constants.CAR_LENGTH / maxOf(boundingBox.depth, boundingBox.width, 0.01f)
            Gdx.app.log("WorldGenerator", "Car GLB loaded, scale: $carGlbScale")
        } catch (e: Exception) {
            Gdx.app.log("WorldGenerator", "car1.glb not loaded: ${e.message}")
            carGlbAsset = null
        }

        // Load taxi GLB model
        try {
            val taxiFile = Gdx.files.internal("taxy.glb")
            taxiGlbAsset = GLBLoader().load(taxiFile)
            val boundingBox = taxiGlbAsset!!.scene.model.calculateBoundingBox(com.badlogic.gdx.math.collision.BoundingBox())
            taxiGlbScale = Constants.CAR_LENGTH / maxOf(boundingBox.depth, boundingBox.width, 0.01f)
            Gdx.app.log("WorldGenerator", "Taxi GLB loaded, scale: $taxiGlbScale")
        } catch (e: Exception) {
            Gdx.app.log("WorldGenerator", "taxy.glb not loaded: ${e.message}")
            taxiGlbAsset = null
        }

        // Load sportscar GLB model
        try {
            val sportscarFile = Gdx.files.internal("sportscar.glb")
            sportscarGlbAsset = GLBLoader().load(sportscarFile)
            val boundingBox = sportscarGlbAsset!!.scene.model.calculateBoundingBox(com.badlogic.gdx.math.collision.BoundingBox())
            sportscarGlbScale = Constants.CAR_LENGTH / maxOf(boundingBox.depth, boundingBox.width, 0.01f)
            Gdx.app.log("WorldGenerator", "Sportscar GLB loaded, scale: $sportscarGlbScale")
        } catch (e: Exception) {
            Gdx.app.log("WorldGenerator", "sportscar.glb not loaded: ${e.message}")
            sportscarGlbAsset = null
        }

        // Use GLB cars if at least one model loaded
        useGlbCars = carGlbAsset != null || taxiGlbAsset != null || sportscarGlbAsset != null

        // Create fallback procedural car models
        for (i in 0..3) {
            carModels.add(ModelInstance(models.createCarModel(models.getRandomCarColor())))
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

        // Add birch trees
        for (i in 0..4) {
            val height = MathUtils.random(8f, 12f)
            treeModels.add(ModelInstance(models.createBirchTreeModel(height)))
        }
        // Add a tall birch (rare)
        treeModels.add(ModelInstance(models.createBirchTreeModel(MathUtils.random(13f, 16f))))

        // Create other scenery models
        lampPostModel = ModelInstance(models.createLampPostModel())
        benchModel = ModelInstance(models.createBenchModel())
        trashCanModel = ModelInstance(models.createTrashCanModel())
        bushModel = ModelInstance(models.createBushModel())

        // Create flower beds with varying lengths (3-6 segments) and widths (2-4 rows)
        for (segments in 3..6) {
            for (rows in 2..4) {
                flowerBedModels.add(ModelInstance(models.createFlowerBedModel(segments, rows)))
            }
        }

        // Create cloud variants
        for (i in 0..4) {
            val scaleX = MathUtils.random(0.8f, 1.5f)
            val scaleZ = MathUtils.random(0.8f, 1.2f)
            cloudModels.add(ModelInstance(models.createCloudModel(scaleX, scaleZ)))
        }

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

        // Create tower crane model
        towerCraneModel = ModelInstance(models.createTowerCraneModel())
    }

    fun setRenderDistance(distance: Float) {
        renderDistance = distance
    }

    fun update(playerZ: Float, totalDistance: Float) {
        val behindDistance = 50f
        val startChunk = ((playerZ - behindDistance) / Constants.CHUNK_LENGTH).toInt()
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

        // Tower cranes (rare, decorative, behind buildings)
        entities.addAll(generateTowerCranes(chunkIndex, chunkStartZ))

        // Scenery between buildings (trees, benches, etc.)
        entities.addAll(generateScenery(chunkIndex, chunkStartZ))

        // Sidewalk pedestrians (not crossing, just walking around)
        entities.addAll(generateSidewalkPedestrians(chunkIndex, chunkStartZ))

        // Clouds in the sky
        entities.addAll(generateClouds(chunkIndex, chunkStartZ))

        // Pigeons on sidewalks
        entities.addAll(generatePigeons(chunkIndex, chunkStartZ))

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

        // Add shadow for crossing pedestrian
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(pedestrianShadowModel)
            scale = 1f
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

        // Add shadow for sidewalk pedestrian
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(pedestrianShadowModel)
            scale = 1f
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
        entity1.add(ShadowComponent().apply { shadowInstance = ModelInstance(pedestrianShadowModel); scale = 1f })
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
        entity2.add(ShadowComponent().apply { shadowInstance = ModelInstance(pedestrianShadowModel); scale = 1f })
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
                chunkIndex,
                isLeftSide = true
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
                chunkIndex,
                isLeftSide = false
            ))

            // Add trees between buildings (in the gaps along Z axis) - ~40% chance per gap
            // Buildings have depth of 8m and spacing of ~12m, so gap is about 4m wide
            // Gap center is between current building and next one
            val gapCenterZ = z + buildingSpacing / 2

            if (MathUtils.random() < 0.4f) {
                // Tree on left side - same X line as buildings, in Z gap between them
                val treeX = -Constants.BUILDING_OFFSET_X - leftXOffset
                entities.add(createBetweenBuildingsTree(treeX, gapCenterZ, chunkIndex))
            }
            if (MathUtils.random() < 0.4f) {
                // Tree on right side - same X line as buildings, in Z gap between them
                val treeX = Constants.BUILDING_OFFSET_X + rightXOffset
                entities.add(createBetweenBuildingsTree(treeX, gapCenterZ, chunkIndex))
            }

            z += buildingSpacing + MathUtils.random(-3f, 3f)
        }

        return entities
    }

    /**
     * Creates a tree entity positioned between buildings.
     * Uses slightly smaller trees that fit well in gaps.
     */
    private fun createBetweenBuildingsTree(x: Float, z: Float, chunkIndex: Int): Entity {
        val entity = engine.createEntity()

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
            yaw = MathUtils.random(0f, 360f)
        })

        val modelInstance = ModelInstance(treeModels.random().model)

        // Scale down slightly to fit between buildings
        val treeScale = MathUtils.random(0.6f, 0.85f)
        modelInstance.transform.scale(treeScale, treeScale, treeScale)

        entity.add(ModelComponent().apply {
            this.modelInstance = modelInstance
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createBuildingEntity(
        x: Float,
        z: Float,
        height: Float,
        detailedModel: ModelInstance,
        simpleModel: ModelInstance,
        chunkIndex: Int,
        isLeftSide: Boolean  // Used to rotate building so windows/doors face road
    ): Entity {
        val entity = engine.createEntity()

        // Rotate building so windows/doors face the road
        // Left side buildings: windows on +X side already face road (no rotation)
        // Right side buildings: need 180° rotation so +X side faces road (towards negative X)
        val yaw = if (isLeftSide) 0f else 180f

        val transform = TransformComponent().apply {
            position.set(x, 0f, z)
            this.yaw = yaw
            updateRotationFromYaw()
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

        // Add building shadow - directly under building on grass
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(buildingShadowModel)
            scale = 1f
            xOffset = 0f
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

        // Add small shadow for pigeon
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(pigeonShadowModel)
            scale = 1f
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
     * Generate tower cranes in this chunk.
     * Cranes appear occasionally and not too close together.
     * They are placed near buildings to suggest construction sites.
     */
    private fun generateTowerCranes(chunkIndex: Int, chunkStartZ: Float): List<Entity> {
        val entities = mutableListOf<Entity>()

        // Only generate cranes if enough distance from last crane (at least 4 chunks apart)
        if (chunkIndex - lastCraneChunk < 4) return entities

        // 25% chance to spawn a crane in this chunk
        if (MathUtils.random() > 0.25f) return entities

        // Place crane on one side of the road, near buildings
        val isLeftSide = MathUtils.randomBoolean()
        val sideMultiplier = if (isLeftSide) -1f else 1f

        // Position crane at or slightly behind buildings (buildings are at BUILDING_OFFSET_X = 14f)
        val craneX = sideMultiplier * (Constants.BUILDING_OFFSET_X + MathUtils.random(-2f, 5f))
        val craneZ = chunkStartZ + MathUtils.random(10f, Constants.CHUNK_LENGTH - 10f)

        entities.add(createTowerCraneEntity(craneX, craneZ, chunkIndex, isLeftSide))
        lastCraneChunk = chunkIndex

        return entities
    }

    /**
     * Create a tower crane entity
     */
    private fun createTowerCraneEntity(x: Float, z: Float, chunkIndex: Int, isLeftSide: Boolean): Entity {
        val entity = engine.createEntity()

        // Random rotation so cranes face different directions
        val baseYaw = if (isLeftSide) 90f else -90f  // Face towards road
        val randomYawOffset = MathUtils.random(-45f, 45f)

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
            yaw = baseYaw + randomYawOffset
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(towerCraneModel.model)
        })

        entity.add(GroundComponent().apply {
            type = GroundType.BUILDING
            this.chunkIndex = chunkIndex
        })

        // No collider - cranes are far from the road and purely decorative

        engine.addEntity(entity)
        return entity
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

    @Suppress("UNUSED_PARAMETER")
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

        // Add shadow for tree (scale > 1 renders at sidewalk height)
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(treeShadowModel)
            scale = 1.5f
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
            type = ObstacleType.BENCH
            causesGameOver = true
        })

        // Add shadow for bench
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(benchShadowModel)
            scale = 1f
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
            type = ObstacleType.RECYCLE_BIN
            causesGameOver = false  // Trash cans can be knocked over
            isKnockable = true
        })

        // Add shadow for trash can
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(trashCanShadowModel)
            scale = 1f
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createFlowerBedEntity(x: Float, z: Float, chunkIndex: Int): Entity {
        val entity = engine.createEntity()

        // Pick a random flower bed model (different lengths)
        val randomFlowerBed = flowerBedModels[MathUtils.random(flowerBedModels.size - 1)]

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
            // Rotate 90 degrees so flower bed runs parallel to road (along Z axis)
            yaw = 90f
            updateRotationFromYaw()
        })

        entity.add(ModelComponent().apply {
            modelInstance = ModelInstance(randomFlowerBed.model)
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
            type = ObstacleType.STREET_LIGHT
            causesGameOver = true
        })

        // Add shadow for lamp post (scale > 1 renders at sidewalk height)
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(lampPostShadowModel)
            scale = 1.2f
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
                    staticRoll < 0.10f -> createManholeEntity(x, z)
                    staticRoll < 0.15f -> createPuddleEntity(x, z)
                    staticRoll < 0.20f -> createPotholeEntity(x, z)
                    else -> createCurbEntity(z)  // Curbs are very common (80% chance)
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

        // Random puddle size - width 0.8 to 2.5m, length 1.0 to 3.5m
        val widthScale = MathUtils.random(0.5f, 1.7f)
        val lengthScale = MathUtils.random(0.5f, 1.8f)
        val puddleWidth = Constants.PUDDLE_WIDTH * widthScale
        val puddleLength = Constants.PUDDLE_LENGTH * lengthScale

        // Random rotation for variety (0, 90, or slight angles)
        val rotation = MathUtils.random(-25f, 25f)

        entity.add(TransformComponent().apply {
            position.set(x, 0f, z)
            yaw = rotation
            updateRotationFromYaw()
        })

        // Create custom sized puddle model
        val customPuddleModel = models.createPuddleModel(puddleWidth, puddleLength)
        entity.add(ModelComponent().apply { modelInstance = ModelInstance(customPuddleModel) })

        entity.add(ColliderComponent().apply {
            setSize(puddleWidth, 0.1f, puddleLength)
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

        // Random length 3-20 segments (each segment ~2m, so 6-40 meters)
        val segments = MathUtils.random(3, 20)
        val length = segments * 2f
        val curbModel = curbModels[segments]!!

        // Place curb at edge of road
        val side = if (MathUtils.randomBoolean()) -1 else 1
        val x = side * (Constants.ROAD_WIDTH / 2 - 0.1f)

        // Center the curb at z position (so it extends forward and backward)
        entity.add(TransformComponent().apply { position.set(x, 0f, z + length / 2f) })
        entity.add(ModelComponent().apply { modelInstance = ModelInstance(curbModel) })
        entity.add(ColliderComponent().apply {
            setSize(0.3f, Constants.CURB_HEIGHT, length)
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

        // Add shadow for pedestrian
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(pedestrianShadowModel)
            scale = 1f
        })

        engine.addEntity(entity)
        return entity
    }

    private fun createCarEntity(z: Float, totalDistance: Float): Entity {
        val entity = engine.createEntity()

        // Car in a lane, moving in same or opposite direction
        // 70% chance for same direction (right lane), 30% for oncoming (left lane)
        // In this game: negative X = right side, positive X = left side (from camera view)
        val sameDirection = MathUtils.random() < 0.7f
        val lane = if (sameDirection) -1 else 1  // -1 = right lane (same dir), +1 = left lane (oncoming)
        val x = lane * 1.5f  // Lane position
        val direction = if (sameDirection) 1 else -1  // Same direction = forward, oncoming = backward

        // If same direction, start ahead of the obstacle position so player can catch up
        // If opposite direction, start further ahead so player can see them coming
        val startZ = if (direction == 1) z + 30f else z + 80f

        // Randomly choose between car1, taxi, and sportscar GLB models
        val availableAssets = mutableListOf<Triple<SceneAsset, Float, String>>()
        carGlbAsset?.let { availableAssets.add(Triple(it, carGlbScale, "car")) }
        taxiGlbAsset?.let { availableAssets.add(Triple(it, taxiGlbScale, "taxi")) }
        sportscarGlbAsset?.let { availableAssets.add(Triple(it, sportscarGlbScale, "sportscar")) }

        val chosen = if (availableAssets.isNotEmpty()) availableAssets.random() else null
        val chosenAsset = chosen?.first
        val chosenScale = chosen?.second ?: 1f

        // Apply scale for GLB model (100x bigger for testing)
        entity.add(TransformComponent().apply {
            position.set(x, 0f, startZ)
            yaw = if (direction == -1) 180f else 0f
            updateRotationFromYaw()
            if (useGlbCars && chosenAsset != null) {
                scale.set(chosenScale, chosenScale, chosenScale)
            }
        })

        // Create model component - use Scene for GLB models (PBR rendering)
        entity.add(ModelComponent().apply {
            if (useGlbCars && chosenAsset != null) {
                // Create Scene for PBR rendering with proper materials
                scene = Scene(chosenAsset.scene)
                modelInstance = scene!!.modelInstance
                isPbr = true
            } else {
                modelInstance = ModelInstance(carModels.random().model)
                isPbr = false
            }
        })
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
            this.lane = if (lane == -1) 0 else 1  // 0 = right lane, 1 = left lane
            this.direction = direction
        })

        // Add shadow for car
        entity.add(ShadowComponent().apply {
            shadowInstance = ModelInstance(carShadowModel)
            scale = 1f
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
        // Reset pigeon flock tracking
        nextFlockId = 0
        // Reset tower crane tracking
        lastCraneChunk = -20
    }

    fun dispose() {
        carGlbAsset?.dispose()
        taxiGlbAsset?.dispose()
        sportscarGlbAsset?.dispose()
    }
}
