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

class MenuRenderer : Disposable {
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
    private val layout = GlyphLayout()

    // Fonts with smooth filtering
    private val titleFont = createSmoothFont(5f)
    private val subtitleFont = createSmoothFont(2.8f)
    private val buttonFont = createSmoothFont(2.5f)
    private val statsFont = createSmoothFont(2f)
    private val smallFont = createSmoothFont(1.6f)

    private var screenWidth = Gdx.graphics.width.toFloat()
    private var screenHeight = Gdx.graphics.height.toFloat()

    private val playButton = Rectangle()
    private val calibrateButton = Rectangle()

    // Modern color scheme
    private val bgDark = Color(0.08f, 0.08f, 0.12f, 1f)
    private val accentGreen = Color(0.2f, 0.8f, 0.4f, 1f)
    private val accentGreenDark = Color(0.15f, 0.6f, 0.3f, 1f)
    private val accentBlue = Color(0.3f, 0.5f, 0.9f, 1f)
    private val accentBlueDark = Color(0.2f, 0.4f, 0.7f, 1f)
    private val goldColor = Color(1f, 0.85f, 0.3f, 1f)
    private val textWhite = Color(0.95f, 0.95f, 0.95f, 1f)
    private val textGray = Color(0.6f, 0.6f, 0.65f, 1f)

    private fun createSmoothFont(scale: Float): BitmapFont {
        return BitmapFont().apply {
            data.setScale(scale)
            setUseIntegerPositions(false)
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    enum class ButtonClicked {
        NONE, PLAY, CALIBRATE
    }

    fun render(highScore: Int, maxDistance: Float): ButtonClicked {
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Background
        shapeRenderer.color = bgDark
        shapeRenderer.rect(0f, 0f, screenWidth, screenHeight)

        // Decorative wheel in background (subtle)
        shapeRenderer.color = Color(0.12f, 0.12f, 0.16f, 1f)
        shapeRenderer.circle(centerX - 280, centerY, 150f)
        shapeRenderer.color = bgDark
        shapeRenderer.circle(centerX - 280, centerY, 110f)

        // Button dimensions
        val buttonWidth = 300f
        val buttonHeight = 70f
        val buttonSpacing = 20f

        // Position buttons in center
        val buttonsStartY = centerY - 20
        playButton.set(centerX - buttonWidth / 2, buttonsStartY, buttonWidth, buttonHeight)
        calibrateButton.set(centerX - buttonWidth / 2, buttonsStartY - buttonHeight - buttonSpacing, buttonWidth, buttonHeight)

        // Play button with 3D effect
        shapeRenderer.color = accentGreenDark
        drawRoundedRect(playButton.x, playButton.y - 4, playButton.width, playButton.height, 12f)
        shapeRenderer.color = accentGreen
        drawRoundedRect(playButton.x, playButton.y, playButton.width, playButton.height, 12f)

        // Calibrate button
        shapeRenderer.color = accentBlueDark
        drawRoundedRect(calibrateButton.x, calibrateButton.y - 4, calibrateButton.width, calibrateButton.height, 12f)
        shapeRenderer.color = accentBlue
        drawRoundedRect(calibrateButton.x, calibrateButton.y, calibrateButton.width, calibrateButton.height, 12f)

        // Stats panel at bottom
        val statsHeight = 70f
        shapeRenderer.color = Color(0.1f, 0.1f, 0.14f, 0.9f)
        shapeRenderer.rect(0f, 0f, screenWidth, statsHeight)

        // Decorative line above stats
        shapeRenderer.color = accentGreen
        shapeRenderer.rect(0f, statsHeight - 2, screenWidth, 2f)

        shapeRenderer.end()

        batch.begin()

        // Title "EUC" - positioned at top with proper spacing
        titleFont.color = textWhite
        layout.setText(titleFont, "EUC")
        val titleY = screenHeight - 50
        titleFont.draw(batch, "EUC", centerX - layout.width / 2, titleY)

        // Subtitle "LEAN TOO MUCH" - with good spacing below title
        subtitleFont.color = accentGreen
        layout.setText(subtitleFont, "LEAN TOO MUCH")
        val subtitleY = titleY - 70  // Good gap below title
        subtitleFont.draw(batch, "LEAN TOO MUCH", centerX - layout.width / 2, subtitleY)

        // Button text (centered)
        buttonFont.color = textWhite

        layout.setText(buttonFont, "PLAY")
        buttonFont.draw(batch, "PLAY",
            playButton.x + (playButton.width - layout.width) / 2,
            playButton.y + playButton.height / 2 + layout.height / 2)

        layout.setText(buttonFont, "CALIBRATE")
        buttonFont.draw(batch, "CALIBRATE",
            calibrateButton.x + (calibrateButton.width - layout.width) / 2,
            calibrateButton.y + calibrateButton.height / 2 + layout.height / 2)

        // Stats at bottom
        val statsY = 42f

        statsFont.color = goldColor
        statsFont.draw(batch, "HIGH SCORE: $highScore", 30f, statsY)

        statsFont.color = textGray
        layout.setText(statsFont, "BEST: ${maxDistance.toInt()}m")
        statsFont.draw(batch, "BEST: ${maxDistance.toInt()}m", screenWidth - layout.width - 30, statsY)

        // Instructions in center bottom
        smallFont.color = textGray
        layout.setText(smallFont, "Tilt to control")
        smallFont.draw(batch, "Tilt to control", centerX - layout.width / 2, statsY)

        batch.end()

        // Check for clicks/touches
        if (Gdx.input.justTouched()) {
            val touchX = Gdx.input.x.toFloat()
            val touchY = screenHeight - Gdx.input.y.toFloat()

            if (playButton.contains(touchX, touchY)) {
                return ButtonClicked.PLAY
            }
            if (calibrateButton.contains(touchX, touchY)) {
                return ButtonClicked.CALIBRATE
            }
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            return ButtonClicked.PLAY
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            return ButtonClicked.CALIBRATE
        }

        return ButtonClicked.NONE
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
        subtitleFont.dispose()
        buttonFont.dispose()
        statsFont.dispose()
        smallFont.dispose()
        shapeRenderer.dispose()
    }
}
