package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.model.WheelType
import com.eucleantoomuch.game.state.SettingsManager
import net.mgsx.gltf.loaders.glb.GLBLoader
import net.mgsx.gltf.scene3d.scene.SceneAsset
import net.mgsx.gltf.scene3d.scene.SceneManager
import net.mgsx.gltf.scene3d.scene.Scene
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx

/**
 * Wheel selection screen - allows player to choose between 3 EUC types.
 * Shown after pressing PLAY, before calibration/game start.
 */
class WheelSelectionRenderer(
    private val settingsManager: SettingsManager
) : Disposable {
    private val ui = UIRenderer()

    // Buttons
    private val leftButton = Rectangle()
    private val rightButton = Rectangle()
    private val startButton = Rectangle()
    private val backButton = Rectangle()

    // Animation states
    private var leftButtonHover = 0f
    private var rightButtonHover = 0f
    private var startButtonHover = 0f
    private var backButtonHover = 0f
    private var enterAnimProgress = 0f
    private var wheelRotation = 0f

    // Current selection
    private var currentIndex = 0  // Default to Standard (first in list)
    private val wheels = WheelType.ALL

    // 3D preview with PBR rendering
    private lateinit var previewCamera: PerspectiveCamera
    private lateinit var sceneManager: SceneManager
    private val wheelSceneAssets = mutableMapOf<String, SceneAsset>()
    private val wheelScenes = mutableMapOf<String, Scene>()

    enum class Action {
        NONE, START, BACK
    }

    init {
        // Initialize to saved selection
        currentIndex = wheels.indexOfFirst { it.id == settingsManager.selectedWheelId }
            .takeIf { it >= 0 } ?: 0  // Default to Standard (first in list)

        initPreview()
    }

    private fun initPreview() {
        previewCamera = PerspectiveCamera(45f, 400f, 400f)
        previewCamera.position.set(0f, 0.6f, -1.0f)
        previewCamera.lookAt(0f, 0.4f, 0f)
        previewCamera.near = 0.1f
        previewCamera.far = 10f
        previewCamera.update()

        // Setup SceneManager for PBR rendering
        sceneManager = SceneManager()
        sceneManager.setCamera(previewCamera)
        sceneManager.setAmbientLight(0.02f)  // Low ambient like in game

        // Add directional light - similar to GameRenderer settings
        val light = DirectionalLightEx()
        light.direction.set(-0.5f, -1f, 0.5f).nor()
        light.color.set(1f, 1f, 1f, 1f)
        light.intensity = 5.5f  // Match game lighting
        sceneManager.environment.add(light)

        // Load GLB models for each wheel type
        loadWheelModels()
    }

    private fun loadWheelModels() {
        // Map wheel types to their GLB files
        val wheelFiles = mapOf(
            "standard" to "monowheel.glb",
            "performance" to "monowheel2.glb"
        )

        Gdx.app.log("WheelSelection", "Loading wheel models for ${wheels.size} wheels: ${wheels.map { it.id }}")

        wheels.forEach { wheel ->
            val fileName = wheelFiles[wheel.id] ?: "monowheel.glb"
            Gdx.app.log("WheelSelection", "Attempting to load $fileName for wheel id='${wheel.id}'")
            try {
                val modelFile = Gdx.files.internal(fileName)
                if (modelFile.exists()) {
                    // Create a new loader for each model to avoid caching issues
                    val loader = GLBLoader()
                    val asset = loader.load(modelFile)
                    wheelSceneAssets[wheel.id] = asset
                    val scene = Scene(asset.scene)
                    scene.modelInstance.transform.setToScaling(0.175f, 0.175f, 0.175f)
                    wheelScenes[wheel.id] = scene
                    Gdx.app.log("WheelSelection", "Successfully loaded $fileName for ${wheel.id}, scene nodes: ${asset.scene.model.nodes.size}")
                } else {
                    Gdx.app.error("WheelSelection", "$fileName not found in assets!")
                }
            } catch (e: Exception) {
                Gdx.app.error("WheelSelection", "Failed to load $fileName: ${e.message}")
                e.printStackTrace()
            }
        }

        Gdx.app.log("WheelSelection", "Loaded scenes: ${wheelScenes.keys}")
    }

    fun render(): Action {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val scale = UITheme.Dimensions.scale()
        val delta = Gdx.graphics.deltaTime

        // Update animations
        enterAnimProgress = UITheme.Anim.ease(enterAnimProgress, 1f, 4f)
        wheelRotation += delta * 25f  // Slow rotation

        val currentWheel = wheels[currentIndex]

        // Check hover state
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()

        val leftHovered = leftButton.contains(touchX, touchY)
        val rightHovered = rightButton.contains(touchX, touchY)
        val startHovered = startButton.contains(touchX, touchY)
        val backHovered = backButton.contains(touchX, touchY)

        leftButtonHover = UITheme.Anim.ease(leftButtonHover, if (leftHovered) 1f else 0f, 10f)
        rightButtonHover = UITheme.Anim.ease(rightButtonHover, if (rightHovered) 1f else 0f, 10f)
        startButtonHover = UITheme.Anim.ease(startButtonHover, if (startHovered) 1f else 0f, 10f)
        backButtonHover = UITheme.Anim.ease(backButtonHover, if (backHovered) 1f else 0f, 10f)

        // === Draw Background ===
        ui.beginShapes()

        // Full gradient background (consistent with other screens)
        ui.gradientBackground()

        // === HORIZONTAL LAYOUT for landscape orientation ===
        // Main panel - full screen with margins
        val panelMargin = 40f * scale
        val panelWidth = (sw - panelMargin * 2) * enterAnimProgress
        val panelHeight = (sh - panelMargin * 2) * enterAnimProgress
        val panelX = centerX - panelWidth / 2
        val panelY = panelMargin

        // Glass panel (consistent with Settings, Pause, GameOver)
        ui.glassPanel(panelX, panelY, panelWidth, panelHeight,
            radius = UITheme.Dimensions.panelRadius,
            tintColor = UITheme.surfaceSolid)

        // Layout: Left side = 3D preview, Right side = info/stats/buttons
        val leftSectionWidth = panelWidth * 0.45f
        val rightSectionWidth = panelWidth * 0.55f
        val leftSectionX = panelX
        val rightSectionX = panelX + leftSectionWidth

        // Title at top center
        val titleY = panelY + panelHeight - 40f * scale

        // === LEFT SIDE: 3D Preview with arrows ===
        val previewSize = (panelHeight - 120f * scale).coerceAtMost(leftSectionWidth - 100f * scale)
        val previewX = leftSectionX + leftSectionWidth / 2
        val previewY = panelY + (panelHeight - previewSize) / 2 - 20f * scale

        // Preview background (card style)
        ui.card(
            previewX - previewSize / 2 - 10f * scale,
            previewY - 10f * scale,
            previewSize + 20f * scale,
            previewSize + 20f * scale,
            16f * scale, UITheme.surfaceLight
        )

        // Arrow buttons on sides of preview
        val arrowButtonSize = UITheme.Dimensions.arrowButtonSize

        // Left arrow button (neonButton style)
        val arrowButtonY = previewY + previewSize / 2 - arrowButtonSize / 2 - 120f * scale
        leftButton.set(
            leftSectionX + 20f * scale,
            arrowButtonY,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.neonButton(leftButton, UITheme.secondary)

        // Right arrow button (neonButton style)
        rightButton.set(
            leftSectionX + leftSectionWidth - arrowButtonSize - 20f * scale,
            arrowButtonY,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.neonButton(rightButton, UITheme.secondary)

        // === RIGHT SIDE: Info, Stats, Buttons ===
        val rightCenterX = rightSectionX + rightSectionWidth / 2
        val contentStartY = panelY + panelHeight - 100f * scale

        // Wheel name area
        val nameY = contentStartY - 20f * scale
        val sizeY = nameY - 50f * scale
        val descY = sizeY - 40f * scale

        // Stats section (card style)
        val statsTopY = descY - 50f * scale
        val statsHeight = 130f * scale
        val statsWidth = rightSectionWidth - 80f * scale
        val statsX = rightSectionX + 40f * scale
        val statsY = statsTopY - statsHeight

        ui.card(statsX, statsY, statsWidth, statsHeight, 12f * scale, UITheme.surfaceLight)

        // Stat bars (using neonBar for consistency)
        val barWidth = 160f * scale
        val barHeight = 14f * scale
        val barStartX = rightCenterX + 20f * scale
        val barSpacing = 38f * scale

        // Speed bar
        val speedBarY = statsY + statsHeight - 40f * scale
        val speedNorm = currentWheel.maxSpeed / 27.8f
        ui.neonBar(barStartX, speedBarY, barWidth, barHeight, speedNorm,
            backgroundColor = UITheme.surface, fillColor = UITheme.accent)

        // Stability bar
        val stabilityBarY = speedBarY - barSpacing
        val stabilityNorm = ((currentWheel.criticalLean - 0.85f) / 0.25f).coerceIn(0f, 1f)
        ui.neonBar(barStartX, stabilityBarY, barWidth, barHeight, stabilityNorm,
            backgroundColor = UITheme.surface, fillColor = UITheme.primary)

        // Handling bar
        val handlingBarY = stabilityBarY - barSpacing
        val handlingNorm = ((currentWheel.turnResponsiveness - 2f) / 3f).coerceIn(0f, 1f)
        ui.neonBar(barStartX, handlingBarY, barWidth, barHeight, handlingNorm,
            backgroundColor = UITheme.surface, fillColor = UITheme.secondary)

        // Buttons at bottom of right section - horizontal layout
        // START button - large like PLAY in main menu
        val startButtonWidth = 280f * scale
        val startButtonHeight = 100f * scale
        val backButtonWidth = 140f * scale
        val backButtonHeight = 70f * scale
        val buttonGap = 20f * scale
        val buttonsY = panelY + 40f * scale

        // Total width of both buttons with gap
        val totalButtonsWidth = backButtonWidth + buttonGap + startButtonWidth
        val buttonsStartX = rightCenterX - totalButtonsWidth / 2

        // Back button (smaller, left side)
        backButton.set(buttonsStartX, buttonsY + (startButtonHeight - backButtonHeight) / 2, backButtonWidth, backButtonHeight)
        ui.neonButton(backButton, UITheme.surfaceLight)

        // Start button (large, right side) - same style as PLAY
        startButton.set(buttonsStartX + backButtonWidth + buttonGap, buttonsY, startButtonWidth, startButtonHeight)
        ui.neonButton(startButton, UITheme.accent)

        ui.endShapes()

        // === Render 3D Preview ===
        render3DPreview(previewX, previewY, previewSize, currentWheel)

        // === Draw Text ===
        ui.beginBatch()

        // Title at top
        ui.textCentered("SELECT YOUR WHEEL", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Arrow symbols
        ui.textCentered("<",
            leftButton.x + leftButton.width / 2,
            leftButton.y + leftButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)
        ui.textCentered(">",
            rightButton.x + rightButton.width / 2,
            rightButton.y + rightButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)

        // Wheel info on right side
        ui.textCentered(currentWheel.displayName, rightCenterX, nameY, UIFonts.heading, UITheme.accent)
        ui.textCentered("${currentWheel.wheelSizeInches}\" wheel", rightCenterX, sizeY, UIFonts.body, UITheme.textSecondary)
        ui.textCentered(currentWheel.description, rightCenterX, descY, UIFonts.caption, UITheme.textMuted)

        // Stats labels
        val labelX = statsX + 20f * scale
        UIFonts.body.color = UITheme.textSecondary
        UIFonts.body.draw(ui.batch, "SPEED", labelX, speedBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)
        UIFonts.body.draw(ui.batch, "STABILITY", labelX, stabilityBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)
        UIFonts.body.draw(ui.batch, "HANDLING", labelX, handlingBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)

        // Button labels
        ui.textCentered("BACK", backButton.x + backButton.width / 2,
            backButton.y + backButton.height / 2, UIFonts.body, UITheme.textSecondary)
        // START uses title font like PLAY in main menu
        ui.textCentered("START", startButton.x + startButton.width / 2,
            startButton.y + startButton.height / 2, UIFonts.title, UITheme.textPrimary)

        ui.endBatch()

        // Wheel indicator dots below preview
        val dotY = previewY - 30f * scale
        val dotSpacing = 20f * scale
        val dotRadius = 6f * scale

        ui.beginShapes()
        val totalDotsWidth = (wheels.size - 1) * dotSpacing
        for (i in wheels.indices) {
            val dotX = previewX - totalDotsWidth / 2 + i * dotSpacing
            val dotColor = if (i == currentIndex) UITheme.accent else UITheme.surfaceBorder
            ui.shapes.color = dotColor
            ui.shapes.circle(dotX, dotY, dotRadius)
        }
        ui.endShapes()

        // === Handle Input ===
        return handleInput(touchX, touchY)
    }

    private fun render3DPreview(centerX: Float, y: Float, size: Float, wheel: WheelType) {
        // Set viewport for 3D preview area
        val viewportX = (centerX - size / 2).toInt()
        val viewportY = y.toInt()
        val viewportSize = size.toInt()

        Gdx.gl.glViewport(viewportX, viewportY, viewportSize, viewportSize)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)

        previewCamera.viewportWidth = size
        previewCamera.viewportHeight = size
        previewCamera.update()
        sceneManager.setCamera(previewCamera)

        // Get the scene for current wheel
        val scene = wheelScenes[wheel.id]
        if (scene == null) {
            Gdx.app.error("WheelSelection", "No scene found for wheel.id='${wheel.id}', available: ${wheelScenes.keys}")
            return
        }

        // Update rotation
        scene.modelInstance.transform.setToScaling(0.175f, 0.175f, 0.175f)
        scene.modelInstance.transform.rotate(0f, 1f, 0f, wheelRotation)

        // Render with SceneManager
        sceneManager.getRenderableProviders().clear()
        sceneManager.addScene(scene)
        sceneManager.update(Gdx.graphics.deltaTime)
        sceneManager.render()
        sceneManager.removeScene(scene)

        // Restore full viewport
        Gdx.gl.glViewport(0, 0, Gdx.graphics.width, Gdx.graphics.height)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
    }

    private fun handleInput(touchX: Float, touchY: Float): Action {
        if (Gdx.input.justTouched()) {
            when {
                leftButton.contains(touchX, touchY) -> {
                    UIFeedback.swipe()
                    currentIndex = (currentIndex - 1 + wheels.size) % wheels.size
                }
                rightButton.contains(touchX, touchY) -> {
                    UIFeedback.swipe()
                    currentIndex = (currentIndex + 1) % wheels.size
                }
                startButton.contains(touchX, touchY) -> {
                    UIFeedback.clickHeavy()
                    settingsManager.selectedWheelId = wheels[currentIndex].id
                    return Action.START
                }
                backButton.contains(touchX, touchY) -> {
                    UIFeedback.click()
                    return Action.BACK
                }
            }
        }

        // Keyboard shortcuts
        when {
            Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A) -> {
                UIFeedback.swipe()
                currentIndex = (currentIndex - 1 + wheels.size) % wheels.size
            }
            Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D) -> {
                UIFeedback.swipe()
                currentIndex = (currentIndex + 1) % wheels.size
            }
            Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE) -> {
                UIFeedback.clickHeavy()
                settingsManager.selectedWheelId = wheels[currentIndex].id
                return Action.START
            }
            Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK) -> {
                UIFeedback.click()
                return Action.BACK
            }
        }

        return Action.NONE
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
        enterAnimProgress = 0f
    }

    fun recreate() {
        ui.recreate()
        sceneManager.dispose()
        sceneManager = SceneManager()
        sceneManager.setCamera(previewCamera)
        sceneManager.setAmbientLight(0.02f)
        val light = DirectionalLightEx()
        light.direction.set(-0.5f, -1f, 0.5f).nor()
        light.color.set(1f, 1f, 1f, 1f)
        light.intensity = 5.5f
        sceneManager.environment.add(light)
    }

    override fun dispose() {
        ui.dispose()
        sceneManager.dispose()
        wheelSceneAssets.values.forEach { it.dispose() }
        wheelSceneAssets.clear()
        wheelScenes.clear()
    }
}
