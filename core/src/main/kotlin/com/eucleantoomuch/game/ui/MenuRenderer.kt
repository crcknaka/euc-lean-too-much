package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable

/**
 * Modern main menu with EUC-themed design.
 * Features large touch-friendly buttons and clean typography.
 */
class MenuRenderer : Disposable {
    private val ui = UIRenderer()

    private val playButton = Rectangle()
    private val calibrateButton = Rectangle()
    private val settingsButton = Rectangle()
    private val exitButton = Rectangle()

    // Animation states
    private var playButtonHover = 0f
    private var calibrateButtonHover = 0f
    private var settingsButtonHover = 0f
    private var exitButtonHover = 0f
    private var titlePulse = 0f
    private var enterAnimProgress = 0f

    // Particle system for background
    private val particles = Array(40) { BackgroundParticle() }

    private class BackgroundParticle {
        var x = MathUtils.random(0f, 1f)
        var y = MathUtils.random(0f, 1f)
        var size = MathUtils.random(3f, 8f)
        var speed = MathUtils.random(0.015f, 0.04f)
        var alpha = MathUtils.random(0.15f, 0.4f)

        fun update() {
            y += speed * Gdx.graphics.deltaTime
            if (y > 1.1f) {
                y = -0.1f
                x = MathUtils.random(0f, 1f)
                size = MathUtils.random(3f, 8f)
            }
        }
    }

    enum class ButtonClicked {
        NONE, PLAY, CALIBRATE, SETTINGS, EXIT
    }

    fun render(highScore: Int, maxDistance: Float): ButtonClicked {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val centerY = sh / 2

        // Update animations
        enterAnimProgress = UITheme.Anim.ease(enterAnimProgress, 1f, 3f)
        titlePulse = UITheme.Anim.pulse(1.5f, 0.97f, 1f)

        // Update particles
        particles.forEach { it.update() }

        // Check hover state
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        val playHovered = playButton.contains(touchX, touchY)
        val calibrateHovered = calibrateButton.contains(touchX, touchY)
        val settingsHovered = settingsButton.contains(touchX, touchY)
        val exitHovered = exitButton.contains(touchX, touchY)

        playButtonHover = UITheme.Anim.ease(playButtonHover, if (playHovered) 1f else 0f, 10f)
        calibrateButtonHover = UITheme.Anim.ease(calibrateButtonHover, if (calibrateHovered) 1f else 0f, 10f)
        settingsButtonHover = UITheme.Anim.ease(settingsButtonHover, if (settingsHovered) 1f else 0f, 10f)
        exitButtonHover = UITheme.Anim.ease(exitButtonHover, if (exitHovered) 1f else 0f, 10f)

        val scale = UITheme.Dimensions.scale()

        // === Draw Background ===
        ui.beginShapes()

        // Gradient background with subtle animation
        val bgTop = UITheme.backgroundLight
        val bgBottom = UITheme.background
        for (i in 0 until 20) {
            val t = i / 20f
            val stripY = sh * t
            val stripHeight = sh / 20f + 1
            ui.shapes.color = UITheme.lerp(bgBottom, bgTop, t)
            ui.shapes.rect(0f, stripY, sw, stripHeight)
        }

        // Animated particles
        particles.forEach { p ->
            ui.shapes.color = UITheme.withAlpha(UITheme.accent, p.alpha * enterAnimProgress)
            ui.shapes.circle(p.x * sw, p.y * sh, p.size * scale)
        }

        // Decorative EUC wheel silhouette (left side)
        val wheelX = centerX - 280 * scale * enterAnimProgress
        val wheelY = centerY + 20 * scale
        val wheelRadius = 140f * scale * enterAnimProgress

        // Outer wheel rim
        ui.shapes.color = UITheme.withAlpha(UITheme.surfaceLight, 0.25f)
        ui.shapes.circle(wheelX, wheelY, wheelRadius)
        // Inner wheel
        ui.shapes.color = UITheme.background
        ui.shapes.circle(wheelX, wheelY, wheelRadius * 0.75f)
        // Hub
        ui.shapes.color = UITheme.withAlpha(UITheme.surfaceLight, 0.3f)
        ui.shapes.circle(wheelX, wheelY, wheelRadius * 0.2f)

        // Animated spokes
        ui.shapes.color = UITheme.withAlpha(UITheme.surfaceLight, 0.2f)
        val spokeTime = UITheme.Anim.time() * 0.4f
        for (i in 0 until 8) {
            val angle = (i * 45f + spokeTime * 25f) * MathUtils.degreesToRadians
            val innerR = wheelRadius * 0.22f
            val outerR = wheelRadius * 0.72f
            ui.shapes.rectLine(
                wheelX + innerR * MathUtils.cos(angle),
                wheelY + innerR * MathUtils.sin(angle),
                wheelX + outerR * MathUtils.cos(angle),
                wheelY + outerR * MathUtils.sin(angle),
                4f * scale
            )
        }

        // === Buttons ===
        val buttonWidth = 480f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeight
        val smallButtonWidth = 230f * scale
        val smallButtonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonSpacing = 24f * scale

        val buttonsStartY = centerY + 80f * scale
        playButton.set(centerX - buttonWidth / 2, buttonsStartY, buttonWidth, buttonHeight)
        calibrateButton.set(centerX - buttonWidth / 2, buttonsStartY - buttonHeight - buttonSpacing, buttonWidth, buttonHeight)

        // Settings and Exit buttons side by side
        val smallButtonsY = buttonsStartY - buttonHeight * 2 - buttonSpacing * 3
        val smallButtonSpacing = 24f * scale
        settingsButton.set(centerX - smallButtonWidth - smallButtonSpacing / 2, smallButtonsY, smallButtonWidth, smallButtonHeight)
        exitButton.set(centerX + smallButtonSpacing / 2, smallButtonsY, smallButtonWidth, smallButtonHeight)

        // Apply enter animation
        val playY = playButton.y - (1 - enterAnimProgress) * 120
        val calibrateY = calibrateButton.y - (1 - enterAnimProgress) * 180
        val settingsY = settingsButton.y - (1 - enterAnimProgress) * 240
        val exitY = exitButton.y - (1 - enterAnimProgress) * 240

        // Draw buttons with modern style
        val playRect = Rectangle(playButton.x, playY, playButton.width, playButton.height)
        ui.button(playRect, UITheme.accent, pressedOffset = 0f, glowIntensity = playButtonHover * 0.9f)

        val calibrateRect = Rectangle(calibrateButton.x, calibrateY, calibrateButton.width, calibrateButton.height)
        ui.button(calibrateRect, UITheme.secondary, pressedOffset = 0f, glowIntensity = calibrateButtonHover * 0.6f)

        val settingsRect = Rectangle(settingsButton.x, settingsY, settingsButton.width, settingsButton.height)
        ui.button(settingsRect, UITheme.surfaceLight, pressedOffset = 0f, glowIntensity = settingsButtonHover * 0.4f)

        val exitRect = Rectangle(exitButton.x, exitY, exitButton.width, exitButton.height)
        ui.button(exitRect, UITheme.danger, pressedOffset = 0f, glowIntensity = exitButtonHover * 0.6f)

        // Stats panel at bottom
        val statsHeight = 110f * scale
        ui.panel(0f, 0f, sw, statsHeight, radius = 0f, shadowOffset = 0f,
            backgroundColor = UITheme.withAlpha(UITheme.surface, 0.95f))

        // Accent line at top of stats panel
        ui.shapes.color = UITheme.accent
        ui.shapes.rect(0f, statsHeight - 4f, sw, 4f)

        ui.endShapes()

        // === Draw Text ===
        ui.beginBatch()

        // Title "EUC" with subtle animation
        val titleY = sh - 80f * scale + (1 - enterAnimProgress) * 60
        val titleScale = titlePulse
        UIFonts.display.data.setScale(UIFonts.display.data.scaleX * titleScale)
        ui.textCentered("EUC", centerX, titleY, UIFonts.display, UITheme.textPrimary)
        UIFonts.display.data.setScale(UIFonts.display.data.scaleX / titleScale)

        // Subtitle with glow effect
        val subtitleY = titleY - 90f * scale
        ui.textCentered("LEAN TOO MUCH", centerX, subtitleY, UIFonts.heading, UITheme.accent)

        // Button labels with larger text
        ui.textCentered("PLAY", playRect.x + playRect.width / 2, playRect.y + playRect.height / 2, UIFonts.button, UITheme.textPrimary)
        ui.textCentered("CALIBRATE", calibrateRect.x + calibrateRect.width / 2, calibrateRect.y + calibrateRect.height / 2, UIFonts.button, UITheme.textPrimary)
        ui.textCentered("SETTINGS", settingsRect.x + settingsRect.width / 2, settingsRect.y + settingsRect.height / 2, UIFonts.body, UITheme.textPrimary)
        ui.textCentered("EXIT", exitRect.x + exitRect.width / 2, exitRect.y + exitRect.height / 2, UIFonts.body, UITheme.textPrimary)

        // Stats with improved layout
        val statsLabelY = 90f * scale
        val statsValueY = statsLabelY - 35f * scale
        val sideMargin = 80f * scale

        // High score (left)
        UIFonts.caption.color = UITheme.textSecondary
        ui.layout.setText(UIFonts.caption, "HIGH SCORE")
        UIFonts.caption.draw(ui.batch, "HIGH SCORE", sideMargin, statsLabelY)

        UIFonts.heading.color = UITheme.accent
        UIFonts.heading.draw(ui.batch, highScore.toString(), sideMargin, statsValueY)

        // Best distance (right)
        UIFonts.caption.color = UITheme.textSecondary
        val distLabel = "BEST DISTANCE"
        ui.layout.setText(UIFonts.caption, distLabel)
        UIFonts.caption.draw(ui.batch, distLabel, sw - ui.layout.width - sideMargin, statsLabelY)

        val distValue = "${maxDistance.toInt()}m"
        ui.layout.setText(UIFonts.heading, distValue)
        UIFonts.heading.color = UITheme.textPrimary
        UIFonts.heading.draw(ui.batch, distValue, sw - ui.layout.width - sideMargin, statsValueY)

        // Center hint
        ui.textCentered("Tilt to control - Lean to accelerate", centerX, 55f * scale, UIFonts.caption, UITheme.textMuted)

        ui.endBatch()

        // === Handle Input ===
        if (Gdx.input.justTouched()) {
            if (playButton.contains(touchX, touchY)) {
                return ButtonClicked.PLAY
            }
            if (calibrateButton.contains(touchX, touchY)) {
                return ButtonClicked.CALIBRATE
            }
            if (settingsButton.contains(touchX, touchY)) {
                return ButtonClicked.SETTINGS
            }
            if (exitButton.contains(touchX, touchY)) {
                return ButtonClicked.EXIT
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            return ButtonClicked.PLAY
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            return ButtonClicked.CALIBRATE
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            return ButtonClicked.SETTINGS
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            return ButtonClicked.EXIT
        }

        return ButtonClicked.NONE
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
        enterAnimProgress = 0f
    }

    override fun dispose() {
        ui.dispose()
    }
}
