package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.state.GameSession
import kotlin.math.sqrt

class Hud : Disposable {
    private val batch = SpriteBatch()
    private val shapeRenderer = ShapeRenderer()
    private val layout = GlyphLayout()

    // Fonts with smooth filtering
    private val scoreFont = createSmoothFont(3.5f)
    private val labelFont = createSmoothFont(1.4f)
    private val speedFont = createSmoothFont(2.8f)
    private val unitFont = createSmoothFont(1.6f)
    private val warningFont = createSmoothFont(2.8f)
    private val countdownFont = createSmoothFont(5f)

    private var screenWidth = Gdx.graphics.width.toFloat()
    private var screenHeight = Gdx.graphics.height.toFloat()

    // Modern colors
    private val panelBg = Color(0.1f, 0.1f, 0.15f, 0.85f)
    private val accentGreen = Color(0.2f, 0.8f, 0.4f, 1f)
    private val accentYellow = Color(1f, 0.9f, 0.3f, 1f)
    private val accentRed = Color(0.9f, 0.3f, 0.3f, 1f)
    private val accentCyan = Color(0.3f, 0.9f, 0.9f, 1f)
    private val textWhite = Color(0.95f, 0.95f, 0.95f, 1f)
    private val textGray = Color(0.6f, 0.6f, 0.65f, 1f)

    private fun createSmoothFont(scale: Float): BitmapFont {
        return BitmapFont().apply {
            data.setScale(scale)
            setUseIntegerPositions(false)
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    fun render(session: GameSession, euc: EucComponent) {
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)

        // Draw lean indicator
        drawLeanIndicator(euc)

        // Draw speed panel
        drawSpeedPanel(euc)

        batch.begin()

        // Score (top center)
        scoreFont.color = textWhite
        val scoreText = session.score.toString()
        layout.setText(scoreFont, scoreText)
        scoreFont.draw(batch, scoreText, screenWidth / 2 - layout.width / 2, screenHeight - 25)

        // "SCORE" label
        labelFont.color = textGray
        layout.setText(labelFont, "SCORE")
        labelFont.draw(batch, "SCORE", screenWidth / 2 - layout.width / 2, screenHeight - 70)

        // Puddle warning
        if (euc.inPuddle) {
            drawWarningBadge("PUDDLE!", accentCyan, 0f)
        }

        // Danger warning
        if (euc.isAboutToFall()) {
            drawWarningBadge("DANGER!", accentRed, 50f)
        }

        batch.end()
    }

    private fun drawWarningBadge(text: String, color: Color, offsetY: Float) {
        // Pulsing effect
        val pulse = (Math.sin(Gdx.graphics.frameId * 0.15) * 0.3 + 0.7).toFloat()
        warningFont.color = Color(color.r, color.g, color.b, pulse)
        layout.setText(warningFont, text)
        warningFont.draw(batch, text, screenWidth / 2 - layout.width / 2, screenHeight / 2 + offsetY)
    }

    private fun drawSpeedPanel(euc: EucComponent) {
        val panelWidth = 160f
        val panelHeight = 80f
        val panelX = 15f
        val panelY = 15f

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Panel background
        shapeRenderer.color = panelBg
        drawRoundedRect(panelX, panelY, panelWidth, panelHeight, 10f)

        // Speed bar background
        val barX = panelX + 10
        val barY = panelY + 10
        val barWidth = panelWidth - 20
        val barHeight = 6f

        shapeRenderer.color = Color(0.2f, 0.2f, 0.25f, 1f)
        shapeRenderer.rect(barX, barY, barWidth, barHeight)

        // Speed bar fill
        val speedPercent = (euc.speed / 20f).coerceIn(0f, 1f)
        val barColor = when {
            speedPercent < 0.5f -> accentGreen
            speedPercent < 0.8f -> accentYellow
            else -> accentRed
        }
        shapeRenderer.color = barColor
        shapeRenderer.rect(barX, barY, barWidth * speedPercent, barHeight)

        shapeRenderer.end()

        batch.begin()

        val speedKmh = (euc.speed * 3.6f).toInt()
        speedFont.color = textWhite
        layout.setText(speedFont, "$speedKmh")
        speedFont.draw(batch, "$speedKmh", panelX + 18, panelY + panelHeight - 12)

        unitFont.color = textGray
        unitFont.draw(batch, "km/h", panelX + 18 + layout.width + 8, panelY + panelHeight - 20)

        batch.end()
    }

    private fun drawLeanIndicator(euc: EucComponent) {
        val indicatorSize = 120f
        val indicatorX = screenWidth - indicatorSize - 15
        val indicatorY = 15f
        val centerX = indicatorX + indicatorSize / 2
        val centerY = indicatorY + indicatorSize / 2

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)

        // Shadow
        shapeRenderer.color = Color(0f, 0f, 0f, 0.3f)
        shapeRenderer.circle(centerX + 2, centerY - 2, indicatorSize / 2 + 2)

        // Background
        shapeRenderer.color = panelBg
        shapeRenderer.circle(centerX, centerY, indicatorSize / 2)

        // Danger zone (red)
        val dangerThreshold = 0.7f
        shapeRenderer.color = Color(0.5f, 0.12f, 0.12f, 0.6f)
        shapeRenderer.circle(centerX, centerY, indicatorSize / 2 - 3)

        // Warning zone (yellow)
        shapeRenderer.color = Color(0.5f, 0.4f, 0.1f, 0.5f)
        shapeRenderer.circle(centerX, centerY, indicatorSize / 2 * 0.82f)

        // Safe zone (green)
        shapeRenderer.color = Color(0.12f, 0.4f, 0.15f, 0.7f)
        shapeRenderer.circle(centerX, centerY, indicatorSize / 2 * dangerThreshold)

        // Crosshair
        shapeRenderer.color = Color(0.35f, 0.35f, 0.4f, 0.5f)
        shapeRenderer.rectLine(centerX - indicatorSize / 2 + 8, centerY,
            centerX + indicatorSize / 2 - 8, centerY, 1.5f)
        shapeRenderer.rectLine(centerX, centerY - indicatorSize / 2 + 8,
            centerX, centerY + indicatorSize / 2 - 8, 1.5f)

        // Current lean position
        val totalLean = sqrt(euc.forwardLean * euc.forwardLean + euc.sideLean * euc.sideLean)
        val indicatorPosX = centerX + euc.sideLean * indicatorSize / 2 * 0.82f
        val indicatorPosY = centerY + euc.forwardLean * indicatorSize / 2 * 0.82f

        // Dot shadow
        shapeRenderer.color = Color(0f, 0f, 0f, 0.4f)
        shapeRenderer.circle(indicatorPosX + 1.5f, indicatorPosY - 1.5f, 9f)

        // Dot
        val dotColor = when {
            totalLean > 0.85f -> accentRed
            totalLean > dangerThreshold -> accentYellow
            else -> textWhite
        }
        shapeRenderer.color = dotColor
        shapeRenderer.circle(indicatorPosX, indicatorPosY, 8f)

        // Highlight
        shapeRenderer.color = Color(1f, 1f, 1f, 0.4f)
        shapeRenderer.circle(indicatorPosX - 2, indicatorPosY + 2, 3f)

        shapeRenderer.end()
    }

    private fun drawRoundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float) {
        shapeRenderer.rect(x + radius, y, width - 2 * radius, height)
        shapeRenderer.rect(x, y + radius, width, height - 2 * radius)
        shapeRenderer.circle(x + radius, y + radius, radius)
        shapeRenderer.circle(x + width - radius, y + radius, radius)
        shapeRenderer.circle(x + radius, y + height - radius, radius)
        shapeRenderer.circle(x + width - radius, y + height - radius, radius)
    }

    fun renderCountdown(seconds: Int) {
        Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.color = Color(0f, 0f, 0f, 0.5f)
        shapeRenderer.circle(screenWidth / 2, screenHeight / 2, 70f)
        shapeRenderer.end()

        batch.begin()
        countdownFont.color = if (seconds > 0) accentYellow else accentGreen
        val text = if (seconds > 0) seconds.toString() else "GO!"
        layout.setText(countdownFont, text)
        countdownFont.draw(batch, text, screenWidth / 2 - layout.width / 2, screenHeight / 2 + layout.height / 2)
        batch.end()
    }

    fun resize(width: Int, height: Int) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        batch.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapeRenderer.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
    }

    override fun dispose() {
        batch.dispose()
        scoreFont.dispose()
        labelFont.dispose()
        speedFont.dispose()
        unitFont.dispose()
        warningFont.dispose()
        countdownFont.dispose()
        shapeRenderer.dispose()
    }
}
