package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable

/**
 * Modern pause menu with large, easy-to-tap buttons.
 * Clean vertical layout for quick navigation.
 */
class PauseRenderer : Disposable {
    private val ui = UIRenderer()

    private val resumeButton = Rectangle()
    private val restartButton = Rectangle()
    private val menuButton = Rectangle()

    // Animation states
    private var overlayAlpha = 0f
    private var panelScale = 0f
    private var resumeHover = 0f
    private var restartHover = 0f
    private var menuHover = 0f

    enum class ButtonClicked {
        NONE, RESUME, RESTART, MENU
    }

    fun render(): ButtonClicked {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val centerY = sh / 2
        val scale = UITheme.Dimensions.scale()

        // Update animations
        overlayAlpha = UITheme.Anim.ease(overlayAlpha, 0.78f, 6f)
        panelScale = UITheme.Anim.ease(panelScale, 1f, 6f)

        // Check hover
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        resumeHover = UITheme.Anim.ease(resumeHover, if (resumeButton.contains(touchX, touchY)) 1f else 0f, 10f)
        restartHover = UITheme.Anim.ease(restartHover, if (restartButton.contains(touchX, touchY)) 1f else 0f, 10f)
        menuHover = UITheme.Anim.ease(menuHover, if (menuButton.contains(touchX, touchY)) 1f else 0f, 10f)

        ui.beginShapes()

        // Dark overlay
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, overlayAlpha)
        ui.shapes.rect(0f, 0f, sw, sh)

        // Panel dimensions with scale animation (taller for more padding around title)
        val panelWidth = 480f * scale * panelScale
        val panelHeight = 580f * scale * panelScale  // Increased height for more spacing
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        // Main panel with primary color accent border
        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface,
            borderColor = UITheme.primary)

        // Button layout - vertical stack with generous spacing
        val buttonWidth = 340f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonSpacing = 22f * scale
        val buttonX = centerX - buttonWidth / 2

        // Calculate buttons from top to bottom (more space from title)
        val firstButtonY = panelY + panelHeight - 220f * scale  // More space below title

        resumeButton.set(buttonX, firstButtonY, buttonWidth, buttonHeight)
        restartButton.set(buttonX, firstButtonY - buttonHeight - buttonSpacing, buttonWidth, buttonHeight)
        menuButton.set(buttonX, firstButtonY - (buttonHeight + buttonSpacing) * 2, buttonWidth, buttonHeight)

        // Buttons with modern styling
        ui.button(resumeButton, UITheme.primary, glowIntensity = resumeHover * 0.7f)
        ui.button(restartButton, UITheme.secondary, glowIntensity = restartHover * 0.6f)
        ui.button(menuButton, UITheme.surfaceLight, glowIntensity = menuHover * 0.4f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        if (panelScale > 0.5f) {
            // Title with more top padding
            val titleY = panelY + panelHeight - 70f * scale  // More space from top edge
            ui.textCentered("PAUSED", centerX, titleY, UIFonts.title, UITheme.textPrimary)

            // Button labels with larger font
            ui.textCentered("RESUME", resumeButton.x + resumeButton.width / 2, resumeButton.y + resumeButton.height / 2,
                UIFonts.button, UITheme.textPrimary)
            ui.textCentered("RESTART", restartButton.x + restartButton.width / 2, restartButton.y + restartButton.height / 2,
                UIFonts.button, UITheme.textPrimary)
            ui.textCentered("MENU", menuButton.x + menuButton.width / 2, menuButton.y + menuButton.height / 2,
                UIFonts.button, UITheme.textPrimary)
        }

        ui.endBatch()

        // === Input ===
        if (Gdx.input.justTouched()) {
            if (resumeButton.contains(touchX, touchY)) return ButtonClicked.RESUME
            if (restartButton.contains(touchX, touchY)) return ButtonClicked.RESTART
            if (menuButton.contains(touchX, touchY)) return ButtonClicked.MENU
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.P) ||
            Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            return ButtonClicked.RESUME
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            return ButtonClicked.RESTART
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            return ButtonClicked.MENU
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

    override fun dispose() {
        ui.dispose()
    }
}
