package com.eucleantoomuch.game.ui

import com.badlogic.ashley.core.Engine
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.ColliderComponent
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.ecs.components.ObstacleComponent
import com.eucleantoomuch.game.ecs.components.PedestrianComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.badlogic.ashley.core.ComponentMapper

/**
 * Admin debug menu for development and testing.
 *
 * =====================================================
 * DEBUG MENU ENABLED FLAG - SET TO false FOR RELEASE!
 * =====================================================
 */
object DebugConfig {
    /**
     * Master flag to enable/disable the debug menu.
     * Set to FALSE before release builds!
     */
    const val DEBUG_MENU_ENABLED = true

    /**
     * Keyboard shortcut to toggle debug menu (F3 on desktop)
     */
    const val TOGGLE_KEY = Input.Keys.F3
}

/**
 * Debug menu overlay with various debugging tools.
 * Only renders when DEBUG_MENU_ENABLED is true and menu is open.
 */
class DebugMenu(private val engine: Engine) : Disposable {
    private val ui = UIRenderer()

    // Menu state
    private var isOpen = false
    private var selectedOption = 0
    private var touchCooldown = 0f

    // Triple-finger touch detection
    private var wasThreeFingerTouch = false

    // Debug options state
    var showColliders = false
        private set
    var showEntityInfo = false
        private set
    var showPhysicsDebug = false
        private set
    var showPerformanceStats = false
        private set
    var showWorldGenDebug = false
        private set
    var godMode = false
        private set
    var slowMotion = false
        private set
    var showInputDebug = false
        private set
    var showCameraDebug = false
        private set
    var freezeAI = false
        private set

    // Time scale for slow motion
    var timeScale = 1f
        private set

    // Debug stats
    private var entityCount = 0
    private var visibleEntityCount = 0
    private var colliderCount = 0

    // Component mappers
    private val transformMapper = ComponentMapper.getFor(TransformComponent::class.java)
    private val colliderMapper = ComponentMapper.getFor(ColliderComponent::class.java)
    private val obstacleMapper = ComponentMapper.getFor(ObstacleComponent::class.java)
    private val eucMapper = ComponentMapper.getFor(EucComponent::class.java)
    private val pedestrianMapper = ComponentMapper.getFor(PedestrianComponent::class.java)

    // Menu options
    private val options = listOf(
        DebugOption("Show Colliders", "Visualize all collision boxes") { showColliders = !showColliders; showColliders },
        DebugOption("Show Entity Info", "Display entity data overlay") { showEntityInfo = !showEntityInfo; showEntityInfo },
        DebugOption("Show Physics", "Visualize physics debug info") { showPhysicsDebug = !showPhysicsDebug; showPhysicsDebug },
        DebugOption("Performance Stats", "Show detailed FPS/memory info") { showPerformanceStats = !showPerformanceStats; showPerformanceStats },
        DebugOption("World Gen Debug", "Show chunk boundaries and spawn points") { showWorldGenDebug = !showWorldGenDebug; showWorldGenDebug },
        DebugOption("God Mode", "Disable player death") { godMode = !godMode; godMode },
        DebugOption("Slow Motion (0.25x)", "Reduce game speed") {
            slowMotion = !slowMotion
            timeScale = if (slowMotion) 0.25f else 1f
            slowMotion
        },
        DebugOption("Input Debug", "Show input values and calibration") { showInputDebug = !showInputDebug; showInputDebug },
        DebugOption("Camera Debug", "Show camera position and settings") { showCameraDebug = !showCameraDebug; showCameraDebug },
        DebugOption("Freeze AI", "Stop all AI movement") { freezeAI = !freezeAI; freezeAI }
    )

    // Button rectangles for touch input
    private val buttonRects = mutableListOf<Rectangle>()
    private var closeButtonRect = Rectangle()

    data class DebugOption(
        val name: String,
        val description: String,
        val toggle: () -> Boolean
    )

    /**
     * Check for toggle key press and update menu state.
     * Call this every frame.
     */
    fun update(delta: Float) {
        if (!DebugConfig.DEBUG_MENU_ENABLED) return

        touchCooldown -= delta

        // Toggle menu with F3 key (desktop)
        if (Gdx.input.isKeyJustPressed(DebugConfig.TOGGLE_KEY)) {
            isOpen = !isOpen
        }

        // Toggle with 4+ finger touch (mobile) - touch with 4 fingers, release to toggle
        val hasFourOrMoreFingers = Gdx.input.isTouched(3)  // If finger index 3 exists, we have at least 4 fingers

        if (hasFourOrMoreFingers) {
            wasThreeFingerTouch = true
        } else if (wasThreeFingerTouch && touchCooldown <= 0f) {
            // Fingers released after 4-finger touch - toggle menu
            isOpen = !isOpen
            touchCooldown = 0.8f  // Prevent rapid toggling
            wasThreeFingerTouch = false
            Gdx.app.log("DebugMenu", "Toggled by 4-finger touch, isOpen=$isOpen")
        }

        // Update entity counts
        if (isOpen || showEntityInfo) {
            entityCount = 0
            visibleEntityCount = 0
            colliderCount = 0

            for (entity in engine.getEntitiesFor(Families.renderable)) {
                entityCount++
                val model = entity.getComponent(com.eucleantoomuch.game.ecs.components.ModelComponent::class.java)
                if (model?.visible == true) visibleEntityCount++
            }

            for (entity in engine.getEntitiesFor(Families.collidable)) {
                colliderCount++
            }
        }

        // Handle keyboard navigation
        if (isOpen) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
                selectedOption = (selectedOption - 1 + options.size) % options.size
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
                selectedOption = (selectedOption + 1) % options.size
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                options[selectedOption].toggle()
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                isOpen = false
            }
        }
    }

    /**
     * Render the debug menu overlay.
     * Call this after game rendering but before UI.
     */
    fun render() {
        if (!DebugConfig.DEBUG_MENU_ENABLED) return
        if (!isOpen) return

        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val scale = UITheme.Dimensions.scale()

        // Semi-transparent background
        ui.beginShapes()
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.85f)
        ui.shapes.rect(0f, 0f, sw, sh)

        // Menu panel
        val panelWidth = 500f * scale
        val panelHeight = 650f * scale
        val panelX = sw / 2 - panelWidth / 2
        val panelY = sh / 2 - panelHeight / 2

        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface,
            borderColor = UITheme.accent)

        ui.endShapes()

        // Title
        ui.beginBatch()
        ui.textCentered("DEBUG MENU", sw / 2, panelY + panelHeight - 40f * scale,
            UIFonts.title, UITheme.accent)
        ui.textCentered("Development Tools - DO NOT SHIP!", sw / 2, panelY + panelHeight - 70f * scale,
            UIFonts.caption, UITheme.danger)
        ui.endBatch()

        // Options
        buttonRects.clear()
        val optionStartY = panelY + panelHeight - 120f * scale
        val optionHeight = 50f * scale
        val optionSpacing = 8f * scale

        ui.beginShapes()
        for ((index, _) in options.withIndex()) {
            val optionY = optionStartY - index * (optionHeight + optionSpacing)
            val isSelected = index == selectedOption
            val isEnabled = getOptionState(index)

            val buttonRect = Rectangle(
                panelX + 20f * scale,
                optionY - optionHeight,
                panelWidth - 40f * scale,
                optionHeight
            )
            buttonRects.add(buttonRect)

            // Option background
            val bgColor = when {
                isSelected -> UITheme.withAlpha(UITheme.accent, 0.3f)
                isEnabled -> UITheme.withAlpha(UITheme.primary, 0.2f)
                else -> UITheme.withAlpha(UITheme.surfaceLight, 0.5f)
            }
            ui.roundedRect(buttonRect.x, buttonRect.y, buttonRect.width, buttonRect.height,
                10f * scale, bgColor)

            // Status indicator
            val indicatorColor = if (isEnabled) UITheme.accent else UITheme.textMuted
            ui.shapes.color = indicatorColor
            ui.shapes.circle(buttonRect.x + 25f * scale, buttonRect.y + buttonRect.height / 2, 8f * scale)
        }

        // Close button
        closeButtonRect = Rectangle(
            panelX + panelWidth - 60f * scale,
            panelY + panelHeight - 50f * scale,
            40f * scale,
            40f * scale
        )
        ui.roundedRect(closeButtonRect.x, closeButtonRect.y, closeButtonRect.width, closeButtonRect.height,
            8f * scale, UITheme.withAlpha(UITheme.danger, 0.8f))

        ui.endShapes()

        // Option text
        ui.beginBatch()
        for ((index, option) in options.withIndex()) {
            val optionY = optionStartY - index * (optionHeight + optionSpacing)
            val isEnabled = getOptionState(index)
            val textColor = if (isEnabled) UITheme.textPrimary else UITheme.textSecondary

            UIFonts.body.color = textColor
            UIFonts.body.draw(ui.batch, option.name,
                panelX + 50f * scale, optionY - 12f * scale)

            // Status text
            val statusText = if (isEnabled) "ON" else "OFF"
            val statusColor = if (isEnabled) UITheme.accent else UITheme.textMuted
            UIFonts.caption.color = statusColor
            ui.layout.setText(UIFonts.caption, statusText)
            UIFonts.caption.draw(ui.batch, statusText,
                panelX + panelWidth - 60f * scale - ui.layout.width,
                optionY - 15f * scale)
        }

        // Close button X
        ui.textCentered("X", closeButtonRect.x + closeButtonRect.width / 2,
            closeButtonRect.y + closeButtonRect.height / 2, UIFonts.heading, Color.WHITE)

        // Instructions
        UIFonts.caption.color = UITheme.textMuted
        UIFonts.caption.draw(ui.batch, "F3: Toggle menu | Enter: Select | Esc: Close",
            panelX + 20f * scale, panelY + 30f * scale)
        UIFonts.caption.draw(ui.batch, "Touch with 4 fingers to toggle on mobile",
            panelX + 20f * scale, panelY + 50f * scale)

        ui.endBatch()

        // Handle touch input
        handleTouchInput()
    }

    private fun handleTouchInput() {
        if (touchCooldown > 0f) return
        if (!Gdx.input.justTouched()) return

        val touchX = Gdx.input.x.toFloat()
        val touchY = ui.screenHeight - Gdx.input.y.toFloat()  // Flip Y

        // Check close button
        if (closeButtonRect.contains(touchX, touchY)) {
            isOpen = false
            touchCooldown = 0.3f
            return
        }

        // Check option buttons
        for ((index, rect) in buttonRects.withIndex()) {
            if (rect.contains(touchX, touchY)) {
                selectedOption = index
                options[index].toggle()
                touchCooldown = 0.3f
                return
            }
        }
    }

    private fun getOptionState(index: Int): Boolean {
        return when (index) {
            0 -> showColliders
            1 -> showEntityInfo
            2 -> showPhysicsDebug
            3 -> showPerformanceStats
            4 -> showWorldGenDebug
            5 -> godMode
            6 -> slowMotion
            7 -> showInputDebug
            8 -> showCameraDebug
            9 -> freezeAI
            else -> false
        }
    }

    /**
     * Render debug overlays on top of the game.
     * Call this after game UI but before debug menu.
     */
    fun renderOverlays(
        playerEntity: com.badlogic.ashley.core.Entity?,
        inputData: com.eucleantoomuch.game.input.InputData?,
        cameraPosition: Vector3?,
        cameraYaw: Float = 0f
    ) {
        if (!DebugConfig.DEBUG_MENU_ENABLED) return

        UIFonts.initialize()

        val scale = UITheme.Dimensions.scale()
        val lineHeight = 22f * scale
        var yOffset = ui.screenHeight - 50f * scale

        ui.beginBatch()

        // Performance stats
        if (showPerformanceStats) {
            val fps = Gdx.graphics.framesPerSecond
            val heap = Gdx.app.javaHeap / 1024 / 1024
            val native = Gdx.app.nativeHeap / 1024 / 1024

            UIFonts.caption.color = UITheme.accent
            UIFonts.caption.draw(ui.batch, "=== PERFORMANCE ===", 10f * scale, yOffset)
            yOffset -= lineHeight

            val fpsColor = when {
                fps >= 55 -> UITheme.accent
                fps >= 30 -> UITheme.warning
                else -> UITheme.danger
            }
            UIFonts.caption.color = fpsColor
            UIFonts.caption.draw(ui.batch, "FPS: $fps", 10f * scale, yOffset)
            yOffset -= lineHeight

            UIFonts.caption.color = UITheme.textPrimary
            UIFonts.caption.draw(ui.batch, "Heap: ${heap}MB | Native: ${native}MB", 10f * scale, yOffset)
            yOffset -= lineHeight
            UIFonts.caption.draw(ui.batch, "Entities: $entityCount (visible: $visibleEntityCount)", 10f * scale, yOffset)
            yOffset -= lineHeight
            UIFonts.caption.draw(ui.batch, "Colliders: $colliderCount", 10f * scale, yOffset)
            yOffset -= lineHeight * 1.5f
        }

        // Entity info
        if (showEntityInfo && playerEntity != null) {
            val transform = transformMapper.get(playerEntity)
            val euc = eucMapper.get(playerEntity)

            UIFonts.caption.color = UITheme.accent
            UIFonts.caption.draw(ui.batch, "=== PLAYER ===", 10f * scale, yOffset)
            yOffset -= lineHeight

            if (transform != null) {
                UIFonts.caption.color = UITheme.textPrimary
                UIFonts.caption.draw(ui.batch,
                    "Pos: (%.1f, %.1f, %.1f)".format(transform.position.x, transform.position.y, transform.position.z),
                    10f * scale, yOffset)
                yOffset -= lineHeight
                UIFonts.caption.draw(ui.batch, "Yaw: %.1f°".format(transform.yaw), 10f * scale, yOffset)
                yOffset -= lineHeight
            }

            if (euc != null) {
                val speedKmh = euc.speed * 3.6f
                val pwmPercent = (euc.pwm * 100).toInt()
                UIFonts.caption.draw(ui.batch, "Speed: %.1f km/h".format(speedKmh), 10f * scale, yOffset)
                yOffset -= lineHeight
                UIFonts.caption.draw(ui.batch, "PWM: $pwmPercent%", 10f * scale, yOffset)
                yOffset -= lineHeight
                UIFonts.caption.draw(ui.batch,
                    "Lean: F=%.2f S=%.2f".format(euc.forwardLean, euc.sideLean),
                    10f * scale, yOffset)
                yOffset -= lineHeight
                UIFonts.caption.draw(ui.batch,
                    "Visual: F=%.2f S=%.2f".format(euc.visualForwardLean, euc.visualSideLean),
                    10f * scale, yOffset)
                yOffset -= lineHeight

                if (euc.isWobbling) {
                    UIFonts.caption.color = UITheme.warning
                    UIFonts.caption.draw(ui.batch,
                        "WOBBLING: %.1f (%.1fs)".format(euc.wobbleIntensity, euc.wobbleTimer),
                        10f * scale, yOffset)
                    yOffset -= lineHeight
                }
                if (euc.inPuddle) {
                    UIFonts.caption.color = UITheme.cyan
                    UIFonts.caption.draw(ui.batch, "IN PUDDLE", 10f * scale, yOffset)
                    yOffset -= lineHeight
                }
            }
            yOffset -= lineHeight * 0.5f
        }

        // Input debug
        if (showInputDebug && inputData != null) {
            UIFonts.caption.color = UITheme.accent
            UIFonts.caption.draw(ui.batch, "=== INPUT ===", 10f * scale, yOffset)
            yOffset -= lineHeight

            UIFonts.caption.color = UITheme.textPrimary
            UIFonts.caption.draw(ui.batch,
                "Forward: %.3f".format(inputData.forward),
                10f * scale, yOffset)
            yOffset -= lineHeight
            UIFonts.caption.draw(ui.batch,
                "Side: %.3f".format(inputData.side),
                10f * scale, yOffset)
            yOffset -= lineHeight * 1.5f
        }

        // Camera debug
        if (showCameraDebug && cameraPosition != null) {
            UIFonts.caption.color = UITheme.accent
            UIFonts.caption.draw(ui.batch, "=== CAMERA ===", 10f * scale, yOffset)
            yOffset -= lineHeight

            UIFonts.caption.color = UITheme.textPrimary
            UIFonts.caption.draw(ui.batch,
                "Pos: (%.1f, %.1f, %.1f)".format(cameraPosition.x, cameraPosition.y, cameraPosition.z),
                10f * scale, yOffset)
            yOffset -= lineHeight
            UIFonts.caption.draw(ui.batch, "Yaw: %.1f°".format(cameraYaw), 10f * scale, yOffset)
            yOffset -= lineHeight * 1.5f
        }

        // God mode indicator
        if (godMode) {
            UIFonts.caption.color = UITheme.accent
            UIFonts.caption.draw(ui.batch, "[GOD MODE]",
                ui.screenWidth - 120f * scale, ui.screenHeight - 30f * scale)
        }

        // Slow motion indicator
        if (slowMotion) {
            UIFonts.caption.color = UITheme.warning
            UIFonts.caption.draw(ui.batch, "[SLOW-MO 0.25x]",
                ui.screenWidth - 150f * scale, ui.screenHeight - 50f * scale)
        }

        // Freeze AI indicator
        if (freezeAI) {
            UIFonts.caption.color = UITheme.cyan
            UIFonts.caption.draw(ui.batch, "[AI FROZEN]",
                ui.screenWidth - 120f * scale, ui.screenHeight - 70f * scale)
        }

        ui.endBatch()
    }

    /**
     * Render 3D debug visualizations.
     * Call this during the 3D render pass.
     */
    fun render3DDebug(
        shapeRenderer: ShapeRenderer,
        camera: com.badlogic.gdx.graphics.Camera,
        playerPosition: Vector3?
    ) {
        if (!DebugConfig.DEBUG_MENU_ENABLED) return
        if (!showColliders && !showWorldGenDebug && !showPhysicsDebug) return

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)  // Draw on top

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        if (showColliders) {
            renderColliders(shapeRenderer, playerPosition)
        }

        if (showWorldGenDebug && playerPosition != null) {
            renderWorldGenDebug(shapeRenderer, playerPosition)
        }

        shapeRenderer.end()

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    private fun renderColliders(shapeRenderer: ShapeRenderer, playerPosition: Vector3?) {
        val viewDistance = 50f

        // Render player collider
        for (entity in engine.getEntitiesFor(Families.player)) {
            val collider = colliderMapper.get(entity) ?: continue
            val transform = transformMapper.get(entity) ?: continue
            collider.updateBounds(transform.position)

            shapeRenderer.color = Color.GREEN
            drawBoundingBox(shapeRenderer, collider)
        }

        // Render obstacle colliders
        for (entity in engine.getEntitiesFor(Families.obstacles)) {
            val collider = colliderMapper.get(entity) ?: continue
            val transform = transformMapper.get(entity) ?: continue
            val obstacle = obstacleMapper.get(entity)

            // Skip if too far from player
            if (playerPosition != null) {
                val dist = transform.position.dst(playerPosition)
                if (dist > viewDistance) continue
            }

            collider.updateBounds(transform.position)

            // Color by type
            shapeRenderer.color = when (obstacle?.type) {
                com.eucleantoomuch.game.ecs.components.ObstacleType.PEDESTRIAN -> Color.YELLOW
                com.eucleantoomuch.game.ecs.components.ObstacleType.CAR -> Color.RED
                com.eucleantoomuch.game.ecs.components.ObstacleType.PUDDLE -> Color.CYAN
                com.eucleantoomuch.game.ecs.components.ObstacleType.MANHOLE -> Color.ORANGE
                com.eucleantoomuch.game.ecs.components.ObstacleType.POTHOLE -> Color.BROWN
                com.eucleantoomuch.game.ecs.components.ObstacleType.STREET_LIGHT -> Color.GRAY
                com.eucleantoomuch.game.ecs.components.ObstacleType.RECYCLE_BIN -> Color.LIME
                com.eucleantoomuch.game.ecs.components.ObstacleType.CURB -> Color.MAGENTA
                else -> Color.WHITE
            }

            drawBoundingBox(shapeRenderer, collider)
        }

        // Render other collidables (buildings, etc.)
        for (entity in engine.getEntitiesFor(Families.collidable)) {
            val collider = colliderMapper.get(entity) ?: continue
            val transform = transformMapper.get(entity) ?: continue

            // Skip players and obstacles (already drawn)
            if (engine.getEntitiesFor(Families.player).contains(entity, true)) continue
            if (engine.getEntitiesFor(Families.obstacles).contains(entity, true)) continue

            // Skip if too far
            if (playerPosition != null) {
                val dist = transform.position.dst(playerPosition)
                if (dist > viewDistance) continue
            }

            collider.updateBounds(transform.position)
            shapeRenderer.color = UITheme.withAlpha(Color.BLUE, 0.5f)
            drawBoundingBox(shapeRenderer, collider)
        }
    }

    private fun drawBoundingBox(shapeRenderer: ShapeRenderer, collider: ColliderComponent) {
        val min = collider.bounds.min
        val max = collider.bounds.max

        // Bottom face
        shapeRenderer.line(min.x, min.y, min.z, max.x, min.y, min.z)
        shapeRenderer.line(max.x, min.y, min.z, max.x, min.y, max.z)
        shapeRenderer.line(max.x, min.y, max.z, min.x, min.y, max.z)
        shapeRenderer.line(min.x, min.y, max.z, min.x, min.y, min.z)

        // Top face
        shapeRenderer.line(min.x, max.y, min.z, max.x, max.y, min.z)
        shapeRenderer.line(max.x, max.y, min.z, max.x, max.y, max.z)
        shapeRenderer.line(max.x, max.y, max.z, min.x, max.y, max.z)
        shapeRenderer.line(min.x, max.y, max.z, min.x, max.y, min.z)

        // Vertical edges
        shapeRenderer.line(min.x, min.y, min.z, min.x, max.y, min.z)
        shapeRenderer.line(max.x, min.y, min.z, max.x, max.y, min.z)
        shapeRenderer.line(max.x, min.y, max.z, max.x, max.y, max.z)
        shapeRenderer.line(min.x, min.y, max.z, min.x, max.y, max.z)
    }

    private fun renderWorldGenDebug(shapeRenderer: ShapeRenderer, playerPosition: Vector3) {
        // Draw chunk grid
        shapeRenderer.color = UITheme.withAlpha(Color.PURPLE, 0.5f)

        val chunkSize = 50f  // Approximate chunk size
        val viewChunks = 3
        val playerChunkZ = (playerPosition.z / chunkSize).toInt()

        for (i in -viewChunks..viewChunks) {
            val chunkZ = (playerChunkZ + i) * chunkSize

            // Draw chunk boundary line
            shapeRenderer.line(-20f, 0.1f, chunkZ, 20f, 0.1f, chunkZ)
        }

        // Draw road boundaries
        shapeRenderer.color = UITheme.withAlpha(Color.WHITE, 0.3f)
        val roadWidth = 8f
        shapeRenderer.line(-roadWidth / 2, 0.1f, playerPosition.z - 100f, -roadWidth / 2, 0.1f, playerPosition.z + 100f)
        shapeRenderer.line(roadWidth / 2, 0.1f, playerPosition.z - 100f, roadWidth / 2, 0.1f, playerPosition.z + 100f)
    }

    fun isMenuOpen(): Boolean = isOpen

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
    }

    fun recreate() {
        ui.recreate()
    }

    override fun dispose() {
        ui.dispose()
    }
}
