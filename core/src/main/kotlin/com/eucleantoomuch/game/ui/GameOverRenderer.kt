package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.state.GameSession

/**
 * Modern game over screen with statistics and large action buttons.
 * Features celebratory effects for new high scores.
 */
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
        val scale = UITheme.Dimensions.scale()

        // Update animations
        overlayAlpha = UITheme.Anim.ease(overlayAlpha, 0.88f, 4f)
        panelScale = UITheme.Anim.ease(panelScale, 1f, 5f)
        statsReveal = UITheme.Anim.ease(statsReveal, 1f, 3f)

        if (isNewHighScore) {
            newHighScoreAnim += Gdx.graphics.deltaTime
        }

        // Check hover
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        retryHover = UITheme.Anim.ease(retryHover, if (retryButton.contains(touchX, touchY)) 1f else 0f, 10f)
        menuHover = UITheme.Anim.ease(menuHover, if (menuButton.contains(touchX, touchY)) 1f else 0f, 10f)

        ui.beginShapes()

        // Dark overlay with vignette effect
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, overlayAlpha)
        ui.shapes.rect(0f, 0f, sw, sh)

        // Panel dimensions with scale animation
        val panelWidth = 560f * scale * panelScale
        val panelHeight = 520f * scale * panelScale
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        // Panel glow for new high score
        if (isNewHighScore && panelScale > 0.9f) {
            val glowPulse = UITheme.Anim.pulse(3f, 0.25f, 0.55f)
            for (i in 5 downTo 1) {
                ui.roundedRect(
                    panelX - i * 5, panelY - i * 5,
                    panelWidth + i * 10, panelHeight + i * 10,
                    UITheme.Dimensions.panelRadius + i * 3,
                    UITheme.withAlpha(UITheme.accent, glowPulse * 0.12f * i)
                )
            }
        }

        // Main panel
        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface,
            borderColor = if (isNewHighScore) UITheme.accent else UITheme.primary)

        // Button layout - side by side at bottom (lower position)
        val buttonWidth = 220f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonSpacing = 28f * scale
        val totalWidth = buttonWidth * 2 + buttonSpacing
        val buttonsY = panelY - 5f * scale  // At very bottom of panel

        retryButton.set(centerX - totalWidth / 2, buttonsY, buttonWidth, buttonHeight)
        menuButton.set(centerX + buttonSpacing / 2, buttonsY, buttonWidth, buttonHeight)

        // Buttons with modern style
        ui.button(retryButton, UITheme.primary, glowIntensity = retryHover * 0.7f)
        ui.button(menuButton, UITheme.surfaceLight, glowIntensity = menuHover * 0.4f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        if (panelScale > 0.5f) {
            // Title
            val titleY = panelY + panelHeight - 65f * scale
            val titleColor = if (isNewHighScore) {
                UITheme.lerp(UITheme.danger, UITheme.accent, UITheme.Anim.pulse(2f, 0f, 1f))
            } else {
                UITheme.danger
            }
            ui.textCentered("GAME OVER", centerX, titleY, UIFonts.title, titleColor)

            // New high score badge
            var statsStartY = titleY - 85f * scale
            if (isNewHighScore) {
                val badgePulse = UITheme.Anim.pulse(4f, 0.8f, 1f)
                val badgeColor = UITheme.lerp(UITheme.accent, UITheme.accentBright, badgePulse)
                ui.textCentered("NEW HIGH SCORE!", centerX, statsStartY, UIFonts.heading, badgeColor)
                statsStartY -= 70f * scale
            }

            // Stats with reveal animation
            val statsAlpha = (statsReveal * 1.5f - 0.5f).coerceIn(0f, 1f)
            if (statsAlpha > 0) {
                val lineHeight = 75f * scale

                // Score - centered with larger text
                UIFonts.caption.color = UITheme.withAlpha(UITheme.textSecondary, statsAlpha)
                ui.textCentered("SCORE", centerX, statsStartY, UIFonts.caption, UIFonts.caption.color)
                UIFonts.heading.color = UITheme.withAlpha(UITheme.accent, statsAlpha)
                ui.textCentered(session.score.toString(), centerX, statsStartY - 42f * scale, UIFonts.heading, UIFonts.heading.color)

                // Distance - centered
                UIFonts.caption.color = UITheme.withAlpha(UITheme.textSecondary, statsAlpha)
                ui.textCentered("DISTANCE", centerX, statsStartY - lineHeight * 1.25f, UIFonts.caption, UIFonts.caption.color)
                UIFonts.heading.color = UITheme.withAlpha(UITheme.textPrimary, statsAlpha)
                ui.textCentered("${session.distanceTraveled.toInt()} m", centerX, statsStartY - lineHeight * 1.25f - 42f * scale, UIFonts.heading, UIFonts.heading.color)

                // Top Speed - centered
                UIFonts.caption.color = UITheme.withAlpha(UITheme.textSecondary, statsAlpha)
                ui.textCentered("TOP SPEED", centerX, statsStartY - lineHeight * 2.5f, UIFonts.caption, UIFonts.caption.color)
                UIFonts.heading.color = UITheme.withAlpha(UITheme.textPrimary, statsAlpha)
                ui.textCentered("${(session.maxSpeed * 3.6f).toInt()} km/h", centerX, statsStartY - lineHeight * 2.5f - 42f * scale, UIFonts.heading, UIFonts.heading.color)
            }

            // Button labels with larger text
            ui.textCentered("RETRY", retryButton.x + retryButton.width / 2, retryButton.y + retryButton.height / 2,
                UIFonts.button, UITheme.textPrimary)
            ui.textCentered("MENU", menuButton.x + menuButton.width / 2, menuButton.y + menuButton.height / 2,
                UIFonts.button, UITheme.textPrimary)
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
