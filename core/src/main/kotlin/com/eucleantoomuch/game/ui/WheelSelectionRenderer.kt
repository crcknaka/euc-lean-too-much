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
import com.eucleantoomuch.game.state.VoltsManager
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.Texture
import net.mgsx.gltf.loaders.glb.GLBLoader
import net.mgsx.gltf.scene3d.attributes.PBRCubemapAttribute
import net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute
import net.mgsx.gltf.scene3d.scene.SceneAsset
import net.mgsx.gltf.scene3d.scene.SceneManager
import net.mgsx.gltf.scene3d.scene.Scene
import net.mgsx.gltf.scene3d.lights.DirectionalLightEx
import net.mgsx.gltf.scene3d.utils.IBLBuilder

/**
 * Wheel selection screen - allows player to choose between 3 EUC types.
 * Shown after pressing PLAY, before calibration/game start.
 */
class WheelSelectionRenderer(
    private val settingsManager: SettingsManager,
    private val voltsManager: VoltsManager
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

    // Touch camera controls
    private val previewBounds = Rectangle()
    private var cameraAngle = 0f           // Horizontal rotation of model (unlimited)
    private val cameraPitch = 20f          // Fixed vertical angle (no vertical control)
    private var cameraDistance = 1.0f      // Distance from model (zoom)
    private var lastTouchX = 0f
    private var isDragging = false
    private var lastPinchDistance = 0f
    private var isPinching = false

    // Double-tap to pause rotation
    private var lastTapTime = 0L
    private var isRotationPaused = false

    // 3D preview with PBR rendering
    private lateinit var previewCamera: PerspectiveCamera
    private lateinit var sceneManager: SceneManager
    private val wheelSceneAssets = mutableMapOf<String, SceneAsset>()
    private val wheelScenes = mutableMapOf<String, Scene>()

    // IBL textures for PBR rendering
    private var diffuseCubemap: Cubemap? = null
    private var specularCubemap: Cubemap? = null
    private var brdfLUT: Texture? = null

    enum class Action {
        NONE, START, BACK
    }

    /** Check if a wheel is unlocked based on total volts collected */
    private fun isWheelUnlocked(wheel: WheelType): Boolean {
        return voltsManager.totalVolts >= wheel.unlockVoltsRequired
    }

    /** Format volts with K suffix for thousands */
    private fun formatVolts(volts: Int): String {
        return when {
            volts >= 1_000_000 -> "${volts / 1_000_000}.${(volts % 1_000_000) / 100_000}M"
            volts >= 1_000 -> "${volts / 1_000}.${(volts % 1_000) / 100}K"
            else -> "$volts"
        }
    }

    init {
        // Initialize to saved selection, but only if that wheel is unlocked
        val savedIndex = wheels.indexOfFirst { it.id == settingsManager.selectedWheelId }
            .takeIf { it >= 0 } ?: 0

        currentIndex = if (savedIndex >= 0 && isWheelUnlocked(wheels[savedIndex])) {
            // Saved wheel is unlocked, use it
            savedIndex
        } else {
            // Saved wheel is locked, find the last unlocked wheel
            wheels.indexOfLast { isWheelUnlocked(it) }.takeIf { it >= 0 } ?: 0
        }

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

        // Setup directional light for PBR
        val light = DirectionalLightEx()
        light.direction.set(-0.5f, -1f, 0.5f).nor()
        light.color.set(1.0f, 1.0f, 1.0f, 1f)
        light.intensity = 3.0f
        sceneManager.environment.add(light)

        // Setup IBL (Image Based Lighting) for proper PBR rendering
        // Lower resolution for better performance
        val iblBuilder = IBLBuilder.createOutdoor(light)
        diffuseCubemap = iblBuilder.buildIrradianceMap(64)
        specularCubemap = iblBuilder.buildRadianceMap(6)
        iblBuilder.dispose()

        // BRDF lookup texture (provided by gdx-gltf library)
        brdfLUT = Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"))

        // Apply IBL to environment
        sceneManager.setAmbientLight(1f)
        sceneManager.environment.set(PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT))
        sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap))
        sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap))

        // Load GLB models for each wheel type
        loadWheelModels()
    }

    private fun loadWheelModels() {
        // Map wheel types to their GLB files
        val wheelFiles = mapOf(
            "simple" to "nicola.glb",
            "standard" to "v12.glb",
            "performance" to "lynx.glb"
        )

        Gdx.app.log("WheelSelection", "Loading wheel models for ${wheels.size} wheels: ${wheels.map { it.id }}")

        wheels.forEach { wheel ->
            val fileName = wheelFiles[wheel.id] ?: "v12.glb"
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
        // Auto-rotate wheel only when not manually controlling and not paused
        if (!isDragging && !isPinching && !isRotationPaused) {
            wheelRotation += delta * 25f
        }

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

        // Store preview bounds for touch detection
        previewBounds.set(
            previewX - previewSize / 2,
            previewY,
            previewSize,
            previewSize
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
        val statsHeight = 170f * scale  // Increased for 4 stats
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

        // Battery bar
        val batteryBarY = handlingBarY - barSpacing
        val batteryNorm = currentWheel.batteryCapacity / 4000f  // 4000 mAh is max (Speed Demon)
        ui.neonBar(barStartX, batteryBarY, barWidth, barHeight, batteryNorm,
            backgroundColor = UITheme.surface, fillColor = UITheme.warning)

        // Buttons at bottom of right section - horizontal layout
        // START button - large like PLAY in main menu
        val startButtonWidth = 280f * scale
        val startButtonHeight = 100f * scale
        val backButtonWidth = 200f * scale
        val backButtonHeight = 80f * scale
        val buttonGap = 20f * scale
        val buttonsY = panelY + 40f * scale

        // Total width of both buttons with gap
        val totalButtonsWidth = backButtonWidth + buttonGap + startButtonWidth
        val buttonsStartX = rightCenterX - totalButtonsWidth / 2

        // Back button (smaller, left side)
        backButton.set(buttonsStartX, buttonsY + (startButtonHeight - backButtonHeight) / 2, backButtonWidth, backButtonHeight)
        ui.neonButton(backButton, UITheme.surfaceLight)

        // Start button (large, right side) - same style as PLAY
        // Gray out if wheel is locked
        val isCurrentWheelUnlocked = isWheelUnlocked(currentWheel)
        startButton.set(buttonsStartX + backButtonWidth + buttonGap, buttonsY, startButtonWidth, startButtonHeight)
        ui.neonButton(startButton, if (isCurrentWheelUnlocked) UITheme.accent else UITheme.surfaceLight)

        ui.endShapes()

        // === Render 3D Preview (skip first frames to avoid flash) ===
        if (enterAnimProgress > 0.1f) {
            render3DPreview(previewX, previewY, previewSize, currentWheel, isCurrentWheelUnlocked)
        }

        // === Draw lock overlay for locked wheels ===
        if (!isCurrentWheelUnlocked) {
            ui.beginShapes()
            // Dark overlay to dim the preview
            ui.shapes.color = com.badlogic.gdx.graphics.Color(0f, 0f, 0f, 0.5f)
            ui.shapes.rect(
                previewX - previewSize / 2 - 10f * scale,
                previewY - 10f * scale,
                previewSize + 20f * scale,
                previewSize + 20f * scale
            )

            // Redraw arrow buttons on top of overlay
            ui.neonButton(leftButton, UITheme.secondary)
            ui.neonButton(rightButton, UITheme.secondary)

            // Large lock icon in center of preview
            val lockSize = 120f * scale
            val lockColor = com.badlogic.gdx.graphics.Color(1f, 0.85f, 0.2f, 1f)  // Bright gold
            ui.lock(previewX, previewY + previewSize / 2, lockSize, lockColor)

            // Lock icon on START button (left side)
            val buttonLockSize = 40f * scale
            ui.lock(
                startButton.x + 55f * scale,
                startButton.y + startButton.height / 2,
                buttonLockSize,
                com.badlogic.gdx.graphics.Color(0.7f, 0.6f, 0.3f, 1f)  // Bronze/gold
            )
            ui.endShapes()
        }

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
        val nameColor = if (isCurrentWheelUnlocked) UITheme.accent else UITheme.textMuted
        ui.textCentered(currentWheel.displayName, rightCenterX, nameY, UIFonts.heading, nameColor)
        ui.textCentered("${currentWheel.wheelSizeInches}\" wheel", rightCenterX, sizeY, UIFonts.body, UITheme.textSecondary)

        // Show unlock progress for locked wheels, or description for unlocked
        if (isCurrentWheelUnlocked) {
            ui.textCentered(currentWheel.description, rightCenterX, descY, UIFonts.caption, UITheme.textMuted)
        } else {
            // Show unlock progress: "12,500 / 100,000 V"
            val current = voltsManager.totalVolts
            val required = currentWheel.unlockVoltsRequired
            val progressText = "${formatVolts(current)} / ${formatVolts(required)} V"
            ui.textCentered(progressText, rightCenterX, descY, UIFonts.caption, UITheme.warning)
        }

        // Stats labels
        val labelX = statsX + 20f * scale
        UIFonts.body.color = UITheme.textSecondary
        UIFonts.body.draw(ui.batch, "SPEED", labelX, speedBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)
        UIFonts.body.draw(ui.batch, "STABILITY", labelX, stabilityBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)
        UIFonts.body.draw(ui.batch, "HANDLING", labelX, handlingBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)
        UIFonts.body.draw(ui.batch, "BATTERY", labelX, batteryBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)

        // Button labels
        ui.textCentered("BACK", backButton.x + backButton.width / 2,
            backButton.y + backButton.height / 2, UIFonts.body, UITheme.textSecondary)

        // START button - show unlock requirement if locked
        if (isCurrentWheelUnlocked) {
            ui.textCentered("START", startButton.x + startButton.width / 2,
                startButton.y + startButton.height / 2, UIFonts.title, UITheme.textPrimary)
        } else {
            // Show volts required (lock icon is drawn separately in shapes)
            // Shift text right to leave space for lock icon
            val voltsNeeded = currentWheel.unlockVoltsRequired
            ui.textCentered("${formatVolts(voltsNeeded)}V", startButton.x + startButton.width / 2 + 25f * scale,
                startButton.y + startButton.height / 2, UIFonts.heading, UITheme.textMuted)
        }

        ui.endBatch()

        // Wheel indicator dots below preview (with lock icons for locked wheels)
        val dotY = previewY - 30f * scale
        val dotSpacing = 24f * scale
        val dotRadius = 6f * scale

        ui.beginShapes()
        val totalDotsWidth = (wheels.size - 1) * dotSpacing
        for (i in wheels.indices) {
            val wheel = wheels[i]
            val dotX = previewX - totalDotsWidth / 2 + i * dotSpacing
            val isUnlocked = isWheelUnlocked(wheel)

            if (isUnlocked) {
                // Regular dot
                val dotColor = if (i == currentIndex) UITheme.accent else UITheme.surfaceBorder
                ui.shapes.color = dotColor
                ui.shapes.circle(dotX, dotY, dotRadius)
            } else {
                // Lock icon for locked wheels
                val lockColor = if (i == currentIndex)
                    com.badlogic.gdx.graphics.Color(0.9f, 0.75f, 0.2f, 1f)  // Gold
                else
                    UITheme.surfaceBorder
                ui.lock(dotX, dotY, 16f * scale, lockColor)
            }
        }
        ui.endShapes()

        // === Handle Input ===
        return handleInput(touchX, touchY)
    }

    private fun render3DPreview(centerX: Float, y: Float, size: Float, wheel: WheelType, isUnlocked: Boolean = true) {
        // Set viewport for 3D preview area
        val viewportX = (centerX - size / 2).toInt()
        val viewportY = y.toInt()
        val viewportSize = size.toInt()

        Gdx.gl.glViewport(viewportX, viewportY, viewportSize, viewportSize)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glClear(GL20.GL_DEPTH_BUFFER_BIT)

        // Darken ambient light for locked wheels
        if (!isUnlocked) {
            sceneManager.setAmbientLight(0.15f)  // Much darker for locked
        } else {
            sceneManager.setAmbientLight(1f)     // Normal
        }

        // Fixed camera position (only zoom affects distance, no orbit)
        // Slight top-down angle for better view
        val pitchRad = Math.toRadians(cameraPitch.toDouble()).toFloat()
        previewCamera.position.set(
            0f,
            kotlin.math.sin(pitchRad) * cameraDistance + 0.4f,
            -kotlin.math.cos(pitchRad) * cameraDistance
        )
        previewCamera.lookAt(0f, 0.4f, 0f)
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

        // Rotate model: cameraAngle (from touch drag) + wheelRotation (auto-spin)
        // This keeps the spin axis constant relative to the viewer
        scene.modelInstance.transform.setToScaling(0.175f, 0.175f, 0.175f)
        scene.modelInstance.transform.rotate(0f, 1f, 0f, cameraAngle + wheelRotation)

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
        // Handle preview touch controls (drag to rotate, pinch to zoom)
        handlePreviewTouch(touchX, touchY)

        if (Gdx.input.justTouched()) {
            // Arrow buttons have priority - check them first regardless of preview area
            when {
                leftButton.contains(touchX, touchY) -> {
                    UIFeedback.swipe()
                    currentIndex = (currentIndex - 1 + wheels.size) % wheels.size
                    resetCameraView()
                    return Action.NONE  // Consume touch
                }
                rightButton.contains(touchX, touchY) -> {
                    UIFeedback.swipe()
                    currentIndex = (currentIndex + 1) % wheels.size
                    resetCameraView()
                    return Action.NONE  // Consume touch
                }
            }

            // Other buttons - don't process if touch is in preview area (for drag/zoom)
            if (!previewBounds.contains(touchX, touchY)) {
                when {
                    startButton.contains(touchX, touchY) -> {
                        val currentWheel = wheels[currentIndex]
                        if (isWheelUnlocked(currentWheel)) {
                            UIFeedback.clickHeavy()
                            settingsManager.selectedWheelId = currentWheel.id
                            return Action.START
                        } else {
                            // Locked wheel - play error feedback
                            UIFeedback.click()
                        }
                    }
                    backButton.contains(touchX, touchY) -> {
                        UIFeedback.click()
                        return Action.BACK
                    }
                }
            }
        }

        // Keyboard shortcuts
        when {
            Gdx.input.isKeyJustPressed(Input.Keys.LEFT) || Gdx.input.isKeyJustPressed(Input.Keys.A) -> {
                UIFeedback.swipe()
                currentIndex = (currentIndex - 1 + wheels.size) % wheels.size
                resetCameraView()
            }
            Gdx.input.isKeyJustPressed(Input.Keys.RIGHT) || Gdx.input.isKeyJustPressed(Input.Keys.D) -> {
                UIFeedback.swipe()
                currentIndex = (currentIndex + 1) % wheels.size
                resetCameraView()
            }
            Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE) -> {
                val currentWheel = wheels[currentIndex]
                if (isWheelUnlocked(currentWheel)) {
                    UIFeedback.clickHeavy()
                    settingsManager.selectedWheelId = currentWheel.id
                    return Action.START
                } else {
                    UIFeedback.click()
                }
            }
            Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK) -> {
                UIFeedback.click()
                return Action.BACK
            }
        }

        return Action.NONE
    }

    private fun resetCameraView() {
        cameraAngle = 0f
        cameraDistance = 1.0f
        isRotationPaused = false
    }

    private fun handlePreviewTouch(touchX: Float, touchY: Float) {
        val sh = ui.screenHeight

        // Count active touches
        var touchCount = 0
        for (i in 0..4) {
            if (Gdx.input.isTouched(i)) touchCount++
        }

        // Handle pinch to zoom (two fingers)
        if (touchCount >= 2) {
            val x0 = Gdx.input.getX(0).toFloat()
            val y0 = sh - Gdx.input.getY(0).toFloat()
            val x1 = Gdx.input.getX(1).toFloat()
            val y1 = sh - Gdx.input.getY(1).toFloat()

            val currentDistance = kotlin.math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))

            if (isPinching) {
                val delta = currentDistance - lastPinchDistance
                // Zoom: closer pinch = zoom out, spread = zoom in
                cameraDistance = (cameraDistance - delta * 0.003f).coerceIn(0.5f, 2.5f)
            }

            lastPinchDistance = currentDistance
            isPinching = true
            isDragging = false
            return
        }

        isPinching = false

        // Handle single finger drag to rotate (horizontal only)
        if (Gdx.input.isTouched) {
            if (Gdx.input.justTouched() && previewBounds.contains(touchX, touchY)) {
                // Check for double-tap to toggle rotation pause
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTapTime < 300) {
                    // Double-tap detected - toggle rotation pause
                    isRotationPaused = !isRotationPaused
                    UIFeedback.click()
                }
                lastTapTime = currentTime

                // Start drag
                isDragging = true
                lastTouchX = touchX
            } else if (isDragging) {
                // Continue drag - rotate model left/right
                val deltaX = touchX - lastTouchX
                cameraAngle -= deltaX * 0.5f
                lastTouchX = touchX
            }
        } else {
            isDragging = false
        }
    }

    /** Called when entering the wheel selection screen */
    fun onEnter() {
        enterAnimProgress = 0f

        // Always reset camera to default position
        resetCameraView()

        // Reset to an unlocked wheel if current is locked
        if (!isWheelUnlocked(wheels[currentIndex])) {
            currentIndex = wheels.indexOfLast { isWheelUnlocked(it) }.takeIf { it >= 0 } ?: 0
        }
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
        onEnter()
    }

    fun recreate() {
        ui.recreate()
        // Dispose old IBL textures
        diffuseCubemap?.dispose()
        specularCubemap?.dispose()
        brdfLUT?.dispose()
        sceneManager.dispose()

        // Recreate SceneManager with IBL
        sceneManager = SceneManager()
        sceneManager.setCamera(previewCamera)

        val light = DirectionalLightEx()
        light.direction.set(-0.5f, -1f, 0.5f).nor()
        light.color.set(1f, 1f, 1f, 1f)
        light.intensity = 3.0f
        sceneManager.environment.add(light)

        // Rebuild IBL
        val iblBuilder = IBLBuilder.createOutdoor(light)
        diffuseCubemap = iblBuilder.buildIrradianceMap(64)
        specularCubemap = iblBuilder.buildRadianceMap(6)
        iblBuilder.dispose()

        brdfLUT = Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"))

        sceneManager.setAmbientLight(1f)
        sceneManager.environment.set(PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdfLUT))
        sceneManager.environment.set(PBRCubemapAttribute.createSpecularEnv(specularCubemap))
        sceneManager.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuseCubemap))
    }

    override fun dispose() {
        ui.dispose()
        sceneManager.dispose()
        wheelSceneAssets.values.forEach { it.dispose() }
        wheelSceneAssets.clear()
        wheelScenes.clear()
        diffuseCubemap?.dispose()
        specularCubemap?.dispose()
        brdfLUT?.dispose()
    }
}
