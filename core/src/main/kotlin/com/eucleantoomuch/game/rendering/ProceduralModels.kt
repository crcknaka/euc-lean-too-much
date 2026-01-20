@file:Suppress("DEPRECATION")

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
    private val sidewalkColor = Color(0.5f, 0.5f, 0.5f, 1f)      // Medium gray
    private val roadLineColor = Color(1f, 1f, 1f, 1f)            // White
    private val manholeColor = Color(0.15f, 0.15f, 0.15f, 1f)    // Very dark
    private val puddleColor = Color(0.3f, 0.4f, 0.6f, 0.7f)      // Blue-ish
    private val curbColor = Color(0.65f, 0.63f, 0.60f, 1f)       // Light beige-gray (distinct from sidewalk)
    private val potholeColor = Color(0.1f, 0.1f, 0.1f, 1f)       // Black
    private val pedestrianColor = Color(0.8f, 0.4f, 0.3f, 1f)    // Reddish
    private val carColor1 = Color(0.8f, 0.2f, 0.2f, 1f)          // Red
    private val carColor2 = Color(0.2f, 0.4f, 0.8f, 1f)          // Blue

    // Environment colors
    private val grassColor = Color(0.35f, 0.55f, 0.25f, 1f)      // Green grass
    private val dirtColor = Color(0.4f, 0.3f, 0.2f, 1f)          // Brown dirt
    private val treeTrunkColor = Color(0.4f, 0.28f, 0.18f, 1f)   // Brown trunk
    private val treeLeavesColor = Color(0.2f, 0.45f, 0.2f, 1f)   // Green leaves
    private val birchTrunkColor = Color(0.9f, 0.88f, 0.85f, 1f)  // White birch trunk
    private val birchLeavesColor = Color(0.35f, 0.55f, 0.25f, 1f) // Lighter green for birch
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

    // Shadow color (semi-transparent dark)
    private val shadowColor = Color(0f, 0f, 0f, 0.15f)

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

    // Rider scale constant for consistency
    val riderScale = 1.4f

    fun createRiderModel(): Model {
        modelBuilder.begin()

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

        // Head is now a separate model for animation
        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Create the head model (head + helmet) as a separate animatable part.
     * Pivot is at the neck base (origin at 0,0,0).
     */
    fun createHeadModel(): Model {
        modelBuilder.begin()

        // Head position relative to neck (pivot point)
        val headOffsetY = 0.10f * riderScale  // Head center above neck

        // Head (face area visible below helmet)
        val skinMaterial = Material(ColorAttribute.createDiffuse(riderSkinColor))
        val headPart = modelBuilder.part("head", GL20.GL_TRIANGLES, attributes, skinMaterial)
        headPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, headOffsetY, 0.02f * riderScale))
        headPart.sphere(0.18f * riderScale, 0.22f * riderScale, 0.18f * riderScale, 8, 8)

        // Helmet shell (covers top and back of head)
        val helmetMaterial = Material(ColorAttribute.createDiffuse(helmetColor))
        val helmetPart = modelBuilder.part("helmet", GL20.GL_TRIANGLES, attributes, helmetMaterial)
        helmetPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, headOffsetY + 0.06f * riderScale, -0.02f * riderScale))
        helmetPart.sphere(0.26f * riderScale, 0.24f * riderScale, 0.26f * riderScale, 10, 10)

        // Helmet visor/brim (front protective part)
        val visorMaterial = Material(ColorAttribute.createDiffuse(helmetVisorColor))
        val visorPart = modelBuilder.part("visor", GL20.GL_TRIANGLES, attributes, visorMaterial)
        visorPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, headOffsetY + 0.04f * riderScale, 0.12f * riderScale))
        visorPart.box(0.22f * riderScale, 0.06f * riderScale, 0.08f * riderScale)

        // Helmet vents (top detail)
        val ventPart = modelBuilder.part("vent", GL20.GL_TRIANGLES, attributes, visorMaterial)
        ventPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, headOffsetY + 0.18f * riderScale, 0f))
        ventPart.box(0.08f * riderScale, 0.03f * riderScale, 0.16f * riderScale)

        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Create an arm model (upper arm + forearm + hand).
     * The arm is created with pivot at the shoulder (origin at 0,0,0).
     * @param isLeft true for left arm, false for right arm (currently both arms use same model)
     */
    @Suppress("UNUSED_PARAMETER")
    fun createArmModel(isLeft: Boolean): Model {
        modelBuilder.begin()

        val bodyMaterial = Material(ColorAttribute.createDiffuse(riderBodyColor))
        val skinMaterial = Material(ColorAttribute.createDiffuse(riderSkinColor))

        // Arm dimensions
        val upperArmLength = 0.35f * riderScale
        val upperArmThickness = 0.09f * riderScale
        val forearmLength = 0.3f * riderScale
        val forearmThickness = 0.07f * riderScale
        val handSize = 0.08f * riderScale

        // Upper arm (from shoulder down)
        // Positioned so top of upper arm is at origin (shoulder attachment point)
        val upperArmPart = modelBuilder.part("upper_arm", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        upperArmPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, -upperArmLength / 2, 0f))
        upperArmPart.box(upperArmThickness, upperArmLength, upperArmThickness)

        // Forearm (continues from upper arm)
        val forearmPart = modelBuilder.part("forearm", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        forearmPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, -upperArmLength - forearmLength / 2, 0f))
        forearmPart.box(forearmThickness, forearmLength, forearmThickness)

        // Hand (at end of forearm)
        val handPart = modelBuilder.part("hand", GL20.GL_TRIANGLES, attributes, skinMaterial)
        handPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, -upperArmLength - forearmLength - handSize / 2, 0f))
        handPart.box(handSize, handSize * 0.6f, handSize * 0.4f)

        return modelBuilder.end().also { models.add(it) }
    }

    fun createGroundChunkModel(chunkLength: Float): Model {
        modelBuilder.begin()

        val roadMaterial = Material(ColorAttribute.createDiffuse(roadColor))
        val sidewalkMaterial = Material(ColorAttribute.createDiffuse(sidewalkColor))
        val lineMaterial = Material(ColorAttribute.createDiffuse(roadLineColor))
        val grassMaterial = Material(ColorAttribute.createDiffuse(grassColor))
        // Darker grass for variation
        val grassDarkMaterial = Material(ColorAttribute.createDiffuse(Color(0.28f, 0.45f, 0.2f, 1f)))

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

        // Grass areas (between sidewalk and buildings)
        val sidewalkEdge = Constants.ROAD_WIDTH / 2 + Constants.SIDEWALK_WIDTH  // 7f
        val grassWidth = Constants.BUILDING_OFFSET_X - sidewalkEdge + 5f  // Extend past buildings

        // Left grass - main area
        val leftGrass = modelBuilder.part("grass_left", GL20.GL_TRIANGLES, attributes, grassMaterial)
        leftGrass.rect(
            -sidewalkEdge - grassWidth, 0f, 0f,
            -sidewalkEdge - grassWidth, 0f, chunkLength,
            -sidewalkEdge, 0f, chunkLength,
            -sidewalkEdge, 0f, 0f,
            0f, 1f, 0f
        )

        // Right grass - main area
        val rightGrass = modelBuilder.part("grass_right", GL20.GL_TRIANGLES, attributes, grassMaterial)
        rightGrass.rect(
            sidewalkEdge, 0f, 0f,
            sidewalkEdge, 0f, chunkLength,
            sidewalkEdge + grassWidth, 0f, chunkLength,
            sidewalkEdge + grassWidth, 0f, 0f,
            0f, 1f, 0f
        )

        // Add grass variation patches (darker spots)
        val patchSize = 3f
        var patchZ = 2f
        var patchIndex = 0
        while (patchZ < chunkLength - patchSize) {
            // Left side patches - randomish pattern
            if ((patchIndex + 1) % 3 == 0) {
                val leftPatch = modelBuilder.part("grass_patch_l_$patchIndex", GL20.GL_TRIANGLES, attributes, grassDarkMaterial)
                val patchX = -sidewalkEdge - 2f - (patchIndex % 5) * 1.5f
                leftPatch.rect(
                    patchX - patchSize / 2, 0.005f, patchZ,
                    patchX - patchSize / 2, 0.005f, patchZ + patchSize,
                    patchX + patchSize / 2, 0.005f, patchZ + patchSize,
                    patchX + patchSize / 2, 0.005f, patchZ,
                    0f, 1f, 0f
                )
            }
            // Right side patches
            if ((patchIndex + 2) % 4 == 0) {
                val rightPatch = modelBuilder.part("grass_patch_r_$patchIndex", GL20.GL_TRIANGLES, attributes, grassDarkMaterial)
                val patchX = sidewalkEdge + 3f + (patchIndex % 4) * 1.2f
                rightPatch.rect(
                    patchX - patchSize / 2, 0.005f, patchZ,
                    patchX - patchSize / 2, 0.005f, patchZ + patchSize,
                    patchX + patchSize / 2, 0.005f, patchZ + patchSize,
                    patchX + patchSize / 2, 0.005f, patchZ,
                    0f, 1f, 0f
                )
            }
            patchZ += 5f
            patchIndex++
        }

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

        // Collect all window positions - only on inner side facing road (positive X side)
        // Buildings on left side of road have road on their +X side
        // Buildings on right side of road have road on their -X side
        // Since we create one model and flip it, we only need windows on ONE side (+X)
        for (floor in 0 until numFloors) {
            val windowY = floor * floorHeight + floorHeight * 0.6f
            val isGroundFloor = floor == 0

            for (w in 0 until windowsPerFloor) {
                val windowZ = -depth / 2 + 1f + w * windowSpacingZ + windowSpacingZ / 2
                val isNearDoor = kotlin.math.abs(windowZ) < (doorWidth / 2 + windowWidth / 2 + 0.2f)
                if (isGroundFloor && isNearDoor) continue

                val isLit = Math.random() < 0.3
                val list = if (isLit) litWindows else darkWindows

                // Only inner side window (facing road) - positive X side
                // The model will be mirrored for the other side of the road
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

        // Only inner side door (facing road) - positive X side
        val door = modelBuilder.part("door", GL20.GL_TRIANGLES, attributes, doorMat)
        door.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(width / 2 + 0.03f, doorHeight / 2, 0f))
        door.box(0.06f, doorHeight, doorWidth)

        // Door frame - only on inner side
        val frameThickness = 0.15f
        val frameTop = modelBuilder.part("frame_top", GL20.GL_TRIANGLES, attributes, trimMat)
        frameTop.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(width / 2 + 0.04f, doorHeight + frameThickness / 2, 0f))
        frameTop.box(0.08f, frameThickness, doorWidth + frameThickness * 2)

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
        return createPuddleModel(Constants.PUDDLE_WIDTH, Constants.PUDDLE_LENGTH)
    }

    /**
     * Create puddle model with custom size.
     * @param width Width of the puddle (X axis)
     * @param length Length of the puddle (Z axis)
     */
    fun createPuddleModel(width: Float, length: Float): Model {
        modelBuilder.begin()
        val material = Material(ColorAttribute.createDiffuse(puddleColor))
        val part = modelBuilder.part("puddle", GL20.GL_TRIANGLES, attributes, material)
        part.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.005f, 0f))
        part.ellipse(width, length, 0f, 0f, 16,
            0f, 0f, 0f, 0f, 1f, 0f)
        return modelBuilder.end().also { models.add(it) }
    }

    fun createCurbModel(length: Float = 1f): Model {
        modelBuilder.begin()
        val material = Material(ColorAttribute.createDiffuse(curbColor))
        val part = modelBuilder.part("curb", GL20.GL_TRIANGLES, attributes, material)
        part.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, Constants.CURB_HEIGHT / 2, 0f))
        part.box(0.3f, Constants.CURB_HEIGHT, length)
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

    /**
     * Create a detailed articulated pedestrian model with separate body parts.
     * Structure matches the ragdoll physics bodies for seamless transition.
     * Body parts are named: head, torso, leftUpperArm, leftLowerArm, rightUpperArm, rightLowerArm,
     * leftUpperLeg, leftLowerLeg, rightUpperLeg, rightLowerLeg
     */
    fun createPedestrianModel(shirtColor: Color = pedestrianColor): Model {
        modelBuilder.begin()
        val scale = 1.7f  // 1.7x size for better visibility
        val legScale = 0.85f  // Shorter legs

        val shirtMaterial = Material(ColorAttribute.createDiffuse(shirtColor))
        val pantsMaterial = Material(ColorAttribute.createDiffuse(Color(0.25f, 0.25f, 0.35f, 1f)))  // Dark pants
        val skinMaterial = Material(ColorAttribute.createDiffuse(Color(0.85f, 0.65f, 0.55f, 1f)))  // Skin
        val hairMaterial = Material(ColorAttribute.createDiffuse(Color(0.3f, 0.2f, 0.15f, 1f)))  // Brown hair

        // Body dimensions (match ragdoll physics)
        val torsoWidth = 0.15f * scale
        val torsoHeight = 0.5f * scale
        val torsoDepth = 0.1f * scale
        val upperArmWidth = 0.035f * scale
        val upperArmLength = 0.28f * scale
        val lowerArmWidth = 0.03f * scale
        val lowerArmLength = 0.25f * scale
        val upperLegWidth = 0.05f * scale
        val upperLegLength = 0.4f * scale * legScale
        val lowerLegWidth = 0.04f * scale
        val lowerLegLength = 0.4f * scale * legScale

        // Base Y positions
        val hipY = 0.9f * scale * legScale  // Lower hip due to shorter legs
        val torsoY = hipY + torsoHeight / 2
        val headY = hipY + torsoHeight + 0.09f * scale * 0.8f
        val shoulderY = hipY + torsoHeight - 0.05f * scale
        val shoulderOffset = torsoWidth + 0.02f * scale
        val hipOffset = 0.08f * scale

        // Head (face sphere + hair)
        val headPart = modelBuilder.part("head", GL20.GL_TRIANGLES, attributes, skinMaterial)
        headPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, headY, 0f))
        headPart.sphere(0.14f * scale, 0.18f * scale, 0.14f * scale, 8, 8)

        // Hair (on top of head)
        val hairPart = modelBuilder.part("hair", GL20.GL_TRIANGLES, attributes, hairMaterial)
        hairPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, headY + 0.04f * scale, -0.01f * scale))
        hairPart.sphere(0.16f * scale, 0.12f * scale, 0.16f * scale, 8, 8, 0f, 360f, 0f, 90f)

        // Torso
        val torsoPart = modelBuilder.part("torso", GL20.GL_TRIANGLES, attributes, shirtMaterial)
        torsoPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, torsoY, 0f))
        torsoPart.box(torsoWidth * 2, torsoHeight, torsoDepth * 2)

        // Left upper arm (shirt color)
        val leftUpperArmPart = modelBuilder.part("leftUpperArm", GL20.GL_TRIANGLES, attributes, shirtMaterial)
        leftUpperArmPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-shoulderOffset, shoulderY - upperArmLength / 2, 0f))
        leftUpperArmPart.box(upperArmWidth * 2, upperArmLength, upperArmWidth * 2)

        // Left lower arm (skin color - forearm)
        val leftLowerArmPart = modelBuilder.part("leftLowerArm", GL20.GL_TRIANGLES, attributes, skinMaterial)
        leftLowerArmPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, 0f))
        leftLowerArmPart.box(lowerArmWidth * 2, lowerArmLength, lowerArmWidth * 2)

        // Right upper arm
        val rightUpperArmPart = modelBuilder.part("rightUpperArm", GL20.GL_TRIANGLES, attributes, shirtMaterial)
        rightUpperArmPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(shoulderOffset, shoulderY - upperArmLength / 2, 0f))
        rightUpperArmPart.box(upperArmWidth * 2, upperArmLength, upperArmWidth * 2)

        // Right lower arm
        val rightLowerArmPart = modelBuilder.part("rightLowerArm", GL20.GL_TRIANGLES, attributes, skinMaterial)
        rightLowerArmPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(shoulderOffset, shoulderY - upperArmLength - lowerArmLength / 2, 0f))
        rightLowerArmPart.box(lowerArmWidth * 2, lowerArmLength, lowerArmWidth * 2)

        // Left upper leg
        val leftUpperLegPart = modelBuilder.part("leftUpperLeg", GL20.GL_TRIANGLES, attributes, pantsMaterial)
        leftUpperLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-hipOffset, hipY - upperLegLength / 2, 0f))
        leftUpperLegPart.box(upperLegWidth * 2, upperLegLength, upperLegWidth * 2)

        // Left lower leg
        val leftLowerLegPart = modelBuilder.part("leftLowerLeg", GL20.GL_TRIANGLES, attributes, pantsMaterial)
        leftLowerLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-hipOffset, hipY - upperLegLength - lowerLegLength / 2, 0f))
        leftLowerLegPart.box(lowerLegWidth * 2, lowerLegLength, lowerLegWidth * 2)

        // Right upper leg
        val rightUpperLegPart = modelBuilder.part("rightUpperLeg", GL20.GL_TRIANGLES, attributes, pantsMaterial)
        rightUpperLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(hipOffset, hipY - upperLegLength / 2, 0f))
        rightUpperLegPart.box(upperLegWidth * 2, upperLegLength, upperLegWidth * 2)

        // Right lower leg
        val rightLowerLegPart = modelBuilder.part("rightLowerLeg", GL20.GL_TRIANGLES, attributes, pantsMaterial)
        rightLowerLegPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(hipOffset, hipY - upperLegLength - lowerLegLength / 2, 0f))
        rightLowerLegPart.box(lowerLegWidth * 2, lowerLegLength, lowerLegWidth * 2)

        return modelBuilder.end().also { models.add(it) }
    }

    // Cached body part models for articulated pedestrians
    private val pedBodyPartModels = mutableMapOf<String, Model>()

    /**
     * Get or create a pedestrian body part model.
     * These are simple box/sphere models that can be positioned/rotated individually.
     */
    fun getPedestrianBodyPartModel(partName: String, shirtColor: Color): Model {
        val cacheKey = "$partName-${shirtColor.toIntBits()}"
        return pedBodyPartModels.getOrPut(cacheKey) {
            createPedestrianBodyPartModel(partName, shirtColor)
        }
    }

    private fun createPedestrianBodyPartModel(partName: String, shirtColor: Color): Model {
        val scale = 1.7f  // 1.7x size to match pedestrian model
        val legScale = 0.85f  // Shorter legs
        val shirtMaterial = Material(ColorAttribute.createDiffuse(shirtColor))
        val pantsMaterial = Material(ColorAttribute.createDiffuse(Color(0.25f, 0.25f, 0.35f, 1f)))
        val skinMaterial = Material(ColorAttribute.createDiffuse(Color(0.85f, 0.65f, 0.55f, 1f)))
        val hairMaterial = Material(ColorAttribute.createDiffuse(Color(0.3f, 0.2f, 0.15f, 1f)))

        return when (partName) {
            "head" -> modelBuilder.createSphere(0.14f * scale, 0.18f * scale, 0.14f * scale, 8, 8, skinMaterial, attributes)
            "hair" -> {
                modelBuilder.begin()
                val part = modelBuilder.part("hair", GL20.GL_TRIANGLES, attributes, hairMaterial)
                part.sphere(0.16f * scale, 0.12f * scale, 0.16f * scale, 8, 8, 0f, 360f, 0f, 90f)
                modelBuilder.end()
            }
            "torso" -> modelBuilder.createBox(0.30f * scale, 0.5f * scale, 0.20f * scale, shirtMaterial, attributes)
            "leftUpperArm", "rightUpperArm" -> modelBuilder.createBox(0.07f * scale, 0.28f * scale, 0.07f * scale, shirtMaterial, attributes)
            "leftLowerArm", "rightLowerArm" -> modelBuilder.createBox(0.06f * scale, 0.25f * scale, 0.06f * scale, skinMaterial, attributes)
            "leftUpperLeg", "rightUpperLeg" -> modelBuilder.createBox(0.10f * scale, 0.40f * scale * legScale, 0.10f * scale, pantsMaterial, attributes)
            "leftLowerLeg", "rightLowerLeg" -> modelBuilder.createBox(0.08f * scale, 0.40f * scale * legScale, 0.08f * scale, pantsMaterial, attributes)
            else -> modelBuilder.createBox(0.1f * scale, 0.1f * scale, 0.1f * scale, skinMaterial, attributes)
        }.also { models.add(it) }
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

    /**
     * Creates a birch tree with thin white trunk and multiple leaf clusters.
     */
    fun createBirchTreeModel(height: Float = 10f): Model {
        modelBuilder.begin()

        val trunkMaterial = Material(ColorAttribute.createDiffuse(birchTrunkColor))
        val leavesMaterial = Material(ColorAttribute.createDiffuse(birchLeavesColor))

        val trunkHeight = height * 0.7f
        val trunkRadius = 0.15f  // Thin trunk

        // Main trunk (cylinder)
        val trunkPart = modelBuilder.part("trunk", GL20.GL_TRIANGLES, attributes, trunkMaterial)
        trunkPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, trunkHeight / 2, 0f))
        trunkPart.cylinder(trunkRadius * 2, trunkHeight, trunkRadius * 2, 8)

        // Multiple leaf clusters at different heights (drooping birch style)
        val clusterCount = 4
        for (i in 0 until clusterCount) {
            val clusterY = trunkHeight * 0.5f + (trunkHeight * 0.5f * i / clusterCount)
            val clusterRadius = height * 0.2f * (1f - i * 0.1f)  // Slightly smaller at top

            // Main cluster
            val clusterPart = modelBuilder.part("leaves_$i", GL20.GL_TRIANGLES, attributes, leavesMaterial)
            clusterPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, clusterY, 0f))
            clusterPart.sphere(clusterRadius * 2.5f, clusterRadius * 1.2f, clusterRadius * 2.5f, 8, 8)

            // Side drooping clusters
            if (i < clusterCount - 1) {
                val sidePart1 = modelBuilder.part("leaves_side_${i}_1", GL20.GL_TRIANGLES, attributes, leavesMaterial)
                sidePart1.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(clusterRadius * 0.8f, clusterY - clusterRadius * 0.3f, 0f))
                sidePart1.sphere(clusterRadius * 1.5f, clusterRadius * 0.8f, clusterRadius * 1.5f, 6, 6)

                val sidePart2 = modelBuilder.part("leaves_side_${i}_2", GL20.GL_TRIANGLES, attributes, leavesMaterial)
                sidePart2.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(-clusterRadius * 0.8f, clusterY - clusterRadius * 0.3f, 0f))
                sidePart2.sphere(clusterRadius * 1.5f, clusterRadius * 0.8f, clusterRadius * 1.5f, 6, 6)
            }
        }

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

    /**
     * Create a flower bed with variable length and width.
     * @param segments Number of segments along length (3-6)
     * @param rows Number of flower rows for width (2-4)
     */
    fun createFlowerBedModel(segments: Int = 4, rows: Int = 2): Model {
        modelBuilder.begin()

        val dirtMaterial = Material(ColorAttribute.createDiffuse(dirtColor))

        // Each segment is ~0.8m wide, total length based on segment count
        val segmentWidth = 0.8f
        val rowSpacing = 0.3f  // Space between flower rows
        val totalLength = segmentWidth * segments
        val totalDepth = rowSpacing * (rows - 1) + 0.3f  // Extra padding
        val startX = -totalLength / 2 + segmentWidth / 2

        // Dirt bed - full size
        val bedPart = modelBuilder.part("bed", GL20.GL_TRIANGLES, attributes, dirtMaterial)
        bedPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(0f, 0.05f, 0f))
        bedPart.box(totalLength, 0.1f, totalDepth)

        // Flower colors
        val flowerColors = listOf(
            Color(0.9f, 0.3f, 0.3f, 1f),  // Red
            Color(0.9f, 0.9f, 0.3f, 1f),  // Yellow
            Color(0.9f, 0.5f, 0.8f, 1f),  // Pink
            Color(0.6f, 0.3f, 0.9f, 1f),  // Purple
            Color(1.0f, 0.6f, 0.2f, 1f),  // Orange
        )

        // Calculate Z positions for flower rows
        val rowPositions = mutableListOf<Float>()
        val startZ = -totalDepth / 2 + 0.15f
        for (r in 0 until rows) {
            rowPositions.add(startZ + r * rowSpacing)
        }

        // Add flowers to each segment and row
        var flowerIndex = 0
        for (seg in 0 until segments) {
            val segCenterX = startX + seg * segmentWidth

            for (z in rowPositions) {
                // Slight random offset within segment
                val offsetX = (seg % 2) * 0.1f - 0.05f
                val offsetZ = ((seg + flowerIndex) % 2) * 0.05f - 0.025f
                val flowerMaterial = Material(ColorAttribute.createDiffuse(flowerColors[flowerIndex % flowerColors.size]))
                val flowerPart = modelBuilder.part("flower_$flowerIndex", GL20.GL_TRIANGLES, attributes, flowerMaterial)
                flowerPart.setVertexTransform(com.badlogic.gdx.math.Matrix4().translate(segCenterX + offsetX, 0.18f, z + offsetZ))
                flowerPart.sphere(0.1f, 0.1f, 0.1f, 6, 6)
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

    /**
     * Create a player shadow model - simple circle for the wheel.
     */
    fun createPlayerShadowModel(): Model {
        // Simple round shadow under the wheel only
        return createBlobShadowModel(0.25f, 0.25f)
    }

    /**
     * Create a rectangular shadow model for buildings.
     * Shadow is centered around origin and extends in all directions.
     * @param width shadow width (along X axis)
     * @param depth shadow depth (along Z axis)
     */
    fun createBuildingShadowModel(width: Float, depth: Float): Model {
        modelBuilder.begin()

        val material = Material(
            ColorAttribute.createDiffuse(shadowColor),
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )

        val shadow = modelBuilder.part("shadow", GL20.GL_TRIANGLES, attributes, material)

        val y = 0f  // At origin, will be positioned by renderer
        val halfW = width / 2
        val halfD = depth / 2

        // Centered rectangle - counter-clockwise winding for upward-facing normal
        shadow.triangle(
            com.badlogic.gdx.math.Vector3(-halfW, y, halfD),
            com.badlogic.gdx.math.Vector3(halfW, y, halfD),
            com.badlogic.gdx.math.Vector3(halfW, y, -halfD)
        )
        shadow.triangle(
            com.badlogic.gdx.math.Vector3(-halfW, y, halfD),
            com.badlogic.gdx.math.Vector3(halfW, y, -halfD),
            com.badlogic.gdx.math.Vector3(-halfW, y, -halfD)
        )

        return modelBuilder.end().also { models.add(it) }
    }

    /**
     * Create a blob shadow model - a flat ellipse that renders on the ground
     * @param radiusX horizontal radius (width/2)
     * @param radiusZ depth radius (length/2)
     */
    fun createBlobShadowModel(radiusX: Float = 0.5f, radiusZ: Float = 0.4f): Model {
        modelBuilder.begin()

        // Use blending for semi-transparency
        val material = Material(
            ColorAttribute.createDiffuse(shadowColor),
            com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        )

        // Create flat ellipse on the ground (XZ plane, Y = 0)
        val shadow = modelBuilder.part("shadow", GL20.GL_TRIANGLES, attributes, material)

        // Create ellipse with segments
        val segments = 16
        val angleStep = MathUtils.PI2 / segments

        // Center vertex
        val centerX = 0f
        val centerY = 0.01f  // Slightly above ground to avoid z-fighting
        val centerZ = 0f

        // Build triangles from center to edge
        for (i in 0 until segments) {
            val angle1 = i * angleStep
            val angle2 = (i + 1) * angleStep

            val x1 = MathUtils.cos(angle1) * radiusX
            val z1 = MathUtils.sin(angle1) * radiusZ
            val x2 = MathUtils.cos(angle2) * radiusX
            val z2 = MathUtils.sin(angle2) * radiusZ

            // Triangle with normal pointing up (counter-clockwise when viewed from above)
            shadow.triangle(
                com.badlogic.gdx.math.Vector3(x2, centerY, z2),
                com.badlogic.gdx.math.Vector3(x1, centerY, z1),
                com.badlogic.gdx.math.Vector3(centerX, centerY, centerZ)
            )
        }

        return modelBuilder.end().also { models.add(it) }
    }

    override fun dispose() {
        models.forEach { it.dispose() }
        models.clear()
    }
}
