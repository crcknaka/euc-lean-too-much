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
import com.eucleantoomuch.game.ecs.components.PowerupType
import com.eucleantoomuch.game.ecs.components.TransformComponent
import com.eucleantoomuch.game.ecs.systems.*
import com.eucleantoomuch.game.feedback.FallAnimationController
import com.eucleantoomuch.game.feedback.MotorSoundManager
import com.eucleantoomuch.game.feedback.MusicManager
import com.eucleantoomuch.game.feedback.SpeedWarningManager
import com.eucleantoomuch.game.input.AccelerometerInput
import com.eucleantoomuch.game.input.GameInput
import com.eucleantoomuch.game.input.KeyboardInput
import com.eucleantoomuch.game.input.SwitchableGameInput
import com.eucleantoomuch.game.input.TouchOrKeyboardInput
import com.eucleantoomuch.game.input.TouchSteerInput
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
import com.eucleantoomuch.game.state.VoltsManager
import com.eucleantoomuch.game.state.TimeTrialManager
import com.eucleantoomuch.game.state.TimeTrialLevel
import com.eucleantoomuch.game.ui.CalibrationRenderer
import com.eucleantoomuch.game.ui.CreditsRenderer
import com.eucleantoomuch.game.ui.HelpRenderer
import com.eucleantoomuch.game.ui.GameOverRenderer
import com.eucleantoomuch.game.ui.Hud
import com.eucleantoomuch.game.ui.MenuRenderer
import com.eucleantoomuch.game.ui.PauseRenderer
import com.eucleantoomuch.game.ui.SettingsRenderer
import com.eucleantoomuch.game.ui.UIFeedback
import com.eucleantoomuch.game.ui.UIFonts
import com.eucleantoomuch.game.ui.WheelSelectionRenderer
import com.eucleantoomuch.game.ui.ModeSelectionRenderer
import com.eucleantoomuch.game.ui.TimeTrialLevelRenderer
import com.eucleantoomuch.game.physics.RagdollEngine
import com.eucleantoomuch.game.physics.RagdollInteractions
import com.eucleantoomuch.game.physics.RagdollRenderer
import com.eucleantoomuch.game.physics.RagdollTypes

class EucGame(
    private val platformServices: PlatformServices = DefaultPlatformServices()
) : ApplicationAdapter() {
    private lateinit var engine: Engine
    private lateinit var gameInput: SwitchableGameInput
    private lateinit var accelerometerInput: AccelerometerInput
    private lateinit var touchSteerInput: TouchSteerInput
    private lateinit var keyboardInput: KeyboardInput
    private lateinit var touchOrKeyboardInput: TouchOrKeyboardInput
    private lateinit var tiltProvider: com.eucleantoomuch.game.input.TiltProvider
    private lateinit var models: ProceduralModels
    private lateinit var renderer: GameRenderer
    private lateinit var worldGenerator: WorldGenerator
    private lateinit var entityFactory: EntityFactory
    private lateinit var stateManager: GameStateManager
    private lateinit var highScoreManager: HighScoreManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var voltsManager: VoltsManager
    private lateinit var timeTrialManager: TimeTrialManager

    // UI Renderers
    private lateinit var hud: Hud
    private lateinit var menuRenderer: MenuRenderer
    private lateinit var gameOverRenderer: GameOverRenderer
    private lateinit var pauseRenderer: PauseRenderer
    private lateinit var calibrationRenderer: CalibrationRenderer
    private lateinit var settingsRenderer: SettingsRenderer
    private lateinit var creditsRenderer: CreditsRenderer
    private lateinit var helpRenderer: HelpRenderer
    private lateinit var wheelSelectionRenderer: WheelSelectionRenderer
    private lateinit var modeSelectionRenderer: ModeSelectionRenderer
    private lateinit var timeTrialLevelRenderer: TimeTrialLevelRenderer

    // Game state
    private var session = GameSession()
    private var playerEntity: Entity? = null
    private var riderEntity: Entity? = null
    private var leftArmEntity: Entity? = null
    private var rightArmEntity: Entity? = null
    private var countdownTimer = 3f
    private var lastCountdownSecond = -1  // Track last displayed second for beep
    private var isNewHighScore = false
    private var timeTrialResult: GameOverRenderer.TimeTrialResult? = null
    private var pendingHardcoreMode = false  // Flag to start hardcore mode after wheel selection
    private var pendingNightHardcoreMode = false  // Flag to start night hardcore mode after wheel selection

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
    private var ragdollPhysics: com.eucleantoomuch.game.physics.RagdollEngine? = null
    private var ragdollRenderer: com.eucleantoomuch.game.physics.RagdollRenderer? = null
    private var useRagdollPhysics = true  // Toggle for ragdoll vs scripted animation
    private lateinit var ragdollInteractions: RagdollInteractions

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

    // Guards pause/resume/dispose against firing before create() has built the subsystems
    private var created = false

    override fun create() {
        Gdx.app.logLevel = Application.LOG_DEBUG

        // Disable libGDX foreground FPS limit to allow high refresh rates (120Hz+)
        // By default libGDX limits to 60 FPS on Android
        // Note: 0 means "use default" in libGDX, so we set a high value instead
        raiseForegroundFpsCap()

        // Initialize state manager
        stateManager = GameStateManager()
        highScoreManager = HighScoreManager()
        settingsManager = SettingsManager()
        voltsManager = VoltsManager()
        timeTrialManager = TimeTrialManager()

        // Initialize input based on platform
        tiltProvider = platformServices.createTiltProvider()
        accelerometerInput = AccelerometerInput(tiltProvider)
        touchSteerInput = TouchSteerInput()
        keyboardInput = KeyboardInput()
        touchOrKeyboardInput = TouchOrKeyboardInput(touchSteerInput, keyboardInput)
        // Wrapped, because the answer can change after startup: a browser only starts
        // reporting tilt once the player has granted permission.
        gameInput = SwitchableGameInput(pickInput())

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
                ObstacleType.MANHOLE -> {
                    platformServices.playManholeSound()
                    voltsManager.onManholeHit()
                }
                ObstacleType.PUDDLE -> {
                    platformServices.playWaterSplashSound()
                    playerTransformOrNull()?.let { renderer.particles.splash(it.position, playerSpeed()) }
                }
                ObstacleType.STREET_LIGHT -> platformServices.playStreetLightImpactSound()
                ObstacleType.RECYCLE_BIN -> platformServices.playRecycleBinImpactSound()
                ObstacleType.PEDESTRIAN -> platformServices.playPersonImpactSound()
                ObstacleType.CAR -> platformServices.playCarCrashSound()
                ObstacleType.BENCH -> platformServices.playBenchImpactSound()
                ObstacleType.CURB, ObstacleType.POTHOLE -> platformServices.playGenericHitSound()
            }
            // Anything solid throws debris and jolts the camera; a puddle got its splash above
            if (obstacleType != ObstacleType.PUDDLE) {
                val strength = (playerSpeed() / 12f).coerceIn(0.5f, 1.5f)
                playerTransformOrNull()?.let { renderer.particles.impact(it.position, strength) }
                renderer.cameraController.kick(0.08f * strength)
            } else {
                renderer.cameraController.kick(0.025f)
            }
            if (causesGameOver) {
                handlePlayerFall()
            }
        }
        collisionSystem.onNearMiss = { obstacleType ->
            // Trigger near miss feedback (visual + sound + haptic)
            hud.triggerNearMiss()
            UIFeedback.nearMiss()
            session.nearMisses++

            // Award Volts for near miss
            val isCar = obstacleType == ObstacleType.CAR
            val voltsEarned = voltsManager.awardNearMiss(isCar)
            val reason = if (isCar) "car" else "ped"
            hud.triggerVoltsEarned(voltsEarned, reason)
        }
        collisionSystem.onPedestrianHit = { pedestrianEntity ->
            // Start ragdoll physics for the hit pedestrian
            ragdollInteractions.startPedestrianRagdoll(pedestrianEntity)
        }
        collisionSystem.onKnockableHit = { obstacleEntity ->
            // Start ragdoll physics for knockable objects (trash cans)
            ragdollInteractions.startTrashCanRagdoll(obstacleEntity)
        }
        collisionSystem.onPowerupCollected = { powerupComponent ->
            when (powerupComponent.type) {
                PowerupType.BATTERY -> {
                    // Restore battery and play sound
                    session.restoreBattery(powerupComponent.batteryRestoreAmount)
                    platformServices.playPowerupSound()
                }
                PowerupType.VOLTS -> {
                    // Award Volts currency
                    val voltsEarned = voltsManager.awardPickup()
                    hud.triggerVoltsEarned(voltsEarned, "pickup")
                    platformServices.playPowerupSound()
                }
            }
        }

        pigeonSystem = PigeonSystem(models, platformServices)
        pigeonSystem.onFlockStartled = {
            val voltsEarned = voltsManager.awardPigeonStartle()
            hud.triggerVoltsEarned(voltsEarned, "Pigeons")
        }
        ragdollInteractions = RagdollInteractions(
            engine = engine,
            platformServices = platformServices,
            pigeonSystem = pigeonSystem,
            ragdollPhysics = { ragdollPhysics },
            playerEntity = { playerEntity },
            useRagdollPhysics = { useRagdollPhysics }
        )
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
        engine.addSystem(PowerupAnimationSystem())
        engine.addSystem(LampFlickerSystem())
        engine.addSystem(collisionSystem)
        engine.addSystem(cullingSystem)

        // Initialize UI
        hud = Hud(settingsManager)
        menuRenderer = MenuRenderer()
        menuRenderer.showExit = platformServices.canExit()
        gameOverRenderer = GameOverRenderer()
        pauseRenderer = PauseRenderer()
        calibrationRenderer = CalibrationRenderer()
        settingsRenderer = SettingsRenderer(settingsManager)
        creditsRenderer = CreditsRenderer()
        helpRenderer = HelpRenderer()
        wheelSelectionRenderer = WheelSelectionRenderer(settingsManager, voltsManager)
        modeSelectionRenderer = ModeSelectionRenderer(highScoreManager)
        timeTrialLevelRenderer = TimeTrialLevelRenderer(timeTrialManager)

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
            ragdollPhysics = platformServices.createRagdollEngine()
            ragdollRenderer = com.eucleantoomuch.game.physics.RagdollRenderer()

            // Set up collision callback for ragdoll hitting objects during flight
            ragdollPhysics?.onRagdollCollision = { colliderType ->
                playRagdollCollisionSound(colliderType)
            }

            // Set up secondary collision callback for pedestrian ragdolls hitting objects (quieter)
            ragdollPhysics?.onSecondaryRagdollCollision = { colliderType ->
                playSecondaryRagdollCollisionSound(colliderType)
            }

            // Set up ground impact callback to startle pigeons when ragdoll lands nearby
            ragdollPhysics?.onRagdollGroundImpact = { impactPosition ->
                pigeonSystem.addStartleSource(impactPosition)
                renderer.particles.groundPuff(impactPosition)
                renderer.cameraController.kick(0.09f)
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
        musicManager.initialize { platformServices.createMusic(it) }
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

        created = true
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

    /**
     * Lift libGDX's foreground FPS cap so 120 Hz screens are not limited to 60.
     *
     * Guarded because it is a desktop/Android-only concept: the web backend throws from
     * setForegroundFPS, and this runs as the very first statement of create(), so an
     * unguarded call killed the browser build before a single line was even logged.
     */
    /**
     * Best steering source available right now: the tilt sensor if there is one, otherwise a
     * finger on a touch screen, otherwise the keyboard.
     */
    private fun pickInput(): GameInput = when {
        tiltProvider.isAvailable() -> accelerometerInput
        platformServices.hasTouchScreen() -> touchOrKeyboardInput
        else -> keyboardInput
    }

    /**
     * Clear to the palette's own background rather than an arbitrary slate blue. Menu screens
     * paint their own backdrop over it, but anything they do not cover - and the moment before
     * they draw - used to flash a washed-out blue that belongs to no part of the design.
     */
    private fun clearToThemeBackground() {
        val c = com.eucleantoomuch.game.ui.UITheme.background
        Gdx.gl.glClearColor(c.r, c.g, c.b, 1f)
    }

    /** The player's transform, or null before a run has started. */
    private fun playerTransformOrNull(): TransformComponent? =
        playerEntity?.getComponent(TransformComponent::class.java)

    private fun playerSpeed(): Float =
        playerEntity?.getComponent(EucComponent::class.java)?.speed ?: 0f

    private fun raiseForegroundFpsCap() {
        if (Gdx.app.type == Application.ApplicationType.WebGL) return
        try {
            Gdx.graphics.setForegroundFPS(240)
        } catch (e: Throwable) {
            Gdx.app.log("EucGame", "setForegroundFPS unsupported on this backend")
        }
    }

    private fun applyFpsLimit() {
        // Check if max FPS setting changed
        val settingMaxFps = settingsManager.maxFps
        if (settingMaxFps != currentMaxFps) {
            currentMaxFps = settingMaxFps
        }

        // Never busy-sleep in the browser: frames are already paced by requestAnimationFrame,
        // and TeaVM implements Thread.sleep with green threads - calling it inside the frame
        // callback suspends the render loop and the game silently freezes after a few frames.
        if (Gdx.app.type == Application.ApplicationType.WebGL) return

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
                    // Millisecond-only sleep: the (millis, nanos) overload does not exist in
                    // TeaVM's class library, and sub-millisecond precision is meaningless for
                    // frame pacing anyway (the browser paces frames via requestAnimationFrame).
                    if (sleepMillis > 0) {
                        Thread.sleep(sleepMillis)
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

        // Re-check the steering source: in a browser the tilt sensor appears only once the
        // player has granted permission, which is well after create().
        gameInput.switchTo(pickInput())

        // Update input
        gameInput.update(delta)

        when (stateManager.current()) {
            is GameState.Loading -> renderLoading()
            is GameState.Menu -> renderMenu()
            is GameState.ModeSelection -> renderModeSelection()
            is GameState.TimeTrialLevelSelection -> renderTimeTrialLevelSelection()
            is GameState.WheelSelection -> renderWheelSelection()
            is GameState.Settings -> renderSettings()
            is GameState.Credits -> renderCredits()
            is GameState.Help -> renderHelp()
            is GameState.Calibrating -> renderCalibration()
            is GameState.Countdown -> renderCountdown(delta)
            is GameState.Playing -> renderPlaying(delta)
            is GameState.Paused -> renderPaused()
            is GameState.Falling -> renderFalling(delta)
            is GameState.GameOver -> renderGameOver()
        }

        // Check for pause - keyboard or two-finger tap (during playing or countdown)
        val currentState = stateManager.current()
        if (currentState is GameState.Playing || currentState is GameState.Countdown) {
            val shouldPause = Gdx.input.isKeyJustPressed(Input.Keys.BACK) ||
                Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
                (Gdx.input.justTouched() && Gdx.input.isTouched(1))  // Two fingers touched

            if (shouldPause) {
                UIFeedback.pauseOpen()
                pauseRenderer.reset()
                // Reset touch state and ignore taps for a few frames when returning
                wasTouched = false
                ignoreTapFrames = 5
                val fromCountdown = currentState is GameState.Countdown
                stateManager.transition(GameState.Paused(session, fromCountdown))
                return  // Don't process further input this frame
            }

            // Single tap to change camera view (only one finger, not two)
            // Skip tap detection for a few frames after pause/resume to avoid accidental triggers
            if (ignoreTapFrames > 0) {
                ignoreTapFrames--
                wasTouched = Gdx.input.isTouched(0)
            } else {
                val isTouched = Gdx.input.isTouched(0) && !Gdx.input.isTouched(1)
                // When the finger is also the steering stick, only a gesture that never
                // travelled counts as a tap - otherwise every turn would cycle the camera.
                val steeringByTouch = gameInput.delegate === touchOrKeyboardInput
                if (!isTouched && wasTouched && !(steeringByTouch && touchSteerInput.wasDrag())) {
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
    }

    private fun renderLoading() {
        // Simple loading - transition to menu immediately since we have no assets to load
        stateManager.transition(GameState.Menu)
    }

    private fun renderMenu() {
        clearToThemeBackground()
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        // Play menu music
        musicManager.playMenuMusic()
        musicManager.update(Gdx.graphics.deltaTime)

        when (menuRenderer.render(highScoreManager.highScore, highScoreManager.maxDistance, highScoreManager.maxNearMisses, voltsManager.totalVolts)) {
            MenuRenderer.ButtonClicked.PLAY -> {
                // Go to mode selection first
                stateManager.transition(GameState.ModeSelection)
            }
            MenuRenderer.ButtonClicked.CALIBRATE -> {
                stateManager.transition(GameState.Calibrating())
            }
            MenuRenderer.ButtonClicked.SETTINGS -> {
                stateManager.transition(GameState.Settings(returnTo = GameState.Menu))
            }
            MenuRenderer.ButtonClicked.CREDITS -> {
                creditsRenderer.reset()
                stateManager.transition(GameState.Credits)
            }
            MenuRenderer.ButtonClicked.HELP -> {
                helpRenderer.reset()
                stateManager.transition(GameState.Help)
            }
            MenuRenderer.ButtonClicked.EXIT -> {
                // Gdx.app.exit() is the portable path. The hard System.exit() that used to
                // follow it (to fully kill the Android process) does not exist in TeaVM's
                // class library, so it is delegated to the platform layer instead - a browser
                // tab cannot quit itself anyway, where this is simply a no-op.
                Gdx.app.exit()
                platformServices.exitProcess()
            }
            MenuRenderer.ButtonClicked.NONE -> {}
        }
    }

    private fun renderModeSelection() {
        clearToThemeBackground()
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        musicManager.update(Gdx.graphics.deltaTime)

        when (modeSelectionRenderer.render()) {
            ModeSelectionRenderer.Action.ENDLESS -> {
                timeTrialManager.clearSelection()
                pendingHardcoreMode = false
                pendingNightHardcoreMode = false
                wheelSelectionRenderer.onEnter()
                stateManager.transition(GameState.WheelSelection)
            }
            ModeSelectionRenderer.Action.TIME_TRIAL -> {
                pendingHardcoreMode = false
                pendingNightHardcoreMode = false
                stateManager.transition(GameState.TimeTrialLevelSelection)
            }
            ModeSelectionRenderer.Action.HARDCORE -> {
                timeTrialManager.clearSelection()
                pendingHardcoreMode = true
                pendingNightHardcoreMode = false
                wheelSelectionRenderer.onEnter()
                stateManager.transition(GameState.WheelSelection)
            }
            ModeSelectionRenderer.Action.NIGHT_HARDCORE -> {
                timeTrialManager.clearSelection()
                pendingHardcoreMode = false
                pendingNightHardcoreMode = true
                wheelSelectionRenderer.onEnter()
                stateManager.transition(GameState.WheelSelection)
            }
            ModeSelectionRenderer.Action.BACK -> {
                stateManager.transition(GameState.Menu)
            }
            ModeSelectionRenderer.Action.NONE -> {}
        }
    }

    private fun renderTimeTrialLevelSelection() {
        clearToThemeBackground()
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        musicManager.update(Gdx.graphics.deltaTime)

        when (timeTrialLevelRenderer.render()) {
            TimeTrialLevelRenderer.Action.SELECT_LEVEL -> {
                wheelSelectionRenderer.onEnter()
                stateManager.transition(GameState.WheelSelection)
            }
            TimeTrialLevelRenderer.Action.BACK -> {
                timeTrialManager.clearSelection()
                stateManager.transition(GameState.ModeSelection)
            }
            TimeTrialLevelRenderer.Action.NONE -> {}
        }
    }

    private fun renderWheelSelection() {
        clearToThemeBackground()
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        // Continue menu music during wheel selection
        musicManager.update(Gdx.graphics.deltaTime)

        when (wheelSelectionRenderer.render()) {
            WheelSelectionRenderer.Action.START -> {
                // Proceed to calibration or game
                if (gameInput.isCalibrated()) {
                    startGame()
                } else {
                    stateManager.transition(GameState.Calibrating())
                }
            }
            WheelSelectionRenderer.Action.BACK -> {
                // Go back to mode selection (or time trial level selection if selected)
                if (timeTrialManager.selectedLevel != null) {
                    stateManager.transition(GameState.TimeTrialLevelSelection)
                } else {
                    stateManager.transition(GameState.ModeSelection)
                }
            }
            WheelSelectionRenderer.Action.NONE -> {}
        }
    }

    private fun renderSettings() {
        clearToThemeBackground()
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
        clearToThemeBackground()
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

    private fun renderHelp() {
        clearToThemeBackground()
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT)

        // Continue music during help
        musicManager.update(Gdx.graphics.deltaTime)

        when (helpRenderer.render()) {
            HelpRenderer.ButtonClicked.BACK -> {
                stateManager.transition(GameState.Menu)
            }
            HelpRenderer.ButtonClicked.NONE -> {}
        }
    }

    private fun renderCalibration() {
        accelerometerInput.update(Gdx.graphics.deltaTime)
        val (rawX, rawY) = accelerometerInput.getRawValues()

        // Continue menu music during calibration
        musicManager.update(Gdx.graphics.deltaTime)

        val calibratingState = stateManager.current() as GameState.Calibrating

        when (calibrationRenderer.render(rawX, rawY)) {
            CalibrationRenderer.Action.CALIBRATE -> {
                accelerometerInput.calibrate()
                highScoreManager.saveCalibration(
                    accelerometerInput.getCalibrationX(),
                    accelerometerInput.getCalibrationY()
                )
                if (calibratingState.returnTo != null) {
                    stateManager.transition(calibratingState.returnTo)
                } else {
                    startGame()
                }
            }
            CalibrationRenderer.Action.SKIP -> {
                // Use default calibration (current position)
                if (!accelerometerInput.isCalibrated()) {
                    accelerometerInput.calibrate()
                }
                if (calibratingState.returnTo != null) {
                    stateManager.transition(calibratingState.returnTo)
                } else {
                    startGame()
                }
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
        ragdollInteractions.updateFallingPedestrians()
        ragdollInteractions.updateKnockedOverTrashCans()

        // Check if ragdoll bodies knock down standing pedestrians
        ragdollInteractions.checkRagdollPedestrianCollisions()

        // Update session
        val playerTransform = playerEntity?.getComponent(TransformComponent::class.java)
        val eucComponent = playerEntity?.getComponent(EucComponent::class.java)

        if (playerTransform != null && eucComponent != null) {
            session.update(delta, eucComponent.speed)

            // Update hardcore difficulty based on play time
            if (session.isHardcoreMode) {
                worldGenerator.setHardcoreMode(true, session.hardcoreDifficulty)
            }

            // Update Volts system
            voltsManager.update(delta)

            // PWM risk reward (holding >90% for 5 seconds)
            val pwmReward = voltsManager.updatePwmRisk(eucComponent.pwm, delta)
            if (pwmReward > 0) {
                hud.triggerVoltsEarned(pwmReward, "risk!")
            }

            // Manhole survival reward (wobble ended without dying)
            if (!eucComponent.isWobbling && eucComponent.wobbleIntensity <= 0.01f) {
                val manholeReward = voltsManager.awardManholeSurvival()
                if (manholeReward > 0) {
                    hud.triggerVoltsEarned(manholeReward, "manhole")
                }
            }

            // Check for battery death - causes fall
            if (session.isBatteryDead) {
                platformServices.playGenericHitSound()
                handlePlayerFall()
                return
            }

            // Check for time trial completion or timeout
            if (session.isTimeTrial) {
                if (session.levelCompleted) {
                    // Level completed successfully!
                    handleTimeTrialCompletion()
                    return
                } else if (session.timeRemaining <= 0f) {
                    // Time ran out
                    handleTimeTrialTimeout()
                    return
                }
            }

            // Sync rider position with player
            riderEntity?.getComponent(TransformComponent::class.java)?.let { riderTransform ->
                riderTransform.position.set(playerTransform.position)
                riderTransform.position.y += 0.55f  // Stand on top of EUC (lowered)
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

            // Update pedestrian road crossing probability based on distance/mode
            pedestrianAISystem.crossingProbability = worldGenerator.getPedestrianCrossingProbability(session.distanceTraveled)

            // Dust off the contact patch, thrown backwards and scaled by how fast it is going
            renderer.particles.tyreSpray(
                playerTransform.position, playerTransform.yaw, eucComponent.speed, delta
            )

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
                // Prevent camera mode change on resume - ignore taps for a few frames
                wasTouched = false
                ignoreTapFrames = 5
                if (pausedState.fromCountdown) {
                    // Resume to countdown
                    stateManager.transition(GameState.Countdown(countdownTimer.toInt() + 1))
                } else {
                    // Resume motor sound
                    motorSoundManager.start()
                    // Resume music
                    musicManager.resume()
                    stateManager.transition(GameState.Playing(pausedState.session))
                }
            }
            PauseRenderer.ButtonClicked.RESTART -> {
                resetGame()
                startGame()
            }
            PauseRenderer.ButtonClicked.CALIBRATE -> {
                val pausedState = stateManager.current() as GameState.Paused
                stateManager.transition(GameState.Calibrating(returnTo = pausedState))
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

        // Render game over UI
        when (gameOverRenderer.render(state.session, isNewHighScore, voltsManager.sessionVolts, voltsManager.totalVolts, timeTrialResult)) {
            GameOverRenderer.ButtonClicked.RETRY -> {
                resetGame()
                startGame()
            }
            GameOverRenderer.ButtonClicked.NEXT_LEVEL -> {
                // Go to next level
                val nextLevel = timeTrialResult?.nextLevelUnlocked
                if (nextLevel != null) {
                    timeTrialManager.selectLevel(nextLevel)
                    resetGame()
                    startGame()
                }
            }
            GameOverRenderer.ButtonClicked.MENU -> {
                resetGame()
                timeTrialManager.clearSelection()
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
        session.setBatteryCapacity(wheelType.batteryCapacity)  // Set battery from wheel type

        // Set time trial level if selected
        session.timeTrialLevel = timeTrialManager.selectedLevel

        // Set hardcore/night hardcore mode if selected
        session.isHardcoreMode = pendingHardcoreMode || pendingNightHardcoreMode
        session.isNightHardcoreMode = pendingNightHardcoreMode

        // Configure world generator for hardcore mode (night hardcore uses same mechanics)
        val isAnyHardcore = pendingHardcoreMode || pendingNightHardcoreMode
        worldGenerator.setHardcoreMode(isAnyHardcore, 0f)
        worldGenerator.setTimeTrialMode(session.isTimeTrial)
        voltsManager.setHardcoreMode(isAnyHardcore, pendingNightHardcoreMode)

        // Configure night mode - night hardcore always uses night mode
        val useNightMode = session.isEffectiveNightMode
        renderer.setNightMode(useNightMode)
        worldGenerator.setNightMode(useNightMode)

        // Night hardcore: enable lamp flickering
        worldGenerator.setLampFlickeringEnabled(pendingNightHardcoreMode)

        hud.reset()
        voltsManager.resetSession()
        isNewHighScore = false
        timeTrialResult = null
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
        // Otherwise the last run's dust hangs in the air over the new one
        renderer.particles.clear()

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

        // Reset collider tracking to prevent memory leak
        resetColliderTracking()

        // Reset night mode to day
        renderer.setNightMode(false)
        worldGenerator.setNightMode(false)

        // Reset hardcore mode and lamp flickering
        worldGenerator.setHardcoreMode(false, 0f)
        worldGenerator.setTimeTrialMode(false)
        worldGenerator.setLampFlickeringEnabled(false)

        // Reset pedestrian crossing probability
        pedestrianAISystem.crossingProbability = 0f

        // Drop tracked ragdoll entities from the previous session
        ragdollInteractions.reset()

        // Re-arm pigeon flock-startle rewards (flock IDs from the last run must not linger)
        pigeonSystem.clearStartleTracking()
    }

    private fun handlePlayerFall() {
        // Stop speed warnings
        speedWarningManager.stop()
        // Stop motor sound
        motorSoundManager.stop()

        // Record score
        isNewHighScore = highScoreManager.recordGame(session)

        // Award Volts for beating high score
        if (isNewHighScore) {
            voltsManager.awardHighScoreBeaten()
        }

        // Finalize Volts session (persist to storage)
        voltsManager.finalizeSession()

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
                // Reset smoothed rotation values for new fall
                smoothedEucRoll = eucComponent.sideLean * 25f
                smoothedEucPitch = eucComponent.forwardLean * 20f

                ragdollPhysics!!.startFall(
                    eucPosition = playerTransform.position,
                    eucYaw = playerTransform.yaw,
                    playerVelocity = eucComponent.speed,
                    sideLean = eucComponent.sideLean,
                    forwardLean = eucComponent.forwardLean
                )

                // Defer heavy collider setup to first frame of Falling state
                resetColliderTracking()
                pendingRagdollColliderSetup = true
            }

            // Startle nearby pigeons when player crashes
            pigeonSystem.addStartleSource(playerTransform.position)
        }

        // Transition to falling state (will show animation before game over)
        stateManager.transition(GameState.Falling(session))

        // Set time trial result as failed (crashed) if in time trial mode
        if (session.isTimeTrial) {
            timeTrialResult = GameOverRenderer.TimeTrialResult(
                completed = false,
                isNewBestTime = false,
                nextLevelUnlocked = null,
                completionTime = session.playTimeSeconds
            )
        }
    }

    private fun handleTimeTrialCompletion() {
        val level = session.timeTrialLevel ?: return

        // Stop sounds
        speedWarningManager.stop()
        motorSoundManager.stop()

        // Check if next level was locked BEFORE recording completion
        val nextLevel = level.nextLevel()
        val wasNextLevelLocked = nextLevel != null && !timeTrialManager.isUnlocked(nextLevel)

        // Record completion and check for new best time (this unlocks next level)
        val isNewBest = timeTrialManager.recordCompletion(level, session.playTimeSeconds)

        // Award Volts for completing the level - exactly the advertised reward,
        // not scaled by the near-miss streak multiplier or rounded via pickup chunks.
        voltsManager.awardFlat(level.voltReward)
        voltsManager.finalizeSession()

        // Set time trial result for game over screen
        val completionTime = session.playTimeSeconds
        timeTrialResult = GameOverRenderer.TimeTrialResult(
            completed = true,
            isNewBestTime = isNewBest,
            nextLevelUnlocked = if (wasNextLevelLocked) nextLevel else null,
            completionTime = completionTime
        )

        // Flush deferred writes to persist level unlock and volts
        timeTrialManager.flushDeferred()
        voltsManager.flushDeferred()

        // Skip falling animation, go directly to game over
        gameOverRenderer.reset()
        stateManager.transition(GameState.GameOver(session))
    }

    private fun handleTimeTrialTimeout() {
        // Stop sounds
        speedWarningManager.stop()
        motorSoundManager.stop()

        // Finalize Volts (player still keeps what they earned)
        voltsManager.finalizeSession()

        // Set time trial result as failed (timeout)
        timeTrialResult = GameOverRenderer.TimeTrialResult(
            completed = false,
            isNewBestTime = false,
            nextLevelUnlocked = null,
            completionTime = session.playTimeSeconds
        )

        // Skip falling animation, go directly to game over
        gameOverRenderer.reset()
        stateManager.transition(GameState.GameOver(session))
    }

    // Temp vectors for ragdoll position extraction
    private val ragdollEucPos = com.badlogic.gdx.math.Vector3()
    private val ragdollTorsoPos = com.badlogic.gdx.math.Vector3()

    // Smoothing for EUC visual rotation during ragdoll (prevents jitter)
    private var smoothedEucRoll = 0f
    private var smoothedEucPitch = 0f

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
            pedestrianAISystem.crossingProbability = worldGenerator.getPedestrianCrossingProbability(state.session.distanceTraveled)
        }

        // Always update ragdoll physics (for pedestrians even if player ragdoll is inactive)
        ragdollPhysics?.update(delta)

        // Deferred ragdoll collider setup (moved from handlePlayerFall to avoid collision-frame lag)
        if (pendingRagdollColliderSetup) {
            val playerTransform = playerEntity?.getComponent(TransformComponent::class.java)
            if (playerTransform != null) {
                addWorldCollidersForRagdoll(playerTransform.position)
            }
            pendingRagdollColliderSetup = false
            // Also flush deferred disk writes here (non-critical frame)
            highScoreManager.flushDeferred()
            voltsManager.flushDeferred()
            timeTrialManager.flushDeferred()
        }

        // Check if ragdoll bodies knock down standing pedestrians
        ragdollInteractions.checkRagdollPedestrianCollisions()

        // Check if moving cars hit the player ragdoll
        ragdollInteractions.checkRagdollCarCollisions()

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
                    // Extract rotation angles directly from physics (in degrees)
                    val rollDegrees = extractRollFromMatrix(eucTransform)
                    val pitchDegrees = extractPitchFromMatrix(eucTransform)

                    // Convert to visualLean: renderer uses sideLean * 25 for normal,
                    // but for falling we need full 90° range, so divide by 25 to get proper angle
                    // Roll of 90° (lying on side) should give visualSideLean = 3.6
                    eucComponent.visualSideLean = rollDegrees / 25f
                    eucComponent.visualForwardLean = pitchDegrees / 20f
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

                    val pitchCompensation = (fallAnimationController.riderPitch / 90f) * 0.5f
                    val baseY = 0.55f + fallAnimationController.riderYOffset + pitchCompensation
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

            // Keep camera following
            val cameraFollowPos = if (usePhysicsPositions) ragdollTorsoPos else playerTransform.position
            renderer.cameraController.update(cameraFollowPos, playerTransform.yaw, delta, 0f)
        }

        // Update falling pedestrians (always, not just when player ragdoll is active)
        ragdollInteractions.updateFallingPedestrians()
        ragdollInteractions.updateKnockedOverTrashCans()

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

    override fun resize(width: Int, height: Int) {
        renderer.resize(width, height)
        hud.resize(width, height)
        menuRenderer.resize(width, height)
        gameOverRenderer.resize(width, height)
        pauseRenderer.resize(width, height)
        calibrationRenderer.resize(width, height)
        settingsRenderer.resize(width, height)
        creditsRenderer.resize(width, height)
        helpRenderer.resize(width, height)
        wheelSelectionRenderer.resize(width, height)
        modeSelectionRenderer.resize(width, height)
        timeTrialLevelRenderer.resize(width, height)
    }

    override fun pause() {
        // The browser fires visibilitychange while create() is still loading assets, so pause
        // can arrive before any subsystem exists - touching them would throw on a lateinit.
        if (!created) return

        // App sent to background (Android lifecycle). The render loop is suspended, so
        // nothing else will stop the audio threads - do it explicitly here, otherwise the
        // motor whine / wobble / music keep playing over other apps. Also drop a live run
        // into the pause menu so the player doesn't return mid-crash.
        motorSoundManager.stop()
        speedWarningManager.stop()
        platformServices.stopWobbleSound()
        musicManager.pause()

        when (val state = stateManager.current()) {
            is GameState.Playing -> stateManager.transition(GameState.Paused(state.session, fromCountdown = false))
            is GameState.Countdown -> stateManager.transition(GameState.Paused(session, fromCountdown = true))
            else -> { /* menus/game-over: nothing to pause */ }
        }
    }

    override fun resume() {
        if (!created) return

        // Re-enable high FPS on resume (Android may reset this)
        raiseForegroundFpsCap()

        // Force font reinitialization on resume (GL context may have been lost on Android).
        // Fonts are regenerated lazily on the next render, when the surface size is final -
        // regenerating here can bake in a stale height and break all text scaling.
        UIFonts.invalidate()

        // Recreate all UI renderer resources (SpriteBatch, ShapeRenderer)
        hud.recreate()
        menuRenderer.recreate()
        gameOverRenderer.recreate()
        pauseRenderer.recreate()
        calibrationRenderer.recreate()
        settingsRenderer.recreate()
        creditsRenderer.recreate()
        helpRenderer.recreate()
        wheelSelectionRenderer.recreate()
        modeSelectionRenderer.recreate()
        timeTrialLevelRenderer.recreate()
    }

    override fun dispose() {
        if (!created) return

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
        helpRenderer.dispose()
        wheelSelectionRenderer.dispose()
        modeSelectionRenderer.dispose()
        timeTrialLevelRenderer.dispose()
        ragdollPhysics?.dispose()
        ragdollRenderer?.dispose()
        platformServices.releaseAudio()
    }

    // Add physics colliders for nearby world objects during ragdoll
    private val colliderMapper = com.badlogic.ashley.core.ComponentMapper.getFor(
        com.eucleantoomuch.game.ecs.components.ColliderComponent::class.java
    )
    private val obstacleMapper = com.badlogic.ashley.core.ComponentMapper.getFor(
        com.eucleantoomuch.game.ecs.components.ObstacleComponent::class.java
    )
    private val groundMapper = com.badlogic.ashley.core.ComponentMapper.getFor(
        com.eucleantoomuch.game.ecs.components.GroundComponent::class.java
    )
    private val transformMapperForCollider = com.badlogic.ashley.core.ComponentMapper.getFor(
        TransformComponent::class.java
    )
    private val tempColliderPos = com.badlogic.gdx.math.Vector3()
    private val tempHalfExtents = com.badlogic.gdx.math.Vector3()

    private fun addWorldCollidersForRagdoll(playerPos: com.badlogic.gdx.math.Vector3) {
        val physics = ragdollPhysics ?: return

        // Search radius for nearby objects - increased to reach buildings (14m from road center + margin)
        val searchRadius = 30f
        val searchRadiusSq = searchRadius * searchRadius

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

            // Check obstacle type and ground type for special handling
            val obstacle = obstacleMapper.get(entity)
            val ground = groundMapper.get(entity)

            // Determine collider type - obstacle type takes priority over ground type
            val colliderType = when {
                obstacle != null -> obstacleTypeToColliderType(obstacle.type)
                ground?.type == com.eucleantoomuch.game.ecs.components.GroundType.BUILDING ->
                    RagdollTypes.ColliderType.BUILDING
                else -> RagdollTypes.ColliderType.GENERIC
            }

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
                physics.addBoxCollider(tempColliderPos, collider.halfExtents, transform.yaw, RagdollTypes.ColliderType.CAR)
            } else {
                // Default car size if no collider
                tempColliderPos.set(transform.position.x, transform.position.y + 0.7f, transform.position.z)
                tempHalfExtents.set(1.0f, 0.7f, 2.2f)
                physics.addBoxCollider(tempColliderPos, tempHalfExtents, transform.yaw, RagdollTypes.ColliderType.CAR)
            }
        }
    }

    // Deferred ragdoll collider setup - done on first frame of Falling state to avoid collision-frame lag
    private var pendingRagdollColliderSetup = false

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

        // Search radius for nearby objects - increased to reach buildings
        val searchRadius = 35f
        val searchRadiusSq = searchRadius * searchRadius

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

            // Check obstacle type and ground type for special handling
            val obstacle = obstacleMapper.get(entity)
            val ground = groundMapper.get(entity)

            // Determine collider type - obstacle type takes priority over ground type
            val colliderType = when {
                obstacle != null -> obstacleTypeToColliderType(obstacle.type)
                ground?.type == com.eucleantoomuch.game.ecs.components.GroundType.BUILDING ->
                    RagdollTypes.ColliderType.BUILDING
                else -> RagdollTypes.ColliderType.GENERIC
            }

            if (obstacle != null && obstacle.type == ObstacleType.STREET_LIGHT) {
                physics.addCylinderCollider(tempColliderPos, 0.15f, collider.halfExtents.y * 2f, colliderType)
            } else {
                physics.addBoxCollider(tempColliderPos, tempHalfExtents, transform.yaw, colliderType)
            }

            addedColliderEntities.add(entity)
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
            physics.addBoxCollider(tempColliderPos, collider.halfExtents, transform.yaw, RagdollTypes.ColliderType.CAR)

            addedColliderEntities.add(entity)
        }
    }

    /**
     * Reset collider tracking when starting new ragdoll.
     */
    private fun resetColliderTracking() {
        addedColliderEntities.clear()
        lastColliderUpdateZ = Float.MIN_VALUE
        ragdollInteractions.resetCarHits()  // Reset car collision tracking
    }

    /**
     * Play appropriate sound when ragdoll collides with an object during flight.
     */
    private fun playRagdollCollisionSound(colliderType: RagdollTypes.ColliderType) {
        when (colliderType) {
            RagdollTypes.ColliderType.STREET_LIGHT -> platformServices.playStreetLightImpactSound()
            RagdollTypes.ColliderType.RECYCLE_BIN -> platformServices.playRecycleBinImpactSound()
            RagdollTypes.ColliderType.CAR -> platformServices.playCarCrashSound()
            RagdollTypes.ColliderType.PEDESTRIAN -> platformServices.playPersonImpactSound()
            RagdollTypes.ColliderType.BENCH -> platformServices.playBenchImpactSound()
            RagdollTypes.ColliderType.BUILDING -> platformServices.playGenericHitSound()  // Building wall impact
            RagdollTypes.ColliderType.TREE -> platformServices.playGenericHitSound()  // Tree impact
            RagdollTypes.ColliderType.GENERIC -> platformServices.playGenericHitSound()
            RagdollTypes.ColliderType.GROUND -> { /* Ground impacts handled by fall animation */ }
        }
    }

    /**
     * Play quieter sound when pedestrian ragdoll (secondary) collides with an object.
     * These are "chain reaction" sounds - quieter than direct player collisions.
     */
    private fun playSecondaryRagdollCollisionSound(colliderType: RagdollTypes.ColliderType) {
        // Play same sounds but at lower volume (0.4x)
        when (colliderType) {
            RagdollTypes.ColliderType.STREET_LIGHT -> platformServices.playStreetLightImpactSound(0.4f)
            RagdollTypes.ColliderType.RECYCLE_BIN -> platformServices.playRecycleBinImpactSound(0.4f)
            RagdollTypes.ColliderType.CAR -> platformServices.playCarCrashSound(0.4f)
            RagdollTypes.ColliderType.PEDESTRIAN -> platformServices.playPersonImpactSound(0.4f)
            RagdollTypes.ColliderType.BENCH -> platformServices.playBenchImpactSound(0.4f)
            RagdollTypes.ColliderType.BUILDING -> platformServices.playGenericHitSound(0.4f)
            RagdollTypes.ColliderType.TREE -> platformServices.playGenericHitSound(0.4f)
            RagdollTypes.ColliderType.GENERIC -> platformServices.playGenericHitSound(0.4f)
            RagdollTypes.ColliderType.GROUND -> { /* Ground impacts not needed for secondary */ }
        }
    }

    /**
     * Convert ObstacleType to RagdollTypes.ColliderType for physics collisions.
     */
    private fun obstacleTypeToColliderType(obstacleType: ObstacleType?): RagdollTypes.ColliderType {
        return when (obstacleType) {
            ObstacleType.STREET_LIGHT -> RagdollTypes.ColliderType.STREET_LIGHT
            ObstacleType.RECYCLE_BIN -> RagdollTypes.ColliderType.RECYCLE_BIN
            ObstacleType.CAR -> RagdollTypes.ColliderType.CAR
            ObstacleType.PEDESTRIAN -> RagdollTypes.ColliderType.PEDESTRIAN
            ObstacleType.BENCH -> RagdollTypes.ColliderType.BENCH
            else -> RagdollTypes.ColliderType.GENERIC
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
