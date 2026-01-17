package com.eucleantoomuch.game

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.eucleantoomuch.game.ecs.EntityFactory
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.ArmComponent
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.ecs.components.PlayerComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.systems.*
import com.eucleantoomuch.game.input.AccelerometerInput
import com.eucleantoomuch.game.input.GameInput
import com.eucleantoomuch.game.input.KeyboardInput
import com.eucleantoomuch.game.procedural.WorldGenerator
import com.eucleantoomuch.game.rendering.GameRenderer
import com.eucleantoomuch.game.rendering.ProceduralModels
import com.eucleantoomuch.game.state.GameSession
import com.eucleantoomuch.game.state.GameState
import com.eucleantoomuch.game.state.GameStateManager
import com.eucleantoomuch.game.state.HighScoreManager
import com.eucleantoomuch.game.state.SettingsManager
import com.eucleantoomuch.game.ui.CalibrationRenderer
import com.eucleantoomuch.game.ui.GameOverRenderer
import com.eucleantoomuch.game.ui.Hud
import com.eucleantoomuch.game.ui.MenuRenderer
import com.eucleantoomuch.game.ui.SettingsRenderer

class EucGame : ApplicationAdapter() {
    private lateinit var engine: Engine
    private lateinit var gameInput: GameInput
    private lateinit var accelerometerInput: AccelerometerInput
    private lateinit var models: ProceduralModels
    private lateinit var renderer: GameRenderer
    private lateinit var worldGenerator: WorldGenerator
    private lateinit var entityFactory: EntityFactory
    private lateinit var stateManager: GameStateManager
    private lateinit var highScoreManager: HighScoreManager
    private lateinit var settingsManager: SettingsManager

    // UI Renderers
    private lateinit var hud: Hud
    private lateinit var menuRenderer: MenuRenderer
    private lateinit var gameOverRenderer: GameOverRenderer
    private lateinit var calibrationRenderer: CalibrationRenderer
    private lateinit var settingsRenderer: SettingsRenderer

    // Game state
    private var session = GameSession()
    private var playerEntity: Entity? = null
    private var riderEntity: Entity? = null
    private var leftArmEntity: Entity? = null
    private var rightArmEntity: Entity? = null
    private var countdownTimer = 3f
    private var isNewHighScore = false

    // Systems that need direct access
    private lateinit var eucPhysicsSystem: EucPhysicsSystem
    private lateinit var collisionSystem: CollisionSystem

    override fun create() {
        Gdx.app.logLevel = Application.LOG_DEBUG

        // Initialize state manager
        stateManager = GameStateManager()
        highScoreManager = HighScoreManager()
        settingsManager = SettingsManager()

        // Initialize input based on platform
        accelerometerInput = AccelerometerInput()
        gameInput = if (Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer)) {
            accelerometerInput
        } else {
            KeyboardInput()
        }

        // Load saved calibration if available
        if (highScoreManager.hasCalibration()) {
            accelerometerInput.setCalibration(
                highScoreManager.getCalibrationX(),
                highScoreManager.getCalibrationY()
            )
        }

        // Initialize ECS engine
        engine = Engine()

        // Initialize procedural models
        models = ProceduralModels()

        // Initialize renderer
        renderer = GameRenderer(engine, models)

        // Initialize entity factory
        entityFactory = EntityFactory(engine, models)

        // Initialize world generator
        worldGenerator = WorldGenerator(engine, models)

        // Add systems to engine
        eucPhysicsSystem = EucPhysicsSystem(gameInput)
        eucPhysicsSystem.onPlayerFall = { handlePlayerFall() }

        collisionSystem = CollisionSystem()
        collisionSystem.onCollision = { type, causesGameOver ->
            if (causesGameOver) {
                handlePlayerFall()
            }
        }

        engine.addSystem(eucPhysicsSystem)
        engine.addSystem(MovementSystem())
        engine.addSystem(PedestrianAISystem())
        engine.addSystem(CarAISystem())
        engine.addSystem(collisionSystem)
        engine.addSystem(CullingSystem())

        // Initialize UI
        hud = Hud()
        menuRenderer = MenuRenderer()
        gameOverRenderer = GameOverRenderer()
        calibrationRenderer = CalibrationRenderer()
        settingsRenderer = SettingsRenderer(settingsManager)

        // Apply saved render distance setting
        applyRenderDistance()

        // Start at menu
        stateManager.transition(GameState.Menu)
    }

    private fun applyRenderDistance() {
        val distance = settingsManager.renderDistance
        worldGenerator.setRenderDistance(distance)
        renderer.setCameraFar(distance)
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime

        // Update input
        gameInput.update(delta)

        when (val state = stateManager.current()) {
            is GameState.Loading -> renderLoading()
            is GameState.Menu -> renderMenu()
            is GameState.Settings -> renderSettings()
            is GameState.Calibrating -> renderCalibration()
            is GameState.Countdown -> renderCountdown(delta)
            is GameState.Playing -> renderPlaying(delta)
            is GameState.Paused -> renderPaused()
            is GameState.GameOver -> renderGameOver()
        }

        // Check for pause (Android back button)
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACK) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            when (stateManager.current()) {
                is GameState.Playing -> {
                    val playingState = stateManager.current() as GameState.Playing
                    stateManager.transition(GameState.Paused(playingState.session))
                }
                is GameState.Paused -> {
                    val pausedState = stateManager.current() as GameState.Paused
                    stateManager.transition(GameState.Playing(pausedState.session))
                }
                else -> {}
            }
        }
    }

    private fun renderLoading() {
        // Simple loading - transition to menu immediately since we have no assets to load
        stateManager.transition(GameState.Menu)
    }

    private fun renderMenu() {
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.4f, 1f)
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        when (menuRenderer.render(highScoreManager.highScore, highScoreManager.maxDistance)) {
            MenuRenderer.ButtonClicked.PLAY -> {
                if (gameInput.isCalibrated()) {
                    startGame()
                } else {
                    stateManager.transition(GameState.Calibrating)
                }
            }
            MenuRenderer.ButtonClicked.CALIBRATE -> {
                stateManager.transition(GameState.Calibrating)
            }
            MenuRenderer.ButtonClicked.SETTINGS -> {
                stateManager.transition(GameState.Settings)
            }
            MenuRenderer.ButtonClicked.EXIT -> {
                Gdx.app.exit()
            }
            MenuRenderer.ButtonClicked.NONE -> {}
        }
    }

    private fun renderSettings() {
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.4f, 1f)
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        when (settingsRenderer.render()) {
            SettingsRenderer.Action.BACK -> {
                // Apply render distance to world generator and camera
                applyRenderDistance()
                stateManager.transition(GameState.Menu)
            }
            SettingsRenderer.Action.NONE -> {}
        }
    }

    private fun renderCalibration() {
        accelerometerInput.update(Gdx.graphics.deltaTime)
        val (rawX, rawY) = accelerometerInput.getRawValues()

        when (calibrationRenderer.render(rawX, rawY)) {
            CalibrationRenderer.Action.CALIBRATE -> {
                accelerometerInput.calibrate()
                highScoreManager.saveCalibration(
                    accelerometerInput.getCalibrationX(),
                    accelerometerInput.getCalibrationY()
                )
                startGame()
            }
            CalibrationRenderer.Action.SKIP -> {
                // Use default calibration (current position)
                if (!accelerometerInput.isCalibrated()) {
                    accelerometerInput.calibrate()
                }
                startGame()
            }
            CalibrationRenderer.Action.NONE -> {}
        }
    }

    private fun renderCountdown(delta: Float) {
        // Render the game world in background
        updateGameWorld(delta, processInput = false)

        // Position rider standing next to EUC during countdown (waiting to mount)
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java)
        if (playerTransform != null) {
            riderEntity?.getComponent(TransformComponent::class.java)?.let { riderTransform ->
                riderTransform.position.set(playerTransform.position)
                riderTransform.position.x += 0.7f  // Standing to the left of EUC (positive X is left from camera view)
                riderTransform.position.z -= 0.8f  // Closer to camera (behind EUC)
                riderTransform.position.y += 0f    // Standing on ground level
                riderTransform.yaw = playerTransform.yaw
                riderTransform.updateRotationFromYaw()
            }

            // Position arms with rider during countdown
            updateArmPositionForCountdown(leftArmEntity, playerTransform, isLeft = true)
            updateArmPositionForCountdown(rightArmEntity, playerTransform, isLeft = false)

            // Update camera
            renderer.cameraController.update(playerTransform.position, playerTransform.yaw, delta)
        }

        renderer.render()

        // Render countdown overlay
        hud.renderCountdown(countdownTimer.toInt() + 1)

        countdownTimer -= delta
        if (countdownTimer <= 0) {
            stateManager.transition(GameState.Playing(session))
        }
    }

    private fun updateArmPositionForCountdown(armEntity: Entity?, playerTransform: TransformComponent, isLeft: Boolean) {
        armEntity ?: return

        val armTransform = armEntity.getComponent(TransformComponent::class.java) ?: return
        val armComponent = armEntity.getComponent(ArmComponent::class.java) ?: return

        // Arms down by sides during countdown
        armComponent.armAngle = 0f
        armComponent.waveOffset = 0f

        // Position arm at shoulder level relative to rider (who is standing next to EUC)
        val shoulderOffsetX = if (isLeft) -0.25f else 0.25f
        val riderOffsetX = 0.7f  // Same as rider offset (positive X = left from camera view)
        val riderOffsetZ = -0.8f  // Same as rider offset

        armTransform.position.set(playerTransform.position)
        armTransform.position.x += riderOffsetX + shoulderOffsetX
        armTransform.position.z += riderOffsetZ
        armTransform.position.y += 1.75f  // Shoulder height on ground-standing rider
        armTransform.yaw = playerTransform.yaw
        armTransform.updateRotationFromYaw()
    }

    private fun renderPlaying(delta: Float) {
        // Update game
        updateGameWorld(delta, processInput = true)

        // Update session
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java)
        val eucComponent = playerEntity?.getComponent(EucComponent::class.java)

        if (playerTransform != null && eucComponent != null) {
            session.update(delta, eucComponent.speed)

            // Sync rider position with player
            riderEntity?.getComponent(TransformComponent::class.java)?.let { riderTransform ->
                riderTransform.position.set(playerTransform.position)
                riderTransform.position.y += 0.7f  // Stand on top of EUC
                riderTransform.yaw = playerTransform.yaw
                riderTransform.updateRotationFromYaw()
            }
            riderEntity?.getComponent(EucComponent::class.java)?.let { riderEuc ->
                riderEuc.forwardLean = eucComponent.forwardLean
                riderEuc.sideLean = eucComponent.sideLean
                riderEuc.visualForwardLean = eucComponent.visualForwardLean
                riderEuc.visualSideLean = eucComponent.visualSideLean
            }

            // Update arms position and angle based on speed
            // Speed is in m/s: 15 km/h = 4.17 m/s, 40 km/h = 11.1 m/s
            val speed = eucComponent.speed
            val targetArmAngle = when {
                speed <= 4.2f -> 80f   // Arms spread at low speed (<=15 km/h) - balancing
                speed >= 11.1f -> -30f // Arms behind back at high speed (>=40 km/h) - flying pose
                else -> 0f             // Arms down at medium speed (15-40 km/h)
            }

            updateArm(leftArmEntity, playerTransform, eucComponent, targetArmAngle, isLeft = true, delta)
            updateArm(rightArmEntity, playerTransform, eucComponent, targetArmAngle, isLeft = false, delta)

            // Update world generation
            worldGenerator.update(playerTransform.position.z, session.distanceTraveled)

            // Update camera
            renderer.cameraController.update(playerTransform.position, playerTransform.yaw, delta)
        }

        // Render
        renderer.render()

        // Render HUD
        if (eucComponent != null) {
            hud.render(session, eucComponent)
        }
    }

    private fun renderPaused() {
        // Render frozen game state
        renderer.render()

        // TODO: Add pause overlay
    }

    private fun renderGameOver() {
        val state = stateManager.current() as GameState.GameOver

        // Render frozen game state
        renderer.render()

        // Render game over UI
        when (gameOverRenderer.render(state.session, isNewHighScore)) {
            GameOverRenderer.ButtonClicked.RETRY -> {
                resetGame()
                startGame()
            }
            GameOverRenderer.ButtonClicked.MENU -> {
                resetGame()
                stateManager.transition(GameState.Menu)
            }
            GameOverRenderer.ButtonClicked.NONE -> {}
        }
    }

    private fun updateGameWorld(delta: Float, processInput: Boolean) {
        if (processInput) {
            engine.update(delta)
        }
    }

    private fun updateArm(
        armEntity: Entity?,
        playerTransform: TransformComponent,
        eucComponent: EucComponent,
        targetAngle: Float,
        isLeft: Boolean,
        delta: Float
    ) {
        armEntity ?: return

        val armTransform = armEntity.getComponent(TransformComponent::class.java) ?: return
        val armComponent = armEntity.getComponent(ArmComponent::class.java) ?: return
        val armEuc = armEntity.getComponent(EucComponent::class.java) ?: return

        // Smoothly interpolate arm angle
        armComponent.targetArmAngle = targetAngle
        val lerpSpeed = 5f
        armComponent.armAngle += (targetAngle - armComponent.armAngle) * lerpSpeed * delta

        // Waving animation at low speed (when arms are spread)
        if (eucComponent.speed <= 4.2f) {
            // Increment wave timer with different phase for each arm
            val waveSpeed = 3f + eucComponent.speed * 0.5f  // Faster waving at higher speeds within range
            armComponent.waveTime += delta * waveSpeed

            // Calculate wave offset using sin, with opposite phase for left/right arm
            val phase = if (isLeft) 0f else Math.PI.toFloat()
            armComponent.waveOffset = kotlin.math.sin(armComponent.waveTime + phase) * 15f  // ±15 degrees wave
        } else {
            // Reset waving when moving fast
            armComponent.waveOffset = armComponent.waveOffset * 0.9f  // Smooth fade out
            if (kotlin.math.abs(armComponent.waveOffset) < 0.1f) {
                armComponent.waveOffset = 0f
                armComponent.waveTime = 0f
            }
        }

        // Position arm at shoulder level relative to rider (scaled 1.4x)
        val shoulderOffsetX = if (isLeft) -0.25f else 0.25f
        val shoulderOffsetY = 0.7f + 1.75f  // rider offset + shoulder height on scaled rider

        armTransform.position.set(playerTransform.position)
        armTransform.position.y += shoulderOffsetY
        armTransform.position.x += shoulderOffsetX
        armTransform.yaw = playerTransform.yaw
        armTransform.updateRotationFromYaw()

        // Copy lean values
        armEuc.forwardLean = eucComponent.forwardLean
        armEuc.sideLean = eucComponent.sideLean
        armEuc.visualForwardLean = eucComponent.visualForwardLean
        armEuc.visualSideLean = eucComponent.visualSideLean
    }

    private fun startGame() {
        resetGame()

        // Create player
        playerEntity = entityFactory.createPlayer()
        riderEntity = entityFactory.createRiderModel()
        leftArmEntity = entityFactory.createArm(isLeft = true)
        rightArmEntity = entityFactory.createArm(isLeft = false)

        // Initialize camera
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java)
        if (playerTransform != null) {
            renderer.cameraController.initialize(playerTransform.position)
        }

        // Generate initial world
        worldGenerator.update(0f, 0f)

        // Start countdown
        countdownTimer = 3f
        session.reset()
        isNewHighScore = false
        stateManager.transition(GameState.Countdown(3))
    }

    private fun resetGame() {
        // Remove rider and arm entities explicitly (they don't have PlayerComponent)
        riderEntity?.let { engine.removeEntity(it) }
        leftArmEntity?.let { engine.removeEntity(it) }
        rightArmEntity?.let { engine.removeEntity(it) }

        // Remove all entities
        for (entity in engine.getEntitiesFor(Families.player)) {
            engine.removeEntity(entity)
        }
        for (entity in engine.getEntitiesFor(Families.obstacles)) {
            engine.removeEntity(entity)
        }
        for (entity in engine.getEntitiesFor(Families.ground)) {
            engine.removeEntity(entity)
        }

        worldGenerator.reset()
        playerEntity = null
        riderEntity = null
        leftArmEntity = null
        rightArmEntity = null
    }

    private fun handlePlayerFall() {
        // Record score
        isNewHighScore = highScoreManager.recordGame(session)

        // Reset game over animations
        gameOverRenderer.reset()

        // Transition to game over
        stateManager.transition(GameState.GameOver(session))
    }

    override fun resize(width: Int, height: Int) {
        renderer.resize(width, height)
        hud.resize(width, height)
        menuRenderer.resize(width, height)
        gameOverRenderer.resize(width, height)
        calibrationRenderer.resize(width, height)
        settingsRenderer.resize(width, height)
    }

    override fun dispose() {
        renderer.dispose()
        models.dispose()
        hud.dispose()
        menuRenderer.dispose()
        gameOverRenderer.dispose()
        calibrationRenderer.dispose()
        settingsRenderer.dispose()
    }
}
