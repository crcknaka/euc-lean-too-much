package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable

/**
 * Help screen showing game instructions and controls.
 */
class HelpRenderer : Disposable {
    private val ui = UIRenderer()
    private val backButton = Rectangle()

    // Animation states
    private var overlayAlpha = 0f
    private var panelScale = 0f
    private var backHover = 0f

    enum class ButtonClicked {
        NONE, BACK
    }

    fun render(): ButtonClicked {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val scale = UITheme.Dimensions.scale()

        // Update animations
        overlayAlpha = UITheme.Anim.ease(overlayAlpha, 0.85f, 6f)
        panelScale = UITheme.Anim.ease(panelScale, 1f, 6f)

        // Check hover
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        backHover = UITheme.Anim.ease(backHover, if (backButton.contains(touchX, touchY)) 1f else 0f, 10f)

        ui.beginShapes()

        // Dark overlay
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, overlayAlpha)
        ui.shapes.rect(0f, 0f, sw, sh)

        // Panel dimensions - wider for help content
        val panelWidth = 950f * scale * panelScale
        val panelHeight = 850f * scale * panelScale
        val panelX = centerX - panelWidth / 2
        val panelY = (sh - panelHeight) / 2

        // Main panel
        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface,
            borderColor = UITheme.cyan)

        // Back button at bottom - lower position
        val buttonWidth = 200f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonX = centerX - buttonWidth / 2
        val buttonY = panelY + 20f * scale
        backButton.set(buttonX, buttonY, buttonWidth, buttonHeight)

        ui.button(backButton, UITheme.surfaceLight, glowIntensity = backHover * 0.4f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        if (panelScale > 0.5f) {
            var textY = panelY + panelHeight - 50f * scale

            // Title
            ui.textCentered("HOW TO PLAY", centerX, textY, UIFonts.title, UITheme.cyan)
            textY -= 90f * scale

            // Two-column layout
            val leftCol = centerX - 220f * scale
            val rightCol = centerX + 220f * scale

            // Left column - Controls
            var leftY = textY
            ui.textCentered("CONTROLS", leftCol, leftY, UIFonts.heading, UITheme.accent)
            leftY -= 50f * scale

            ui.textCentered("TILT LEFT/RIGHT - Steer", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)
            leftY -= 28f * scale
            ui.textCentered("LEAN FORWARD - Accelerate", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)
            leftY -= 28f * scale
            ui.textCentered("LEAN BACK - Brake", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)
            leftY -= 50f * scale

            // Gameplay
            ui.textCentered("GAMEPLAY", leftCol, leftY, UIFonts.heading, UITheme.accent)
            leftY -= 50f * scale

            ui.textCentered("Dodge pedestrians", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)
            leftY -= 28f * scale
            ui.textCentered("Pigeons fly away - safe!", leftCol, leftY, UIFonts.caption, UITheme.textSecondary)
            leftY -= 28f * scale
            ui.textCentered("Near misses = bonus points!", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)
            leftY -= 28f * scale
            ui.textCentered("Collect VOLTS for new wheels", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)
            leftY -= 45f * scale

            // Game Modes
            ui.textCentered("GAME MODES", leftCol, leftY, UIFonts.heading, UITheme.accent)
            leftY -= 50f * scale

            ui.textCentered("ENDLESS - Ride until crash", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)
            leftY -= 26f * scale
            ui.textCentered("TIME TRIAL - Beat the clock", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)
            leftY -= 26f * scale
            ui.textCentered("HARDCORE - 2x volts, chaos", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)
            leftY -= 26f * scale
            ui.textCentered("NIGHT - 2.5x volts, darkness", leftCol, leftY, UIFonts.caption, UITheme.textPrimary)

            // Right column - Wobble (important!)
            var rightY = textY
            ui.textCentered("WOBBLE", rightCol, rightY, UIFonts.heading, UITheme.danger)
            rightY -= 50f * scale

            ui.textCentered("At high speeds, your wheel", rightCol, rightY, UIFonts.caption, UITheme.textPrimary)
            rightY -= 26f * scale
            ui.textCentered("starts to WOBBLE!", rightCol, rightY, UIFonts.caption, UITheme.warning)
            rightY -= 32f * scale
            ui.textCentered("The faster you go,", rightCol, rightY, UIFonts.caption, UITheme.textPrimary)
            rightY -= 26f * scale
            ui.textCentered("the stronger the wobble.", rightCol, rightY, UIFonts.caption, UITheme.textPrimary)
            rightY -= 32f * scale
            ui.textCentered("SLOW DOWN to stabilize!", rightCol, rightY, UIFonts.caption, UITheme.cyan)
            rightY -= 26f * scale
            ui.textCentered("Or you will crash...", rightCol, rightY, UIFonts.caption, UITheme.textMuted)
            rightY -= 55f * scale

            // In-Game controls on right side
            ui.textCentered("IN-GAME", rightCol, rightY, UIFonts.heading, UITheme.accent)
            rightY -= 50f * scale

            ui.textCentered("TAP - Change camera view", rightCol, rightY, UIFonts.caption, UITheme.textPrimary)
            rightY -= 26f * scale
            ui.textCentered("2-FINGER TAP - Pause", rightCol, rightY, UIFonts.caption, UITheme.textPrimary)
            rightY -= 26f * scale
            ui.textCentered("Settings in pause menu", rightCol, rightY, UIFonts.caption, UITheme.textSecondary)
            rightY -= 50f * scale

            // Tips
            ui.textCentered("TIPS", rightCol, rightY, UIFonts.heading, UITheme.accent)
            rightY -= 45f * scale

            ui.textCentered("Calibrate phone first!", rightCol, rightY, UIFonts.caption, UITheme.textSecondary)
            rightY -= 24f * scale
            ui.textCentered("Better wheels = less wobble", rightCol, rightY, UIFonts.caption, UITheme.textSecondary)

            // Back button label
            ui.textCentered("BACK", backButton.x + backButton.width / 2, backButton.y + backButton.height / 2,
                UIFonts.body, UITheme.textPrimary)
        }

        ui.endBatch()

        // === Input ===
        if (Gdx.input.justTouched()) {
            if (backButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.BACK
            }
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) ||
            Gdx.input.isKeyJustPressed(Input.Keys.BACK) ||
            Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            UIFeedback.click()
            return ButtonClicked.BACK
        }

        return ButtonClicked.NONE
    }

    fun reset() {
        overlayAlpha = 0f
        panelScale = 0f
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
