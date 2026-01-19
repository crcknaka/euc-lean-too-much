package com.eucleantoomuch.game

import com.badlogic.ashley.core.Engine
import com.badlogic.ashley.core.Entity
import com.badlogic.gdx.Application
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.eucleantoomuch.game.ecs.EntityFactory
import com.eucleantoomuch.game.ecs.Families
import com.eucleantoomuch.game.ecs.components.ArmComponent
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.ecs.components.HeadComponent
import com.eucleantoomuch.game.ecs.components.ModelComponent
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
import com.eucleantoomuch.game.replay.ReplaySystem
import com.eucleantoomuch.game.ui.CalibrationRenderer
import com.eucleantoomuch.game.ui.CreditsRenderer
import com.eucleantoomuch.game.ui.GameOverRenderer
import com.eucleantoomuch.game.ui.Hud
import com.eucleantoomuch.game.ui.MenuRenderer
import com.eucleantoomuch.game.ui.PauseRenderer
import com.eucleantoomuch.game.ui.ReplayRenderer
import com.eucleantoomuch.game.ui.SettingsRenderer
import com.eucleantoomuch.game.ui.UIFeedback
import com.eucleantoomuch.game.ui.UIFonts
import com.eucleantoomuch.game.ui.WheelSelectionRenderer
import com.eucleantoomuch.game.ui.DebugMenu
import com.eucleantoomuch.game.ui.DebugConfig
import com.eucleantoomuch.game.physics.RagdollPhysics

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
    private lateinit var replayRenderer: ReplayRenderer

    // Debug menu (admin tools)
    private lateinit var debugMenu: DebugMenu
    private lateinit var debugShapeRenderer: com.badlogic.gdx.graphics.glutils.ShapeRenderer

    // Replay system
    private lateinit var replaySystem: ReplaySystem

    // Game state
    private var session = GameSession()
    private var playerEntity: Entity? = null
    private var riderEntity: Entity? = null
    private var leftArmEntity: Entity? = null
    private var rightArmEntity: Entity? = null
    private var countdownTimer = 3f
    private var lastCountdownSecond = -1  // Track last displayed second for beep
    private var isNewHighScore = false

    // Systems that need direct access
    private lateinit var eucPhysicsSystem: EucPhysicsSystem
    private lateinit var collisionSystem: CollisionSystem
    private lateinit var cullingSystem: CullingSystem
    private lateinit var pigeonSystem: PigeonSystem
    private lateinit var pedestrianAISystem: PedestrianAISystem
    private lateinit var carAISystem: CarAISystem

    // Speed warning system (beeps and vibration at high speed)
    private lateinit var speedWarningManager: SpeedWarningManager

    // Motor sound synthesis
    private lateinit var motorSoundManager: MotorSoundManager

    // Fall animation controller
    private lateinit var fallAnimationController: FallAnimationController

    // Ragdoll physics for fall animation
    private var ragdollPhysics: com.eucleantoomuch.game.physics.RagdollPhysics? = null
    private var ragdollRenderer: com.eucleantoomuch.game.physics.RagdollRenderer? = null
    private var useRagdollPhysics = true  // Toggle for ragdoll vs scripted animation

    // Background music manager
    private lateinit var musicManager: MusicManager

    // Camera view mode switching - track single tap
    private var wasTouched = false
    private var cameraViewModeText = ""
    private var cameraViewModeTimer = 0f
    private var ignoreTapFrames = 0  // Skip tap detection for N frames after pause/resume

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
        collisionSystem.onPedestrianHit = { pedestrianEntity ->
            // Start ragdoll physics for the hit pedestrian
            startPedestrianRagdoll(pedestrianEntity)
        }
        collisionSystem.onKnockableHit = { obstacleEntity ->
            // Start ragdoll physics for knockable objects (trash cans)
            startTrashCanRagdoll(obstacleEntity)
        }

        pigeonSystem = PigeonSystem(models, platformServices)
        cullingSystem = CullingSystem()
        pedestrianAISystem = PedestrianAISystem()
        carAISystem = CarAISystem()

        engine.addSystem(eucPhysicsSystem)
        engine.addSystem(MovementSystem())
        engine.addSystem(pedestrianAISystem)
        engine.addSystem(carAISystem)
        engine.addSystem(pigeonSystem)
        engine.addSystem(ArmAnimationSystem())
        engine.addSystem(HeadAnimationSystem())
        engine.addSystem(collisionSystem)
        engine.addSystem(cullingSystem)

        // Initialize UI
        hud = Hud(settingsManager)
        menuRenderer = MenuRenderer()
        gameOverRenderer = GameOverRenderer()
        pauseRenderer = PauseRenderer()
        calibrationRenderer = CalibrationRenderer()
        settingsRenderer = SettingsRenderer(settingsManager)
        creditsRenderer = CreditsRenderer()
        wheelSelectionRenderer = WheelSelectionRenderer(settingsManager)
        replayRenderer = ReplayRenderer()

        // Initialize debug menu (admin tools)
        debugMenu = DebugMenu(engine)
        debugShapeRenderer = com.badlogic.gdx.graphics.glutils.ShapeRenderer()

        // Initialize replay system
        replaySystem = ReplaySystem()

        // Apply saved render distance setting
        applyRenderDistance()

        // Apply graphics preset (post-processing on/off)
        applyGraphicsPreset()

        // Apply shadows setting
        applyShadowsSetting()

        // Initialize speed warning system
        speedWarningManager = SpeedWarningManager(platformServices)
        applyPwmWarningThreshold()

        // Initialize motor sound manager
        motorSoundManager = MotorSoundManager(platformServices)
        applyAvasSetting()

        // Initialize fall animation controller
        fallAnimationController = FallAnimationController(platformServices)

        // Initialize ragdoll physics and renderer
        try {
            ragdollPhysics = RagdollPhysics()
            ragdollRenderer = com.eucleantoomuch.game.physics.RagdollRenderer()

            // Set up collision callback for ragdoll hitting objects during flight
            ragdollPhysics?.onRagdollCollision = { colliderType ->
                playRagdollCollisionSound(colliderType)
            }

            // Set up secondary collision callback for pedestrian ragdolls hitting objects (quieter)
            ragdollPhysics?.onSecondaryRagdollCollision = { colliderType ->
                playSecondaryRagdollCollisionSound(colliderType)
            }

            // Set up pedestrian ragdoll rendering (always active during gameplay)
            renderer.pedestrianRagdollRenderer = ragdollRenderer
            renderer.pedestrianRagdollPhysics = ragdollPhysics

            Gdx.app.log("EucGame", "Ragdoll physics initialized")
        } catch (e: Exception) {
            Gdx.app.error("EucGame", "Failed to initialize ragdoll physics: ${e.message}")
            useRagdollPhysics = false
        }

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

    private fun applyGraphicsPreset() {
        renderer.postProcessing.setEnabled(settingsManager.isPostProcessingEnabled())
    }

    private fun applyShadowsSetting() {
        renderer.shadowsEnabled = settingsManager.shadowsEnabled
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

        var delta = Gdx.graphics.deltaTime

        // Apply slow motion from debug menu
        if (DebugConfig.DEBUG_MENU_ENABLED && debugMenu.slowMotion) {
            delta *= debugMenu.timeScale
        }

        // Update debug menu
        if (DebugConfig.DEBUG_MENU_ENABLED) {
            debugMenu.update(Gdx.graphics.deltaTime)  // Use real delta for menu responsiveness

            // Sync freeze AI state to AI systems
            pedestrianAISystem.frozen = debugMenu.freezeAI
            carAISystem.frozen = debugMenu.freezeAI
        }

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
            is GameState.Replay -> renderReplay(delta)
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
                // Reset touch state and ignore taps for a few frames when returning
                wasTouched = false
                ignoreTapFrames = 5
                stateManager.transition(GameState.Paused(playingState.session))
                return  // Don't process further input this frame
            }

            // Single tap to change camera view (only one finger, not two)
            // Skip tap detection for a few frames after pause/resume to avoid accidental triggers
            if (ignoreTapFrames > 0) {
                ignoreTapFrames--
                wasTouched = Gdx.input.isTouched(0)
            } else {
                val isTouched = Gdx.input.isTouched(0) && !Gdx.input.isTouched(1)
                if (!isTouched && wasTouched) {
                    // Finger lifted - this is a tap
                    cameraViewModeText = renderer.cameraController.cycleViewMode()
                    cameraViewModeTimer = 1.5f  // Show text for 1.5 seconds
                    UIFeedback.tap()
                }
                wasTouched = isTouched
            }

            // Update camera view mode text timer
            if (cameraViewModeTimer > 0) {
                cameraViewModeTimer -= delta
            }
        }

        // Render debug overlays and menu (always on top, after all other rendering)
        if (DebugConfig.DEBUG_MENU_ENABLED) {
            // Render debug info overlays (stats, entity info, etc.)
            debugMenu.renderOverlays(
                playerEntity = playerEntity,
                inputData = gameInput.getInput(),
                cameraPosition = renderer.cameraController.getCameraPosition(),
                cameraYaw = renderer.cameraController.getCameraYaw()
            )

            // Render debug toggle button (when menu is closed)
            debugMenu.renderDebugButton()

            // Render debug menu panel (if open)
            debugMenu.render()
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
                applyGraphicsPreset()
                applyShadowsSetting()
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

        // Update pedestrian ragdoll physics (if any pedestrians are falling)
        ragdollPhysics?.update(delta)
        updateFallingPedestrians()
        updateKnockedOverTrashCans()

        // Check if ragdoll bodies knock down standing pedestrians
        checkRagdollPedestrianCollisions()

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

            // Record frame for replay
            recordReplayFrame(delta, playerTransform, eucComponent)

            // Update world generation
            worldGenerator.update(playerTransform.position.z, session.distanceTraveled)

            // Update camera with speed for FOV effect
            renderer.cameraController.update(playerTransform.position, playerTransform.yaw, delta, eucComponent.speed)

            // Update PWM warning system (beeps when PWM exceeds threshold)
            speedWarningManager.update(eucComponent.pwm, delta)

            // Update motor sound (pitch/volume based on speed and PWM)
            motorSoundManager.update(eucComponent.speed, eucComponent.pwm, delta)

            // Update post-processing effects (only in High graphics preset)
            if (settingsManager.isPostProcessingEnabled()) {
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
            } else {
                // Normal preset: disable all post-processing effects
                renderer.postProcessing.blurStrength = 0f
                renderer.postProcessing.vignetteDanger = 0f
                renderer.postProcessing.dangerTint = 0f
            }
        }

        // Update music fade
        musicManager.update(delta)

        // Render
        renderer.render()

        // Render 3D debug visualizations (colliders, etc.)
        if (DebugConfig.DEBUG_MENU_ENABLED) {
            debugMenu.render3DDebug(
                shapeRenderer = debugShapeRenderer,
                camera = renderer.camera,
                playerPosition = playerTransform?.position
            )
        }

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
                // Prevent camera mode change on resume - ignore taps for a few frames
                wasTouched = false
                ignoreTapFrames = 5
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

        // Keep ragdoll visible if frozen
        if (ragdollPhysics != null && ragdollPhysics!!.isActive()) {
            renderer.activeRagdollRenderer = ragdollRenderer
            renderer.activeRagdollPhysics = ragdollPhysics
        }

        // Render frozen game state
        renderer.render()

        // Tell game over renderer if replay is available
        gameOverRenderer.setHasReplayFrames(replaySystem.hasFrames())

        // Render game over UI
        when (gameOverRenderer.render(state.session, isNewHighScore)) {
            GameOverRenderer.ButtonClicked.RETRY -> {
                resetGame()
                startGame()
            }
            GameOverRenderer.ButtonClicked.REPLAY -> {
                // Start replay playback
                replayRenderer.reset()
                replayCameraYaw = 0f
                replayCameraPitch = 20f

                // Prepare world chunks for replay - regenerate any missing chunks
                val zRange = replaySystem.getZRange()
                if (zRange != null) {
                    worldGenerator.prepareForReplay(zRange.first, zRange.second, state.session.distanceTraveled)
                }

                replaySystem.startPlayback()
                // No motor sound in replay - just visual playback
                stateManager.transition(GameState.Replay(state.session))
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

        // Start countdown (3 seconds for proper 3-2-1 beep spacing)
        countdownTimer = 3f
        lastCountdownSecond = -1  // Reset for beep tracking
        session.reset()
        hud.reset()
        isNewHighScore = false
        replaySystem.reset()  // Clear replay buffer for new game
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
        // Stop ragdoll physics completely
        ragdollPhysics?.stop()
        renderer.activeRagdollRenderer = null
        renderer.activeRagdollPhysics = null
        renderer.hideHead = false  // Restore head visibility

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

    // Temp vector for pedestrian ragdoll
    private val pedestrianImpactDir = com.badlogic.gdx.math.Vector3()

    private fun startPedestrianRagdoll(pedestrianEntity: com.badlogic.ashley.core.Entity) {
        // Early exit without logging to reduce overhead
        if (!useRagdollPhysics || ragdollPhysics == null) return

        val pedestrianComponent = pedestrianEntity.getComponent(
            com.eucleantoomuch.game.ecs.components.PedestrianComponent::class.java
        ) ?: return

        // Don't ragdoll if already ragdolling
        if (pedestrianComponent.isRagdolling) return

        val pedestrianTransform = pedestrianEntity.getComponent(TransformComponent::class.java) ?: return
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java) ?: return
        val eucComponent = playerEntity?.getComponent(EucComponent::class.java) ?: return

        // Calculate impact direction (from player to pedestrian)
        val yawRad = Math.toRadians(playerTransform.yaw.toDouble()).toFloat()
        pedestrianImpactDir.set(
            kotlin.math.sin(yawRad),
            0f,
            kotlin.math.cos(yawRad)
        )

        // Hide models first to avoid any visual glitch
        val modelComponent = pedestrianEntity.getComponent(
            com.eucleantoomuch.game.ecs.components.ModelComponent::class.java
        )
        modelComponent?.visible = false

        val shadowComponent = pedestrianEntity.getComponent(
            com.eucleantoomuch.game.ecs.components.ShadowComponent::class.java
        )
        shadowComponent?.visible = false

        // Extract shirt color from pedestrian model for ragdoll rendering
        val shirtColor = extractPedestrianShirtColor(modelComponent?.modelInstance)
            ?: com.badlogic.gdx.graphics.Color.GREEN

        // Add pedestrian ragdoll body (this is the heavy operation)
        val bodyIndex = ragdollPhysics!!.addPedestrianRagdoll(
            position = pedestrianTransform.position,
            yaw = pedestrianTransform.yaw,
            playerVelocity = eucComponent.speed,
            playerDirection = pedestrianImpactDir,
            entityIndex = pedestrianEntity.hashCode(),
            shirtColor = shirtColor
        )

        // Mark pedestrian as ragdolling
        pedestrianComponent.isRagdolling = true
        pedestrianComponent.ragdollBodyIndex = bodyIndex
        pedestrianComponent.state = com.eucleantoomuch.game.ecs.components.PedestrianState.FALLING
    }

    /**
     * Start ragdoll physics for a trash can (knockable object).
     */
    private fun startTrashCanRagdoll(obstacleEntity: com.badlogic.ashley.core.Entity) {
        if (ragdollPhysics == null) return

        val obstacleComponent = obstacleEntity.getComponent(
            com.eucleantoomuch.game.ecs.components.ObstacleComponent::class.java
        ) ?: return

        val obstacleTransform = obstacleEntity.getComponent(TransformComponent::class.java) ?: return
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java) ?: return
        val eucComponent = playerEntity?.getComponent(EucComponent::class.java) ?: return

        // Calculate impact direction (from player to obstacle)
        val yawRad = Math.toRadians(playerTransform.yaw.toDouble()).toFloat()
        val impactDir = com.badlogic.gdx.math.Vector3(
            kotlin.math.sin(yawRad),
            0f,
            kotlin.math.cos(yawRad)
        )

        // Hide original model
        val modelComponent = obstacleEntity.getComponent(
            com.eucleantoomuch.game.ecs.components.ModelComponent::class.java
        )
        modelComponent?.visible = false

        // Hide shadow
        val shadowComponent = obstacleEntity.getComponent(
            com.eucleantoomuch.game.ecs.components.ShadowComponent::class.java
        )
        shadowComponent?.visible = false

        // Add trash can ragdoll
        val bodyIndex = ragdollPhysics!!.addTrashCanRagdoll(
            position = obstacleTransform.position,
            playerVelocity = eucComponent.speed,
            playerDirection = impactDir,
            entityIndex = obstacleEntity.hashCode()
        )

        obstacleComponent.ragdollBodyIndex = bodyIndex
    }

    /**
     * Extract shirt color from pedestrian model instance for ragdoll rendering.
     */
    private fun extractPedestrianShirtColor(modelInstance: com.badlogic.gdx.graphics.g3d.ModelInstance?): com.badlogic.gdx.graphics.Color? {
        if (modelInstance == null) return null

        // Find the torso material and extract its color
        for (material in modelInstance.materials) {
            val id = material.id?.lowercase() ?: ""
            if (id.contains("torso") || id.contains("shirt") || id.contains("upper")) {
                val colorAttr = material.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse)
                    as? com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
                if (colorAttr != null) return colorAttr.color
            }
        }

        // Fallback: return first material color that's not skin/pants/hair
        for (material in modelInstance.materials) {
            val colorAttr = material.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse)
                as? com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
            if (colorAttr != null) {
                val c = colorAttr.color
                // Skip skin-like colors (high R, medium G, low-medium B)
                if (c.r > 0.7f && c.g > 0.5f && c.b > 0.4f) continue
                // Skip pants-like colors (dark)
                if (c.r < 0.35f && c.g < 0.35f && c.b < 0.45f) continue
                // Skip hair-like colors (brown)
                if (c.r < 0.4f && c.g < 0.3f && c.b < 0.2f) continue
                return c
            }
        }
        return null
    }

    // Temp vectors for ragdoll-to-pedestrian collision checking
    private val ragdollCheckPos = com.badlogic.gdx.math.Vector3()
    private val pedestrianCheckPos = com.badlogic.gdx.math.Vector3()
    private val ragdollImpactDir = com.badlogic.gdx.math.Vector3()
    private val ragdollCollisionRadius = 0.8f  // Collision radius for ragdoll body

    /**
     * Check if any ragdoll body (player or pedestrian) collides with standing pedestrians.
     * If collision detected, knock down the standing pedestrian.
     */
    private fun checkRagdollPedestrianCollisions() {
        if (!useRagdollPhysics || ragdollPhysics == null) return

        // Get all active ragdoll bodies with significant velocity
        val ragdollBodies = ragdollPhysics!!.getActiveRagdollBodies(minVelocity = 3f)
        if (ragdollBodies.isEmpty()) return

        // Get all pedestrians
        val pedestrians = engine.getEntitiesFor(Families.pedestrians)
        if (pedestrians.size() == 0) return

        for (ragdollBody in ragdollBodies) {
            ragdollCheckPos.set(ragdollBody.position)

            for (i in 0 until pedestrians.size()) {
                val pedestrianEntity = pedestrians[i]
                val pedestrianComponent = pedestrianEntity.getComponent(
                    com.eucleantoomuch.game.ecs.components.PedestrianComponent::class.java
                ) ?: continue

                // Skip if already ragdolling
                if (pedestrianComponent.isRagdolling) continue

                val pedestrianTransform = pedestrianEntity.getComponent(TransformComponent::class.java) ?: continue
                pedestrianCheckPos.set(pedestrianTransform.position)
                pedestrianCheckPos.y += 0.8f  // Check at torso height

                // Simple distance check
                val dx = ragdollCheckPos.x - pedestrianCheckPos.x
                val dy = ragdollCheckPos.y - pedestrianCheckPos.y
                val dz = ragdollCheckPos.z - pedestrianCheckPos.z
                val distSq = dx * dx + dy * dy + dz * dz

                if (distSq < ragdollCollisionRadius * ragdollCollisionRadius) {
                    // Collision detected! Start ragdoll for this pedestrian
                    startRagdollFromImpact(pedestrianEntity, ragdollBody.velocity)

                    // Play quieter impact sound (chain reaction)
                    platformServices.playPersonImpactSound(0.4f)
                }
            }
        }
    }

    /**
     * Start pedestrian ragdoll from being hit by another ragdoll body.
     * Similar to startPedestrianRagdoll but uses the ragdoll's velocity as impact direction.
     */
    private fun startRagdollFromImpact(
        pedestrianEntity: com.badlogic.ashley.core.Entity,
        impactVelocity: com.badlogic.gdx.math.Vector3
    ) {
        if (!useRagdollPhysics || ragdollPhysics == null) return

        val pedestrianComponent = pedestrianEntity.getComponent(
            com.eucleantoomuch.game.ecs.components.PedestrianComponent::class.java
        ) ?: return

        if (pedestrianComponent.isRagdolling) return

        val pedestrianTransform = pedestrianEntity.getComponent(TransformComponent::class.java) ?: return

        // Hide models
        val modelComponent = pedestrianEntity.getComponent(
            com.eucleantoomuch.game.ecs.components.ModelComponent::class.java
        )
        modelComponent?.visible = false

        val shadowComponent = pedestrianEntity.getComponent(
            com.eucleantoomuch.game.ecs.components.ShadowComponent::class.java
        )
        shadowComponent?.visible = false

        // Calculate impact direction from velocity (normalized)
        ragdollImpactDir.set(impactVelocity).nor()
        ragdollImpactDir.y = 0f  // Keep horizontal

        // Impact speed is reduced (secondary impact)
        val impactSpeed = impactVelocity.len() * 0.6f

        // Extract shirt color
        val shirtColor = extractPedestrianShirtColor(modelComponent?.modelInstance)
            ?: com.badlogic.gdx.graphics.Color.GREEN

        // Add pedestrian ragdoll
        val bodyIndex = ragdollPhysics!!.addPedestrianRagdoll(
            position = pedestrianTransform.position,
            yaw = pedestrianTransform.yaw,
            playerVelocity = impactSpeed,
            playerDirection = ragdollImpactDir,
            entityIndex = pedestrianEntity.hashCode(),
            shirtColor = shirtColor
        )

        // Mark pedestrian as ragdolling
        pedestrianComponent.isRagdolling = true
        pedestrianComponent.ragdollBodyIndex = bodyIndex
        pedestrianComponent.state = com.eucleantoomuch.game.ecs.components.PedestrianState.FALLING
    }

    private fun handlePlayerFall() {
        // God mode - prevent death
        if (DebugConfig.DEBUG_MENU_ENABLED && debugMenu.godMode) {
            Gdx.app.log("Debug", "God mode: Player death prevented")
            return
        }

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

            // Start ragdoll physics simulation
            if (useRagdollPhysics && ragdollPhysics != null) {
                ragdollPhysics!!.startFall(
                    eucPosition = playerTransform.position,
                    eucYaw = playerTransform.yaw,
                    playerVelocity = eucComponent.speed,
                    sideLean = eucComponent.sideLean,
                    forwardLean = eucComponent.forwardLean
                )

                // Reset collider tracking and add initial colliders
                resetColliderTracking()
                addWorldCollidersForRagdoll(playerTransform.position)
            }
        }

        // Transition to falling state (will show animation before game over)
        stateManager.transition(GameState.Falling(session))
    }

    // Temp vectors for ragdoll position extraction
    private val ragdollEucPos = com.badlogic.gdx.math.Vector3()
    private val ragdollTorsoPos = com.badlogic.gdx.math.Vector3()

    private fun renderFalling(delta: Float) {
        val state = stateManager.current() as GameState.Falling

        // Fade out music during fall
        musicManager.fadeOut()
        musicManager.update(delta)

        // Update fall animation (for camera effects and timing)
        fallAnimationController.update(delta)

        // Keep updating the world (cars, pedestrians, etc.) during fall
        engine.update(delta)

        // Update world generator to keep spawning chunks
        val playerTransformForWorld = playerEntity?.getComponent(TransformComponent::class.java)
        if (playerTransformForWorld != null) {
            worldGenerator.update(playerTransformForWorld.position.z, state.session.distanceTraveled)
        }

        // Always update ragdoll physics (for pedestrians even if player ragdoll is inactive)
        ragdollPhysics?.update(delta)

        // Check if ragdoll bodies knock down standing pedestrians
        checkRagdollPedestrianCollisions()

        // Update ragdoll physics if active
        val ragdollActive = useRagdollPhysics && ragdollPhysics != null && ragdollPhysics!!.isActive()
        if (ragdollActive) {

            // Hide rider models during ragdoll - use RagdollRenderer instead
            // BUT keep playerEntity (EUC wheel) visible - it uses original model
            riderEntity?.getComponent(ModelComponent::class.java)?.visible = false
            leftArmEntity?.getComponent(ModelComponent::class.java)?.visible = false
            rightArmEntity?.getComponent(ModelComponent::class.java)?.visible = false
            renderer.hideHead = true

            // Use RagdollRenderer to draw physics-driven ragdoll (rider only, not EUC)
            renderer.activeRagdollRenderer = ragdollRenderer
            renderer.activeRagdollPhysics = ragdollPhysics
        } else {
            renderer.activeRagdollRenderer = null
            renderer.activeRagdollPhysics = null
        }

        // Get base transforms
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java)
        val eucComponent = playerEntity?.getComponent(EucComponent::class.java)

        if (playerTransform != null && eucComponent != null) {
            // Apply camera effects (always from scripted animation for consistency)
            renderer.cameraController.setShake(fallAnimationController.cameraShake)
            renderer.cameraController.setFovPunch(fallAnimationController.fovPunch)
            renderer.cameraController.setDropOffset(fallAnimationController.cameraDropOffset)
            renderer.cameraController.setForwardOffset(fallAnimationController.cameraForwardOffset)
            renderer.cameraController.setRoll(fallAnimationController.cameraRoll)

            // Use ragdoll physics for positions if available
            val usePhysicsPositions = useRagdollPhysics && ragdollPhysics != null && ragdollPhysics!!.isActive()

            if (usePhysicsPositions) {
                // Get EUC position from physics
                ragdollPhysics!!.getEucPosition(ragdollEucPos)
                playerTransform.position.set(ragdollEucPos)

                // Dynamically update world colliders as ragdoll moves
                updateWorldCollidersForRagdoll(ragdollEucPos)

                // Get rotation from physics transform matrix
                val eucTransform = ragdollPhysics!!.getEucTransform()
                if (eucTransform != null) {
                    // Extract rotation and apply to visual lean for EUC wheel
                    eucComponent.visualSideLean = extractRollFromMatrix(eucTransform) / 45f
                    eucComponent.visualForwardLean = extractPitchFromMatrix(eucTransform) / 45f
                }

                // Get torso/rider position from physics
                ragdollPhysics!!.getTorsoPosition(ragdollTorsoPos)
                riderEntity?.getComponent(TransformComponent::class.java)?.let { riderTransform ->
                    riderTransform.position.set(ragdollTorsoPos)
                    // Keep Y above ground
                    riderTransform.position.y = riderTransform.position.y.coerceAtLeast(0.3f)
                }

                // Get rider rotation from physics
                val torsoTransform = ragdollPhysics!!.getTorsoTransform()
                riderEntity?.getComponent(EucComponent::class.java)?.let { riderEuc ->
                    if (torsoTransform != null) {
                        riderEuc.visualForwardLean = eucComponent.visualForwardLean + extractPitchFromMatrix(torsoTransform) / 90f
                        riderEuc.visualSideLean = eucComponent.visualSideLean + extractRollFromMatrix(torsoTransform) / 45f
                    }
                }
            } else {
                // Fallback to scripted animation
                // Update rider position with fall animation offsets
                riderEntity?.getComponent(TransformComponent::class.java)?.let { riderTransform ->
                    riderTransform.position.set(playerTransform.position)
                    riderTransform.position.x -= 0.05f

                    val pitchCompensation = (fallAnimationController.riderPitch / 90f) * 0.5f
                    val baseY = 0.7f + fallAnimationController.riderYOffset + pitchCompensation
                    riderTransform.position.y = (playerTransform.position.y + baseY).coerceAtLeast(0.3f)

                    riderTransform.position.z += fallAnimationController.riderForwardOffset
                    riderTransform.yaw = playerTransform.yaw
                    riderTransform.updateRotationFromYaw()
                }
                riderEntity?.getComponent(EucComponent::class.java)?.let { riderEuc ->
                    val clampedPitch = fallAnimationController.riderPitch.coerceAtMost(75f)
                    riderEuc.visualForwardLean = eucComponent.visualForwardLean + clampedPitch / 90f
                    riderEuc.visualSideLean = eucComponent.visualSideLean + fallAnimationController.riderRoll / 45f
                }

                // Update EUC position with fall animation
                playerTransform.position.y = fallAnimationController.eucYOffset
                playerTransform.position.z += fallAnimationController.eucForwardOffset * delta
                playerTransform.position.x += fallAnimationController.eucSideOffset * delta
                eucComponent.visualSideLean = eucComponent.sideLean + fallAnimationController.eucRoll / 90f
            }

            // Update arm positions during fall - arms reach forward to brace for impact
            riderEntity?.getComponent(ArmComponent::class.java)?.let { arm ->
                val fallProgress = (fallAnimationController.riderPitch / 90f).coerceIn(0f, 1f)
                arm.leftArmYaw = 45f + fallProgress * 10f
                arm.leftArmPitch = -60f - fallProgress * 30f
                arm.rightArmYaw = 45f + fallProgress * 10f
                arm.rightArmPitch = -60f - fallProgress * 30f
            }
            updateArmPositions()

            // Record fall frame for replay (so we can see the crash)
            recordFallFrame(delta, playerTransform, eucComponent)

            // Keep camera following
            val cameraFollowPos = if (usePhysicsPositions) ragdollTorsoPos else playerTransform.position
            renderer.cameraController.update(cameraFollowPos, playerTransform.yaw, delta, 0f)
        }

        // Update falling pedestrians (always, not just when player ragdoll is active)
        updateFallingPedestrians()
        updateKnockedOverTrashCans()

        // Render the scene (ragdoll is rendered inside main render pass via activeRagdollRenderer)
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

            // Freeze ragdoll in place (keep visible, stop simulating)
            ragdollPhysics?.freeze()

            // Reset game over animations
            gameOverRenderer.reset()

            // Transition to game over
            stateManager.transition(GameState.GameOver(state.session))
        }
    }

    private fun renderReplay(delta: Float) {
        val state = stateManager.current() as GameState.Replay

        // Enable replay mode for systems
        cullingSystem.enabled = false
        pigeonSystem.replayMode = true
        worldGenerator.replayMode = true

        // Normal camera far distance in replay (no fog)
        renderer.camera.far = 400f
        renderer.camera.update()

        // Check if current frame has ragdoll data
        val frame = replaySystem.getCurrentFrame()
        val showRagdoll = frame?.isRagdollActive == true && frame.ragdollTransforms != null

        if (showRagdoll) {
            // Hide rider models during ragdoll - we'll render from recorded transforms
            playerEntity?.getComponent(ModelComponent::class.java)?.visible = true  // EUC wheel visible
            riderEntity?.getComponent(ModelComponent::class.java)?.visible = false
            leftArmEntity?.getComponent(ModelComponent::class.java)?.visible = false
            rightArmEntity?.getComponent(ModelComponent::class.java)?.visible = false
            renderer.hideHead = true
        } else {
            // Ensure all player/rider models are visible during non-ragdoll replay
            playerEntity?.getComponent(ModelComponent::class.java)?.visible = true
            riderEntity?.getComponent(ModelComponent::class.java)?.visible = true
            leftArmEntity?.getComponent(ModelComponent::class.java)?.visible = true
            rightArmEntity?.getComponent(ModelComponent::class.java)?.visible = true
            renderer.hideHead = false
        }

        // Disable live ragdoll physics renderers during replay (we use recorded data instead)
        renderer.activeRagdollRenderer = null
        renderer.activeRagdollPhysics = null

        // Update music (keep faded out)
        musicManager.update(delta)

        // Update replay playback
        replaySystem.updatePlayback(delta)

        // Play crash sound when replay reaches the end (crash moment)
        if (replaySystem.justReachedEnd()) {
            platformServices.playCrashSound(0.8f)
        }

        // Update pigeon animations (flying pigeons continue)
        engine.update(delta)

        // Get current interpolated frame (reuse from earlier check)
        val currentFrame = replaySystem.getCurrentFrame()
        if (currentFrame != null) {
            // Apply frame data to entities for rendering
            playerEntity?.getComponent(TransformComponent::class.java)?.let { transform ->
                transform.position.set(currentFrame.playerPosition)
                transform.yaw = currentFrame.playerYaw
                transform.updateRotationFromYaw()
            }
            playerEntity?.getComponent(EucComponent::class.java)?.let { euc ->
                euc.visualForwardLean = currentFrame.eucForwardLean
                euc.visualSideLean = currentFrame.eucSideLean
                euc.speed = currentFrame.eucSpeed
                // Apply eucRoll if we stored it (for fall animation)
            }

            // Update rider position and lean
            riderEntity?.getComponent(TransformComponent::class.java)?.let { transform ->
                transform.position.set(currentFrame.playerPosition)
                transform.position.x -= 0.05f
                transform.position.y += 0.7f
                transform.yaw = currentFrame.playerYaw
                transform.updateRotationFromYaw()
            }
            riderEntity?.getComponent(EucComponent::class.java)?.let { euc ->
                euc.visualForwardLean = currentFrame.riderVisualForwardLean
                euc.visualSideLean = currentFrame.riderVisualSideLean
                euc.speed = currentFrame.eucSpeed
            }

            // Update head animation
            riderEntity?.getComponent(HeadComponent::class.java)?.let { head ->
                head.yaw = currentFrame.headYaw
                head.pitch = currentFrame.headPitch
                head.roll = currentFrame.headRoll
            }

            // Update arm positions
            riderEntity?.getComponent(ArmComponent::class.java)?.let { arm ->
                arm.leftArmPitch = currentFrame.leftArmPitch
                arm.leftArmYaw = currentFrame.leftArmYaw
                arm.rightArmPitch = currentFrame.rightArmPitch
                arm.rightArmYaw = currentFrame.rightArmYaw
            }

            // Free camera control - user drags to rotate around player
            updateReplayCamera(delta, currentFrame)

            // Update world generator to fill in chunks around player position (for 360 view)
            worldGenerator.update(currentFrame.playerPosition.z, state.session.distanceTraveled)
        }

        // Reset post-processing effects for clear replay view
        renderer.postProcessing.blurStrength = 0f
        renderer.postProcessing.dangerTint = 0f
        renderer.postProcessing.vignetteDanger = 0f

        // Render the scene
        renderer.render()

        // Render ragdoll from recorded transforms if active
        val ragdollTransforms = frame?.ragdollTransforms
        if (showRagdoll && ragdollTransforms != null) {
            ragdollRenderer?.let { rr ->
                renderer.renderRagdollFromTransforms(rr, ragdollTransforms)
            }
        }

        // Render replay UI
        val result = replayRenderer.render(replaySystem)
        when (result.action) {
            ReplayRenderer.Action.EXIT -> {
                replaySystem.stopPlayback()
                replayRenderer.reset()
                gameOverRenderer.reset()
                // Reset systems from replay mode
                cullingSystem.enabled = true
                pigeonSystem.replayMode = false
                worldGenerator.replayMode = false
                motorSoundManager.stop()
                stateManager.transition(GameState.GameOver(state.session))
            }
            ReplayRenderer.Action.TOGGLE_PAUSE -> {
                replaySystem.togglePause()
            }
            ReplayRenderer.Action.TOGGLE_SLOWMO -> {
                replaySystem.toggleSlowMo()
            }
            ReplayRenderer.Action.SEEK -> {
                replaySystem.seekTo(result.seekPosition)
            }
            ReplayRenderer.Action.NONE -> {}
        }
    }

    // Replay camera variables
    private var replayCameraYaw = 0f
    private var replayCameraPitch = 20f
    private var replayCameraDistance = 5f
    private val replayCameraMinDistance = 2f
    private val replayCameraMaxDistance = 15f
    private var lastReplayTouchX = 0f
    private var lastReplayTouchY = 0f
    private var isReplayDragging = false
    private var lastPinchDistance = 0f
    private var isPinching = false

    private fun updateReplayCamera(delta: Float, frame: com.eucleantoomuch.game.replay.ReplayFrame) {
        val sh = Gdx.graphics.height.toFloat()

        // Check if touch is in the middle area (not on UI controls at top/bottom)
        val topBarHeight = 70f * (sh / 720f)
        val bottomBarHeight = 140f * (sh / 720f)

        // Handle pinch-to-zoom (two fingers)
        if (Gdx.input.isTouched(0) && Gdx.input.isTouched(1)) {
            val x0 = Gdx.input.getX(0).toFloat()
            val y0 = Gdx.input.getY(0).toFloat()
            val x1 = Gdx.input.getX(1).toFloat()
            val y1 = Gdx.input.getY(1).toFloat()

            val currentDistance = kotlin.math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))

            if (isPinching) {
                // Calculate zoom change based on pinch delta
                val pinchDelta = currentDistance - lastPinchDistance
                replayCameraDistance -= pinchDelta * 0.015f
                replayCameraDistance = replayCameraDistance.coerceIn(replayCameraMinDistance, replayCameraMaxDistance)
            }

            lastPinchDistance = currentDistance
            isPinching = true
            isReplayDragging = false  // Don't rotate while pinching
        } else {
            isPinching = false

            // Single finger drag for rotation
            val touchX = Gdx.input.x.toFloat()
            val touchY = Gdx.input.y.toFloat()
            val isInCameraZone = touchY > topBarHeight && touchY < (sh - bottomBarHeight)

            if (Gdx.input.isTouched && isInCameraZone) {
                if (!isReplayDragging) {
                    isReplayDragging = true
                    lastReplayTouchX = touchX
                    lastReplayTouchY = touchY
                } else {
                    val deltaX = touchX - lastReplayTouchX
                    val deltaY = touchY - lastReplayTouchY

                    // Rotate camera around player (full 360 degrees)
                    replayCameraYaw -= deltaX * 0.3f
                    replayCameraPitch += deltaY * 0.2f
                    replayCameraPitch = replayCameraPitch.coerceIn(-10f, 60f)

                    lastReplayTouchX = touchX
                    lastReplayTouchY = touchY
                }
            } else {
                isReplayDragging = false
            }
        }

        // Determine target position - follow rider/torso, not the wheel
        val targetX: Float
        val targetY: Float
        val targetZ: Float

        if (frame.isRagdollActive && frame.ragdollTransforms != null) {
            // During ragdoll - follow the torso position from recorded transforms
            val torsoPos = Vector3()
            frame.ragdollTransforms.torso.getTranslation(torsoPos)
            targetX = torsoPos.x
            targetY = torsoPos.y
            targetZ = torsoPos.z
        } else {
            // Normal riding - follow rider position (above the wheel)
            targetX = frame.playerPosition.x
            targetY = frame.playerPosition.y + 1.2f  // Rider height above wheel
            targetZ = frame.playerPosition.z
        }

        // Calculate camera position orbiting around target
        val yawRad = Math.toRadians(replayCameraYaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(replayCameraPitch.toDouble()).toFloat()

        val camX = targetX + replayCameraDistance * kotlin.math.sin(yawRad) * kotlin.math.cos(pitchRad)
        val camY = targetY + 0.5f + replayCameraDistance * kotlin.math.sin(pitchRad)
        val camZ = targetZ - replayCameraDistance * kotlin.math.cos(yawRad) * kotlin.math.cos(pitchRad)

        // Update camera to look at target
        renderer.cameraController.setReplayCamera(
            camX, camY, camZ,
            targetX, targetY, targetZ
        )
    }

    private fun recordReplayFrame(delta: Float, playerTransform: TransformComponent, eucComponent: EucComponent) {
        val riderEuc = riderEntity?.getComponent(EucComponent::class.java)
        val headComponent = riderEntity?.getComponent(HeadComponent::class.java)
        val armComponent = riderEntity?.getComponent(ArmComponent::class.java)

        replaySystem.recordFrame(
            delta = delta,
            playerPos = playerTransform.position,
            playerYaw = playerTransform.yaw,
            eucForwardLean = eucComponent.visualForwardLean,
            eucSideLean = eucComponent.visualSideLean,
            eucSpeed = eucComponent.speed,
            eucRoll = eucComponent.visualSideLean,  // Use visual lean for EUC roll
            riderForwardLean = riderEuc?.visualForwardLean ?: 0f,
            riderSideLean = riderEuc?.visualSideLean ?: 0f,
            headYaw = headComponent?.yaw ?: 0f,
            headPitch = headComponent?.pitch ?: 0f,
            headRoll = headComponent?.roll ?: 0f,
            leftArmPitch = armComponent?.leftArmPitch ?: 0f,
            leftArmYaw = armComponent?.leftArmYaw ?: 0f,
            rightArmPitch = armComponent?.rightArmPitch ?: 0f,
            rightArmYaw = armComponent?.rightArmYaw ?: 0f,
            cameraPos = renderer.cameraController.getCameraPosition(),
            cameraYaw = renderer.cameraController.getCameraYaw()
        )
    }

    private fun recordFallFrame(delta: Float, playerTransform: TransformComponent, eucComponent: EucComponent) {
        val riderEuc = riderEntity?.getComponent(EucComponent::class.java)
        val headComponent = riderEntity?.getComponent(HeadComponent::class.java)
        val armComponent = riderEntity?.getComponent(ArmComponent::class.java)

        // Capture ragdoll transforms if ragdoll is active
        val ragdollActive = useRagdollPhysics && ragdollPhysics != null && ragdollPhysics!!.isActive()
        val ragdollTransforms = if (ragdollActive) {
            captureRagdollTransforms()
        } else null

        // Use torso position from ragdoll if active, otherwise use player transform
        val recordPos = if (ragdollActive && ragdollTransforms != null) {
            val torsoPos = Vector3()
            ragdollTransforms.torso.getTranslation(torsoPos)
            torsoPos
        } else {
            playerTransform.position
        }

        replaySystem.recordFrame(
            delta = delta,
            playerPos = recordPos,
            playerYaw = playerTransform.yaw,
            eucForwardLean = eucComponent.visualForwardLean,
            eucSideLean = eucComponent.visualSideLean,
            eucSpeed = eucComponent.speed,
            eucRoll = eucComponent.visualSideLean,  // EUC roll during fall
            riderForwardLean = riderEuc?.visualForwardLean ?: 0f,
            riderSideLean = riderEuc?.visualSideLean ?: 0f,
            headYaw = headComponent?.yaw ?: 0f,
            headPitch = headComponent?.pitch ?: 0f,
            headRoll = headComponent?.roll ?: 0f,
            leftArmPitch = armComponent?.leftArmPitch ?: 0f,
            leftArmYaw = armComponent?.leftArmYaw ?: 0f,
            rightArmPitch = armComponent?.rightArmPitch ?: 0f,
            rightArmYaw = armComponent?.rightArmYaw ?: 0f,
            cameraPos = renderer.cameraController.getCameraPosition(),
            cameraYaw = renderer.cameraController.getCameraYaw(),
            isRagdollActive = ragdollActive,
            ragdollTransforms = ragdollTransforms
        )
    }

    /**
     * Capture current ragdoll transforms for replay recording.
     */
    private fun captureRagdollTransforms(): com.eucleantoomuch.game.replay.ReplayFrame.RagdollTransforms? {
        val physics = ragdollPhysics ?: return null
        if (!physics.isActive()) return null

        return com.eucleantoomuch.game.replay.ReplayFrame.RagdollTransforms(
            eucWheel = com.badlogic.gdx.math.Matrix4(physics.getEucTransform() ?: com.badlogic.gdx.math.Matrix4()),
            head = com.badlogic.gdx.math.Matrix4(physics.getHeadTransform() ?: com.badlogic.gdx.math.Matrix4()),
            torso = com.badlogic.gdx.math.Matrix4(physics.getTorsoTransform() ?: com.badlogic.gdx.math.Matrix4()),
            leftUpperArm = com.badlogic.gdx.math.Matrix4(physics.getLeftUpperArmTransform() ?: com.badlogic.gdx.math.Matrix4()),
            leftLowerArm = com.badlogic.gdx.math.Matrix4(physics.getLeftLowerArmTransform() ?: com.badlogic.gdx.math.Matrix4()),
            rightUpperArm = com.badlogic.gdx.math.Matrix4(physics.getRightUpperArmTransform() ?: com.badlogic.gdx.math.Matrix4()),
            rightLowerArm = com.badlogic.gdx.math.Matrix4(physics.getRightLowerArmTransform() ?: com.badlogic.gdx.math.Matrix4()),
            leftUpperLeg = com.badlogic.gdx.math.Matrix4(physics.getLeftUpperLegTransform() ?: com.badlogic.gdx.math.Matrix4()),
            leftLowerLeg = com.badlogic.gdx.math.Matrix4(physics.getLeftLowerLegTransform() ?: com.badlogic.gdx.math.Matrix4()),
            rightUpperLeg = com.badlogic.gdx.math.Matrix4(physics.getRightUpperLegTransform() ?: com.badlogic.gdx.math.Matrix4()),
            rightLowerLeg = com.badlogic.gdx.math.Matrix4(physics.getRightLowerLegTransform() ?: com.badlogic.gdx.math.Matrix4())
        )
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
        replayRenderer.resize(width, height)
        if (DebugConfig.DEBUG_MENU_ENABLED) {
            debugMenu.resize(width, height)
        }
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
        replayRenderer.recreate()
        if (DebugConfig.DEBUG_MENU_ENABLED) {
            debugMenu.recreate()
        }
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
        replayRenderer.dispose()
        ragdollPhysics?.dispose()
        ragdollRenderer?.dispose()
        if (DebugConfig.DEBUG_MENU_ENABLED) {
            debugMenu.dispose()
            debugShapeRenderer.dispose()
        }
    }

    // Add physics colliders for nearby world objects during ragdoll
    private val colliderMapper = com.badlogic.ashley.core.ComponentMapper.getFor(
        com.eucleantoomuch.game.ecs.components.ColliderComponent::class.java
    )
    private val obstacleMapper = com.badlogic.ashley.core.ComponentMapper.getFor(
        com.eucleantoomuch.game.ecs.components.ObstacleComponent::class.java
    )
    private val transformMapperForCollider = com.badlogic.ashley.core.ComponentMapper.getFor(
        TransformComponent::class.java
    )
    private val tempColliderPos = com.badlogic.gdx.math.Vector3()
    private val tempHalfExtents = com.badlogic.gdx.math.Vector3()

    private fun addWorldCollidersForRagdoll(playerPos: com.badlogic.gdx.math.Vector3) {
        val physics = ragdollPhysics ?: return

        // Search radius for nearby objects
        val searchRadius = 20f
        val searchRadiusSq = searchRadius * searchRadius

        var colliderCount = 0

        // Add colliders for ALL collidable objects (includes obstacles, buildings, cars, etc.)
        // Use index-based loop to avoid nested iterator issue with GDX Array
        val collidables = engine.getEntitiesFor(Families.collidable)
        for (i in 0 until collidables.size()) {
            val entity = collidables[i]

            // Skip player and rider entities - they shouldn't collide with themselves
            if (entity == playerEntity || entity == riderEntity) continue

            val transform = transformMapperForCollider.get(entity) ?: continue
            val collider = colliderMapper.get(entity) ?: continue

            // Skip very small colliders
            if (collider.halfExtents.len() < 0.1f) continue

            // Check if within range
            val dx = transform.position.x - playerPos.x
            val dz = transform.position.z - playerPos.z
            val distSq = dx * dx + dz * dz
            if (distSq > searchRadiusSq) continue

            // Get collider center position (adjust Y to center of collider)
            tempColliderPos.set(
                transform.position.x,
                transform.position.y + collider.halfExtents.y,
                transform.position.z
            )
            tempHalfExtents.set(collider.halfExtents)

            // Check obstacle type for special handling
            val obstacle = obstacleMapper.get(entity)
            val colliderType = obstacleTypeToColliderType(obstacle?.type)

            if (obstacle != null && obstacle.type == ObstacleType.STREET_LIGHT) {
                // Street lights are thin cylinders
                physics.addCylinderCollider(
                    tempColliderPos,
                    0.15f,  // thin pole
                    collider.halfExtents.y * 2f,
                    colliderType
                )
            } else {
                // Default: box collider
                physics.addBoxCollider(tempColliderPos, tempHalfExtents, transform.yaw, colliderType)
            }
            colliderCount++
        }

        // Also add colliders for moving cars (they may not have ColliderComponent in collidable family)
        val cars = engine.getEntitiesFor(Families.cars)
        for (i in 0 until cars.size()) {
            val entity = cars[i]
            val transform = transformMapperForCollider.get(entity) ?: continue

            // Check if within range
            val dx = transform.position.x - playerPos.x
            val dz = transform.position.z - playerPos.z
            val distSq = dx * dx + dz * dz
            if (distSq > searchRadiusSq) continue

            // Car dimensions
            val collider = colliderMapper.get(entity)
            if (collider != null) {
                tempColliderPos.set(
                    transform.position.x,
                    transform.position.y + collider.halfExtents.y,
                    transform.position.z
                )
                physics.addBoxCollider(tempColliderPos, collider.halfExtents, transform.yaw, RagdollPhysics.ColliderType.CAR)
            } else {
                // Default car size if no collider
                tempColliderPos.set(transform.position.x, transform.position.y + 0.7f, transform.position.z)
                tempHalfExtents.set(1.0f, 0.7f, 2.2f)
                physics.addBoxCollider(tempColliderPos, tempHalfExtents, transform.yaw, RagdollPhysics.ColliderType.CAR)
            }
            colliderCount++
        }

        Gdx.app.log("EucGame", "Added $colliderCount world colliders for ragdoll physics")
    }

    // Track entities that already have colliders added (to avoid duplicates)
    private val addedColliderEntities = mutableSetOf<com.badlogic.ashley.core.Entity>()
    private var lastColliderUpdateZ = Float.MIN_VALUE

    /**
     * Dynamically update world colliders as ragdoll moves forward.
     * Only adds NEW colliders for objects that weren't in range before.
     */
    private fun updateWorldCollidersForRagdoll(ragdollPos: com.badlogic.gdx.math.Vector3) {
        val physics = ragdollPhysics ?: return

        // Only update every 5 meters of forward movement to avoid constant updates
        if (ragdollPos.z - lastColliderUpdateZ < 5f) return
        lastColliderUpdateZ = ragdollPos.z

        // Search radius for nearby objects
        val searchRadius = 25f
        val searchRadiusSq = searchRadius * searchRadius

        var newColliderCount = 0

        // Check all collidable objects
        val collidables = engine.getEntitiesFor(Families.collidable)
        for (i in 0 until collidables.size()) {
            val entity = collidables[i]

            // Skip if already added
            if (entity in addedColliderEntities) continue

            // Skip player and rider entities
            if (entity == playerEntity || entity == riderEntity) continue

            val transform = transformMapperForCollider.get(entity) ?: continue
            val collider = colliderMapper.get(entity) ?: continue

            // Skip very small colliders
            if (collider.halfExtents.len() < 0.1f) continue

            // Check if within range
            val dx = transform.position.x - ragdollPos.x
            val dz = transform.position.z - ragdollPos.z
            val distSq = dx * dx + dz * dz
            if (distSq > searchRadiusSq) continue

            // Get collider center position
            tempColliderPos.set(
                transform.position.x,
                transform.position.y + collider.halfExtents.y,
                transform.position.z
            )
            tempHalfExtents.set(collider.halfExtents)

            // Check obstacle type for special handling
            val obstacle = obstacleMapper.get(entity)
            val colliderType = obstacleTypeToColliderType(obstacle?.type)

            if (obstacle != null && obstacle.type == ObstacleType.STREET_LIGHT) {
                physics.addCylinderCollider(tempColliderPos, 0.15f, collider.halfExtents.y * 2f, colliderType)
            } else {
                physics.addBoxCollider(tempColliderPos, tempHalfExtents, transform.yaw, colliderType)
            }

            addedColliderEntities.add(entity)
            newColliderCount++
        }

        // Also check cars
        val cars = engine.getEntitiesFor(Families.cars)
        for (i in 0 until cars.size()) {
            val entity = cars[i]
            if (entity in addedColliderEntities) continue

            val transform = transformMapperForCollider.get(entity) ?: continue
            val collider = colliderMapper.get(entity) ?: continue

            val dx = transform.position.x - ragdollPos.x
            val dz = transform.position.z - ragdollPos.z
            val distSq = dx * dx + dz * dz
            if (distSq > searchRadiusSq) continue

            tempColliderPos.set(
                transform.position.x,
                transform.position.y + collider.halfExtents.y,
                transform.position.z
            )
            physics.addBoxCollider(tempColliderPos, collider.halfExtents, transform.yaw, RagdollPhysics.ColliderType.CAR)

            addedColliderEntities.add(entity)
            newColliderCount++
        }

        if (newColliderCount > 0) {
            Gdx.app.log("EucGame", "Added $newColliderCount new colliders during ragdoll flight")
        }
    }

    /**
     * Reset collider tracking when starting new ragdoll.
     */
    private fun resetColliderTracking() {
        addedColliderEntities.clear()
        lastColliderUpdateZ = Float.MIN_VALUE
    }

    // Temp matrix for pedestrian transform updates
    private val pedestrianTempMatrix = com.badlogic.gdx.math.Matrix4()
    private val pedestrianTempPos = com.badlogic.gdx.math.Vector3()

    /**
     * Update positions of falling pedestrians from ragdoll physics.
     * Hides original model and uses ragdoll renderer for articulated body.
     */
    private fun updateFallingPedestrians() {
        if (ragdollPhysics == null) return

        val pedestrians = engine.getEntitiesFor(Families.pedestrians)
        for (i in 0 until pedestrians.size()) {
            val entity = pedestrians[i]
            val pedestrianComponent = entity.getComponent(
                com.eucleantoomuch.game.ecs.components.PedestrianComponent::class.java
            ) ?: continue

            if (!pedestrianComponent.isRagdolling) continue

            val transform = entity.getComponent(TransformComponent::class.java) ?: continue
            val modelComponent = entity.getComponent(ModelComponent::class.java) ?: continue

            // Hide original pedestrian model - ragdoll renderer will draw the articulated body
            modelComponent.visible = false

            // Get physics transform (torso position)
            val physicsTransform = ragdollPhysics!!.getPedestrianTransform(pedestrianComponent.ragdollBodyIndex)
            if (physicsTransform != null) {
                // Extract position from physics
                physicsTransform.getTranslation(pedestrianTempPos)

                // Update entity transform position (for culling/tracking)
                transform.position.set(pedestrianTempPos)
                // Offset Y down by half torso height since physics center is at torso
                transform.position.y = pedestrianTempPos.y - 0.25f
            }
        }
    }

    // Temp matrix for dynamic object (trash can) transform updates
    private val trashCanTempMatrix = com.badlogic.gdx.math.Matrix4()
    private val trashCanTempPos = com.badlogic.gdx.math.Vector3()

    /**
     * Update positions and render knocked over trash cans using ragdoll physics transforms.
     */
    private fun updateKnockedOverTrashCans() {
        if (ragdollPhysics == null) return

        val obstacles = engine.getEntitiesFor(Families.obstacles)
        for (i in 0 until obstacles.size()) {
            val entity = obstacles[i]
            val obstacleComponent = entity.getComponent(
                com.eucleantoomuch.game.ecs.components.ObstacleComponent::class.java
            ) ?: continue

            if (!obstacleComponent.isKnockedOver || obstacleComponent.ragdollBodyIndex < 0) continue

            val transform = entity.getComponent(TransformComponent::class.java) ?: continue
            val modelComponent = entity.getComponent(ModelComponent::class.java) ?: continue

            // Get physics transform
            val physicsTransform = ragdollPhysics!!.getDynamicObjectTransform(obstacleComponent.ragdollBodyIndex)
            if (physicsTransform != null) {
                // Update model instance transform directly from physics
                modelComponent.modelInstance?.transform?.set(physicsTransform)

                // Also update entity position for culling
                physicsTransform.getTranslation(trashCanTempPos)
                transform.position.set(trashCanTempPos)

                // Make model visible again (it's now controlled by physics)
                modelComponent.visible = true
            }
        }
    }

    /**
     * Play appropriate sound when ragdoll collides with an object during flight.
     */
    private fun playRagdollCollisionSound(colliderType: RagdollPhysics.ColliderType) {
        when (colliderType) {
            RagdollPhysics.ColliderType.STREET_LIGHT -> platformServices.playStreetLightImpactSound()
            RagdollPhysics.ColliderType.RECYCLE_BIN -> platformServices.playRecycleBinImpactSound()
            RagdollPhysics.ColliderType.CAR -> platformServices.playCarCrashSound()
            RagdollPhysics.ColliderType.PEDESTRIAN -> platformServices.playPersonImpactSound()
            RagdollPhysics.ColliderType.GENERIC -> platformServices.playGenericHitSound()
            RagdollPhysics.ColliderType.GROUND -> { /* Ground impacts handled by fall animation */ }
        }
    }

    /**
     * Play quieter sound when pedestrian ragdoll (secondary) collides with an object.
     * These are "chain reaction" sounds - quieter than direct player collisions.
     */
    private fun playSecondaryRagdollCollisionSound(colliderType: RagdollPhysics.ColliderType) {
        // Play same sounds but at lower volume (0.4x)
        when (colliderType) {
            RagdollPhysics.ColliderType.STREET_LIGHT -> platformServices.playStreetLightImpactSound(0.4f)
            RagdollPhysics.ColliderType.RECYCLE_BIN -> platformServices.playRecycleBinImpactSound(0.4f)
            RagdollPhysics.ColliderType.CAR -> platformServices.playCarCrashSound(0.4f)
            RagdollPhysics.ColliderType.PEDESTRIAN -> platformServices.playPersonImpactSound(0.4f)
            RagdollPhysics.ColliderType.GENERIC -> platformServices.playGenericHitSound(0.4f)
            RagdollPhysics.ColliderType.GROUND -> { /* Ground impacts not needed for secondary */ }
        }
    }

    /**
     * Convert ObstacleType to RagdollPhysics.ColliderType for physics collisions.
     */
    private fun obstacleTypeToColliderType(obstacleType: ObstacleType?): RagdollPhysics.ColliderType {
        return when (obstacleType) {
            ObstacleType.STREET_LIGHT -> RagdollPhysics.ColliderType.STREET_LIGHT
            ObstacleType.RECYCLE_BIN -> RagdollPhysics.ColliderType.RECYCLE_BIN
            ObstacleType.CAR -> RagdollPhysics.ColliderType.CAR
            ObstacleType.PEDESTRIAN -> RagdollPhysics.ColliderType.PEDESTRIAN
            else -> RagdollPhysics.ColliderType.GENERIC
        }
    }

    // Helper functions to extract rotation angles from transformation matrix
    private fun extractRollFromMatrix(matrix: com.badlogic.gdx.math.Matrix4): Float {
        // Extract roll (rotation around Z axis) from matrix
        // matrix.val indices: 0,1,2,3 = first column, etc.
        val m00 = matrix.`val`[com.badlogic.gdx.math.Matrix4.M00]
        val m10 = matrix.`val`[com.badlogic.gdx.math.Matrix4.M10]
        return Math.toDegrees(kotlin.math.atan2(m10.toDouble(), m00.toDouble())).toFloat()
    }

    private fun extractPitchFromMatrix(matrix: com.badlogic.gdx.math.Matrix4): Float {
        // Extract pitch (rotation around X axis) from matrix
        val m21 = matrix.`val`[com.badlogic.gdx.math.Matrix4.M21]
        val m22 = matrix.`val`[com.badlogic.gdx.math.Matrix4.M22]
        return Math.toDegrees(kotlin.math.atan2(m21.toDouble(), m22.toDouble())).toFloat()
    }
}
