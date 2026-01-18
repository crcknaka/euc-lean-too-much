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
    private var safetyTipAnim = 0f  // Animation timer for safety tip

    // Current safety tip (randomized on reset)
    private var currentSafetyTip = ""

    companion object {
        private val safetyTips = listOf(
            "Wear your gear. Always.",
            "Helmet on, worries off.",
            "Slow down, stay safe.",
            "Know your limits.",
            "Protect your head.",
            "Ride within your skill.",
            "Gear up before you roll.",
            "Safety first, tricks later.",
            "One fall can change everything.",
            "Your brain is irreplaceable.",
            "Pads today, ride tomorrow.",
            "Respect the wheel.",
            "Stay alert, stay alive.",
            "Don't push too hard.",
            "Learn to fall safely.",
            "Check your gear regularly.",
            "Watch for obstacles.",
            "Balance is everything.",
            "Practice makes perfect.",
            "Ride smart, ride long."
        )
    }

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

        // Panel dimensions with scale animation - wider panel
        val panelWidth = 640f * scale * panelScale
        val panelHeight = 580f * scale * panelScale  // Compact height with side-by-side stats
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
            borderColor = if (isNewHighScore) UITheme.accent else UITheme.surfaceBorder)

        // Button layout - side by side at bottom with more padding
        val buttonWidth = 240f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonSpacing = 32f * scale
        val totalWidth = buttonWidth * 2 + buttonSpacing
        val buttonsY = panelY + 70f * scale  // Lower position for buttons

        retryButton.set(centerX - totalWidth / 2, buttonsY, buttonWidth, buttonHeight)
        menuButton.set(centerX + buttonSpacing / 2, buttonsY, buttonWidth, buttonHeight)

        // Buttons with modern style
        ui.button(retryButton, UITheme.accent, glowIntensity = retryHover * 0.7f)
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

            // Safety tip under title - large with pulsing attention animation
            var statsStartY = titleY - 75f * scale  // More space between GAME OVER and tip
            if (currentSafetyTip.isNotEmpty()) {
                safetyTipAnim += Gdx.graphics.deltaTime

                // Pulsing glow effect - stronger at start, then settles
                val glowIntensity = if (safetyTipAnim < 2f) {
                    // First 2 seconds: strong pulsing to grab attention
                    UITheme.Anim.pulse(3f, 0.6f, 1f)
                } else {
                    // After: gentle pulse
                    UITheme.Anim.pulse(2f, 0.85f, 1f)
                }

                // Color shifts between warning yellow and white for attention
                val tipColor = if (safetyTipAnim < 2f) {
                    UITheme.lerp(UITheme.warning, UITheme.textPrimary, UITheme.Anim.pulse(4f, 0f, 1f))
                } else {
                    UITheme.lerp(UITheme.warning, UITheme.textSecondary, 0.3f)
                }

                val finalColor = UITheme.withAlpha(tipColor, glowIntensity)
                ui.textCentered(currentSafetyTip, centerX, statsStartY, UIFonts.heading, finalColor)
                statsStartY -= 60f * scale
            }

            // New high score badge
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
                val columnOffset = 130f * scale  // Half-width between SCORE and DISTANCE columns

                // Score and Distance in one row
                UIFonts.caption.color = UITheme.withAlpha(UITheme.textSecondary, statsAlpha)
                ui.textCentered("SCORE", centerX - columnOffset, statsStartY, UIFonts.caption, UIFonts.caption.color)
                UIFonts.heading.color = UITheme.withAlpha(UITheme.accent, statsAlpha)
                ui.textCentered(session.score.toString(), centerX - columnOffset, statsStartY - 42f * scale, UIFonts.heading, UIFonts.heading.color)

                UIFonts.caption.color = UITheme.withAlpha(UITheme.textSecondary, statsAlpha)
                ui.textCentered("DISTANCE", centerX + columnOffset, statsStartY, UIFonts.caption, UIFonts.caption.color)
                UIFonts.heading.color = UITheme.withAlpha(UITheme.textPrimary, statsAlpha)
                ui.textCentered("${session.distanceTraveled.toInt()} m", centerX + columnOffset, statsStartY - 42f * scale, UIFonts.heading, UIFonts.heading.color)

                // Top Speed - centered below
                UIFonts.caption.color = UITheme.withAlpha(UITheme.textSecondary, statsAlpha)
                ui.textCentered("TOP SPEED", centerX, statsStartY - lineHeight * 1.25f, UIFonts.caption, UIFonts.caption.color)
                UIFonts.heading.color = UITheme.withAlpha(UITheme.textPrimary, statsAlpha)
                ui.textCentered("${(session.maxSpeed * 3.6f).toInt()} km/h", centerX, statsStartY - lineHeight * 1.25f - 42f * scale, UIFonts.heading, UIFonts.heading.color)
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
        safetyTipAnim = 0f
        // Pick a random safety tip
        currentSafetyTip = safetyTips.random()
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
