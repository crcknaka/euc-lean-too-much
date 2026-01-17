package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.state.GameSession

class GameOverRenderer : Disposable {
    private val ui = UIRenderer()

    private val retryButton = Rectangle()
    private val menuButton = Rectangle()

    // Animation states
    private var overlayAlpha = 0f
    private var panelScale = 0f
    private var statsReveal = 0f
    private var newHighScoreAnim = 0f
    private var retryHover = 0f
    private var menuHover = 0f

    enum class ButtonClicked {
        NONE, RETRY, MENU
    }

    fun render(session: GameSession, isNewHighScore: Boolean): ButtonClicked {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val centerY = sh / 2

        // Update animations
        overlayAlpha = UITheme.Anim.ease(overlayAlpha, 0.85f, 4f)
        panelScale = UITheme.Anim.ease(panelScale, 1f, 5f)
        statsReveal = UITheme.Anim.ease(statsReveal, 1f, 3f)

        if (isNewHighScore) {
            newHighScoreAnim += Gdx.graphics.deltaTime
        }

        // Check hover
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        retryHover = UITheme.Anim.ease(retryHover, if (retryButton.contains(touchX, touchY)) 1f else 0f, 8f)
        menuHover = UITheme.Anim.ease(menuHover, if (menuButton.contains(touchX, touchY)) 1f else 0f, 8f)

        ui.beginShapes()

        // Dark overlay with vignette effect
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, overlayAlpha)
        ui.shapes.rect(0f, 0f, sw, sh)

        // Vignette corners
        val vignetteColor = UITheme.withAlpha(Color.BLACK, overlayAlpha * 0.3f)
        ui.shapes.color = vignetteColor
        ui.shapes.circle(0f, 0f, sh * 0.3f)
        ui.shapes.circle(sw, 0f, sh * 0.3f)
        ui.shapes.circle(0f, sh, sh * 0.3f)
        ui.shapes.circle(sw, sh, sh * 0.3f)

        // Panel dimensions with scale animation
        val uiScale = UITheme.Dimensions.scale()
        val panelWidth = 480f * uiScale * panelScale
        val panelHeight = 450f * uiScale * panelScale
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        // Panel with glow for new high score
        if (isNewHighScore && panelScale > 0.9f) {
            val glowPulse = UITheme.Anim.pulse(3f, 0.2f, 0.5f)
            for (i in 4 downTo 1) {
                ui.roundedRect(
                    panelX - i * 4, panelY - i * 4,
                    panelWidth + i * 8, panelHeight + i * 8,
                    UITheme.Dimensions.panelRadius + i * 2,
                    UITheme.withAlpha(UITheme.accent, glowPulse * 0.15f * i)
                )
            }
        }

        // Main panel
        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface,
            borderColor = if (isNewHighScore) UITheme.accent else null)

        // Button layout
        val buttonWidth = 190f * uiScale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonSpacing = 20f * uiScale
        val totalWidth = buttonWidth * 2 + buttonSpacing
        val buttonsY = panelY + 28f * uiScale

        retryButton.set(centerX - totalWidth / 2, buttonsY, buttonWidth, buttonHeight)
        menuButton.set(centerX + buttonSpacing / 2, buttonsY, buttonWidth, buttonHeight)

        // Buttons
        ui.button(retryButton, UITheme.primary, glowIntensity = retryHover * 0.6f)
        ui.button(menuButton, UITheme.surfaceLight, glowIntensity = menuHover * 0.3f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        if (panelScale > 0.5f) {
            // Title
            val titleY = panelY + panelHeight - 50 * uiScale
            val titleColor = if (isNewHighScore) {
                UITheme.lerp(UITheme.danger, UITheme.accent, UITheme.Anim.pulse(2f, 0f, 1f))
            } else {
                UITheme.danger
            }
            ui.textCentered("GAME OVER", centerX, titleY, UIFonts.title, titleColor)

            // New high score badge
            var statsStartY = titleY - 70f * uiScale
            if (isNewHighScore) {
                val badgePulse = UITheme.Anim.pulse(4f, 0.8f, 1f)
                val badgeColor = UITheme.lerp(UITheme.accent, UITheme.accentBright, badgePulse)
                ui.textCentered("** NEW HIGH SCORE! **", centerX, statsStartY, UIFonts.body, badgeColor)
                statsStartY -= 55f * uiScale
            }

            // Stats with reveal animation
            val statsAlpha = (statsReveal * 1.5f - 0.5f).coerceIn(0f, 1f)
            if (statsAlpha > 0) {
                val lineHeight = 60f * uiScale

                // Score - centered
                UIFonts.caption.color = UITheme.withAlpha(UITheme.textSecondary, statsAlpha)
                ui.textCentered("SCORE", centerX, statsStartY, UIFonts.caption, UIFonts.caption.color)
                UIFonts.heading.color = UITheme.withAlpha(UITheme.accent, statsAlpha)
                ui.textCentered(session.score.toString(), centerX, statsStartY - 35f * uiScale, UIFonts.heading, UIFonts.heading.color)

                // Distance - centered
                UIFonts.caption.color = UITheme.withAlpha(UITheme.textSecondary, statsAlpha)
                ui.textCentered("DISTANCE", centerX, statsStartY - lineHeight * 1.3f, UIFonts.caption, UIFonts.caption.color)
                UIFonts.heading.color = UITheme.withAlpha(UITheme.textPrimary, statsAlpha)
                ui.textCentered("${session.distanceTraveled.toInt()} m", centerX, statsStartY - lineHeight * 1.3f - 35f * uiScale, UIFonts.heading, UIFonts.heading.color)

                // Top Speed - centered
                UIFonts.caption.color = UITheme.withAlpha(UITheme.textSecondary, statsAlpha)
                ui.textCentered("TOP SPEED", centerX, statsStartY - lineHeight * 2.6f, UIFonts.caption, UIFonts.caption.color)
                UIFonts.heading.color = UITheme.withAlpha(UITheme.textPrimary, statsAlpha)
                ui.textCentered("${(session.maxSpeed * 3.6f).toInt()} km/h", centerX, statsStartY - lineHeight * 2.6f - 35f * uiScale, UIFonts.heading, UIFonts.heading.color)
            }

            // Button labels
            ui.textCentered("RETRY", retryButton.x + retryButton.width / 2, retryButton.y + retryButton.height / 2, UIFonts.button, UITheme.textPrimary)
            ui.textCentered("MENU", menuButton.x + menuButton.width / 2, menuButton.y + menuButton.height / 2, UIFonts.button, UITheme.textPrimary)
        }

        ui.endBatch()

        // === Input ===
        if (Gdx.input.justTouched()) {
            if (retryButton.contains(touchX, touchY)) return ButtonClicked.RETRY
            if (menuButton.contains(touchX, touchY)) return ButtonClicked.MENU
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            return ButtonClicked.RETRY
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            return ButtonClicked.MENU
        }

        return ButtonClicked.NONE
    }

    fun reset() {
        overlayAlpha = 0f
        panelScale = 0f
        statsReveal = 0f
        newHighScoreAnim = 0f
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
    }

    override fun dispose() {
        ui.dispose()
    }
}
