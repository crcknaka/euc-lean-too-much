package com.eucleantoomuch.game.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.UBJsonReader
import com.eucleantoomuch.game.util.Constants

class ProceduralModels : Disposable {
    private val modelBuilder = ModelBuilder()
    private val models = mutableListOf<Model>()
    private val modelLoader = G3dModelLoader(UBJsonReader())

    private val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

    // Colors
    private val eucBodyColor = Color(0.2f, 0.2f, 0.2f, 1f)       // Dark gray
    private val eucWheelColor = Color(0.1f, 0.1f, 0.1f, 1f)      // Black
    private val riderBodyColor = Color(0.3f, 0.5f, 0.7f, 1f)     // Blue jacket
    private val riderPantsColor = Color(0.2f, 0.2f, 0.3f, 1f)    // Dark pants
    private val riderSkinColor = Color(0.9f, 0.7f, 0.6f, 1f)     // Skin
    private val roadColor = Color(0.3f, 0.3f, 0.3f, 1f)          // Dark gray
    private val sidewalkColor = Color(0.6f, 0.6f, 0.6f, 1f)      // Light gray
    private val roadLineColor = Color(1f, 1f, 1f, 1f)            // White
    private val manholeColor = Color(0.15f, 0.15f, 0.15f, 1f)    // Very dark
    private val puddleColor = Color(0.3f, 0.4f, 0.6f, 0.7f)      // Blue-ish
    private val curbColor = Color(0.5f, 0.5f, 0.5f, 1f)          // Gray
    private val potholeColor = Color(0.1f, 0.1f, 0.1f, 1f)       // Black
    private val pedestrianColor = Color(0.8f, 0.4f, 0.3f, 1f)    // Reddish
    private val carColor1 = Color(0.8f, 0.2f, 0.2f, 1f)          // Red
    private val carColor2 = Color(0.2f, 0.4f, 0.8f, 1f)          // Blue

    // Scale for external model (adjust based on your model's size)
    var eucModelScale = 1f
        private set

    // Rotation offset for external model (to fix orientation)
    var eucModelRotationX = 0f
        private set
    var eucModelRotationY = 0f
        private set

    fun createEucModel(): Model {
        // Try to load external model, fallback to procedural
        return try {
            val modelFile = Gdx.files.internal("monowheel.g3db")
            if (modelFile.exists()) {
                // External model loaded - apply scale and rotation
                eucModelScale = 0.003f  // Even smaller scale
                eucModelRotationX = 180f  // Flip vertically
                eucModelRotationY = 0f    // No horizontal flip (was facing backwards)
                modelLoader.loadModel(modelFile).also { models.add(it) }
            } else {
                eucModelScale = 1f
                createProceduralEucModel()
            }
        } catch (e: Exception) {
            Gdx.app.error("ProceduralModels", "Failed to load monowheel.g3db: ${e.message}")
            eucModelScale = 1f
            createProceduralEucModel()
        }
    }

    private fun createProceduralEucModel(): Model {
        modelBuilder.begin()

        // Wheel (cylinder lying on its side)
        val wheelMaterial = Material(ColorAttribute.createDiffuse(eucWheelColor))
        val wheelPart = modelBuilder.part("wheel", GL20.GL_TRIANGLES, attributes, wheelMaterial)
        wheelPart.cylinder(
            Constants.WHEEL_RADIUS * 2,  // width
            0.1f,                          // height (thickness)
            Constants.WHEEL_RADIUS * 2,  // depth
            16                             // divisions
        )

        // Body (box on top of wheel)
        val bodyMaterial = Material(ColorAttribute.createDiffuse(eucBodyColor))
        val bodyPart = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        bodyPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.25f, 0f))
        bodyPart.box(0.15f, 0.3f, 0.2f)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createRiderModel(): Model {
        modelBuilder.begin()

        val pantsMaterial = Material(ColorAttribute.createDiffuse(riderPantsColor))

        // Left leg
        val leftLegPart = modelBuilder.part("left_leg", GL20.GL_TRIANGLES, attributes, pantsMaterial)
        leftLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-0.08f, 0.5f, 0f))
        leftLegPart.box(0.1f, 0.5f, 0.12f)

        // Right leg
        val rightLegPart = modelBuilder.part("right_leg", GL20.GL_TRIANGLES, attributes, pantsMaterial)
        rightLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0.08f, 0.5f, 0f))
        rightLegPart.box(0.1f, 0.5f, 0.12f)

        // Body/torso
        val bodyMaterial = Material(ColorAttribute.createDiffuse(riderBodyColor))
        val bodyPart = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        bodyPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.0f, 0f))
        bodyPart.box(0.3f, 0.5f, 0.2f)

        // Head
        val skinMaterial = Material(ColorAttribute.createDiffuse(riderSkinColor))
        val headPart = modelBuilder.part("head", GL20.GL_TRIANGLES, attributes, skinMaterial)
        headPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.4f, 0f))
        headPart.sphere(0.2f, 0.25f, 0.2f, 8, 8)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createArmModel(): Model {
        modelBuilder.begin()

        val bodyMaterial = Material(ColorAttribute.createDiffuse(riderBodyColor))
        val armPart = modelBuilder.part("arm", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        // Arm is a box, pivot point at shoulder (top of arm)
        armPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, -0.2f, 0f))
        armPart.box(0.08f, 0.4f, 0.08f)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createGroundChunkModel(chunkLength: Float): Model {
        modelBuilder.begin()

        val roadMaterial = Material(ColorAttribute.createDiffuse(roadColor))
        val sidewalkMaterial = Material(ColorAttribute.createDiffuse(sidewalkColor))
        val lineMaterial = Material(ColorAttribute.createDiffuse(roadLineColor))

        // Road surface
        val roadPart = modelBuilder.part("road", GL20.GL_TRIANGLES, attributes, roadMaterial)
        roadPart.rect(
            -Constants.ROAD_WIDTH / 2, 0f, 0f,
            -Constants.ROAD_WIDTH / 2, 0f, chunkLength,
            Constants.ROAD_WIDTH / 2, 0f, chunkLength,
            Constants.ROAD_WIDTH / 2, 0f, 0f,
            0f, 1f, 0f
        )

        // Center line (dashed effect with small boxes)
        val lineSpacing = 3f
        var z = 1f
        while (z < chunkLength - 1f) {
            val linePart = modelBuilder.part("line_$z", GL20.GL_TRIANGLES, attributes, lineMaterial)
            linePart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.01f, z))
            linePart.box(0.15f, 0.01f, 1.5f)
            z += lineSpacing
        }

        // Left sidewalk
        val leftSidewalk = modelBuilder.part("sidewalk_left", GL20.GL_TRIANGLES, attributes, sidewalkMaterial)
        leftSidewalk.rect(
            -Constants.ROAD_WIDTH / 2 - Constants.SIDEWALK_WIDTH, 0.1f, 0f,
            -Constants.ROAD_WIDTH / 2 - Constants.SIDEWALK_WIDTH, 0.1f, chunkLength,
            -Constants.ROAD_WIDTH / 2, 0.1f, chunkLength,
            -Constants.ROAD_WIDTH / 2, 0.1f, 0f,
            0f, 1f, 0f
        )

        // Right sidewalk
        val rightSidewalk = modelBuilder.part("sidewalk_right", GL20.GL_TRIANGLES, attributes, sidewalkMaterial)
        rightSidewalk.rect(
            Constants.ROAD_WIDTH / 2, 0.1f, 0f,
            Constants.ROAD_WIDTH / 2, 0.1f, chunkLength,
            Constants.ROAD_WIDTH / 2 + Constants.SIDEWALK_WIDTH, 0.1f, chunkLength,
            Constants.ROAD_WIDTH / 2 + Constants.SIDEWALK_WIDTH, 0.1f, 0f,
            0f, 1f, 0f
        )

        return modelBuilder.end().also { models.add(it) }
    }

    fun createBuildingModel(height: Float, color: Color): Model {
        modelBuilder.begin()
        val material = Material(ColorAttribute.createDiffuse(color))
        val part = modelBuilder.part("building", GL20.GL_TRIANGLES, attributes, material)
        part.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, height / 2, 0f))
        part.box(Constants.BUILDING_WIDTH, height, Constants.BUILDING_DEPTH)
        return modelBuilder.end().also { models.add(it) }
    }

    fun createManholeModel(): Model {
        modelBuilder.begin()
        val material = Material(ColorAttribute.createDiffuse(manholeColor))
        val part = modelBuilder.part("manhole", GL20.GL_TRIANGLES, attributes, material)
        part.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.01f, 0f))
        part.cylinder(Constants.MANHOLE_RADIUS * 2, 0.02f, Constants.MANHOLE_RADIUS * 2, 16)
        return modelBuilder.end().also { models.add(it) }
    }

    fun createPuddleModel(): Model {
        modelBuilder.begin()
        val material = Material(ColorAttribute.createDiffuse(puddleColor))
        val part = modelBuilder.part("puddle", GL20.GL_TRIANGLES, attributes, material)
        part.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.005f, 0f))
        part.ellipse(Constants.PUDDLE_WIDTH, Constants.PUDDLE_LENGTH, 0f, 0f, 16,
            0f, 0f, 0f, 0f, 1f, 0f)
        return modelBuilder.end().also { models.add(it) }
    }

    fun createCurbModel(): Model {
        modelBuilder.begin()
        val material = Material(ColorAttribute.createDiffuse(curbColor))
        val part = modelBuilder.part("curb", GL20.GL_TRIANGLES, attributes, material)
        part.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, Constants.CURB_HEIGHT / 2, 0f))
        part.box(0.3f, Constants.CURB_HEIGHT, 1f)
        return modelBuilder.end().also { models.add(it) }
    }

    fun createPotholeModel(): Model {
        modelBuilder.begin()
        val material = Material(ColorAttribute.createDiffuse(potholeColor))
        val part = modelBuilder.part("pothole", GL20.GL_TRIANGLES, attributes, material)
        // Slightly sunken circle
        part.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, -0.02f, 0f))
        part.cylinder(Constants.POTHOLE_RADIUS * 2, 0.05f, Constants.POTHOLE_RADIUS * 2, 12)
        return modelBuilder.end().also { models.add(it) }
    }

    fun createPedestrianModel(): Model {
        modelBuilder.begin()
        val material = Material(ColorAttribute.createDiffuse(pedestrianColor))

        // Body
        val bodyPart = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, material)
        bodyPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.7f, 0f))
        bodyPart.box(Constants.PEDESTRIAN_WIDTH, 1f, 0.3f)

        // Head
        val skinMaterial = Material(ColorAttribute.createDiffuse(riderSkinColor))
        val headPart = modelBuilder.part("head", GL20.GL_TRIANGLES, attributes, skinMaterial)
        headPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.4f, 0f))
        headPart.sphere(0.2f, 0.25f, 0.2f, 8, 8)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createCarModel(color: Color = carColor1): Model {
        modelBuilder.begin()

        // Car body
        val bodyMaterial = Material(ColorAttribute.createDiffuse(color))
        val bodyPart = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        bodyPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.5f, 0f))
        bodyPart.box(Constants.CAR_WIDTH, 0.8f, Constants.CAR_LENGTH)

        // Roof
        val roofPart = modelBuilder.part("roof", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        roofPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.1f, -0.3f))
        roofPart.box(Constants.CAR_WIDTH * 0.9f, 0.5f, Constants.CAR_LENGTH * 0.5f)

        // Wheels (simple black boxes)
        val wheelMaterial = Material(ColorAttribute.createDiffuse(eucWheelColor))
        val positions = listOf(
            Pair(-0.7f, -1.3f), Pair(0.7f, -1.3f),
            Pair(-0.7f, 1.3f), Pair(0.7f, 1.3f)
        )
        positions.forEachIndexed { i, (x, z) ->
            val wheelPart = modelBuilder.part("wheel_$i", GL20.GL_TRIANGLES, attributes, wheelMaterial)
            wheelPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(x, 0.25f, z))
            wheelPart.box(0.2f, 0.5f, 0.4f)
        }

        return modelBuilder.end().also { models.add(it) }
    }

    fun getRandomBuildingColor(): Color {
        val colors = listOf(
            Color(0.7f, 0.7f, 0.7f, 1f),   // Light gray
            Color(0.5f, 0.5f, 0.5f, 1f),   // Gray
            Color(0.8f, 0.75f, 0.7f, 1f),  // Beige
            Color(0.6f, 0.5f, 0.4f, 1f),   // Brown-ish
            Color(0.7f, 0.6f, 0.5f, 1f),   // Tan
        )
        return colors.random()
    }

    fun getRandomCarColor(): Color {
        val colors = listOf(carColor1, carColor2, Color.WHITE, Color.BLACK, Color(0.3f, 0.3f, 0.3f, 1f))
        return colors.random()
    }

    override fun dispose() {
        models.forEach { it.dispose() }
        models.clear()
    }
}
