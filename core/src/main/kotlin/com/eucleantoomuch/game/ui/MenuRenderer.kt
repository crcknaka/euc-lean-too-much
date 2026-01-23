package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable

/**
 * Modern main menu with "Neon Street" design.
 * Features card-style buttons, neon glow effects, and horizontal landscape layout.
 */
class MenuRenderer : Disposable {
    private val ui = UIRenderer()
    private val backgroundTexture: Texture = Texture(Gdx.files.internal("background.jpg"))

    private val playButton = Rectangle()
    private val calibrateButton = Rectangle()
    private val settingsButton = Rectangle()
    private val creditsButton = Rectangle()
    private val exitButton = Rectangle()

    // Animation states
    private var playButtonHover = 0f
    private var calibrateButtonHover = 0f
    private var settingsButtonHover = 0f
    private var creditsButtonHover = 0f
    private var exitButtonHover = 0f
    private var enterAnimProgress = 0f

    // Trail particles (like EUC tire marks)
    private val trailParticles = Array(25) { TrailParticle() }

    private class TrailParticle {
        var x = MathUtils.random(0f, 1f)
        var y = MathUtils.random(0f, 1f)
        var length = MathUtils.random(30f, 80f)
        var speed = MathUtils.random(0.08f, 0.18f)
        var alpha = MathUtils.random(0.08f, 0.2f)
        var angle = MathUtils.random(-15f, 15f)

        fun update() {
            y -= speed * Gdx.graphics.deltaTime
            if (y < -0.15f) {
                y = 1.1f
                x = MathUtils.random(0f, 1f)
                length = MathUtils.random(30f, 80f)
                alpha = MathUtils.random(0.08f, 0.2f)
            }
        }
    }

    enum class ButtonClicked {
        NONE, PLAY, CALIBRATE, SETTINGS, CREDITS, EXIT
    }

    fun render(highScore: Int, maxDistance: Float, maxNearMisses: Int, totalVolts: Int = 0): ButtonClicked {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val scale = UITheme.Dimensions.scale()

        // Update animations
        enterAnimProgress = UITheme.Anim.ease(enterAnimProgress, 1f, 3f)

        // Update trail particles
        trailParticles.forEach { it.update() }

        // Check hover state
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        val playHovered = playButton.contains(touchX, touchY)
        val calibrateHovered = calibrateButton.contains(touchX, touchY)
        val settingsHovered = settingsButton.contains(touchX, touchY)
        val creditsHovered = creditsButton.contains(touchX, touchY)
        val exitHovered = exitButton.contains(touchX, touchY)

        playButtonHover = UITheme.Anim.ease(playButtonHover, if (playHovered) 1f else 0f, 10f)
        calibrateButtonHover = UITheme.Anim.ease(calibrateButtonHover, if (calibrateHovered) 1f else 0f, 10f)
        settingsButtonHover = UITheme.Anim.ease(settingsButtonHover, if (settingsHovered) 1f else 0f, 10f)
        creditsButtonHover = UITheme.Anim.ease(creditsButtonHover, if (creditsHovered) 1f else 0f, 10f)
        exitButtonHover = UITheme.Anim.ease(exitButtonHover, if (exitHovered) 1f else 0f, 10f)

        // === Draw Background Image ===
        ui.beginBatch()
        ui.batch.draw(backgroundTexture, 0f, 0f, sw, sh)
        ui.endBatch()

        ui.beginShapes()

        // Animated trail particles (tire marks effect) - orange falling lines
        trailParticles.forEach { p ->
            ui.shapes.color = UITheme.withAlpha(UITheme.accent, p.alpha * enterAnimProgress)
            val px = p.x * sw
            val py = p.y * sh
            val len = p.length * scale
            // Draw as elongated shape
            ui.shapes.rect(px - 2f * scale, py, 4f * scale, len)
        }

        // === HORIZONTAL LAYOUT ===
        // Left side: Main action buttons (PLAY big, others smaller)
        // Right side: Stats cards

        val margin = 50f * scale
        val leftSectionWidth = sw * 0.55f
        val rightSectionWidth = sw * 0.45f
        val leftCenterX = margin + (leftSectionWidth - margin) / 2
        val rightCenterX = leftSectionWidth + (rightSectionWidth - margin) / 2

        // === LEFT SIDE: Buttons ===
        // PLAY button - large and prominent
        val playWidth = 460f * scale
        val playHeight = 150f * scale
        val playX = leftCenterX - playWidth / 2
        val playY = sh * 0.55f

        playButton.set(playX, playY - (1 - enterAnimProgress) * 100, playWidth, playHeight)
        ui.neonButton(playButton, UITheme.accent, UITheme.accent, 0.4f + playButtonHover * 0.6f)

        // Secondary buttons - all same size, 2x2 grid below PLAY
        val secButtonWidth = 220f * scale
        val secButtonHeight = 110f * scale
        val secButtonGap = 35f * scale
        val secButtonsY = playY - playHeight - 45f * scale

        // Row 1: Calibrate and Settings
        val calibrateX = leftCenterX - secButtonWidth - secButtonGap / 2
        calibrateButton.set(calibrateX, secButtonsY - (1 - enterAnimProgress) * 150, secButtonWidth, secButtonHeight)
        ui.neonButton(calibrateButton, UITheme.secondary, UITheme.secondary, calibrateButtonHover * 0.7f)

        val settingsX = leftCenterX + secButtonGap / 2
        settingsButton.set(settingsX, secButtonsY - (1 - enterAnimProgress) * 150, secButtonWidth, secButtonHeight)
        ui.neonButton(settingsButton, UITheme.surfaceLight, UITheme.textSecondary, settingsButtonHover * 0.5f)

        // Row 2: Credits and Exit - same size as row 1
        val row2Y = secButtonsY - secButtonHeight - secButtonGap

        creditsButton.set(calibrateX, row2Y - (1 - enterAnimProgress) * 200, secButtonWidth, secButtonHeight)
        ui.neonButton(creditsButton, UITheme.surfaceLight, UITheme.textMuted, creditsButtonHover * 0.4f)

        exitButton.set(settingsX, row2Y - (1 - enterAnimProgress) * 200, secButtonWidth, secButtonHeight)
        ui.neonButton(exitButton, UITheme.danger, UITheme.danger, exitButtonHover * 0.6f)

        // === RIGHT SIDE: Stats Cards ===
        val cardWidth = 280f * scale
        val cardHeight = 95f * scale
        val cardGap = 18f * scale
        val cardsStartY = sh * 0.58f
        val cardsOffsetX = 30f * scale  // Shift cards to the right

        // High Score card
        val scoreCardY = cardsStartY - (1 - enterAnimProgress) * 80
        ui.card(rightCenterX - cardWidth / 2 + cardsOffsetX, scoreCardY, cardWidth, cardHeight,
            glowColor = if (highScore > 0) UITheme.accent else null,
            glowIntensity = if (highScore > 0) 0.3f else 0f)

        // Near Misses card
        val nearMissCardY = scoreCardY - cardHeight - cardGap - (1 - enterAnimProgress) * 40
        ui.card(rightCenterX - cardWidth / 2 + cardsOffsetX, nearMissCardY, cardWidth, cardHeight,
            glowColor = if (maxNearMisses > 0) UITheme.cyan else null,
            glowIntensity = if (maxNearMisses > 0) 0.25f else 0f)

        // Best Distance card
        val distCardY = nearMissCardY - cardHeight - cardGap - (1 - enterAnimProgress) * 40
        ui.card(rightCenterX - cardWidth / 2 + cardsOffsetX, distCardY, cardWidth, cardHeight,
            glowColor = if (maxDistance > 100) UITheme.primary else null,
            glowIntensity = if (maxDistance > 100) 0.25f else 0f)

        // Volts card
        val voltsCardColor = com.badlogic.gdx.graphics.Color(1f, 0.85f, 0.1f, 1f)
        val voltsCardY = distCardY - cardHeight - cardGap - (1 - enterAnimProgress) * 40
        ui.card(rightCenterX - cardWidth / 2 + cardsOffsetX, voltsCardY, cardWidth, cardHeight,
            glowColor = if (totalVolts > 0) voltsCardColor else null,
            glowIntensity = if (totalVolts > 0) 0.3f else 0f)

        // Separator line between sections
        ui.separator(leftSectionWidth - 20f * scale, margin, sh - margin * 2, UITheme.surfaceBorder)

        // Bottom hint bar
        val hintHeight = 50f * scale
        ui.glassPanel(0f, 0f, sw, hintHeight, radius = 0f, borderGlow = UITheme.accent)

        ui.endShapes()

        // === Draw Text ===
        ui.beginBatch()

        // Title removed - it's on the background image

        // Button labels
        ui.textCentered("PLAY", playButton.x + playButton.width / 2, playButton.y + playButton.height / 2,
            UIFonts.title, UITheme.textPrimary)
        ui.textCentered("CALIBRATE", calibrateButton.x + calibrateButton.width / 2, calibrateButton.y + calibrateButton.height / 2,
            UIFonts.body, UITheme.textPrimary)
        ui.textCentered("SETTINGS", settingsButton.x + settingsButton.width / 2, settingsButton.y + settingsButton.height / 2,
            UIFonts.body, UITheme.textPrimary)
        ui.textCentered("CREDITS", creditsButton.x + creditsButton.width / 2, creditsButton.y + creditsButton.height / 2,
            UIFonts.body, UITheme.textSecondary)
        ui.textCentered("EXIT", exitButton.x + exitButton.width / 2, exitButton.y + exitButton.height / 2,
            UIFonts.body, UITheme.textPrimary)

        // Stats card content
        val cardLabelOffset = 30f * scale
        val cardValueOffset = -15f * scale
        val cardTextCenterX = rightCenterX + cardsOffsetX

        // High Score
        ui.textCentered("HIGH SCORE", cardTextCenterX, scoreCardY + cardHeight - cardLabelOffset,
            UIFonts.caption, UITheme.textSecondary)
        ui.textCentered(highScore.toString(), cardTextCenterX, scoreCardY + cardHeight / 2 + cardValueOffset,
            UIFonts.heading, UITheme.accent)

        // Near Misses
        ui.textCentered("NEAR MISSES", cardTextCenterX, nearMissCardY + cardHeight - cardLabelOffset,
            UIFonts.caption, UITheme.textSecondary)
        val nearMissColor = if (maxNearMisses > 0) UITheme.cyan else UITheme.textPrimary
        ui.textCentered(maxNearMisses.toString(), cardTextCenterX, nearMissCardY + cardHeight / 2 + cardValueOffset,
            UIFonts.heading, nearMissColor)

        // Best Distance
        ui.textCentered("BEST DISTANCE", cardTextCenterX, distCardY + cardHeight - cardLabelOffset,
            UIFonts.caption, UITheme.textSecondary)
        val distColor = if (maxDistance > 100) UITheme.primary else UITheme.textPrimary
        ui.textCentered("${maxDistance.toInt()}m", cardTextCenterX, distCardY + cardHeight / 2 + cardValueOffset,
            UIFonts.heading, distColor)

        // Volts
        val voltsTextColor = com.badlogic.gdx.graphics.Color(1f, 0.85f, 0.1f, 1f)
        ui.textCentered("VOLTS", cardTextCenterX, voltsCardY + cardHeight - cardLabelOffset,
            UIFonts.caption, UITheme.textSecondary)
        val voltsValueColor = if (totalVolts > 0) voltsTextColor else UITheme.textPrimary
        ui.textCentered("${totalVolts}", cardTextCenterX, voltsCardY + cardHeight / 2 + cardValueOffset,
            UIFonts.heading, voltsValueColor)

        // Bottom hint
        ui.textCentered("Tilt to steer  ~  Lean forward to accelerate", centerX, hintHeight / 2,
            UIFonts.caption, UITheme.textMuted)

        ui.endBatch()

        // === Handle Input ===
        if (Gdx.input.justTouched()) {
            if (playButton.contains(touchX, touchY)) {
                UIFeedback.clickHeavy()
                return ButtonClicked.PLAY
            }
            if (calibrateButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.CALIBRATE
            }
            if (settingsButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.SETTINGS
            }
            if (creditsButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.CREDITS
            }
            if (exitButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.EXIT
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            UIFeedback.clickHeavy()
            return ButtonClicked.PLAY
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            UIFeedback.click()
            return ButtonClicked.CALIBRATE
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            UIFeedback.click()
            return ButtonClicked.SETTINGS
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            UIFeedback.click()
            return ButtonClicked.EXIT
        }

        return ButtonClicked.NONE
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
        enterAnimProgress = 0f
    }

    fun recreate() {
        ui.recreate()
    }

    override fun dispose() {
        backgroundTexture.dispose()
        ui.dispose()
    }
}
