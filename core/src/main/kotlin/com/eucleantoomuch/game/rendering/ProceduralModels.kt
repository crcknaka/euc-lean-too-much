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
import com.badlogic.gdx.math.MathUtils
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
    private val helmetColor = Color(0.15f, 0.15f, 0.15f, 1f)     // Dark helmet
    private val helmetVisorColor = Color(0.1f, 0.1f, 0.12f, 1f)  // Slightly reflective visor
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

    // Environment colors
    private val grassColor = Color(0.35f, 0.55f, 0.25f, 1f)      // Green grass
    private val dirtColor = Color(0.4f, 0.3f, 0.2f, 1f)          // Brown dirt
    private val treeTrunkColor = Color(0.4f, 0.28f, 0.18f, 1f)   // Brown trunk
    private val treeLeavesColor = Color(0.2f, 0.45f, 0.2f, 1f)   // Green leaves
    private val lampPostColor = Color(0.25f, 0.25f, 0.25f, 1f)   // Dark gray metal
    private val benchWoodColor = Color(0.5f, 0.35f, 0.2f, 1f)    // Wood brown
    private val benchMetalColor = Color(0.2f, 0.2f, 0.2f, 1f)    // Dark metal
    private val trashCanColor = Color(0.15f, 0.25f, 0.15f, 1f)   // Dark green
    private val bushColor = Color(0.25f, 0.4f, 0.2f, 1f)         // Dark green bush
    private val cloudColor = Color(1f, 1f, 1f, 0.9f)             // White cloud

    // Tower crane colors
    private val craneYellowColor = Color(0.9f, 0.75f, 0.1f, 1f)    // Construction yellow
    private val craneMetalColor = Color(0.35f, 0.35f, 0.38f, 1f)   // Dark metal gray

    // Airplane colors
    private val airplaneBodyColor = Color(0.95f, 0.95f, 0.95f, 1f)  // White fuselage
    private val airplaneTailColor = Color(0.2f, 0.4f, 0.7f, 1f)     // Blue tail
    private val contrailColor = Color(1f, 1f, 1f, 0.7f)             // White contrail

    // Pigeon colors
    private val pigeonBodyColor = Color(0.45f, 0.45f, 0.5f, 1f)     // Gray body
    private val pigeonHeadColor = Color(0.35f, 0.4f, 0.45f, 1f)     // Darker gray head
    private val pigeonWingColor = Color(0.5f, 0.5f, 0.55f, 1f)      // Lighter gray wings
    private val pigeonBeakColor = Color(0.6f, 0.5f, 0.4f, 1f)       // Orange-ish beak

    // Background/silhouette colors (faded to look distant) - multiple layers
    // Layer 1 - closest background (behind main buildings)
    private val bgBuildingColor1 = Color(0.5f, 0.55f, 0.65f, 1f)   // Blue-gray
    private val bgBuildingColor2 = Color(0.45f, 0.5f, 0.6f, 1f)    // Darker blue-gray
    private val bgBuildingColor3 = Color(0.55f, 0.6f, 0.7f, 1f)    // Lighter blue-gray

    // Layer 2 - mid-distance (more faded)
    private val bgBuildingMidColor1 = Color(0.55f, 0.62f, 0.75f, 1f)  // More faded
    private val bgBuildingMidColor2 = Color(0.52f, 0.6f, 0.72f, 1f)

    // Layer 3 - far distance (almost sky color - heavy fog)
    private val bgBuildingFarColor1 = Color(0.48f, 0.65f, 0.82f, 1f)  // Very faded, close to sky
    private val bgBuildingFarColor2 = Color(0.46f, 0.63f, 0.8f, 1f)

    // Fog wall color (matches sky for seamless blend)
    private val fogWallColor = Color(0.5f, 0.7f, 0.9f, 1f)  // Same as sky

    // Building detail colors
    private val windowColor = Color(0.6f, 0.75f, 0.9f, 1f)       // Light blue glass
    private val windowLitColor = Color(0.95f, 0.9f, 0.7f, 1f)    // Warm yellow lit window
    private val doorColor = Color(0.35f, 0.25f, 0.15f, 1f)       // Dark brown door
    private val roofColor = Color(0.3f, 0.25f, 0.2f, 1f)         // Dark roof
    private val trimColor = Color(0.85f, 0.85f, 0.8f, 1f)        // Light trim/frame

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
                eucModelScale = 0.005f  // Bigger scale
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

        val riderScale = 1.4f  // Scale factor for rider

        val pantsMaterial = Material(ColorAttribute.createDiffuse(riderPantsColor))

        // Left leg
        val leftLegPart = modelBuilder.part("left_leg", GL20.GL_TRIANGLES, attributes, pantsMaterial)
        leftLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-0.1f * riderScale, 0.35f * riderScale, 0f))
        leftLegPart.box(0.12f * riderScale, 0.7f * riderScale, 0.14f * riderScale)

        // Right leg
        val rightLegPart = modelBuilder.part("right_leg", GL20.GL_TRIANGLES, attributes, pantsMaterial)
        rightLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0.1f * riderScale, 0.35f * riderScale, 0f))
        rightLegPart.box(0.12f * riderScale, 0.7f * riderScale, 0.14f * riderScale)

        // Body/torso
        val bodyMaterial = Material(ColorAttribute.createDiffuse(riderBodyColor))
        val bodyPart = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        bodyPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.0f * riderScale, 0f))
        bodyPart.box(0.35f * riderScale, 0.6f * riderScale, 0.22f * riderScale)

        // Head (face area visible below helmet)
        val skinMaterial = Material(ColorAttribute.createDiffuse(riderSkinColor))
        val headPart = modelBuilder.part("head", GL20.GL_TRIANGLES, attributes, skinMaterial)
        headPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.48f * riderScale, 0.02f * riderScale))
        headPart.sphere(0.18f * riderScale, 0.22f * riderScale, 0.18f * riderScale, 8, 8)

        // Helmet shell (covers top and back of head)
        val helmetMaterial = Material(ColorAttribute.createDiffuse(helmetColor))
        val helmetPart = modelBuilder.part("helmet", GL20.GL_TRIANGLES, attributes, helmetMaterial)
        helmetPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.54f * riderScale, -0.02f * riderScale))
        helmetPart.sphere(0.26f * riderScale, 0.24f * riderScale, 0.26f * riderScale, 10, 10)

        // Helmet visor/brim (front protective part)
        val visorMaterial = Material(ColorAttribute.createDiffuse(helmetVisorColor))
        val visorPart = modelBuilder.part("visor", GL20.GL_TRIANGLES, attributes, visorMaterial)
        visorPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.52f * riderScale, 0.12f * riderScale))
        visorPart.box(0.22f * riderScale, 0.06f * riderScale, 0.08f * riderScale)

        // Helmet vents (top detail)
        val ventPart = modelBuilder.part("vent", GL20.GL_TRIANGLES, attributes, visorMaterial)
        ventPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.66f * riderScale, 0f))
        ventPart.box(0.08f * riderScale, 0.03f * riderScale, 0.16f * riderScale)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createArmModel(): Model {
        modelBuilder.begin()

        val armScale = 1.4f  // Same scale as rider

        val bodyMaterial = Material(ColorAttribute.createDiffuse(riderBodyColor))
        val armPart = modelBuilder.part("arm", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        // Arm is a box, pivot point at shoulder (top of arm)
        armPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, -0.25f * armScale, 0f))
        armPart.box(0.1f * armScale, 0.5f * armScale, 0.1f * armScale)

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

        // Curbs along the road edges (fill the gap between road at 0f and sidewalk at 0.1f)
        val curbMaterial = Material(ColorAttribute.createDiffuse(curbColor))
        val curbHeight = 0.1f

        // Left curb - vertical face (at x = -ROAD_WIDTH/2, facing right towards road center)
        val leftCurb = modelBuilder.part("curb_left", GL20.GL_TRIANGLES, attributes, curbMaterial)
        leftCurb.rect(
            -Constants.ROAD_WIDTH / 2, curbHeight, 0f,
            -Constants.ROAD_WIDTH / 2, curbHeight, chunkLength,
            -Constants.ROAD_WIDTH / 2, 0f, chunkLength,
            -Constants.ROAD_WIDTH / 2, 0f, 0f,
            1f, 0f, 0f  // Normal pointing right (towards road center)
        )

        // Right curb - vertical face (at x = +ROAD_WIDTH/2, facing left towards road center)
        val rightCurb = modelBuilder.part("curb_right", GL20.GL_TRIANGLES, attributes, curbMaterial)
        rightCurb.rect(
            Constants.ROAD_WIDTH / 2, 0f, 0f,
            Constants.ROAD_WIDTH / 2, 0f, chunkLength,
            Constants.ROAD_WIDTH / 2, curbHeight, chunkLength,
            Constants.ROAD_WIDTH / 2, curbHeight, 0f,
            -1f, 0f, 0f  // Normal pointing left (towards road center)
        )

        return modelBuilder.end().also { models.add(it) }
    }

    fun createBuildingModel(height: Float, color: Color): Model {
        modelBuilder.begin()

        val width = Constants.BUILDING_WIDTH
        val depth = Constants.BUILDING_DEPTH
        val wallMaterial = Material(ColorAttribute.createDiffuse(color))
        val windowMat = Material(ColorAttribute.createDiffuse(windowColor))
        val windowLitMat = Material(ColorAttribute.createDiffuse(windowLitColor))
        val doorMat = Material(ColorAttribute.createDiffuse(doorColor))
        val roofMat = Material(ColorAttribute.createDiffuse(roofColor))
        val trimMat = Material(ColorAttribute.createDiffuse(trimColor))

        // Main building body
        val body = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, wallMaterial)
        body.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, height / 2, 0f))
        body.box(width, height, depth)

        // Windows only on the side facing the road (X faces)
        val floorHeight = 3.5f
        val numFloors = (height / floorHeight).toInt().coerceAtLeast(1)
        val windowWidth = 1.0f
        val windowHeight = 1.5f
        val windowSpacingZ = 2.0f
        val windowsPerFloor = ((depth - 1f) / windowSpacingZ).toInt().coerceAtLeast(1)

        // Door dimensions (needed to skip window in door area)
        val doorWidth = 1.2f
        val doorHeight = 2.5f

        // Collect window positions for batching - only 2 parts total (dark and lit windows)
        data class WindowPos(val x: Float, val y: Float, val z: Float)
        val darkWindows = mutableListOf<WindowPos>()
        val litWindows = mutableListOf<WindowPos>()

        // Collect all window positions
        for (floor in 0 until numFloors) {
            val windowY = floor * floorHeight + floorHeight * 0.6f
            val isGroundFloor = floor == 0

            for (w in 0 until windowsPerFloor) {
                val windowZ = -depth / 2 + 1f + w * windowSpacingZ + windowSpacingZ / 2
                val isNearDoor = kotlin.math.abs(windowZ) < (doorWidth / 2 + windowWidth / 2 + 0.2f)
                if (isGroundFloor && isNearDoor) continue

                val isLit = Math.random() < 0.3
                val list = if (isLit) litWindows else darkWindows

                // Left side window
                list.add(WindowPos(-width / 2 - 0.02f, windowY, windowZ))
                // Right side window
                list.add(WindowPos(width / 2 + 0.02f, windowY, windowZ))
            }
        }

        // Create single part for all dark windows
        if (darkWindows.isNotEmpty()) {
            val darkWindowsPart = modelBuilder.part("windows_dark", GL20.GL_TRIANGLES, attributes, windowMat)
            val tempMatrix = com.badlogic.gdx.math.Matrix4()
            for (win in darkWindows) {
                darkWindowsPart.setVertexTransform(tempMatrix.idt().translate(win.x, win.y, win.z))
                darkWindowsPart.box(0.05f, windowHeight, windowWidth)
            }
        }

        // Create single part for all lit windows
        if (litWindows.isNotEmpty()) {
            val litWindowsPart = modelBuilder.part("windows_lit", GL20.GL_TRIANGLES, attributes, windowLitMat)
            val tempMatrix = com.badlogic.gdx.math.Matrix4()
            for (win in litWindows) {
                litWindowsPart.setVertexTransform(tempMatrix.idt().translate(win.x, win.y, win.z))
                litWindowsPart.box(0.05f, windowHeight, windowWidth)
            }
        }

        // Left side door
        val doorLeft = modelBuilder.part("door_left", GL20.GL_TRIANGLES, attributes, doorMat)
        doorLeft.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-width / 2 - 0.03f, doorHeight / 2, 0f))
        doorLeft.box(0.06f, doorHeight, doorWidth)

        // Right side door
        val doorRight = modelBuilder.part("door_right", GL20.GL_TRIANGLES, attributes, doorMat)
        doorRight.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(width / 2 + 0.03f, doorHeight / 2, 0f))
        doorRight.box(0.06f, doorHeight, doorWidth)

        // Door frames
        val frameThickness = 0.15f
        // Left door frame
        val frameLeftTop = modelBuilder.part("frame_l_top", GL20.GL_TRIANGLES, attributes, trimMat)
        frameLeftTop.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-width / 2 - 0.04f, doorHeight + frameThickness / 2, 0f))
        frameLeftTop.box(0.08f, frameThickness, doorWidth + frameThickness * 2)

        // Right door frame
        val frameRightTop = modelBuilder.part("frame_r_top", GL20.GL_TRIANGLES, attributes, trimMat)
        frameRightTop.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(width / 2 + 0.04f, doorHeight + frameThickness / 2, 0f))
        frameRightTop.box(0.08f, frameThickness, doorWidth + frameThickness * 2)

        // Roof ledge
        val ledgeHeight = 0.3f
        val ledgeOverhang = 0.2f
        val ledge = modelBuilder.part("ledge", GL20.GL_TRIANGLES, attributes, roofMat)
        ledge.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, height + ledgeHeight / 2, 0f))
        ledge.box(width + ledgeOverhang * 2, ledgeHeight, depth + ledgeOverhang * 2)

        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Simple building model for LOD (far away buildings) - just a box with roof ledge
     */
    fun createBuildingModelSimple(height: Float, color: Color): Model {
        modelBuilder.begin()

        val width = Constants.BUILDING_WIDTH
        val depth = Constants.BUILDING_DEPTH
        val wallMaterial = Material(ColorAttribute.createDiffuse(color))
        val roofMat = Material(ColorAttribute.createDiffuse(roofColor))

        // Main building body - single box
        val body = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, wallMaterial)
        body.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, height / 2, 0f))
        body.box(width, height, depth)

        // Roof ledge
        val ledgeHeight = 0.3f
        val ledgeOverhang = 0.2f
        val ledge = modelBuilder.part("ledge", GL20.GL_TRIANGLES, attributes, roofMat)
        ledge.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, height + ledgeHeight / 2, 0f))
        ledge.box(width + ledgeOverhang * 2, ledgeHeight, depth + ledgeOverhang * 2)

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

    fun createPedestrianModel(shirtColor: Color = pedestrianColor): Model {
        modelBuilder.begin()
        val scale = 1.4f  // Make pedestrians 40% bigger
        val shirtMaterial = Material(ColorAttribute.createDiffuse(shirtColor))
        val pantsMaterial = Material(ColorAttribute.createDiffuse(Color(0.2f, 0.2f, 0.3f, 1f)))  // Dark pants

        // Legs (pants) - from ground to waist, no overlap with torso
        val legsPart = modelBuilder.part("legs", GL20.GL_TRIANGLES, attributes, pantsMaterial)
        legsPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.35f * scale, 0f))
        legsPart.box(Constants.PEDESTRIAN_WIDTH * 0.8f * scale, 0.7f * scale, 0.2f * scale)

        // Torso (shirt) - starts above legs
        val torsoPart = modelBuilder.part("torso", GL20.GL_TRIANGLES, attributes, shirtMaterial)
        torsoPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.95f * scale, 0f))
        torsoPart.box(Constants.PEDESTRIAN_WIDTH * scale, 0.5f * scale, 0.25f * scale)

        // Head
        val skinMaterial = Material(ColorAttribute.createDiffuse(riderSkinColor))
        val headPart = modelBuilder.part("head", GL20.GL_TRIANGLES, attributes, skinMaterial)
        headPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.4f * scale, 0f))
        headPart.sphere(0.2f * scale, 0.25f * scale, 0.2f * scale, 8, 8)

        return modelBuilder.end().also { models.add(it) }
    }

    // Shirt colors for pedestrians
    private val shirtColors = listOf(
        Color(0.8f, 0.2f, 0.2f, 1f),   // Red
        Color(0.2f, 0.5f, 0.8f, 1f),   // Blue
        Color(0.2f, 0.7f, 0.3f, 1f),   // Green
        Color(0.9f, 0.9f, 0.2f, 1f),   // Yellow
        Color(0.9f, 0.5f, 0.1f, 1f),   // Orange
        Color(0.6f, 0.2f, 0.7f, 1f),   // Purple
        Color(0.9f, 0.9f, 0.9f, 1f),   // White
        Color(0.1f, 0.1f, 0.1f, 1f),   // Black
        Color(0.3f, 0.6f, 0.6f, 1f),   // Teal
        Color(0.8f, 0.4f, 0.6f, 1f),   // Pink
    )

    fun getRandomShirtColor(): Color = shirtColors.random()

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
            // Classic colors
            Color(0.75f, 0.75f, 0.75f, 1f),  // Light gray
            Color(0.55f, 0.55f, 0.55f, 1f),  // Gray
            Color(0.85f, 0.8f, 0.72f, 1f),   // Beige/cream
            Color(0.65f, 0.55f, 0.45f, 1f),  // Brown
            Color(0.75f, 0.65f, 0.55f, 1f),  // Tan
            // Brick-like colors
            Color(0.7f, 0.45f, 0.35f, 1f),   // Red brick
            Color(0.6f, 0.4f, 0.3f, 1f),     // Dark brick
            Color(0.8f, 0.6f, 0.5f, 1f),     // Light brick
            // Modern colors
            Color(0.9f, 0.9f, 0.88f, 1f),    // Off-white
            Color(0.4f, 0.45f, 0.5f, 1f),    // Blue-gray
            Color(0.5f, 0.55f, 0.5f, 1f),    // Green-gray
            Color(0.55f, 0.5f, 0.55f, 1f),   // Purple-gray
            // Warm tones
            Color(0.9f, 0.85f, 0.7f, 1f),    // Light yellow
            Color(0.85f, 0.75f, 0.65f, 1f),  // Peach
        )
        return colors.random()
    }

    fun getRandomCarColor(): Color {
        val colors = listOf(carColor1, carColor2, Color.WHITE, Color.BLACK, Color(0.3f, 0.3f, 0.3f, 1f))
        return colors.random()
    }

    // ============ Environment Models ============

    fun createGrassAreaModel(chunkLength: Float): Model {
        modelBuilder.begin()

        val grassMaterial = Material(ColorAttribute.createDiffuse(grassColor))
        val grassWidth = 80f  // Extended grass area to cover horizon (was 15f)

        // Left grass area (beyond sidewalk) - extends far for background buildings
        val leftGrass = modelBuilder.part("grass_left", GL20.GL_TRIANGLES, attributes, grassMaterial)
        val leftStart = -Constants.ROAD_WIDTH / 2 - Constants.SIDEWALK_WIDTH - grassWidth
        val leftEnd = -Constants.ROAD_WIDTH / 2 - Constants.SIDEWALK_WIDTH
        leftGrass.rect(
            leftStart, 0.02f, 0f,
            leftStart, 0.02f, chunkLength,
            leftEnd, 0.02f, chunkLength,
            leftEnd, 0.02f, 0f,
            0f, 1f, 0f
        )

        // Right grass area (beyond sidewalk) - extends far for background buildings
        val rightGrass = modelBuilder.part("grass_right", GL20.GL_TRIANGLES, attributes, grassMaterial)
        val rightStart = Constants.ROAD_WIDTH / 2 + Constants.SIDEWALK_WIDTH
        val rightEnd = Constants.ROAD_WIDTH / 2 + Constants.SIDEWALK_WIDTH + grassWidth
        rightGrass.rect(
            rightStart, 0.02f, 0f,
            rightStart, 0.02f, chunkLength,
            rightEnd, 0.02f, chunkLength,
            rightEnd, 0.02f, 0f,
            0f, 1f, 0f
        )

        return modelBuilder.end().also { models.add(it) }
    }

    fun createTreeModel(height: Float = 14f): Model {
        modelBuilder.begin()

        val trunkMaterial = Material(ColorAttribute.createDiffuse(treeTrunkColor))
        val leavesMaterial = Material(ColorAttribute.createDiffuse(treeLeavesColor))

        val trunkHeight = height * 0.4f
        val trunkRadius = 0.4f
        val crownHeight = height * 0.7f
        val crownRadius = height * 0.35f

        // Trunk (cylinder)
        val trunkPart = modelBuilder.part("trunk", GL20.GL_TRIANGLES, attributes, trunkMaterial)
        trunkPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, trunkHeight / 2, 0f))
        trunkPart.cylinder(trunkRadius * 2, trunkHeight, trunkRadius * 2, 8)

        // Crown (cone/sphere hybrid - using cone for simplicity)
        val crownPart = modelBuilder.part("crown", GL20.GL_TRIANGLES, attributes, leavesMaterial)
        crownPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, trunkHeight + crownHeight / 2, 0f))
        crownPart.cone(crownRadius * 2, crownHeight, crownRadius * 2, 8)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createRoundTreeModel(height: Float = 12f): Model {
        modelBuilder.begin()

        val trunkMaterial = Material(ColorAttribute.createDiffuse(treeTrunkColor))
        val leavesMaterial = Material(ColorAttribute.createDiffuse(treeLeavesColor))

        val trunkHeight = height * 0.35f
        val trunkRadius = 0.3f
        val crownRadius = height * 0.3f

        // Trunk
        val trunkPart = modelBuilder.part("trunk", GL20.GL_TRIANGLES, attributes, trunkMaterial)
        trunkPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, trunkHeight / 2, 0f))
        trunkPart.cylinder(trunkRadius * 2, trunkHeight, trunkRadius * 2, 8)

        // Round crown (sphere)
        val crownPart = modelBuilder.part("crown", GL20.GL_TRIANGLES, attributes, leavesMaterial)
        crownPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, trunkHeight + crownRadius, 0f))
        crownPart.sphere(crownRadius * 2, crownRadius * 2, crownRadius * 2, 10, 10)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createLampPostModel(): Model {
        modelBuilder.begin()

        val postMaterial = Material(ColorAttribute.createDiffuse(lampPostColor))

        val scale = 1.6f  // 60% bigger
        val postHeight = 3.5f * scale
        val postRadius = 0.07f * scale

        // Main post
        val postPart = modelBuilder.part("post", GL20.GL_TRIANGLES, attributes, postMaterial)
        postPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, postHeight / 2, 0f))
        postPart.cylinder(postRadius * 2, postHeight, postRadius * 2, 8)

        // Arm extending towards road (along Z axis, not X - so it points towards road when rotated)
        val armPart = modelBuilder.part("arm", GL20.GL_TRIANGLES, attributes, postMaterial)
        armPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, postHeight - 0.1f * scale, 0.4f * scale))
        armPart.box(0.08f * scale, 0.08f * scale, 0.8f * scale)

        // Lamp head (box) - at end of arm
        val lampPart = modelBuilder.part("lamp", GL20.GL_TRIANGLES, attributes, postMaterial)
        lampPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, postHeight - 0.3f * scale, 0.7f * scale))
        lampPart.box(0.2f * scale, 0.25f * scale, 0.3f * scale)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createBenchModel(): Model {
        modelBuilder.begin()

        val scale = 1.6f  // 60% bigger
        val woodMaterial = Material(ColorAttribute.createDiffuse(benchWoodColor))
        val metalMaterial = Material(ColorAttribute.createDiffuse(benchMetalColor))

        // Seat
        val seatPart = modelBuilder.part("seat", GL20.GL_TRIANGLES, attributes, woodMaterial)
        seatPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.45f * scale, 0f))
        seatPart.box(1.2f * scale, 0.08f * scale, 0.4f * scale)

        // Backrest
        val backPart = modelBuilder.part("back", GL20.GL_TRIANGLES, attributes, woodMaterial)
        backPart.setVertexTransform(com.badlogic.gdx.math.Matrix4()
            .translate(0f, 0.7f * scale, -0.15f * scale)
            .rotate(1f, 0f, 0f, -15f))
        backPart.box(1.2f * scale, 0.4f * scale, 0.06f * scale)

        // Left leg
        val leftLegPart = modelBuilder.part("leg_left", GL20.GL_TRIANGLES, attributes, metalMaterial)
        leftLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-0.5f * scale, 0.22f * scale, 0f))
        leftLegPart.box(0.06f * scale, 0.44f * scale, 0.35f * scale)

        // Right leg
        val rightLegPart = modelBuilder.part("leg_right", GL20.GL_TRIANGLES, attributes, metalMaterial)
        rightLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0.5f * scale, 0.22f * scale, 0f))
        rightLegPart.box(0.06f * scale, 0.44f * scale, 0.35f * scale)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createTrashCanModel(): Model {
        modelBuilder.begin()

        val scale = 1.6f  // 60% bigger
        val canMaterial = Material(ColorAttribute.createDiffuse(trashCanColor))

        // Main body (cylinder)
        val bodyPart = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, canMaterial)
        bodyPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.4f * scale, 0f))
        bodyPart.cylinder(0.4f * scale, 0.8f * scale, 0.4f * scale, 10)

        // Rim at top
        val rimPart = modelBuilder.part("rim", GL20.GL_TRIANGLES, attributes, canMaterial)
        rimPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.82f * scale, 0f))
        rimPart.cylinder(0.45f * scale, 0.06f * scale, 0.45f * scale, 10)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createBushModel(): Model {
        modelBuilder.begin()

        val bushMaterial = Material(ColorAttribute.createDiffuse(bushColor))

        // Main bush body (flattened sphere)
        val bushPart = modelBuilder.part("bush", GL20.GL_TRIANGLES, attributes, bushMaterial)
        bushPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.35f, 0f))
        bushPart.sphere(0.8f, 0.7f, 0.8f, 8, 8)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createFlowerBedModel(): Model {
        modelBuilder.begin()

        val dirtMaterial = Material(ColorAttribute.createDiffuse(dirtColor))

        // Dirt bed
        val bedPart = modelBuilder.part("bed", GL20.GL_TRIANGLES, attributes, dirtMaterial)
        bedPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.05f, 0f))
        bedPart.box(1.5f, 0.1f, 0.8f)

        // Some simple flowers (small colored spheres)
        val flowerColors = listOf(
            Color(0.9f, 0.3f, 0.3f, 1f),  // Red
            Color(0.9f, 0.9f, 0.3f, 1f),  // Yellow
            Color(0.9f, 0.5f, 0.8f, 1f),  // Pink
        )
        var flowerIndex = 0
        for (x in listOf(-0.4f, 0f, 0.4f)) {
            for (z in listOf(-0.2f, 0.2f)) {
                val flowerMaterial = Material(ColorAttribute.createDiffuse(flowerColors[flowerIndex % flowerColors.size]))
                val flowerPart = modelBuilder.part("flower_$flowerIndex", GL20.GL_TRIANGLES, attributes, flowerMaterial)
                flowerPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(x, 0.2f, z))
                flowerPart.sphere(0.12f, 0.12f, 0.12f, 6, 6)
                flowerIndex++
            }
        }

        return modelBuilder.end().also { models.add(it) }
    }

    fun createZebraCrossingModel(): Model {
        modelBuilder.begin()

        val lineMaterial = Material(ColorAttribute.createDiffuse(roadLineColor))

        // Zebra crossing - white stripes perpendicular to road (across the road width)
        val zebraSpacing = 0.5f
        val zebraWidth = 0.4f
        val zebraLength = 4f  // Length along Z axis (crossing width)

        var x = -Constants.ROAD_WIDTH / 2 + 0.5f  // Start from left edge of road
        var stripeIndex = 0
        while (x < Constants.ROAD_WIDTH / 2 - 0.3f) {
            val stripe = modelBuilder.part("zebra_$stripeIndex", GL20.GL_TRIANGLES, attributes, lineMaterial)
            stripe.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(x, 0.012f, 0f))
            stripe.box(zebraWidth, 0.01f, zebraLength)  // Rotated: width along X, length along Z

            x += zebraSpacing + zebraWidth
            stripeIndex++
        }

        return modelBuilder.end().also { models.add(it) }
    }

    fun createCloudModel(scaleX: Float = 1f, scaleZ: Float = 1f): Model {
        modelBuilder.begin()

        val cloudMaterial = Material(ColorAttribute.createDiffuse(cloudColor))
        val baseScale = 3f  // Make clouds 3x bigger

        // Cloud made of several overlapping spheres for fluffy look
        val positions = listOf(
            Triple(0f, 0f, 0f),           // Center
            Triple(-3f * scaleX, -0.5f, 0f),    // Left
            Triple(3f * scaleX, -0.3f, 0f),     // Right
            Triple(-1.5f * scaleX, 0.5f, 1f * scaleZ),   // Front-left up
            Triple(1.5f * scaleX, 0.3f, -1f * scaleZ),   // Back-right
            Triple(0f, -0.7f, 1.5f * scaleZ),   // Front-bottom
        )

        positions.forEachIndexed { i, (x, y, z) ->
            val size = when (i) {
                0 -> 4f  // Center is biggest
                1, 2 -> 3f  // Sides
                else -> 2.5f  // Others
            }
            val cloudPart = modelBuilder.part("cloud_$i", GL20.GL_TRIANGLES, attributes, cloudMaterial)
            cloudPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(x * baseScale, y * baseScale, z * baseScale))
            cloudPart.sphere(size * scaleX * baseScale, size * 0.6f * baseScale, size * scaleZ * baseScale, 8, 8)
        }

        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Create a tower crane model for construction sites
     * @param height Total height of the crane tower
     */
    fun createTowerCraneModel(height: Float = 45f): Model {
        modelBuilder.begin()

        val yellowMaterial = Material(ColorAttribute.createDiffuse(craneYellowColor))
        val metalMaterial = Material(ColorAttribute.createDiffuse(craneMetalColor))

        // Tower dimensions
        val towerWidth = 2.5f
        val latticeThickness = 0.3f

        // Main tower (vertical lattice structure)
        // Four corner posts
        val cornerOffset = towerWidth / 2 - latticeThickness / 2
        val corners = listOf(
            Pair(-cornerOffset, -cornerOffset),
            Pair(cornerOffset, -cornerOffset),
            Pair(cornerOffset, cornerOffset),
            Pair(-cornerOffset, cornerOffset)
        )

        corners.forEachIndexed { i, (x, z) ->
            val postPart = modelBuilder.part("tower_post_$i", GL20.GL_TRIANGLES, attributes, yellowMaterial)
            postPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(x, height / 2, z))
            postPart.box(latticeThickness, height, latticeThickness)
        }

        // Horizontal braces on tower (every 8 units)
        var braceY = 4f
        var braceIndex = 0
        while (braceY < height - 2f) {
            // X-direction braces
            val braceX1 = modelBuilder.part("brace_x1_$braceIndex", GL20.GL_TRIANGLES, attributes, yellowMaterial)
            braceX1.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, braceY, -cornerOffset))
            braceX1.box(towerWidth, latticeThickness * 0.8f, latticeThickness * 0.8f)

            val braceX2 = modelBuilder.part("brace_x2_$braceIndex", GL20.GL_TRIANGLES, attributes, yellowMaterial)
            braceX2.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, braceY, cornerOffset))
            braceX2.box(towerWidth, latticeThickness * 0.8f, latticeThickness * 0.8f)

            // Z-direction braces
            val braceZ1 = modelBuilder.part("brace_z1_$braceIndex", GL20.GL_TRIANGLES, attributes, yellowMaterial)
            braceZ1.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-cornerOffset, braceY, 0f))
            braceZ1.box(latticeThickness * 0.8f, latticeThickness * 0.8f, towerWidth)

            val braceZ2 = modelBuilder.part("brace_z2_$braceIndex", GL20.GL_TRIANGLES, attributes, yellowMaterial)
            braceZ2.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(cornerOffset, braceY, 0f))
            braceZ2.box(latticeThickness * 0.8f, latticeThickness * 0.8f, towerWidth)

            braceY += 8f
            braceIndex++
        }

        // Slewing unit (rotating platform at top of tower)
        val slewingY = height + 0.5f
        val slewingPart = modelBuilder.part("slewing", GL20.GL_TRIANGLES, attributes, metalMaterial)
        slewingPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, slewingY, 0f))
        slewingPart.box(3.5f, 1.5f, 3.5f)

        // Operator cabin
        val cabinPart = modelBuilder.part("cabin", GL20.GL_TRIANGLES, attributes, yellowMaterial)
        cabinPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(1.8f, slewingY + 1.5f, 0f))
        cabinPart.box(2f, 2.5f, 2f)

        // Main jib (horizontal arm extending forward) - the long working arm
        val jibLength = 35f
        val jibHeight = 2f
        val jibY = slewingY + 2f

        // Jib main beam
        val jibPart = modelBuilder.part("jib", GL20.GL_TRIANGLES, attributes, yellowMaterial)
        jibPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(jibLength / 2 - 2f, jibY, 0f))
        jibPart.box(jibLength, latticeThickness, latticeThickness * 1.5f)

        // Jib top chord
        val jibTopPart = modelBuilder.part("jib_top", GL20.GL_TRIANGLES, attributes, yellowMaterial)
        jibTopPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(jibLength / 2 - 2f, jibY + jibHeight, 0f))
        jibTopPart.box(jibLength, latticeThickness * 0.7f, latticeThickness)

        // Jib vertical supports
        for (i in 0..6) {
            val supportX = -2f + i * 5.5f
            if (supportX < jibLength - 4f) {
                val supportPart = modelBuilder.part("jib_support_$i", GL20.GL_TRIANGLES, attributes, yellowMaterial)
                supportPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(supportX, jibY + jibHeight / 2, 0f))
                supportPart.box(latticeThickness * 0.6f, jibHeight, latticeThickness * 0.6f)
            }
        }

        // Counter-jib (rear arm with counterweight)
        val counterJibLength = 12f
        val counterJibPart = modelBuilder.part("counter_jib", GL20.GL_TRIANGLES, attributes, yellowMaterial)
        counterJibPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-counterJibLength / 2 - 1f, jibY, 0f))
        counterJibPart.box(counterJibLength, latticeThickness, latticeThickness * 1.5f)

        // Counterweight (concrete blocks at end of counter-jib)
        val counterweightPart = modelBuilder.part("counterweight", GL20.GL_TRIANGLES, attributes, metalMaterial)
        counterweightPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-counterJibLength - 1f, jibY - 1f, 0f))
        counterweightPart.box(4f, 3f, 2.5f)

        // A-frame (support structure above slewing)
        val aFrameHeight = 6f
        val aFrameTop = slewingY + aFrameHeight

        // A-frame legs
        val aFrameLeg1 = modelBuilder.part("aframe_leg1", GL20.GL_TRIANGLES, attributes, yellowMaterial)
        aFrameLeg1.setVertexTransform(com.badlogic.gdx.math.Matrix4()
            .translate(-1f, slewingY + aFrameHeight / 2, 0f)
            .rotate(0f, 0f, 1f, 10f))
        aFrameLeg1.box(latticeThickness, aFrameHeight, latticeThickness)

        val aFrameLeg2 = modelBuilder.part("aframe_leg2", GL20.GL_TRIANGLES, attributes, yellowMaterial)
        aFrameLeg2.setVertexTransform(com.badlogic.gdx.math.Matrix4()
            .translate(1f, slewingY + aFrameHeight / 2, 0f)
            .rotate(0f, 0f, 1f, -10f))
        aFrameLeg2.box(latticeThickness, aFrameHeight, latticeThickness)

        // A-frame top
        val aFrameTopPart = modelBuilder.part("aframe_top", GL20.GL_TRIANGLES, attributes, yellowMaterial)
        aFrameTopPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, aFrameTop, 0f))
        aFrameTopPart.box(1f, latticeThickness, latticeThickness)

        // Pendant lines (cables from A-frame to jib tip) - simplified as thin boxes
        val pendantPart = modelBuilder.part("pendant", GL20.GL_TRIANGLES, attributes, metalMaterial)
        pendantPart.setVertexTransform(com.badlogic.gdx.math.Matrix4()
            .translate(jibLength / 2 - 5f, jibY + jibHeight + 2f, 0f)
            .rotate(0f, 0f, 1f, -8f))
        pendantPart.box(0.1f, jibLength * 0.4f, 0.1f)

        // Hook block hanging from jib (at roughly 2/3 of jib length)
        val hookX = jibLength * 0.5f
        val hookY = jibY - 8f

        // Trolley on jib
        val trolleyPart = modelBuilder.part("trolley", GL20.GL_TRIANGLES, attributes, metalMaterial)
        trolleyPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(hookX, jibY - 0.3f, 0f))
        trolleyPart.box(1.5f, 0.8f, 1.2f)

        // Hook cable
        val cablePart = modelBuilder.part("cable", GL20.GL_TRIANGLES, attributes, metalMaterial)
        cablePart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(hookX, (jibY + hookY) / 2, 0f))
        cablePart.box(0.08f, jibY - hookY, 0.08f)

        // Hook block
        val hookBlockPart = modelBuilder.part("hook_block", GL20.GL_TRIANGLES, attributes, metalMaterial)
        hookBlockPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(hookX, hookY, 0f))
        hookBlockPart.box(0.8f, 1.5f, 0.8f)

        // Hook
        val hookPart = modelBuilder.part("hook", GL20.GL_TRIANGLES, attributes, yellowMaterial)
        hookPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(hookX, hookY - 1.2f, 0f))
        hookPart.box(0.4f, 0.8f, 0.15f)

        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Create an airplane model for high-altitude flight
     * Simplified jet airliner shape visible from far below
     */
    fun createAirplaneModel(): Model {
        modelBuilder.begin()

        val bodyMaterial = Material(ColorAttribute.createDiffuse(airplaneBodyColor))
        val tailMaterial = Material(ColorAttribute.createDiffuse(airplaneTailColor))

        // Scale for visibility at high altitude (plane appears small but recognizable)
        val scale = 1.5f

        // Fuselage (main body) - elongated cylinder approximated with box
        val fuselagePart = modelBuilder.part("fuselage", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        fuselagePart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0f, 0f))
        fuselagePart.box(1.2f * scale, 1f * scale, 8f * scale)

        // Nose cone
        val nosePart = modelBuilder.part("nose", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        nosePart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0f, 4.5f * scale))
        nosePart.cone(0.8f * scale, 2f * scale, 0.8f * scale, 8)

        // Main wings
        val leftWingPart = modelBuilder.part("left_wing", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        leftWingPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-3f * scale, 0f, -0.5f * scale))
        leftWingPart.box(5f * scale, 0.15f * scale, 2f * scale)

        val rightWingPart = modelBuilder.part("right_wing", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        rightWingPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(3f * scale, 0f, -0.5f * scale))
        rightWingPart.box(5f * scale, 0.15f * scale, 2f * scale)

        // Tail fin (vertical stabilizer)
        val tailFinPart = modelBuilder.part("tail_fin", GL20.GL_TRIANGLES, attributes, tailMaterial)
        tailFinPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.2f * scale, -3.5f * scale))
        tailFinPart.box(0.15f * scale, 2f * scale, 1.5f * scale)

        // Horizontal stabilizers
        val leftStabPart = modelBuilder.part("left_stab", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        leftStabPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-1.2f * scale, 0.3f * scale, -3.5f * scale))
        leftStabPart.box(2f * scale, 0.1f * scale, 1f * scale)

        val rightStabPart = modelBuilder.part("right_stab", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        rightStabPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(1.2f * scale, 0.3f * scale, -3.5f * scale))
        rightStabPart.box(2f * scale, 0.1f * scale, 1f * scale)

        // Engines (under wings)
        val leftEnginePart = modelBuilder.part("left_engine", GL20.GL_TRIANGLES, attributes, tailMaterial)
        leftEnginePart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-2f * scale, -0.4f * scale, 0f))
        leftEnginePart.cylinder(0.4f * scale, 1.2f * scale, 0.4f * scale, 8)

        val rightEnginePart = modelBuilder.part("right_engine", GL20.GL_TRIANGLES, attributes, tailMaterial)
        rightEnginePart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(2f * scale, -0.4f * scale, 0f))
        rightEnginePart.cylinder(0.4f * scale, 1.2f * scale, 0.4f * scale, 8)

        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Create a contrail (condensation trail) segment
     * @param alpha Opacity of the contrail (1.0 = fresh, 0.0 = faded)
     */
    fun createContrailSegmentModel(alpha: Float = 0.6f): Model {
        modelBuilder.begin()

        val trailColor = Color(contrailColor.r, contrailColor.g, contrailColor.b, alpha)
        val trailMaterial = Material(ColorAttribute.createDiffuse(trailColor))

        // Single contrail segment - elongated thin cloud
        val segmentPart = modelBuilder.part("contrail", GL20.GL_TRIANGLES, attributes, trailMaterial)
        segmentPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0f, 0f))
        segmentPart.box(0.8f, 0.5f, 3f)

        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Create a pigeon model
     * Small bird that walks on sidewalks and flies away when player approaches
     * @param isFlying If true, wings are spread for flying pose
     */
    fun createPigeonModel(isFlying: Boolean = false): Model {
        modelBuilder.begin()

        val bodyMaterial = Material(ColorAttribute.createDiffuse(pigeonBodyColor))
        val headMaterial = Material(ColorAttribute.createDiffuse(pigeonHeadColor))
        val wingMaterial = Material(ColorAttribute.createDiffuse(pigeonWingColor))
        val beakMaterial = Material(ColorAttribute.createDiffuse(pigeonBeakColor))

        // Pigeon size - visible but not too large
        val scale = 0.35f

        // Body (oval shape)
        val bodyPart = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        bodyPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.8f * scale, 0f))
        bodyPart.sphere(1.2f * scale, 0.9f * scale, 1.8f * scale, 8, 8)

        // Head (smaller sphere)
        val headPart = modelBuilder.part("head", GL20.GL_TRIANGLES, attributes, headMaterial)
        headPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.3f * scale, 0.9f * scale))
        headPart.sphere(0.6f * scale, 0.55f * scale, 0.6f * scale, 6, 6)

        // Beak
        val beakPart = modelBuilder.part("beak", GL20.GL_TRIANGLES, attributes, beakMaterial)
        beakPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 1.2f * scale, 1.3f * scale))
        beakPart.box(0.15f * scale, 0.1f * scale, 0.3f * scale)

        // Tail feathers
        val tailPart = modelBuilder.part("tail", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        tailPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.7f * scale, -0.9f * scale))
        tailPart.box(0.4f * scale, 0.1f * scale, 0.6f * scale)

        if (isFlying) {
            // Wings spread out for flying
            val leftWingPart = modelBuilder.part("left_wing", GL20.GL_TRIANGLES, attributes, wingMaterial)
            leftWingPart.setVertexTransform(com.badlogic.gdx.math.Matrix4()
                .translate(-1.0f * scale, 1.0f * scale, 0f)
                .rotate(0f, 0f, 1f, -30f))
            leftWingPart.box(1.4f * scale, 0.08f * scale, 0.8f * scale)

            val rightWingPart = modelBuilder.part("right_wing", GL20.GL_TRIANGLES, attributes, wingMaterial)
            rightWingPart.setVertexTransform(com.badlogic.gdx.math.Matrix4()
                .translate(1.0f * scale, 1.0f * scale, 0f)
                .rotate(0f, 0f, 1f, 30f))
            rightWingPart.box(1.4f * scale, 0.08f * scale, 0.8f * scale)
        } else {
            // Wings folded at sides for walking
            val leftWingPart = modelBuilder.part("left_wing", GL20.GL_TRIANGLES, attributes, wingMaterial)
            leftWingPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-0.5f * scale, 0.8f * scale, -0.1f * scale))
            leftWingPart.box(0.3f * scale, 0.5f * scale, 1.0f * scale)

            val rightWingPart = modelBuilder.part("right_wing", GL20.GL_TRIANGLES, attributes, wingMaterial)
            rightWingPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0.5f * scale, 0.8f * scale, -0.1f * scale))
            rightWingPart.box(0.3f * scale, 0.5f * scale, 1.0f * scale)
        }

        // Legs (simple sticks)
        val legMaterial = Material(ColorAttribute.createDiffuse(pigeonBeakColor))

        val leftLegPart = modelBuilder.part("left_leg", GL20.GL_TRIANGLES, attributes, legMaterial)
        leftLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-0.2f * scale, 0.2f * scale, 0.1f * scale))
        leftLegPart.box(0.06f * scale, 0.4f * scale, 0.06f * scale)

        val rightLegPart = modelBuilder.part("right_leg", GL20.GL_TRIANGLES, attributes, legMaterial)
        rightLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0.2f * scale, 0.2f * scale, 0.1f * scale))
        rightLegPart.box(0.06f * scale, 0.4f * scale, 0.06f * scale)

        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Create a simple background building silhouette for distant horizon
     * These are simple boxes with faded colors to simulate depth/fog
     * @param fogLevel 0 = closest (less fog), 1 = mid, 2 = far (heavy fog)
     */
    fun createBackgroundBuildingModel(height: Float, fogLevel: Int = 0): Model {
        modelBuilder.begin()

        // Pick color based on fog level - further = more faded
        val color = when (fogLevel) {
            0 -> listOf(bgBuildingColor1, bgBuildingColor2, bgBuildingColor3).random()
            1 -> listOf(bgBuildingMidColor1, bgBuildingMidColor2).random()
            else -> listOf(bgBuildingFarColor1, bgBuildingFarColor2).random()
        }
        val material = Material(ColorAttribute.createDiffuse(color))

        // Wide, simple box - no details
        val width = MathUtils.random(8f, 15f)
        val depth = MathUtils.random(8f, 12f)

        val body = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, material)
        body.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, height / 2, 0f))
        body.box(width, height, depth)

        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Create a fog wall - large plane that blends with sky to hide world edges
     */
    fun createFogWallModel(width: Float, height: Float): Model {
        modelBuilder.begin()

        val material = Material(ColorAttribute.createDiffuse(fogWallColor))

        // Vertical plane facing the camera
        val wall = modelBuilder.part("fog_wall", GL20.GL_TRIANGLES, attributes, material)
        wall.rect(
            -width / 2, 0f, 0f,
            width / 2, 0f, 0f,
            width / 2, height, 0f,
            -width / 2, height, 0f,
            0f, 0f, -1f  // Normal facing back towards camera
        )

        return modelBuilder.end().also { models.add(it) }
    }

    fun getRandomBackgroundBuildingColor(): Color {
        return listOf(bgBuildingColor1, bgBuildingColor2, bgBuildingColor3).random()
    }

    override fun dispose() {
        models.forEach { it.dispose() }
        models.clear()
    }
}
