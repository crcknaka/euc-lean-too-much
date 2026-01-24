package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable

/**
 * Modern pause menu with glassmorphism effect.
 * Horizontal layout optimized for landscape mobile screens.
 */
class PauseRenderer : Disposable {
    private val ui = UIRenderer()

    private val resumeButton = Rectangle()
    private val restartButton = Rectangle()
    private val calibrateButton = Rectangle()
    private val settingsButton = Rectangle()
    private val menuButton = Rectangle()

    // Animation states
    private var overlayAlpha = 0f
    private var panelScale = 0f
    private var resumeHover = 0f
    private var restartHover = 0f
    private var calibrateHover = 0f
    private var settingsHover = 0f
    private var menuHover = 0f

    enum class ButtonClicked {
        NONE, RESUME, RESTART, CALIBRATE, SETTINGS, MENU
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
        overlayAlpha = UITheme.Anim.ease(overlayAlpha, 0.7f, 6f)
        panelScale = UITheme.Anim.ease(panelScale, 1f, 6f)

        // Check hover
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        resumeHover = UITheme.Anim.ease(resumeHover, if (resumeButton.contains(touchX, touchY)) 1f else 0f, 10f)
        restartHover = UITheme.Anim.ease(restartHover, if (restartButton.contains(touchX, touchY)) 1f else 0f, 10f)
        calibrateHover = UITheme.Anim.ease(calibrateHover, if (calibrateButton.contains(touchX, touchY)) 1f else 0f, 10f)
        settingsHover = UITheme.Anim.ease(settingsHover, if (settingsButton.contains(touchX, touchY)) 1f else 0f, 10f)
        menuHover = UITheme.Anim.ease(menuHover, if (menuButton.contains(touchX, touchY)) 1f else 0f, 10f)

        ui.beginShapes()

        // Full gradient background overlay with visible color transition
        val topBgColor = UITheme.withAlpha(Color(0x1a1a30FF.toInt()), overlayAlpha)
        val bottomBgColor = UITheme.withAlpha(Color(0x0a0a18FF.toInt()), overlayAlpha)
        for (i in 0 until 12) {
            val t = i / 12f
            val segColor = UITheme.lerp(bottomBgColor, topBgColor, t)
            ui.shapes.color = segColor
            ui.shapes.rect(0f, sh * t, sw, sh / 12f + 1)
        }

        // Glass panel - wider for horizontal layout, taller for more vertical space
        val panelWidth = 700f * scale * panelScale
        val panelHeight = 480f * scale * panelScale  // Increased height
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2  // Centered

        // Glass effect panel
        ui.glassPanel(panelX, panelY, panelWidth, panelHeight,
            tintColor = UITheme.withAlpha(UITheme.surfaceSolid, 0.85f),
            borderGlow = UITheme.accent)

        // Accent line at top
        ui.shapes.color = UITheme.accent
        ui.roundedRect(panelX + 30f * scale, panelY + panelHeight - 8f * scale,
            panelWidth - 60f * scale, 4f * scale, 2f * scale, UITheme.accent)

        // === HORIZONTAL BUTTON LAYOUT ===
        // Two rows: Resume (big) on top, 3 smaller buttons below
        val buttonGap = 20f * scale

        // Resume button - full width, positioned lower in panel
        val resumeWidth = panelWidth - 80f * scale
        val resumeHeight = 100f * scale
        val resumeX = panelX + 40f * scale
        val resumeY = panelY + panelHeight - 280f * scale  // Moved down more

        resumeButton.set(resumeX, resumeY, resumeWidth, resumeHeight)
        ui.neonButton(resumeButton, UITheme.accent, UITheme.accent, 0.3f + resumeHover * 0.7f)

        // Second row: 4 buttons side by side
        val smallButtonWidth = (resumeWidth - buttonGap * 3) / 4
        val smallButtonHeight = 80f * scale
        val smallButtonsY = resumeY - resumeHeight - 30f * scale

        restartButton.set(resumeX, smallButtonsY, smallButtonWidth, smallButtonHeight)
        ui.neonButton(restartButton, UITheme.danger, UITheme.danger, restartHover * 0.6f)

        calibrateButton.set(resumeX + (smallButtonWidth + buttonGap), smallButtonsY, smallButtonWidth, smallButtonHeight)
        ui.neonButton(calibrateButton, UITheme.secondary, UITheme.secondary, calibrateHover * 0.6f)

        settingsButton.set(resumeX + (smallButtonWidth + buttonGap) * 2, smallButtonsY, smallButtonWidth, smallButtonHeight)
        ui.neonButton(settingsButton, UITheme.surfaceLight, UITheme.textSecondary, settingsHover * 0.4f)

        menuButton.set(resumeX + (smallButtonWidth + buttonGap) * 3, smallButtonsY, smallButtonWidth, smallButtonHeight)
        ui.neonButton(menuButton, UITheme.danger, UITheme.danger, menuHover * 0.6f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        if (panelScale > 0.5f) {
            // Title - positioned lower
            val titleY = panelY + panelHeight - 90f * scale
            ui.textCentered("PAUSED", centerX, titleY, UIFonts.title, UITheme.textPrimary)

            // Button labels
            ui.textCentered("RESUME", resumeButton.x + resumeButton.width / 2,
                resumeButton.y + resumeButton.height / 2, UIFonts.button, UITheme.textPrimary)

            ui.textCentered("RESTART", restartButton.x + restartButton.width / 2,
                restartButton.y + restartButton.height / 2, UIFonts.caption, UITheme.textPrimary)

            ui.textCentered("CALIBRATE", calibrateButton.x + calibrateButton.width / 2,
                calibrateButton.y + calibrateButton.height / 2, UIFonts.caption, UITheme.textPrimary)

            ui.textCentered("SETTINGS", settingsButton.x + settingsButton.width / 2,
                settingsButton.y + settingsButton.height / 2, UIFonts.caption, UITheme.textPrimary)

            ui.textCentered("MENU", menuButton.x + menuButton.width / 2,
                menuButton.y + menuButton.height / 2, UIFonts.caption, UITheme.textPrimary)
        }

        ui.endBatch()

        // === Input ===
        if (Gdx.input.justTouched()) {
            if (resumeButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.RESUME
            }
            if (restartButton.contains(touchX, touchY)) {
                UIFeedback.clickHeavy()
                return ButtonClicked.RESTART
            }
            if (calibrateButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.CALIBRATE
            }
            if (settingsButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.SETTINGS
            }
            if (menuButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.MENU
            }
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.P) ||
            Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            UIFeedback.click()
            return ButtonClicked.RESUME
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            UIFeedback.clickHeavy()
            return ButtonClicked.RESTART
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            UIFeedback.click()
            return ButtonClicked.SETTINGS
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            UIFeedback.click()
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

    fun recreate() {
        ui.recreate()
    }

    override fun dispose() {
        ui.dispose()
    }
}
