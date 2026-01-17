package com.eucleantoomuch.game

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.eucleantoomuch.game.ecs.EntityFactory
import com.eucleantoomuch.game.ecs.Families
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
import com.eucleantoomuch.game.ui.CalibrationRenderer
import com.eucleantoomuch.game.ui.GameOverRenderer
import com.eucleantoomuch.game.ui.Hud
import com.eucleantoomuch.game.ui.MenuRenderer

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

    // UI Renderers
    private lateinit var hud: Hud
    private lateinit var menuRenderer: MenuRenderer
    private lateinit var gameOverRenderer: GameOverRenderer
    private lateinit var calibrationRenderer: CalibrationRenderer

    // Game state
    private var session = GameSession()
    private var playerEntity: Entity? = null
    private var riderEntity: Entity? = null
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
        renderer = GameRenderer(engine)

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

        // Start at menu
        stateManager.transition(GameState.Menu)
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime

        // Update input
        gameInput.update(delta)

        when (val state = stateManager.current()) {
            is GameState.Loading -> renderLoading()
            is GameState.Menu -> renderMenu()
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
            MenuRenderer.ButtonClicked.NONE -> {}
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
        renderer.render()

        // Render countdown overlay
        hud.renderCountdown(countdownTimer.toInt() + 1)

        countdownTimer -= delta
        if (countdownTimer <= 0) {
            stateManager.transition(GameState.Playing(session))
        }
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
                riderTransform.position.y += 0.2f
                riderTransform.yaw = playerTransform.yaw
                riderTransform.updateRotationFromYaw()
            }
            riderEntity?.getComponent(EucComponent::class.java)?.let { riderEuc ->
                riderEuc.forwardLean = eucComponent.forwardLean
                riderEuc.sideLean = eucComponent.sideLean
                riderEuc.visualForwardLean = eucComponent.visualForwardLean
                riderEuc.visualSideLean = eucComponent.visualSideLean
            }

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

    private fun startGame() {
        resetGame()

        // Create player
        playerEntity = entityFactory.createPlayer()
        riderEntity = entityFactory.createRiderModel()

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
    }

    private fun handlePlayerFall() {
        // Record score
        isNewHighScore = highScoreManager.recordGame(session)

        // Transition to game over
        stateManager.transition(GameState.GameOver(session))
    }

    override fun resize(width: Int, height: Int) {
        renderer.resize(width, height)
        hud.resize(width, height)
        menuRenderer.resize(width, height)
        gameOverRenderer.resize(width, height)
        calibrationRenderer.resize(width, height)
    }

    override fun dispose() {
        renderer.dispose()
        models.dispose()
        hud.dispose()
        menuRenderer.dispose()
        gameOverRenderer.dispose()
        calibrationRenderer.dispose()
    }
}
