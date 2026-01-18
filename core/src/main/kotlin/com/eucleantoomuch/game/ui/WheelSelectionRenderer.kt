package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.model.WheelType
import com.eucleantoomuch.game.state.SettingsManager

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

    // 3D preview
    private lateinit var previewCamera: PerspectiveCamera
    private lateinit var previewBatch: ModelBatch
    private lateinit var previewEnvironment: Environment
    private val wheelModelInstances = mutableMapOf<String, ModelInstance>()
    private val wheelModels = mutableListOf<Model>()
    private val modelBuilder = ModelBuilder()
    private val attributes = (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()

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
        previewCamera.position.set(0f, 0.4f, -1.0f)
        previewCamera.lookAt(0f, 0.2f, 0f)
        previewCamera.near = 0.1f
        previewCamera.far = 10f
        previewCamera.update()

        previewBatch = ModelBatch()
        previewEnvironment = Environment().apply {
            set(ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f))
            add(DirectionalLight().set(0.9f, 0.9f, 0.9f, -0.5f, -1f, 0.5f))
            add(DirectionalLight().set(0.4f, 0.4f, 0.5f, 0.5f, -0.5f, -0.5f))
        }

        // Create model instances for each wheel type
        wheels.forEach { wheel ->
            val model = createWheelModel(wheel)
            wheelModels.add(model)
            wheelModelInstances[wheel.id] = ModelInstance(model)
        }
    }

    private fun createWheelModel(wheelType: WheelType): Model {
        modelBuilder.begin()

        // Wheel (cylinder)
        val wheelColor = Color(0.1f, 0.1f, 0.1f, 1f)  // Black tire
        val wheelMaterial = Material(ColorAttribute.createDiffuse(wheelColor))
        val wheelPart = modelBuilder.part("wheel", GL20.GL_TRIANGLES, attributes, wheelMaterial)
        wheelPart.cylinder(
            wheelType.wheelRadius * 2,
            0.12f,
            wheelType.wheelRadius * 2,
            20
        )

        // Body - use wheelType.bodyColor
        val bodyMaterial = Material(ColorAttribute.createDiffuse(wheelType.bodyColor))
        val bodyPart = modelBuilder.part("body", GL20.GL_TRIANGLES, attributes, bodyMaterial)
        bodyPart.setVertexTransform(Matrix4().translate(0f, 0.28f, 0f))
        bodyPart.box(0.16f, 0.35f, 0.22f)

        // Accent trim on top
        val accentMaterial = Material(ColorAttribute.createDiffuse(wheelType.accentColor))
        val accentPart = modelBuilder.part("accent", GL20.GL_TRIANGLES, attributes, accentMaterial)
        accentPart.setVertexTransform(Matrix4().translate(0f, 0.47f, 0f))
        accentPart.box(0.17f, 0.03f, 0.23f)

        // Side accents
        val sideAccent1 = modelBuilder.part("side1", GL20.GL_TRIANGLES, attributes, accentMaterial)
        sideAccent1.setVertexTransform(Matrix4().translate(-0.085f, 0.28f, 0f))
        sideAccent1.box(0.02f, 0.25f, 0.18f)

        val sideAccent2 = modelBuilder.part("side2", GL20.GL_TRIANGLES, attributes, accentMaterial)
        sideAccent2.setVertexTransform(Matrix4().translate(0.085f, 0.28f, 0f))
        sideAccent2.box(0.02f, 0.25f, 0.18f)

        return modelBuilder.end()
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

        // Gradient background
        val bgTop = UITheme.backgroundLight
        val bgBottom = UITheme.background
        for (i in 0 until 20) {
            val t = i / 20f
            val stripY = sh * t
            val stripHeight = sh / 20f + 1
            ui.shapes.color = UITheme.lerp(bgBottom, bgTop, t)
            ui.shapes.rect(0f, stripY, sw, stripHeight)
        }

        // Main panel - extends to bottom of screen
        val panelWidth = 700f * scale * enterAnimProgress
        val panelTopY = sh - 60f * scale  // Top of panel with small margin
        val panelHeight = panelTopY * enterAnimProgress  // Extends to bottom
        val panelX = centerX - panelWidth / 2
        val panelY = 0f  // Start from bottom

        ui.panel(panelX, panelY, panelWidth, panelHeight,
            radius = UITheme.Dimensions.panelRadius,
            backgroundColor = UITheme.surface)

        // 3D Preview area background
        val previewSize = 280f * scale
        val previewX = centerX - previewSize / 2
        val previewY = panelY + panelHeight - 120f * scale - previewSize
        ui.roundedRect(previewX - 10f * scale, previewY - 10f * scale,
            previewSize + 20f * scale, previewSize + 20f * scale,
            16f * scale, UITheme.surfaceLight)

        // Arrow button dimensions
        val arrowButtonSize = UITheme.Dimensions.arrowButtonSize
        val valueBoxWidth = 320f * scale

        // Left arrow button
        leftButton.set(
            centerX - valueBoxWidth / 2 - arrowButtonSize - 20f * scale,
            previewY + previewSize / 2 - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(leftButton, UITheme.secondary, glowIntensity = leftButtonHover * 0.6f)

        // Right arrow button
        rightButton.set(
            centerX + valueBoxWidth / 2 + 20f * scale,
            previewY + previewSize / 2 - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(rightButton, UITheme.secondary, glowIntensity = rightButtonHover * 0.6f)

        // Stats section background - more space for wheel description above
        val statsY = previewY - 310f * scale
        val statsHeight = 150f * scale
        ui.roundedRect(panelX + 40f * scale, statsY, panelWidth - 80f * scale, statsHeight,
            12f * scale, UITheme.surfaceLight)

        // Stat bars
        val barWidth = 180f * scale
        val barHeight = 16f * scale
        val barStartX = centerX + 30f * scale
        val barSpacing = 45f * scale

        // Speed bar background
        val speedBarY = statsY + statsHeight - 50f * scale
        ui.roundedRect(barStartX, speedBarY, barWidth, barHeight, barHeight / 2, UITheme.surface)
        // Speed bar fill
        val speedNorm = currentWheel.maxSpeed / 27.8f
        val speedFillWidth = (barWidth * speedNorm).coerceAtLeast(barHeight)
        ui.roundedRect(barStartX, speedBarY, speedFillWidth, barHeight, barHeight / 2, UITheme.accent)

        // Stability bar background
        val stabilityBarY = speedBarY - barSpacing
        ui.roundedRect(barStartX, stabilityBarY, barWidth, barHeight, barHeight / 2, UITheme.surface)
        // Stability bar fill (inverse of pwmSensitivity, higher criticalLean = more stable)
        val stabilityNorm = ((currentWheel.criticalLean - 0.85f) / 0.25f).coerceIn(0f, 1f)
        val stabilityFillWidth = (barWidth * stabilityNorm).coerceAtLeast(barHeight)
        ui.roundedRect(barStartX, stabilityBarY, stabilityFillWidth, barHeight, barHeight / 2, UITheme.primary)

        // Handling bar background
        val handlingBarY = stabilityBarY - barSpacing
        ui.roundedRect(barStartX, handlingBarY, barWidth, barHeight, barHeight / 2, UITheme.surface)
        // Handling bar fill (turn responsiveness)
        val handlingNorm = ((currentWheel.turnResponsiveness - 2f) / 3f).coerceIn(0f, 1f)
        val handlingFillWidth = (barWidth * handlingNorm).coerceAtLeast(barHeight)
        ui.roundedRect(barStartX, handlingBarY, handlingFillWidth, barHeight, barHeight / 2, UITheme.secondary)

        // Start button - positioned lower
        val buttonWidth = 280f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeight
        startButton.set(centerX - buttonWidth / 2, statsY - 160f * scale, buttonWidth, buttonHeight)
        ui.button(startButton, UITheme.accent, glowIntensity = startButtonHover * 0.8f)

        // Back button - more space below START
        val backButtonWidth = 200f * scale
        val backButtonHeight = UITheme.Dimensions.buttonHeightSmall
        backButton.set(centerX - backButtonWidth / 2, startButton.y - backButtonHeight - 35f * scale, backButtonWidth, backButtonHeight)
        ui.button(backButton, UITheme.surfaceLight, glowIntensity = backButtonHover * 0.4f)

        ui.endShapes()

        // === Render 3D Preview ===
        render3DPreview(centerX, previewY, previewSize, currentWheel)

        // === Draw Text ===
        ui.beginBatch()

        // Title
        val titleY = panelY + panelHeight - 50f * scale
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

        // Wheel name - more space below preview
        val nameY = previewY - 45f * scale
        ui.textCentered(currentWheel.displayName, centerX, nameY, UIFonts.heading, UITheme.accent)

        // Wheel size - more space below name
        val sizeY = nameY - 45f * scale
        ui.textCentered("${currentWheel.wheelSizeInches}\" wheel", centerX, sizeY, UIFonts.body, UITheme.textSecondary)

        // Description - more space below size
        val descY = sizeY - 35f * scale
        ui.textCentered(currentWheel.description, centerX, descY, UIFonts.caption, UITheme.textMuted)

        // Stats labels
        val labelX = panelX + 60f * scale
        UIFonts.body.color = UITheme.textSecondary
        UIFonts.body.draw(ui.batch, "SPEED", labelX, speedBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)
        UIFonts.body.draw(ui.batch, "STABILITY", labelX, stabilityBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)
        UIFonts.body.draw(ui.batch, "HANDLING", labelX, handlingBarY + barHeight / 2 + UIFonts.body.lineHeight / 3)


        // Button labels
        ui.textCentered("START", startButton.x + startButton.width / 2,
            startButton.y + startButton.height / 2, UIFonts.button, UITheme.textPrimary)
        ui.textCentered("BACK", backButton.x + backButton.width / 2,
            backButton.y + backButton.height / 2, UIFonts.body, UITheme.textSecondary)

        // Wheel indicator dots
        val dotY = previewY - 10f * scale
        val dotSpacing = 20f * scale
        val dotRadius = 6f * scale
        for (i in wheels.indices) {
            val dotX = centerX + (i - 1) * dotSpacing
            val dotColor = if (i == currentIndex) UITheme.accent else UITheme.surfaceLight
            // Draw dots using text circles since we're in batch mode
        }

        ui.endBatch()

        // Draw indicator dots (need shapes for this)
        ui.beginShapes()
        val totalDotsWidth = (wheels.size - 1) * dotSpacing
        for (i in wheels.indices) {
            val dotX = centerX - totalDotsWidth / 2 + i * dotSpacing
            val dotColor = if (i == currentIndex) UITheme.accent else UITheme.surfaceBorder
            ui.shapes.color = dotColor
            ui.shapes.circle(dotX, dotY, dotRadius)
        }
        ui.endShapes()

        // === Handle Input ===
        return handleInput(touchX, touchY)
    }

    private fun render3DPreview(centerX: Float, y: Float, size: Float, wheel: WheelType) {
        // Save current viewport
        val prevViewportX = Gdx.graphics.backBufferWidth
        val prevViewportY = Gdx.graphics.backBufferHeight

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

        val modelInstance = wheelModelInstances[wheel.id] ?: return
        modelInstance.transform.idt()
        modelInstance.transform.rotate(0f, 1f, 0f, wheelRotation)

        previewBatch.begin(previewCamera)
        previewBatch.render(modelInstance, previewEnvironment)
        previewBatch.end()

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
        previewBatch.dispose()
        previewBatch = ModelBatch()
    }

    override fun dispose() {
        ui.dispose()
        previewBatch.dispose()
        wheelModels.forEach { it.dispose() }
        wheelModels.clear()
        wheelModelInstances.clear()
    }
}
