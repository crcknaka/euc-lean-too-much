package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable

class CalibrationRenderer : Disposable {
    private val ui = UIRenderer()

    private val calibrateButton = Rectangle()
    private val skipButton = Rectangle()

    // Animation states
    private var enterAnim = 0f
    private var dotTrailX = FloatArray(10)
    private var dotTrailY = FloatArray(10)
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

        // Animations
        enterAnim = UITheme.Anim.ease(enterAnim, 1f, 4f)
        successFlash = UITheme.Anim.ease(successFlash, 0f, 3f)

        // Hover states
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        calibrateHover = UITheme.Anim.ease(calibrateHover, if (calibrateButton.contains(touchX, touchY)) 1f else 0f, 8f)
        skipHover = UITheme.Anim.ease(skipHover, if (skipButton.contains(touchX, touchY)) 1f else 0f, 8f)

        // Update dot trail
        trailTimer += Gdx.graphics.deltaTime
        if (trailTimer > 0.05f) {
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

        // Panel
        val uiScale = UITheme.Dimensions.scale()
        val panelWidth = 500f * uiScale * enterAnim
        val panelHeight = 480f * uiScale * enterAnim
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface)

        // Tilt indicator
        val indicatorSize = 180f * enterAnim
        val indicatorCenterY = centerY + 40

        // Indicator shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.3f)
        ui.shapes.circle(centerX + 3, indicatorCenterY - 3, indicatorSize / 2 + 3)

        // Indicator background
        ui.shapes.color = UITheme.surfaceLight
        ui.shapes.circle(centerX, indicatorCenterY, indicatorSize / 2)

        // Grid circles
        ui.shapes.color = UITheme.withAlpha(UITheme.textMuted, 0.15f)
        for (r in listOf(0.33f, 0.66f, 1f)) {
            // Draw circle outline approximation
            val segments = 32
            val radius = indicatorSize / 2 * r - 5
            for (i in 0 until segments) {
                val angle1 = (i.toFloat() / segments) * MathUtils.PI2
                val angle2 = ((i + 1).toFloat() / segments) * MathUtils.PI2
                ui.shapes.rectLine(
                    centerX + radius * MathUtils.cos(angle1),
                    indicatorCenterY + radius * MathUtils.sin(angle1),
                    centerX + radius * MathUtils.cos(angle2),
                    indicatorCenterY + radius * MathUtils.sin(angle2),
                    1f
                )
            }
        }

        // Crosshair
        ui.shapes.color = UITheme.withAlpha(UITheme.textMuted, 0.3f)
        ui.shapes.rectLine(centerX - indicatorSize / 2 + 10, indicatorCenterY,
            centerX + indicatorSize / 2 - 10, indicatorCenterY, 1.5f)
        ui.shapes.rectLine(centerX, indicatorCenterY - indicatorSize / 2 + 10,
            centerX, indicatorCenterY + indicatorSize / 2 - 10, 1.5f)

        // Target zone (center)
        val targetPulse = UITheme.Anim.pulse(2f, 0.4f, 0.6f)
        ui.shapes.color = UITheme.withAlpha(UITheme.primary, targetPulse * 0.4f)
        ui.shapes.circle(centerX, indicatorCenterY, 30f)
        ui.shapes.color = UITheme.withAlpha(UITheme.primary, targetPulse * 0.7f)
        ui.shapes.circle(centerX, indicatorCenterY, 15f)

        // Dot trail
        for (i in dotTrailX.indices) {
            val idx = (trailIndex - i + dotTrailX.size) % dotTrailX.size
            val alpha = (1f - i.toFloat() / dotTrailX.size) * 0.3f
            val trailDotX = centerX + dotTrailX[idx] * indicatorSize / 2 * 0.8f
            val trailDotY = indicatorCenterY + dotTrailY[idx] * indicatorSize / 2 * 0.8f
            ui.shapes.color = UITheme.withAlpha(UITheme.cyan, alpha)
            ui.shapes.circle(trailDotX, trailDotY, 4f - i * 0.3f)
        }

        // Current position dot
        val maxTilt = 10f
        val normalizedX = (rawX / maxTilt).coerceIn(-1f, 1f)
        val normalizedY = (rawY / maxTilt).coerceIn(-1f, 1f)
        val dotX = centerX + normalizedY * indicatorSize / 2 * 0.8f
        val dotY = indicatorCenterY - normalizedX * indicatorSize / 2 * 0.8f

        // Is centered?
        val distFromCenter = Vector2.dst(dotX, dotY, centerX, indicatorCenterY)
        val isCentered = distFromCenter < 25f

        // Dot shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.4f)
        ui.shapes.circle(dotX + 2, dotY - 2, 14f)

        // Dot glow when centered
        if (isCentered) {
            val glowPulse = UITheme.Anim.pulse(4f, 0.3f, 0.6f)
            ui.shapes.color = UITheme.withAlpha(UITheme.primary, glowPulse)
            ui.shapes.circle(dotX, dotY, 20f)
        }

        // Main dot
        val dotColor = if (isCentered) UITheme.primary else UITheme.cyan
        ui.shapes.color = dotColor
        ui.shapes.circle(dotX, dotY, 12f)

        // Dot highlight
        ui.shapes.color = UITheme.withAlpha(Color.WHITE, 0.5f)
        ui.shapes.circle(dotX - 3, dotY + 3, 4f)

        // Success flash
        if (successFlash > 0.1f) {
            ui.shapes.color = UITheme.withAlpha(UITheme.primary, successFlash * 0.3f)
            ui.shapes.rect(0f, 0f, sw, sh)
        }

        // Buttons
        val buttonWidth = 200f * uiScale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonSpacing = 20f * uiScale
        val totalWidth = buttonWidth * 2 + buttonSpacing
        val buttonsY = panelY + 28f * uiScale

        calibrateButton.set(centerX - totalWidth / 2, buttonsY, buttonWidth, buttonHeight)
        skipButton.set(centerX + buttonSpacing / 2, buttonsY, buttonWidth, buttonHeight)

        ui.button(calibrateButton, UITheme.primary, glowIntensity = calibrateHover * 0.6f + (if (isCentered) 0.3f else 0f))
        ui.button(skipButton, UITheme.surfaceLight, glowIntensity = skipHover * 0.3f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        // Title
        val titleY = panelY + panelHeight - 35 * enterAnim
        ui.textCentered("CALIBRATION", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Instructions
        val instrY = titleY - 50
        UIFonts.body.color = UITheme.textSecondary
        ui.textCentered("Hold device in playing position", centerX, instrY, UIFonts.body, UITheme.textSecondary)

        val instrY2 = instrY - 30
        val hintColor = if (isCentered) UITheme.primary else UITheme.textMuted
        ui.textCentered(
            if (isCentered) "Centered! Tap CALIBRATE" else "Center the dot, then tap CALIBRATE",
            centerX, instrY2, UIFonts.caption, hintColor
        )

        // Accelerometer values
        val valuesY = indicatorCenterY - indicatorSize / 2 - 25
        UIFonts.tiny.color = UITheme.textMuted
        val valuesText = "X: ${String.format(java.util.Locale.US, "%.1f", rawX)}  Y: ${String.format(java.util.Locale.US, "%.1f", rawY)}"
        ui.textCentered(valuesText, centerX, valuesY, UIFonts.tiny, UITheme.textMuted)

        // Button labels
        ui.textCentered("CALIBRATE", calibrateButton.x + calibrateButton.width / 2, calibrateButton.y + calibrateButton.height / 2, UIFonts.button, UITheme.textPrimary)
        ui.textCentered("SKIP", skipButton.x + skipButton.width / 2, skipButton.y + skipButton.height / 2, UIFonts.button, UITheme.textPrimary)

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

    override fun dispose() {
        ui.dispose()
    }
}
