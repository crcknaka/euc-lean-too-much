package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.replay.ReplaySystem

/**
 * UI overlay for replay mode with playback controls.
 * Shows play/pause, slow-mo toggle, timeline, and exit button.
 */
class ReplayRenderer : Disposable {
    private val ui = UIRenderer()

    // Control buttons
    private val playPauseButton = Rectangle()
    private val slowMoButton = Rectangle()
    private val exitButton = Rectangle()
    private val timelineBar = Rectangle()

    // Animation states
    private var overlayAlpha = 0f
    private var controlsY = 0f
    private var playPauseHover = 0f
    private var slowMoHover = 0f
    private var exitHover = 0f
    private var timelineHover = 0f

    // Timeline dragging
    private var isDraggingTimeline = false

    enum class Action {
        NONE, EXIT, TOGGLE_PAUSE, TOGGLE_SLOWMO, SEEK
    }

    data class Result(
        val action: Action,
        val seekPosition: Float = 0f
    )

    fun render(replaySystem: ReplaySystem): Result {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val scale = UITheme.Dimensions.scale()

        // Update animations
        overlayAlpha = UITheme.Anim.ease(overlayAlpha, 0.6f, 8f)
        val targetControlsY = 80f * scale
        controlsY = UITheme.Anim.ease(controlsY, targetControlsY, 8f)

        // Check hover
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()

        playPauseHover = UITheme.Anim.ease(playPauseHover, if (playPauseButton.contains(touchX, touchY)) 1f else 0f, 10f)
        slowMoHover = UITheme.Anim.ease(slowMoHover, if (slowMoButton.contains(touchX, touchY)) 1f else 0f, 10f)
        exitHover = UITheme.Anim.ease(exitHover, if (exitButton.contains(touchX, touchY)) 1f else 0f, 10f)
        timelineHover = UITheme.Anim.ease(timelineHover, if (timelineBar.contains(touchX, touchY) || isDraggingTimeline) 1f else 0f, 10f)

        ui.beginShapes()

        // Top bar with gradient for title
        val topBarHeight = 70f * scale
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, overlayAlpha * 0.8f)
        ui.shapes.rect(0f, sh - topBarHeight, sw, topBarHeight)

        // Bottom controls panel
        val controlsHeight = 140f * scale
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, overlayAlpha * 0.85f)
        ui.shapes.rect(0f, 0f, sw, controlsHeight)

        // Button dimensions - larger buttons with more spacing for easier touch
        val buttonSize = 72f * scale  // Larger play/pause button
        val smallButtonSize = 64f * scale  // Larger SLO button
        val buttonSpacing = 40f * scale  // More space between buttons
        val centerX = sw / 2

        // Play/Pause button (center)
        playPauseButton.set(
            centerX - buttonSize / 2,
            controlsY - buttonSize / 2,
            buttonSize,
            buttonSize
        )

        // Slow-Mo button (left of play/pause)
        slowMoButton.set(
            playPauseButton.x - buttonSpacing - smallButtonSize,
            controlsY - smallButtonSize / 2,
            smallButtonSize,
            smallButtonSize
        )

        // Exit button (top right)
        exitButton.set(
            sw - 60f * scale - smallButtonSize,
            sh - topBarHeight + (topBarHeight - smallButtonSize) / 2,
            smallButtonSize,
            smallButtonSize
        )

        // Timeline bar
        val timelineWidth = sw * 0.7f
        val timelineHeight = 8f * scale
        val timelineY = controlsY + buttonSize / 2 + 25f * scale
        timelineBar.set(
            centerX - timelineWidth / 2,
            timelineY - timelineHeight / 2,
            timelineWidth,
            timelineHeight * 3 // Larger hit area
        )

        // Draw timeline background
        val timelineVisualHeight = if (timelineHover > 0.5f) timelineHeight * 1.5f else timelineHeight
        ui.roundedRect(
            timelineBar.x,
            timelineY - timelineVisualHeight / 2,
            timelineBar.width,
            timelineVisualHeight,
            timelineVisualHeight / 2,
            UITheme.withAlpha(UITheme.surfaceLight, 0.5f + timelineHover * 0.3f)
        )

        // Draw timeline progress
        val progress = replaySystem.getPlaybackProgress()
        if (progress > 0) {
            ui.roundedRect(
                timelineBar.x,
                timelineY - timelineVisualHeight / 2,
                timelineBar.width * progress,
                timelineVisualHeight,
                timelineVisualHeight / 2,
                UITheme.accent
            )
        }

        // Draw timeline handle
        val handleSize = 16f * scale * (1f + timelineHover * 0.3f)
        val handleX = timelineBar.x + timelineBar.width * progress
        ui.shapes.color = UITheme.textPrimary
        ui.shapes.circle(handleX, timelineY, handleSize / 2)

        // Draw buttons
        val isPaused = replaySystem.isPaused()
        val isSlowMo = replaySystem.isSlowMo()

        // Play/Pause button
        val playPauseColor = if (isPaused) UITheme.accent else UITheme.surfaceLight
        ui.roundedRect(
            playPauseButton.x, playPauseButton.y,
            playPauseButton.width, playPauseButton.height,
            buttonSize / 4,
            UITheme.lerp(playPauseColor, UITheme.accentBright, playPauseHover * 0.3f)
        )

        // Slow-Mo button
        val slowMoColor = if (isSlowMo) UITheme.warning else UITheme.surfaceLight
        ui.roundedRect(
            slowMoButton.x, slowMoButton.y,
            slowMoButton.width, slowMoButton.height,
            smallButtonSize / 4,
            UITheme.lerp(slowMoColor, UITheme.accentBright, slowMoHover * 0.3f)
        )

        // Exit button
        ui.roundedRect(
            exitButton.x, exitButton.y,
            exitButton.width, exitButton.height,
            smallButtonSize / 4,
            UITheme.lerp(UITheme.danger, UITheme.textPrimary, exitHover * 0.3f)
        )

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        // Title
        ui.textCentered("REPLAY", centerX, sh - topBarHeight / 2, UIFonts.heading, UITheme.textPrimary)

        // Speed indicator
        val speedText = if (isSlowMo) "0.25x" else "1x"
        ui.textCentered(speedText, sw - 130f * scale, sh - topBarHeight / 2, UIFonts.body, UITheme.textSecondary)

        // Play/Pause icon (simple text for now)
        val playPauseIcon = if (isPaused) ">" else "||"
        ui.textCentered(playPauseIcon, playPauseButton.x + playPauseButton.width / 2,
            playPauseButton.y + playPauseButton.height / 2, UIFonts.heading, UITheme.textPrimary)

        // Slow-Mo label
        ui.textCentered("SLO", slowMoButton.x + slowMoButton.width / 2,
            slowMoButton.y + slowMoButton.height / 2, UIFonts.caption, UITheme.textPrimary)

        // Exit X
        ui.textCentered("X", exitButton.x + exitButton.width / 2,
            exitButton.y + exitButton.height / 2, UIFonts.body, UITheme.textPrimary)

        // Time display
        val duration = replaySystem.getDuration()
        val currentTime = duration * progress
        val timeText = String.format("%.1fs / %.1fs", currentTime, duration)
        ui.textCentered(timeText, centerX, timelineY + 30f * scale, UIFonts.caption, UITheme.textSecondary)

        // Instructions
        ui.textCentered("Drag to rotate camera", centerX, sh - topBarHeight - 30f * scale,
            UIFonts.caption, UITheme.withAlpha(UITheme.textMuted, 0.7f))

        ui.endBatch()

        // === Input ===
        var result = Result(Action.NONE)

        // Handle timeline dragging
        if (Gdx.input.isTouched) {
            if (isDraggingTimeline || timelineBar.contains(touchX, touchY)) {
                isDraggingTimeline = true
                val seekPos = ((touchX - timelineBar.x) / timelineBar.width).coerceIn(0f, 1f)
                result = Result(Action.SEEK, seekPos)
            }
        } else {
            isDraggingTimeline = false
        }

        if (Gdx.input.justTouched() && !isDraggingTimeline) {
            when {
                playPauseButton.contains(touchX, touchY) -> {
                    UIFeedback.click()
                    result = Result(Action.TOGGLE_PAUSE)
                }
                slowMoButton.contains(touchX, touchY) -> {
                    UIFeedback.click()
                    result = Result(Action.TOGGLE_SLOWMO)
                }
                exitButton.contains(touchX, touchY) -> {
                    UIFeedback.click()
                    result = Result(Action.EXIT)
                }
            }
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            UIFeedback.click()
            result = Result(Action.TOGGLE_PAUSE)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            UIFeedback.click()
            result = Result(Action.TOGGLE_SLOWMO)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            UIFeedback.click()
            result = Result(Action.EXIT)
        }

        return result
    }

    fun reset() {
        overlayAlpha = 0f
        controlsY = -100f
        isDraggingTimeline = false
    }

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
