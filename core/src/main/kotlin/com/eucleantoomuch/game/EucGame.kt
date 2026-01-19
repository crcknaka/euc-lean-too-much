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
import com.eucleantoomuch.game.ecs.components.ObstacleType
import com.eucleantoomuch.game.ecs.components.PlayerComponent
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.systems.*
import com.eucleantoomuch.game.feedback.FallAnimationController
import com.eucleantoomuch.game.feedback.MotorSoundManager
import com.eucleantoomuch.game.feedback.MusicManager
import com.eucleantoomuch.game.feedback.SpeedWarningManager
import com.eucleantoomuch.game.input.AccelerometerInput
import com.eucleantoomuch.game.input.GameInput
import com.eucleantoomuch.game.input.KeyboardInput
import com.eucleantoomuch.game.platform.DefaultPlatformServices
import com.eucleantoomuch.game.platform.PlatformServices
import com.eucleantoomuch.game.procedural.WorldGenerator
import com.eucleantoomuch.game.rendering.GameRenderer
import com.eucleantoomuch.game.rendering.ProceduralModels
import com.eucleantoomuch.game.state.GameSession
import com.eucleantoomuch.game.state.GameState
import com.eucleantoomuch.game.state.GameStateManager
import com.eucleantoomuch.game.state.HighScoreManager
import com.eucleantoomuch.game.state.SettingsManager
import com.eucleantoomuch.game.ui.CalibrationRenderer
import com.eucleantoomuch.game.ui.CreditsRenderer
import com.eucleantoomuch.game.ui.GameOverRenderer
import com.eucleantoomuch.game.ui.Hud
import com.eucleantoomuch.game.ui.MenuRenderer
import com.eucleantoomuch.game.ui.PauseRenderer
import com.eucleantoomuch.game.ui.SettingsRenderer
import com.eucleantoomuch.game.ui.UIFeedback
import com.eucleantoomuch.game.ui.UIFonts
import com.eucleantoomuch.game.ui.WheelSelectionRenderer

class EucGame(
    private val platformServices: PlatformServices = DefaultPlatformServices()
) : ApplicationAdapter() {
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
    private lateinit var pauseRenderer: PauseRenderer
    private lateinit var calibrationRenderer: CalibrationRenderer
    private lateinit var settingsRenderer: SettingsRenderer
    private lateinit var creditsRenderer: CreditsRenderer
    private lateinit var wheelSelectionRenderer: WheelSelectionRenderer

    // Game state
    private var session = GameSession()
    private var playerEntity: Entity? = null
    private var riderEntity: Entity? = null
    private var leftArmEntity: Entity? = null
    private var rightArmEntity: Entity? = null
    private var countdownTimer = 2f
    private var lastCountdownSecond = -1  // Track last displayed second for beep
    private var isNewHighScore = false

    // Systems that need direct access
    private lateinit var eucPhysicsSystem: EucPhysicsSystem
    private lateinit var collisionSystem: CollisionSystem

    // Speed warning system (beeps and vibration at high speed)
    private lateinit var speedWarningManager: SpeedWarningManager

    // Motor sound synthesis
    private lateinit var motorSoundManager: MotorSoundManager

    // Fall animation controller
    private lateinit var fallAnimationController: FallAnimationController

    // Background music manager
    private lateinit var musicManager: MusicManager

    // Camera view mode switching - track single tap
    private var wasTouched = false
    private var cameraViewModeText = ""
    private var cameraViewModeTimer = 0f

    // FPS limiting (using nanoTime for precision)
    private var lastFrameTimeNanos = 0L
    private var currentMaxFps = 0  // 0 = unlimited

    override fun create() {
        Gdx.app.logLevel = Application.LOG_DEBUG

        // Disable libGDX foreground FPS limit to allow high refresh rates (120Hz+)
        // By default libGDX limits to 60 FPS on Android
        // Note: 0 means "use default" in libGDX, so we set a high value instead
        Gdx.graphics.setForegroundFPS(240)

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
        eucPhysicsSystem.onPlayerFall = {
            // Play generic hit sound for physics-based falls (wobble, loss of balance)
            platformServices.playGenericHitSound()
            handlePlayerFall()
        }

        collisionSystem = CollisionSystem()
        collisionSystem.onCollision = { obstacleType, causesGameOver ->
            // Play obstacle-specific impact sounds
            when (obstacleType) {
                ObstacleType.MANHOLE -> platformServices.playManholeSound()
                ObstacleType.PUDDLE -> platformServices.playWaterSplashSound()
                ObstacleType.STREET_LIGHT -> platformServices.playStreetLightImpactSound()
                ObstacleType.RECYCLE_BIN -> platformServices.playRecycleBinImpactSound()
                ObstacleType.PEDESTRIAN -> platformServices.playPersonImpactSound()
                ObstacleType.CAR -> platformServices.playCarCrashSound()
                ObstacleType.CURB, ObstacleType.POTHOLE -> platformServices.playGenericHitSound()
            }
            if (causesGameOver) {
                handlePlayerFall()
            }
        }
        collisionSystem.onNearMiss = {
            // Trigger near miss feedback (visual + sound + haptic)
            hud.triggerNearMiss()
            UIFeedback.nearMiss()
            session.nearMisses++
        }

        engine.addSystem(eucPhysicsSystem)
        engine.addSystem(MovementSystem())
        engine.addSystem(PedestrianAISystem())
        engine.addSystem(CarAISystem())
        engine.addSystem(PigeonSystem(models, platformServices))
        engine.addSystem(ArmAnimationSystem())
        engine.addSystem(HeadAnimationSystem())
        engine.addSystem(collisionSystem)
        engine.addSystem(CullingSystem())

        // Initialize UI
        hud = Hud(settingsManager)
        menuRenderer = MenuRenderer()
        gameOverRenderer = GameOverRenderer()
        pauseRenderer = PauseRenderer()
        calibrationRenderer = CalibrationRenderer()
        settingsRenderer = SettingsRenderer(settingsManager)
        creditsRenderer = CreditsRenderer()
        wheelSelectionRenderer = WheelSelectionRenderer(settingsManager)

        // Apply saved render distance setting
        applyRenderDistance()

        // Initialize speed warning system
        speedWarningManager = SpeedWarningManager(platformServices)
        applyPwmWarningThreshold()

        // Initialize motor sound manager
        motorSoundManager = MotorSoundManager(platformServices)
        applyAvasSetting()

        // Initialize fall animation controller
        fallAnimationController = FallAnimationController(platformServices)

        // Initialize music manager
        musicManager = MusicManager()
        musicManager.initialize()
        musicManager.setEnabled(settingsManager.musicEnabled)

        // Initialize UI feedback (sounds and haptics)
        UIFeedback.initialize()
        UIFeedback.hapticProvider = object : UIFeedback.HapticProvider {
            override fun vibrate(durationMs: Long, amplitude: Int) {
                platformServices.vibrate(durationMs, amplitude)
            }
            override fun hasVibrator(): Boolean = platformServices.hasVibrator()
        }
        UIFeedback.beepProvider = object : UIFeedback.BeepProvider {
            override fun playBeep(frequencyHz: Int, durationMs: Int) {
                platformServices.playBeep(frequencyHz, durationMs)
            }
        }
        UIFeedback.whooshProvider = object : UIFeedback.WhooshProvider {
            override fun playWhoosh() {
                platformServices.playWhooshSound()
            }
        }
        UIFeedback.wobbleProvider = object : UIFeedback.WobbleProvider {
            override fun playWobble(intensity: Float) {
                platformServices.playWobbleSound(intensity)
            }
            override fun stopWobble() {
                platformServices.stopWobbleSound()
            }
        }

        // Start at menu
        stateManager.transition(GameState.Menu)
    }

    private fun applyRenderDistance() {
        val distance = settingsManager.renderDistance
        worldGenerator.setRenderDistance(distance)
        renderer.setCameraFar(distance)
    }

    private fun applyPwmWarningThreshold() {
        // Convert percentage (0, 60, 70, 80, 90) to float (0, 0.6, 0.7, 0.8, 0.9)
        speedWarningManager.pwmWarningThreshold = settingsManager.pwmWarning / 100f
        speedWarningManager.beepsEnabled = settingsManager.beepsEnabled
    }

    private fun applyAvasSetting() {
        motorSoundManager.avasMode = settingsManager.avasMode
    }

    private fun applyMusicSetting() {
        musicManager.setEnabled(settingsManager.musicEnabled)
    }

    private fun applyFpsLimit() {
        // Check if max FPS setting changed
        val settingMaxFps = settingsManager.maxFps
        if (settingMaxFps != currentMaxFps) {
            currentMaxFps = settingMaxFps
        }

        // Apply FPS limit only if explicitly set (0 = unlimited, let VSync handle it)
        if (currentMaxFps > 0) {
            val targetFrameTimeNanos = 1_000_000_000L / currentMaxFps
            val currentTimeNanos = System.nanoTime()
            val elapsedNanos = currentTimeNanos - lastFrameTimeNanos

            if (elapsedNanos < targetFrameTimeNanos) {
                val sleepNanos = targetFrameTimeNanos - elapsedNanos
                val sleepMillis = sleepNanos / 1_000_000L
                val sleepNanosRemainder = (sleepNanos % 1_000_000L).toInt()

                try {
                    if (sleepMillis > 0 || sleepNanosRemainder > 0) {
                        Thread.sleep(sleepMillis, sleepNanosRemainder)
                    }
                } catch (e: InterruptedException) {
                    // Ignore
                }
            }
            lastFrameTimeNanos = System.nanoTime()
        }
        // When unlimited (0), don't track time - let VSync/display handle frame pacing
    }

    override fun render() {
        // Apply FPS limit if set
        applyFpsLimit()

        val delta = Gdx.graphics.deltaTime

        // Update input
        gameInput.update(delta)

        when (stateManager.current()) {
            is GameState.Loading -> renderLoading()
            is GameState.Menu -> renderMenu()
            is GameState.WheelSelection -> renderWheelSelection()
            is GameState.Settings -> renderSettings()
            is GameState.Credits -> renderCredits()
            is GameState.Calibrating -> renderCalibration()
            is GameState.Countdown -> renderCountdown(delta)
            is GameState.Playing -> renderPlaying(delta)
            is GameState.Paused -> renderPaused()
            is GameState.Falling -> renderFalling(delta)
            is GameState.GameOver -> renderGameOver()
        }

        // Check for pause - keyboard or two-finger tap (only when playing)
        if (stateManager.current() is GameState.Playing) {
            val shouldPause = Gdx.input.isKeyJustPressed(Input.Keys.BACK) ||
                Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
                (Gdx.input.justTouched() && Gdx.input.isTouched(1))  // Two fingers touched

            if (shouldPause) {
                UIFeedback.pauseOpen()
                val playingState = stateManager.current() as GameState.Playing
                pauseRenderer.reset()
                stateManager.transition(GameState.Paused(playingState.session))
            }

            // Single tap to change camera view (only one finger, not two)
            val isTouched = Gdx.input.isTouched(0) && !Gdx.input.isTouched(1)
            if (!isTouched && wasTouched) {
                // Finger lifted - this is a tap
                cameraViewModeText = renderer.cameraController.cycleViewMode()
                cameraViewModeTimer = 1.5f  // Show text for 1.5 seconds
                UIFeedback.tap()
            }
            wasTouched = isTouched

            // Update camera view mode text timer
            if (cameraViewModeTimer > 0) {
                cameraViewModeTimer -= delta
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

        // Play menu music
        musicManager.playMenuMusic()
        musicManager.update(Gdx.graphics.deltaTime)

        when (menuRenderer.render(highScoreManager.highScore, highScoreManager.maxDistance, highScoreManager.maxNearMisses)) {
            MenuRenderer.ButtonClicked.PLAY -> {
                // Go to wheel selection first
                stateManager.transition(GameState.WheelSelection)
            }
            MenuRenderer.ButtonClicked.CALIBRATE -> {
                stateManager.transition(GameState.Calibrating)
            }
            MenuRenderer.ButtonClicked.SETTINGS -> {
                stateManager.transition(GameState.Settings(returnTo = GameState.Menu))
            }
            MenuRenderer.ButtonClicked.CREDITS -> {
                creditsRenderer.reset()
                stateManager.transition(GameState.Credits)
            }
            MenuRenderer.ButtonClicked.EXIT -> {
                Gdx.app.exit()
                // Force kill the process on Android (Gdx.app.exit() only minimizes)
                System.exit(0)
            }
            MenuRenderer.ButtonClicked.NONE -> {}
        }
    }

    private fun renderWheelSelection() {
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.4f, 1f)
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        // Continue menu music during wheel selection
        musicManager.update(Gdx.graphics.deltaTime)

        when (wheelSelectionRenderer.render()) {
            WheelSelectionRenderer.Action.START -> {
                // Proceed to calibration or game
                if (gameInput.isCalibrated()) {
                    startGame()
                } else {
                    stateManager.transition(GameState.Calibrating)
                }
            }
            WheelSelectionRenderer.Action.BACK -> {
                stateManager.transition(GameState.Menu)
            }
            WheelSelectionRenderer.Action.NONE -> {}
        }
    }

    private fun renderSettings() {
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.4f, 1f)
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        // Continue music during settings
        musicManager.update(Gdx.graphics.deltaTime)

        when (settingsRenderer.render()) {
            SettingsRenderer.Action.BACK -> {
                // Apply settings
                applyRenderDistance()
                applyPwmWarningThreshold()
                applyAvasSetting()
                applyMusicSetting()
                // Return to previous state (Menu or Paused)
                val settingsState = stateManager.current() as GameState.Settings
                stateManager.transition(settingsState.returnTo)
            }
            SettingsRenderer.Action.NONE -> {}
        }
    }

    private fun renderCredits() {
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.4f, 1f)
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        // Continue music during credits
        musicManager.update(Gdx.graphics.deltaTime)

        when (creditsRenderer.render()) {
            CreditsRenderer.ButtonClicked.BACK -> {
                stateManager.transition(GameState.Menu)
            }
            CreditsRenderer.ButtonClicked.NONE -> {}
        }
    }

    private fun renderCalibration() {
        accelerometerInput.update(Gdx.graphics.deltaTime)
        val (rawX, rawY) = accelerometerInput.getRawValues()

        // Continue menu music during calibration
        musicManager.update(Gdx.graphics.deltaTime)

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
        // Transition to gameplay music during countdown
        musicManager.playGameplayMusic()
        musicManager.update(delta)

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
            // Reset rider lean for standing pose
            riderEntity?.getComponent(EucComponent::class.java)?.let { riderEuc ->
                riderEuc.visualForwardLean = 0f
                riderEuc.visualSideLean = 0f
                riderEuc.speed = 0f
            }
            // Set arms to relaxed standing pose
            riderEntity?.getComponent(ArmComponent::class.java)?.let { arm ->
                arm.poseBlend = 0f
                arm.leftArmYaw = 10f
                arm.leftArmPitch = 0f
                arm.rightArmYaw = 10f
                arm.rightArmPitch = 0f
            }
            updateArmPositions()

            // Update camera
            renderer.cameraController.update(playerTransform.position, playerTransform.yaw, delta)
        }

        // Reset all post-processing effects during countdown
        renderer.postProcessing.blurStrength = 0f
        renderer.postProcessing.dangerTint = 0f
        renderer.postProcessing.vignetteDanger = 0f
        renderer.postProcessing.chromaticAberration = 0f

        renderer.render()

        // Render countdown overlay
        val currentSecond = countdownTimer.toInt() + 1
        hud.renderCountdown(currentSecond)

        // Play beep on each new second (different tones: 3=low, 2=mid, 1=high)
        if (currentSecond != lastCountdownSecond && currentSecond in 1..3) {
            lastCountdownSecond = currentSecond
            val frequency = when (currentSecond) {
                3 -> 400   // Low tone
                2 -> 600   // Mid tone
                1 -> 800   // High tone
                else -> 500
            }
            platformServices.playBeep(frequency, 100)
        }

        countdownTimer -= delta
        if (countdownTimer <= 0) {
            // Play final "GO" beep - highest tone
            platformServices.playBeep(1000, 150)
            // Start motor sound when gameplay begins
            motorSoundManager.start()
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
                riderTransform.position.x -= 0.05f  // Slightly right to center on wheel
                riderTransform.position.y += 0.7f  // Stand on top of EUC
                riderTransform.yaw = playerTransform.yaw
                riderTransform.updateRotationFromYaw()
            }
            riderEntity?.getComponent(EucComponent::class.java)?.let { riderEuc ->
                riderEuc.forwardLean = eucComponent.forwardLean
                riderEuc.sideLean = eucComponent.sideLean
                riderEuc.visualForwardLean = eucComponent.visualForwardLean
                riderEuc.visualSideLean = eucComponent.visualSideLean
                riderEuc.speed = eucComponent.speed
            }

            // Update arm positions - attached to rider's shoulders
            updateArmPositions()

            // Update world generation
            worldGenerator.update(playerTransform.position.z, session.distanceTraveled)

            // Update camera with speed for FOV effect
            renderer.cameraController.update(playerTransform.position, playerTransform.yaw, delta, eucComponent.speed)

            // Update PWM warning system (beeps when PWM exceeds threshold)
            speedWarningManager.update(eucComponent.pwm, delta)

            // Update motor sound (pitch/volume based on speed and PWM)
            motorSoundManager.update(eucComponent.speed, eucComponent.pwm, delta)

            // Update post-processing effects
            val speedKmh = eucComponent.speed * 3.6f

            // Motion blur based on speed (starts at 30 km/h, max at 70 km/h)
            val blurStart = 30f
            val blurMax = 70f
            renderer.postProcessing.blurStrength = ((speedKmh - blurStart) / (blurMax - blurStart)).coerceIn(0f, 0.5f)

            // Blur direction: mostly vertical with slight horizontal from turning
            val turnFactor = eucComponent.sideLean * 0.3f
            renderer.postProcessing.blurDirection = turnFactor to -1f

            // Danger effects based on PWM
            // Vignette: starts at 80%, max at 98%
            val vignetteStart = 0.8f
            val vignetteMax = 0.98f
            val vignetteLevel = ((eucComponent.pwm - vignetteStart) / (vignetteMax - vignetteStart)).coerceIn(0f, 1f)
            renderer.postProcessing.vignetteDanger = vignetteLevel

            // Red tint: starts at 90%, max at 98%
            val tintStart = 0.9f
            val tintMax = 0.98f
            val tintLevel = ((eucComponent.pwm - tintStart) / (tintMax - tintStart)).coerceIn(0f, 1f)
            renderer.postProcessing.dangerTint = tintLevel

            // Chromatic aberration disabled
            // renderer.postProcessing.chromaticAberration = eucComponent.wobbleIntensity * 1.5f
        }

        // Update music fade
        musicManager.update(delta)

        // Render
        renderer.render()

        // Render HUD
        if (eucComponent != null) {
            hud.render(session, eucComponent, speedWarningManager.isActive())

            // Apply wobble screen shake to camera
            val (shakeX, shakeY) = hud.getScreenShake()
            renderer.cameraController.setWobbleShake(shakeX, shakeY)
        }

        // Show camera view mode text
        if (cameraViewModeTimer > 0) {
            hud.renderCameraMode(cameraViewModeText, cameraViewModeTimer / 1.5f)
        }
    }

    private fun renderPaused() {
        // Stop speed warnings when paused
        speedWarningManager.stop()
        // Stop motor sound when paused
        motorSoundManager.stop()
        // Pause music
        musicManager.pause()
        // Reset wobble shake
        renderer.cameraController.setWobbleShake(0f, 0f)

        // Render frozen game state
        renderer.render()

        // Render pause UI
        when (pauseRenderer.render()) {
            PauseRenderer.ButtonClicked.RESUME -> {
                val pausedState = stateManager.current() as GameState.Paused
                // Resume motor sound
                motorSoundManager.start()
                // Resume music
                musicManager.resume()
                // Prevent camera mode change on resume (finger lift would trigger tap)
                wasTouched = Gdx.input.isTouched(0)
                stateManager.transition(GameState.Playing(pausedState.session))
            }
            PauseRenderer.ButtonClicked.RESTART -> {
                resetGame()
                startGame()
            }
            PauseRenderer.ButtonClicked.SETTINGS -> {
                val pausedState = stateManager.current() as GameState.Paused
                stateManager.transition(GameState.Settings(returnTo = pausedState))
            }
            PauseRenderer.ButtonClicked.MENU -> {
                resetGame()
                stateManager.transition(GameState.Menu)
            }
            PauseRenderer.ButtonClicked.NONE -> {}
        }
    }

    private fun renderGameOver() {
        val state = stateManager.current() as GameState.GameOver

        // Continue music fade update
        musicManager.update(Gdx.graphics.deltaTime)

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

        // Get selected wheel type and create player with it
        val wheelType = settingsManager.getSelectedWheel()
        playerEntity = entityFactory.createPlayer(wheelType)
        riderEntity = entityFactory.createRiderModel()
        leftArmEntity = entityFactory.createArmEntity(isLeft = true)
        rightArmEntity = entityFactory.createArmEntity(isLeft = false)

        // Set rider reference in renderer for arm positioning
        renderer.riderEntity = riderEntity

        // Initialize camera
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java)
        if (playerTransform != null) {
            renderer.cameraController.initialize(playerTransform.position)
            renderer.cameraController.resetViewMode()
        }

        // Reset camera mode UI state
        cameraViewModeTimer = 0f
        wasTouched = false

        // Generate initial world
        worldGenerator.update(0f, 0f)

        // Start countdown (faster: 2 seconds total)
        countdownTimer = 2f
        lastCountdownSecond = -1  // Reset for beep tracking
        session.reset()
        hud.reset()
        isNewHighScore = false
        stateManager.transition(GameState.Countdown(3))
    }

    /**
     * Update arm entity state.
     * Actual positioning is done in the renderer to keep arms properly attached to shoulders.
     */
    private fun updateArmPositions() {
        // Ensure arm entities have proper scale (rendering uses this)
        leftArmEntity?.getComponent(TransformComponent::class.java)?.scale?.set(1f, 1f, 1f)
        rightArmEntity?.getComponent(TransformComponent::class.java)?.scale?.set(1f, 1f, 1f)
    }

    private fun resetGame() {
        // Remove arm entities
        leftArmEntity?.let { engine.removeEntity(it) }
        rightArmEntity?.let { engine.removeEntity(it) }

        // Remove rider entity explicitly (it doesn't have PlayerComponent)
        riderEntity?.let { engine.removeEntity(it) }

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
        renderer.riderEntity = null
    }

    private fun handlePlayerFall() {
        // Stop speed warnings
        speedWarningManager.stop()
        // Stop motor sound
        motorSoundManager.stop()

        // Record score
        isNewHighScore = highScoreManager.recordGame(session)

        // Start fall animation
        val eucComponent = playerEntity?.getComponent(EucComponent::class.java)
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java)
        if (eucComponent != null && playerTransform != null) {
            // Always skip synthesized crash sound - all obstacles have their own sounds now
            fallAnimationController.start(
                speed = eucComponent.speed,
                forwardLean = eucComponent.forwardLean,
                sideLean = eucComponent.sideLean,
                yaw = playerTransform.yaw,
                skipSound = true  // All obstacles have their own sounds
            )
        }

        // Transition to falling state (will show animation before game over)
        stateManager.transition(GameState.Falling(session))
    }

    private fun renderFalling(delta: Float) {
        val state = stateManager.current() as GameState.Falling

        // Fade out music during fall
        musicManager.fadeOut()
        musicManager.update(delta)

        // Update fall animation
        fallAnimationController.update(delta)

        // Get base transforms
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java)
        val eucComponent = playerEntity?.getComponent(EucComponent::class.java)

        if (playerTransform != null && eucComponent != null) {
            // Apply camera effects
            renderer.cameraController.setShake(fallAnimationController.cameraShake)
            renderer.cameraController.setFovPunch(fallAnimationController.fovPunch)
            renderer.cameraController.setDropOffset(fallAnimationController.cameraDropOffset)
            renderer.cameraController.setForwardOffset(fallAnimationController.cameraForwardOffset)
            renderer.cameraController.setRoll(fallAnimationController.cameraRoll)

            // Update rider position with fall animation offsets
            riderEntity?.getComponent(TransformComponent::class.java)?.let { riderTransform ->
                riderTransform.position.set(playerTransform.position)
                riderTransform.position.x -= 0.05f  // Slightly right to center on wheel

                // Compensate Y position for forward pitch - raise rider when leaning forward
                // so the model doesn't clip through ground
                val pitchCompensation = (fallAnimationController.riderPitch / 90f) * 0.5f
                val baseY = 0.7f + fallAnimationController.riderYOffset + pitchCompensation
                riderTransform.position.y = (playerTransform.position.y + baseY).coerceAtLeast(0.3f)

                riderTransform.position.z += fallAnimationController.riderForwardOffset

                // Apply fall rotation to rider's visual lean
                riderTransform.yaw = playerTransform.yaw
                riderTransform.updateRotationFromYaw()
            }
            riderEntity?.getComponent(EucComponent::class.java)?.let { riderEuc ->
                // Apply fall pitch/roll to visual lean (converts rotation to lean values)
                // Clamp pitch to 75 degrees max to prevent model going underground
                val clampedPitch = fallAnimationController.riderPitch.coerceAtMost(75f)
                riderEuc.visualForwardLean = eucComponent.visualForwardLean + clampedPitch / 90f
                riderEuc.visualSideLean = eucComponent.visualSideLean + fallAnimationController.riderRoll / 45f
            }

            // Update EUC position with fall animation (falling on its side)
            playerTransform.position.y = fallAnimationController.eucYOffset
            playerTransform.position.z += fallAnimationController.eucForwardOffset * delta
            playerTransform.position.x += fallAnimationController.eucSideOffset * delta

            // Apply EUC roll to visual lean (90 degrees = lying on side)
            eucComponent.visualSideLean = eucComponent.sideLean + fallAnimationController.eucRoll / 90f

            // Update arm positions during fall - arms reach forward to brace for impact
            riderEntity?.getComponent(ArmComponent::class.java)?.let { arm ->
                // Arms extend forward as rider falls
                val fallProgress = (fallAnimationController.riderPitch / 90f).coerceIn(0f, 1f)
                // armYaw ~45 = arms slightly outward, armPitch negative = arms forward
                arm.leftArmYaw = 45f + fallProgress * 10f    // Slightly out to sides
                arm.leftArmPitch = -60f - fallProgress * 30f // Forward and reaching out (negative = forward)
                arm.rightArmYaw = 45f + fallProgress * 10f
                arm.rightArmPitch = -60f - fallProgress * 30f
            }
            updateArmPositions()

            // Keep camera following (with effects applied)
            renderer.cameraController.update(playerTransform.position, playerTransform.yaw, delta, 0f)
        }

        // Render the scene
        renderer.render()

        // Check if animation is complete
        if (fallAnimationController.isComplete) {
            // Reset camera effects
            renderer.cameraController.setShake(0f)
            renderer.cameraController.setFovPunch(0f)
            renderer.cameraController.setDropOffset(0f)
            renderer.cameraController.setForwardOffset(0f)
            renderer.cameraController.setRoll(0f)
            renderer.cameraController.setWobbleShake(0f, 0f)

            // Reset fall animation
            fallAnimationController.reset()

            // Reset game over animations
            gameOverRenderer.reset()

            // Transition to game over
            stateManager.transition(GameState.GameOver(state.session))
        }
    }

    override fun resize(width: Int, height: Int) {
        renderer.resize(width, height)
        hud.resize(width, height)
        menuRenderer.resize(width, height)
        gameOverRenderer.resize(width, height)
        pauseRenderer.resize(width, height)
        calibrationRenderer.resize(width, height)
        settingsRenderer.resize(width, height)
        creditsRenderer.resize(width, height)
        wheelSelectionRenderer.resize(width, height)
    }

    override fun resume() {
        // Re-enable high FPS on resume (Android may reset this)
        Gdx.graphics.setForegroundFPS(240)

        // Force font and UI reinitialization on resume (GL context may have been lost on Android)
        UIFonts.dispose()

        // Recreate all UI renderer resources (SpriteBatch, ShapeRenderer)
        hud.recreate()
        menuRenderer.recreate()
        gameOverRenderer.recreate()
        pauseRenderer.recreate()
        calibrationRenderer.recreate()
        settingsRenderer.recreate()
        creditsRenderer.recreate()
        wheelSelectionRenderer.recreate()
    }

    override fun dispose() {
        renderer.dispose()
        models.dispose()
        musicManager.dispose()
        UIFeedback.dispose()
        hud.dispose()
        menuRenderer.dispose()
        gameOverRenderer.dispose()
        pauseRenderer.dispose()
        calibrationRenderer.dispose()
        settingsRenderer.dispose()
        creditsRenderer.dispose()
        wheelSelectionRenderer.dispose()
    }
}
