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
import com.eucleantoomuch.game.state.GameSession

class GameOverRenderer : Disposable {
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
    private val layout = GlyphLayout()

    // Fonts with smooth filtering
    private val titleFont = createSmoothFont(4f)
    private val labelFont = createSmoothFont(2f)
    private val valueFont = createSmoothFont(2.2f)
    private val buttonFont = createSmoothFont(2.3f)
    private val badgeFont = createSmoothFont(2.5f)

    private var screenWidth = Gdx.graphics.width.toFloat()
    private var screenHeight = Gdx.graphics.height.toFloat()

    private val retryButton = Rectangle()
    private val menuButton = Rectangle()

    // Modern colors
    private val bgOverlay = Color(0.05f, 0.05f, 0.1f, 0.92f)
    private val panelColor = Color(0.12f, 0.12f, 0.18f, 0.98f)
    private val accentGreen = Color(0.2f, 0.8f, 0.4f, 1f)
    private val accentGreenDark = Color(0.15f, 0.6f, 0.3f, 1f)
    private val accentGray = Color(0.35f, 0.35f, 0.4f, 1f)
    private val accentGrayDark = Color(0.25f, 0.25f, 0.3f, 1f)
    private val goldColor = Color(1f, 0.85f, 0.3f, 1f)
    private val redColor = Color(0.9f, 0.25f, 0.25f, 1f)
    private val textWhite = Color(0.95f, 0.95f, 0.95f, 1f)
    private val textGray = Color(0.65f, 0.65f, 0.7f, 1f)

    private fun createSmoothFont(scale: Float): BitmapFont {
        return BitmapFont().apply {
            data.setScale(scale)
            setUseIntegerPositions(false)
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    enum class ButtonClicked {
        NONE, RETRY, MENU
    }

    fun render(session: GameSession, isNewHighScore: Boolean): ButtonClicked {
        val centerX = screenWidth / 2
        val centerY = screenHeight / 2

        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Full screen dark overlay
        shapeRenderer.color = bgOverlay
        shapeRenderer.rect(0f, 0f, screenWidth, screenHeight)

        // Central panel - sized to fit content properly
        val panelWidth = 480f
        val panelHeight = 380f
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        // Panel shadow
        shapeRenderer.color = Color(0f, 0f, 0f, 0.4f)
        drawRoundedRect(panelX + 5, panelY - 5, panelWidth, panelHeight, 16f)

        // Panel background
        shapeRenderer.color = panelColor
        drawRoundedRect(panelX, panelY, panelWidth, panelHeight, 16f)

        // Button dimensions
        val buttonWidth = 200f
        val buttonHeight = 55f
        val buttonSpacing = 15f
        val totalButtonsWidth = buttonWidth * 2 + buttonSpacing

        // Position buttons side by side at bottom of panel
        val buttonsY = panelY + 25
        retryButton.set(centerX - totalButtonsWidth / 2, buttonsY, buttonWidth, buttonHeight)
        menuButton.set(centerX + buttonSpacing / 2, buttonsY, buttonWidth, buttonHeight)

        // Retry button (green)
        shapeRenderer.color = accentGreenDark
        drawRoundedRect(retryButton.x, retryButton.y - 3, retryButton.width, retryButton.height, 10f)
        shapeRenderer.color = accentGreen
        drawRoundedRect(retryButton.x, retryButton.y, retryButton.width, retryButton.height, 10f)

        // Menu button (gray)
        shapeRenderer.color = accentGrayDark
        drawRoundedRect(menuButton.x, menuButton.y - 3, menuButton.width, menuButton.height, 10f)
        shapeRenderer.color = accentGray
        drawRoundedRect(menuButton.x, menuButton.y, menuButton.width, menuButton.height, 10f)

        shapeRenderer.end()

        batch.begin()

        // Title - "GAME OVER" at top of panel
        titleFont.color = redColor
        layout.setText(titleFont, "GAME OVER")
        val titleY = panelY + panelHeight - 35
        titleFont.draw(batch, "GAME OVER", centerX - layout.width / 2, titleY)

        // New high score badge (if applicable)
        var statsStartY = titleY - 65
        if (isNewHighScore) {
            badgeFont.color = goldColor
            layout.setText(badgeFont, "NEW HIGH SCORE!")
            badgeFont.draw(batch, "NEW HIGH SCORE!", centerX - layout.width / 2, statsStartY)
            statsStartY -= 50
        }

        // Stats layout with proper spacing
        val statsLeftX = centerX - 100
        val statsRightX = centerX + 30
        val lineSpacing = 42f

        // Score
        labelFont.color = textGray
        labelFont.draw(batch, "SCORE", statsLeftX, statsStartY)
        valueFont.color = goldColor
        valueFont.draw(batch, session.score.toString(), statsRightX, statsStartY)

        // Distance
        labelFont.color = textGray
        labelFont.draw(batch, "DISTANCE", statsLeftX, statsStartY - lineSpacing)
        valueFont.color = textWhite
        valueFont.draw(batch, "${session.distanceTraveled.toInt()} m", statsRightX, statsStartY - lineSpacing)

        // Top Speed
        labelFont.color = textGray
        labelFont.draw(batch, "TOP SPEED", statsLeftX, statsStartY - lineSpacing * 2)
        valueFont.color = textWhite
        valueFont.draw(batch, "${(session.maxSpeed * 3.6f).toInt()} km/h", statsRightX, statsStartY - lineSpacing * 2)

        // Button text (centered)
        buttonFont.color = textWhite

        layout.setText(buttonFont, "RETRY")
        buttonFont.draw(batch, "RETRY",
            retryButton.x + (retryButton.width - layout.width) / 2,
            retryButton.y + retryButton.height / 2 + layout.height / 2)

        layout.setText(buttonFont, "MENU")
        buttonFont.draw(batch, "MENU",
            menuButton.x + (menuButton.width - layout.width) / 2,
            menuButton.y + menuButton.height / 2 + layout.height / 2)

        batch.end()

        // Check for clicks/touches
        if (Gdx.input.justTouched()) {
            val touchX = Gdx.input.x.toFloat()
            val touchY = screenHeight - Gdx.input.y.toFloat()

            if (retryButton.contains(touchX, touchY)) {
                return ButtonClicked.RETRY
            }
            if (menuButton.contains(touchX, touchY)) {
                return ButtonClicked.MENU
            }
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            return ButtonClicked.RETRY
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            return ButtonClicked.MENU
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
        labelFont.dispose()
        valueFont.dispose()
        buttonFont.dispose()
        badgeFont.dispose()
        shapeRenderer.dispose()
    }
}
