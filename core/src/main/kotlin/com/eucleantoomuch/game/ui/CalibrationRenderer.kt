package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable

/**
 * Modern calibration screen with large touch targets.
 * Shows device tilt and lets user set current position as neutral.
 */
class CalibrationRenderer : Disposable {
    private val ui = UIRenderer()

    private val calibrateButton = Rectangle()
    private val skipButton = Rectangle()

    // Animation states
    private var enterAnim = 0f
    private var dotTrailX = FloatArray(12)
    private var dotTrailY = FloatArray(12)
    private var trailIndex = 0
    private var trailTimer = 0f
    private var calibrateHover = 0f
    private var skipHover = 0f
    private var successFlash = 0f
    private var readyTimer = 0f  // Time the dot has been stable

    enum class Action {
        NONE, CALIBRATE, SKIP
    }

    fun render(rawX: Float, rawY: Float): Action {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val centerY = sh / 2
        val scale = UITheme.Dimensions.scale()

        // Animations
        enterAnim = UITheme.Anim.ease(enterAnim, 1f, 4f)
        successFlash = UITheme.Anim.ease(successFlash, 0f, 3f)

        // Hover states
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        calibrateHover = UITheme.Anim.ease(calibrateHover, if (calibrateButton.contains(touchX, touchY)) 1f else 0f, 10f)
        skipHover = UITheme.Anim.ease(skipHover, if (skipButton.contains(touchX, touchY)) 1f else 0f, 10f)

        // Update dot trail and check stability
        trailTimer += Gdx.graphics.deltaTime
        if (trailTimer > 0.04f) {
            trailTimer = 0f
            trailIndex = (trailIndex + 1) % dotTrailX.size
            val maxTilt = 10f
            val normalizedX = (rawX / maxTilt).coerceIn(-1f, 1f)
            val normalizedY = (rawY / maxTilt).coerceIn(-1f, 1f)
            dotTrailX[trailIndex] = normalizedY
            dotTrailY[trailIndex] = -normalizedX
        }

        // Check if device is stable (not moving much)
        val recentMovement = calculateRecentMovement()
        if (recentMovement < 0.15f) {
            readyTimer += Gdx.graphics.deltaTime
        } else {
            readyTimer = 0f
        }
        val isStable = readyTimer > 0.5f  // Stable for 0.5 seconds

        ui.beginShapes()

        // Gradient background
        for (i in 0 until 20) {
            val t = i / 20f
            val stripY = sh * t
            ui.shapes.color = UITheme.lerp(UITheme.background, UITheme.backgroundLight, t)
            ui.shapes.rect(0f, stripY, sw, sh / 20f + 1)
        }

        // Panel with enter animation - larger for more breathing room
        val panelWidth = 620f * scale * enterAnim
        val panelHeight = 620f * scale * enterAnim
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface)

        // Tilt indicator - shows current device orientation (moved down for more space from text)
        val indicatorSize = 180f * scale * enterAnim
        val indicatorCenterY = centerY + 20f * scale

        // Indicator shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.35f)
        ui.shapes.circle(centerX + 4, indicatorCenterY - 4, indicatorSize / 2 + 4)

        // Indicator background - changes color when stable
        ui.shapes.color = if (isStable) UITheme.withAlpha(UITheme.accent, 0.15f) else UITheme.surfaceLight
        ui.shapes.circle(centerX, indicatorCenterY, indicatorSize / 2)

        // Outer ring - glows when stable
        if (isStable) {
            val glowPulse = UITheme.Anim.pulse(2f, 0.4f, 0.7f)
            ui.shapes.color = UITheme.withAlpha(UITheme.accent, glowPulse * 0.5f)
            val segments = 36
            val radius = indicatorSize / 2
            for (i in 0 until segments) {
                val angle1 = (i.toFloat() / segments) * MathUtils.PI2
                val angle2 = ((i + 1).toFloat() / segments) * MathUtils.PI2
                ui.shapes.rectLine(
                    centerX + radius * MathUtils.cos(angle1),
                    indicatorCenterY + radius * MathUtils.sin(angle1),
                    centerX + radius * MathUtils.cos(angle2),
                    indicatorCenterY + radius * MathUtils.sin(angle2),
                    4f
                )
            }
        }

        // Simple grid - just one circle
        ui.shapes.color = UITheme.withAlpha(UITheme.textMuted, 0.15f)
        val segments = 36
        val radius = indicatorSize / 2 * 0.5f
        for (i in 0 until segments) {
            val angle1 = (i.toFloat() / segments) * MathUtils.PI2
            val angle2 = ((i + 1).toFloat() / segments) * MathUtils.PI2
            ui.shapes.rectLine(
                centerX + radius * MathUtils.cos(angle1),
                indicatorCenterY + radius * MathUtils.sin(angle1),
                centerX + radius * MathUtils.cos(angle2),
                indicatorCenterY + radius * MathUtils.sin(angle2),
                1.5f
            )
        }

        // Dot trail with larger dots
        for (i in dotTrailX.indices) {
            val idx = (trailIndex - i + dotTrailX.size) % dotTrailX.size
            val alpha = (1f - i.toFloat() / dotTrailX.size) * 0.35f
            val trailDotX = centerX + dotTrailX[idx] * indicatorSize / 2 * 0.8f
            val trailDotY = indicatorCenterY + dotTrailY[idx] * indicatorSize / 2 * 0.8f
            ui.shapes.color = UITheme.withAlpha(UITheme.cyan, alpha)
            ui.shapes.circle(trailDotX, trailDotY, 5f * scale - i * 0.3f * scale)
        }

        // Current position dot
        val maxTilt = 10f
        val normalizedX = (rawX / maxTilt).coerceIn(-1f, 1f)
        val normalizedY = (rawY / maxTilt).coerceIn(-1f, 1f)
        val dotX = centerX + normalizedY * indicatorSize / 2 * 0.8f
        val dotY = indicatorCenterY - normalizedX * indicatorSize / 2 * 0.8f

        // Dot shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.45f)
        ui.shapes.circle(dotX + 3, dotY - 3, 16f * scale)

        // Dot glow when stable
        if (isStable) {
            val glowPulse = UITheme.Anim.pulse(4f, 0.35f, 0.7f)
            ui.shapes.color = UITheme.withAlpha(UITheme.accent, glowPulse)
            ui.shapes.circle(dotX, dotY, 26f * scale)
        }

        // Main dot - color shows stability
        val dotColor = if (isStable) UITheme.accent else UITheme.cyan
        ui.shapes.color = dotColor
        ui.shapes.circle(dotX, dotY, 14f * scale)

        // Dot highlight
        ui.shapes.color = UITheme.withAlpha(Color.WHITE, 0.55f)
        ui.shapes.circle(dotX - 4f * scale, dotY + 4f * scale, 5f * scale)

        // Success flash
        if (successFlash > 0.1f) {
            ui.shapes.color = UITheme.withAlpha(UITheme.accent, successFlash * 0.35f)
            ui.shapes.rect(0f, 0f, sw, sh)
        }

        // Buttons - larger and side by side
        val buttonWidth = 230f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonSpacing = 28f * scale
        val totalWidth = buttonWidth * 2 + buttonSpacing
        val buttonsY = panelY + 40f * scale

        calibrateButton.set(centerX - totalWidth / 2, buttonsY, buttonWidth, buttonHeight)
        skipButton.set(centerX + buttonSpacing / 2, buttonsY, buttonWidth, buttonHeight)

        ui.button(calibrateButton, UITheme.accent, glowIntensity = calibrateHover * 0.7f + (if (isStable) 0.35f else 0f))
        ui.button(skipButton, UITheme.surfaceLight, glowIntensity = skipHover * 0.4f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        // Title
        val titleY = panelY + panelHeight - 55f * scale
        ui.textCentered("CALIBRATION", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Instructions - clearer explanation with more spacing
        val instrY = titleY - 70f * scale
        ui.textCentered("Hold phone as you will during gameplay", centerX, instrY, UIFonts.body, UITheme.textSecondary)

        val instrY2 = instrY - 50f * scale
        ui.textCentered("This position = neutral (no lean)", centerX, instrY2, UIFonts.caption, UITheme.textMuted)

        val instrY3 = instrY2 - 55f * scale
        val hintColor = if (isStable) UITheme.accent else UITheme.textMuted
        val hintText = if (isStable) "Ready! Tap CALIBRATE" else "Hold steady..."
        ui.textCentered(hintText, centerX, instrY3, UIFonts.body, hintColor)

        // Accelerometer values
        val valuesY = indicatorCenterY - indicatorSize / 2 - 30f * scale
        val valuesText = "X: ${String.format(java.util.Locale.US, "%.1f", rawX)}  Y: ${String.format(java.util.Locale.US, "%.1f", rawY)}"
        ui.textCentered(valuesText, centerX, valuesY, UIFonts.caption, UITheme.textMuted)

        // Button labels
        ui.textCentered("CALIBRATE", calibrateButton.x + calibrateButton.width / 2, calibrateButton.y + calibrateButton.height / 2,
            UIFonts.button, UITheme.textPrimary)
        ui.textCentered("SKIP", skipButton.x + skipButton.width / 2, skipButton.y + skipButton.height / 2,
            UIFonts.button, UITheme.textPrimary)

        ui.endBatch()

        // === Input ===
        if (Gdx.input.justTouched()) {
            if (calibrateButton.contains(touchX, touchY)) {
                UIFeedback.clickHeavy()
                successFlash = 1f
                return Action.CALIBRATE
            }
            if (skipButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return Action.SKIP
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            UIFeedback.clickHeavy()
            successFlash = 1f
            return Action.CALIBRATE
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            UIFeedback.click()
            return Action.SKIP
        }

        return Action.NONE
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
        enterAnim = 0f
    }

    fun recreate() {
        ui.recreate()
    }

    override fun dispose() {
        ui.dispose()
    }

    /**
     * Calculate how much the device has moved recently by analyzing the trail.
     * Returns a value representing movement magnitude (0 = perfectly still).
     */
    private fun calculateRecentMovement(): Float {
        if (dotTrailX.size < 2) return 0f

        var totalMovement = 0f
        // Compare recent trail positions to detect movement
        for (i in 0 until dotTrailX.size - 1) {
            val idx1 = (trailIndex - i + dotTrailX.size) % dotTrailX.size
            val idx2 = (trailIndex - i - 1 + dotTrailX.size) % dotTrailX.size

            val dx = dotTrailX[idx1] - dotTrailX[idx2]
            val dy = dotTrailY[idx1] - dotTrailY[idx2]
            totalMovement += kotlin.math.sqrt(dx * dx + dy * dy)
        }

        return totalMovement
    }
}
