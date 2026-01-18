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
 * Features a clear visual indicator for device orientation.
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

        // Update dot trail
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

        ui.beginShapes()

        // Gradient background
        for (i in 0 until 20) {
            val t = i / 20f
            val stripY = sh * t
            ui.shapes.color = UITheme.lerp(UITheme.background, UITheme.backgroundLight, t)
            ui.shapes.rect(0f, stripY, sw, sh / 20f + 1)
        }

        // Panel with enter animation
        val panelWidth = 580f * scale * enterAnim
        val panelHeight = 560f * scale * enterAnim
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface)

        // Tilt indicator - larger for better visibility
        val indicatorSize = 220f * scale * enterAnim
        val indicatorCenterY = centerY + 50f * scale

        // Indicator shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.35f)
        ui.shapes.circle(centerX + 4, indicatorCenterY - 4, indicatorSize / 2 + 4)

        // Indicator background
        ui.shapes.color = UITheme.surfaceLight
        ui.shapes.circle(centerX, indicatorCenterY, indicatorSize / 2)

        // Concentric grid circles
        ui.shapes.color = UITheme.withAlpha(UITheme.textMuted, 0.12f)
        for (r in listOf(0.33f, 0.66f, 1f)) {
            val segments = 36
            val radius = indicatorSize / 2 * r - 6
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
        }

        // Crosshair
        ui.shapes.color = UITheme.withAlpha(UITheme.textMuted, 0.25f)
        ui.shapes.rectLine(centerX - indicatorSize / 2 + 12, indicatorCenterY,
            centerX + indicatorSize / 2 - 12, indicatorCenterY, 2f)
        ui.shapes.rectLine(centerX, indicatorCenterY - indicatorSize / 2 + 12,
            centerX, indicatorCenterY + indicatorSize / 2 - 12, 2f)

        // Target zone (center) with pulsing effect
        val targetPulse = UITheme.Anim.pulse(2f, 0.4f, 0.65f)
        ui.shapes.color = UITheme.withAlpha(UITheme.accent, targetPulse * 0.35f)
        ui.shapes.circle(centerX, indicatorCenterY, 38f * scale)
        ui.shapes.color = UITheme.withAlpha(UITheme.accent, targetPulse * 0.6f)
        ui.shapes.circle(centerX, indicatorCenterY, 20f * scale)

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

        // Is centered?
        val distFromCenter = Vector2.dst(dotX, dotY, centerX, indicatorCenterY)
        val isCentered = distFromCenter < 30f * scale

        // Dot shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.45f)
        ui.shapes.circle(dotX + 3, dotY - 3, 16f * scale)

        // Dot glow when centered
        if (isCentered) {
            val glowPulse = UITheme.Anim.pulse(4f, 0.35f, 0.7f)
            ui.shapes.color = UITheme.withAlpha(UITheme.accent, glowPulse)
            ui.shapes.circle(dotX, dotY, 26f * scale)
        }

        // Main dot - larger for visibility
        val dotColor = if (isCentered) UITheme.accent else UITheme.cyan
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

        ui.button(calibrateButton, UITheme.accent, glowIntensity = calibrateHover * 0.7f + (if (isCentered) 0.35f else 0f))
        ui.button(skipButton, UITheme.surfaceLight, glowIntensity = skipHover * 0.4f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        // Title
        val titleY = panelY + panelHeight - 50f * scale
        ui.textCentered("CALIBRATION", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Instructions
        val instrY = titleY - 60f * scale
        ui.textCentered("Hold device in playing position", centerX, instrY, UIFonts.body, UITheme.textSecondary)

        val instrY2 = instrY - 40f * scale
        val hintColor = if (isCentered) UITheme.accent else UITheme.textMuted
        val hintText = if (isCentered) "Centered! Tap CALIBRATE" else "Center the dot, then tap CALIBRATE"
        ui.textCentered(hintText, centerX, instrY2, UIFonts.caption, hintColor)

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
                successFlash = 1f
                return Action.CALIBRATE
            }
            if (skipButton.contains(touchX, touchY)) {
                return Action.SKIP
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            successFlash = 1f
            return Action.CALIBRATE
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
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
}
