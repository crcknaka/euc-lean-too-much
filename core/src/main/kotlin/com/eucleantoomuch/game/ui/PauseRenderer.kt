package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable

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

        // Update animations
        overlayAlpha = UITheme.Anim.ease(overlayAlpha, 0.75f, 6f)
        panelScale = UITheme.Anim.ease(panelScale, 1f, 6f)

        // Check hover
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        resumeHover = UITheme.Anim.ease(resumeHover, if (resumeButton.contains(touchX, touchY)) 1f else 0f, 8f)
        restartHover = UITheme.Anim.ease(restartHover, if (restartButton.contains(touchX, touchY)) 1f else 0f, 8f)
        menuHover = UITheme.Anim.ease(menuHover, if (menuButton.contains(touchX, touchY)) 1f else 0f, 8f)

        ui.beginShapes()

        // Dark overlay
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, overlayAlpha)
        ui.shapes.rect(0f, 0f, sw, sh)

        // Panel dimensions with scale animation
        val uiScale = UITheme.Dimensions.scale()
        val panelWidth = 400f * uiScale * panelScale
        val panelHeight = 460f * uiScale * panelScale
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        // Main panel
        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface,
            borderColor = UITheme.primary)

        // Button layout - vertical stack
        val buttonWidth = 280f * uiScale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonSpacing = 18f * uiScale
        val buttonX = centerX - buttonWidth / 2

        // Calculate buttons from top to bottom
        val firstButtonY = panelY + panelHeight - 160f * uiScale

        resumeButton.set(buttonX, firstButtonY, buttonWidth, buttonHeight)
        restartButton.set(buttonX, firstButtonY - buttonHeight - buttonSpacing, buttonWidth, buttonHeight)
        menuButton.set(buttonX, firstButtonY - (buttonHeight + buttonSpacing) * 2, buttonWidth, buttonHeight)

        // Buttons
        ui.button(resumeButton, UITheme.primary, glowIntensity = resumeHover * 0.6f)
        ui.button(restartButton, UITheme.secondary, glowIntensity = restartHover * 0.5f)
        ui.button(menuButton, UITheme.surfaceLight, glowIntensity = menuHover * 0.3f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        if (panelScale > 0.5f) {
            // Title
            val titleY = panelY + panelHeight - 30f * uiScale
            ui.textCentered("PAUSED", centerX, titleY, UIFonts.title, UITheme.textPrimary)

            // Button labels
            ui.textCentered("RESUME", resumeButton.x + resumeButton.width / 2, resumeButton.y + resumeButton.height / 2, UIFonts.button, UITheme.textPrimary)
            ui.textCentered("RESTART", restartButton.x + restartButton.width / 2, restartButton.y + restartButton.height / 2, UIFonts.button, UITheme.textPrimary)
            ui.textCentered("MENU", menuButton.x + menuButton.width / 2, menuButton.y + menuButton.height / 2, UIFonts.button, UITheme.textPrimary)
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
