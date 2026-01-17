package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable

class CalibrationRenderer : Disposable {
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
    private val layout = GlyphLayout()

    // Fonts with smooth filtering
    private val titleFont = createSmoothFont(3.5f)
    private val instructionFont = createSmoothFont(1.8f)
    private val buttonFont = createSmoothFont(2.2f)
    private val valueFont = createSmoothFont(1.5f)

    private var screenWidth = Gdx.graphics.width.toFloat()
    private var screenHeight = Gdx.graphics.height.toFloat()

    private val calibrateButton = Rectangle()
    private val skipButton = Rectangle()

    // Modern colors
    private val bgDark = Color(0.08f, 0.08f, 0.12f, 1f)
    private val panelBg = Color(0.12f, 0.12f, 0.18f, 0.98f)
    private val accentGreen = Color(0.2f, 0.8f, 0.4f, 1f)
    private val accentGreenDark = Color(0.15f, 0.6f, 0.3f, 1f)
    private val accentGray = Color(0.35f, 0.35f, 0.4f, 1f)
    private val accentGrayDark = Color(0.25f, 0.25f, 0.3f, 1f)
    private val accentCyan = Color(0.3f, 0.9f, 0.9f, 1f)
    private val textWhite = Color(0.95f, 0.95f, 0.95f, 1f)
    private val textGray = Color(0.55f, 0.55f, 0.6f, 1f)

    private fun createSmoothFont(scale: Float): BitmapFont {
        return BitmapFont().apply {
            data.setScale(scale)
            setUseIntegerPositions(false)
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    enum class Action {
        NONE, CALIBRATE, SKIP
    }

    fun render(rawX: Float, rawY: Float): Action {
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Background
        shapeRenderer.color = bgDark
        shapeRenderer.rect(0f, 0f, screenWidth, screenHeight)

        // Central panel
        val panelWidth = 500f
        val panelHeight = 400f
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        // Panel shadow
        shapeRenderer.color = Color(0f, 0f, 0f, 0.3f)
        drawRoundedRect(panelX + 4, panelY - 4, panelWidth, panelHeight, 16f)

        // Panel background
        shapeRenderer.color = panelBg
        drawRoundedRect(panelX, panelY, panelWidth, panelHeight, 16f)

        // Tilt indicator
        val indicatorSize = 160f
        val indicatorCenterX = centerX
        val indicatorCenterY = centerY + 50

        // Indicator shadow
        shapeRenderer.color = Color(0f, 0f, 0f, 0.3f)
        shapeRenderer.circle(indicatorCenterX + 2, indicatorCenterY - 2, indicatorSize / 2 + 2)

        // Indicator background
        shapeRenderer.color = Color(0.15f, 0.15f, 0.2f, 1f)
        shapeRenderer.circle(indicatorCenterX, indicatorCenterY, indicatorSize / 2)

        // Target zone
        shapeRenderer.color = Color(0.2f, 0.5f, 0.3f, 0.6f)
        shapeRenderer.circle(indicatorCenterX, indicatorCenterY, 25f)

        // Crosshair
        shapeRenderer.color = Color(0.35f, 0.35f, 0.4f, 0.6f)
        shapeRenderer.rectLine(indicatorCenterX - indicatorSize / 2 + 12, indicatorCenterY,
            indicatorCenterX + indicatorSize / 2 - 12, indicatorCenterY, 1.5f)
        shapeRenderer.rectLine(indicatorCenterX, indicatorCenterY - indicatorSize / 2 + 12,
            indicatorCenterX, indicatorCenterY + indicatorSize / 2 - 12, 1.5f)

        // Current tilt position
        val maxTilt = 10f
        val normalizedX = (rawX / maxTilt).coerceIn(-1f, 1f)
        val normalizedY = (rawY / maxTilt).coerceIn(-1f, 1f)
        val dotX = indicatorCenterX + normalizedY * indicatorSize / 2 * 0.75f
        val dotY = indicatorCenterY - normalizedX * indicatorSize / 2 * 0.75f

        // Dot shadow
        shapeRenderer.color = Color(0f, 0f, 0f, 0.4f)
        shapeRenderer.circle(dotX + 1.5f, dotY - 1.5f, 13f)

        // Main dot
        shapeRenderer.color = accentCyan
        shapeRenderer.circle(dotX, dotY, 12f)

        // Highlight
        shapeRenderer.color = Color(1f, 1f, 1f, 0.4f)
        shapeRenderer.circle(dotX - 3, dotY + 3, 4f)

        // Button dimensions
        val buttonWidth = 180f
        val buttonHeight = 50f
        val buttonSpacing = 15f
        val totalButtonsWidth = buttonWidth * 2 + buttonSpacing

        // Buttons side by side
        val buttonsY = panelY + 25
        calibrateButton.set(centerX - totalButtonsWidth / 2, buttonsY, buttonWidth, buttonHeight)
        skipButton.set(centerX + buttonSpacing / 2, buttonsY, buttonWidth, buttonHeight)

        // Calibrate button
        shapeRenderer.color = accentGreenDark
        drawRoundedRect(calibrateButton.x, calibrateButton.y - 3, calibrateButton.width, calibrateButton.height, 10f)
        shapeRenderer.color = accentGreen
        drawRoundedRect(calibrateButton.x, calibrateButton.y, calibrateButton.width, calibrateButton.height, 10f)

        // Skip button
        shapeRenderer.color = accentGrayDark
        drawRoundedRect(skipButton.x, skipButton.y - 3, skipButton.width, skipButton.height, 10f)
        shapeRenderer.color = accentGray
        drawRoundedRect(skipButton.x, skipButton.y, skipButton.width, skipButton.height, 10f)

        shapeRenderer.end()

        batch.begin()

        // Title
        titleFont.color = textWhite
        layout.setText(titleFont, "CALIBRATION")
        titleFont.draw(batch, "CALIBRATION", centerX - layout.width / 2, panelY + panelHeight - 30)

        // Instructions with proper spacing
        instructionFont.color = textGray
        layout.setText(instructionFont, "Hold phone in playing position")
        instructionFont.draw(batch, "Hold phone in playing position", centerX - layout.width / 2, panelY + panelHeight - 75)

        layout.setText(instructionFont, "Center the dot, then tap CALIBRATE")
        instructionFont.draw(batch, "Center the dot, then tap CALIBRATE", centerX - layout.width / 2, panelY + panelHeight - 105)

        // Accelerometer values (below indicator)
        valueFont.color = textGray
        val valuesText = "X: ${String.format("%.1f", rawX)}  Y: ${String.format("%.1f", rawY)}"
        layout.setText(valueFont, valuesText)
        valueFont.draw(batch, valuesText, centerX - layout.width / 2, indicatorCenterY - indicatorSize / 2 - 18)

        // Button text
        buttonFont.color = textWhite

        layout.setText(buttonFont, "CALIBRATE")
        buttonFont.draw(batch, "CALIBRATE",
            calibrateButton.x + (calibrateButton.width - layout.width) / 2,
            calibrateButton.y + calibrateButton.height / 2 + layout.height / 2)

        layout.setText(buttonFont, "SKIP")
        buttonFont.draw(batch, "SKIP",
            skipButton.x + (skipButton.width - layout.width) / 2,
            skipButton.y + skipButton.height / 2 + layout.height / 2)

        batch.end()

        // Check for clicks/touches
        if (Gdx.input.justTouched()) {
            val touchX = Gdx.input.x.toFloat()
            val touchY = screenHeight - Gdx.input.y.toFloat()

            if (calibrateButton.contains(touchX, touchY)) {
                return Action.CALIBRATE
            }
            if (skipButton.contains(touchX, touchY)) {
                return Action.SKIP
            }
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            return Action.CALIBRATE
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            return Action.SKIP
        }

        return Action.NONE
    }

    private fun drawRoundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        shapeRenderer.rect(x + radius, y, width - 2 * radius, height)
        shapeRenderer.rect(x, y + radius, width, height - 2 * radius)
        shapeRenderer.circle(x + radius, y + radius, radius)
        shapeRenderer.circle(x + width - radius, y + radius, radius)
        shapeRenderer.circle(x + radius, y + height - radius, radius)
        shapeRenderer.circle(x + width - radius, y + height - radius, radius)
    }

    fun resize(width: Int, height: Int) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        batch.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
    }

    override fun dispose() {
        batch.dispose()
        titleFont.dispose()
        instructionFont.dispose()
        buttonFont.dispose()
        valueFont.dispose()
        shapeRenderer.dispose()
    }
}
