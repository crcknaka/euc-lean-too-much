package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable

/**
 * Credits screen showing special thanks and third-party assets.
 */
class CreditsRenderer : Disposable {
    private val ui = UIRenderer()
    private val backButton = Rectangle()

    // Animation states
    private var overlayAlpha = 0f
    private var panelScale = 0f
    private var backHover = 0f
    private var scrollOffset = 0f

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

        // Panel dimensions
        val panelWidth = 600f * scale * panelScale
        val panelHeight = 800f * scale * panelScale
        val panelX = centerX - panelWidth / 2
        val panelY = (sh - panelHeight) / 2

        // Main panel
        ui.panel(panelX, panelY, panelWidth, panelHeight,
            backgroundColor = UITheme.surface,
            borderColor = UITheme.accent)

        // Back button at bottom
        val buttonWidth = 200f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        val buttonX = centerX - buttonWidth / 2
        val buttonY = panelY + 40f * scale
        backButton.set(buttonX, buttonY, buttonWidth, buttonHeight)

        ui.button(backButton, UITheme.surfaceLight, glowIntensity = backHover * 0.4f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        if (panelScale > 0.5f) {
            var textY = panelY + panelHeight - 60f * scale

            // Title
            ui.textCentered("CREDITS", centerX, textY, UIFonts.title, UITheme.textPrimary)
            textY -= 80f * scale

            // Game title
            ui.textCentered("EUC: Lean Too Much", centerX, textY, UIFonts.heading, UITheme.accent)
            textY -= 60f * scale

            // Developer
            ui.textCentered("Developed by", centerX, textY, UIFonts.caption, UITheme.textSecondary)
            textY -= 35f * scale
            ui.textCentered("cRc^", centerX, textY, UIFonts.body, UITheme.textPrimary)
            textY -= 60f * scale

            // Special Thanks section
            ui.textCentered("SPECIAL THANKS", centerX, textY, UIFonts.body, UITheme.accent)
            textY -= 45f * scale

            ui.textCentered("The EUC Community", centerX, textY, UIFonts.caption, UITheme.textPrimary)
            textY -= 30f * scale
            ui.textCentered("Beta Testers", centerX, textY, UIFonts.caption, UITheme.textPrimary)
            textY -= 30f * scale
            ui.textCentered("Family & Friends", centerX, textY, UIFonts.caption, UITheme.textPrimary)
            textY -= 60f * scale

            // Third-party assets section
            ui.textCentered("THIRD-PARTY ASSETS", centerX, textY, UIFonts.body, UITheme.accent)
            textY -= 45f * scale

            // Sound effects
            ui.textCentered("Sound Effects", centerX, textY, UIFonts.caption, UITheme.textSecondary)
            textY -= 30f * scale
            ui.textCentered("freesound.org contributors", centerX, textY, UIFonts.caption, UITheme.textPrimary)
            textY -= 50f * scale

            // Powered by
            ui.textCentered("Powered by libGDX", centerX, textY, UIFonts.caption, UITheme.textMuted)
            textY -= 30f * scale
            ui.textCentered("Written in Kotlin", centerX, textY, UIFonts.caption, UITheme.textMuted)

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
        scrollOffset = 0f
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
