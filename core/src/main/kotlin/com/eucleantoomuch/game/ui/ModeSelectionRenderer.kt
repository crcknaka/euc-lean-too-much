package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable

/**
 * Game mode selection screen.
 * Allows player to choose between Endless and Time Trial modes.
 */
class ModeSelectionRenderer : Disposable {
    private val ui = UIRenderer()

    private val endlessButton = Rectangle()
    private val timeTrialButton = Rectangle()
    private val backButton = Rectangle()

    private var endlessHover = 0f
    private var timeTrialHover = 0f
    private var backHover = 0f
    private var enterAnimProgress = 0f

    enum class Action {
        NONE, ENDLESS, TIME_TRIAL, BACK
    }

    fun render(): Action {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val scale = UITheme.Dimensions.scale()

        enterAnimProgress = UITheme.Anim.ease(enterAnimProgress, 1f, 4f)

        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()

        val endlessHovered = endlessButton.contains(touchX, touchY)
        val timeTrialHovered = timeTrialButton.contains(touchX, touchY)
        val backHovered = backButton.contains(touchX, touchY)

        endlessHover = UITheme.Anim.ease(endlessHover, if (endlessHovered) 1f else 0f, 10f)
        timeTrialHover = UITheme.Anim.ease(timeTrialHover, if (timeTrialHovered) 1f else 0f, 10f)
        backHover = UITheme.Anim.ease(backHover, if (backHovered) 1f else 0f, 10f)

        // Draw background
        ui.beginShapes()
        ui.gradientBackground()

        // Panel
        val panelMargin = 60f * scale
        val panelWidth = (sw - panelMargin * 2) * enterAnimProgress
        val panelHeight = (sh - panelMargin * 2) * enterAnimProgress
        val panelX = centerX - panelWidth / 2
        val panelY = panelMargin

        ui.glassPanel(panelX, panelY, panelWidth, panelHeight,
            radius = UITheme.Dimensions.panelRadius,
            tintColor = UITheme.surfaceSolid)

        // Title Y position
        val titleY = panelY + panelHeight - 60f * scale

        // Two large buttons side by side
        val buttonWidth = 320f * scale
        val buttonHeight = 200f * scale
        val buttonGap = 40f * scale
        val totalWidth = buttonWidth * 2 + buttonGap
        val buttonsStartX = centerX - totalWidth / 2
        val buttonsY = panelY + panelHeight / 2 - buttonHeight / 2

        // ENDLESS button (left)
        endlessButton.set(buttonsStartX, buttonsY, buttonWidth, buttonHeight)
        ui.neonButton(endlessButton, UITheme.accent, UITheme.accent, 0.3f + endlessHover * 0.5f)

        // TIME TRIAL button (right)
        timeTrialButton.set(buttonsStartX + buttonWidth + buttonGap, buttonsY, buttonWidth, buttonHeight)
        ui.neonButton(timeTrialButton, UITheme.secondary, UITheme.secondary, 0.3f + timeTrialHover * 0.5f)

        // Back button at bottom
        val backWidth = 140f * scale
        val backHeight = 60f * scale
        backButton.set(centerX - backWidth / 2, panelY + 40f * scale, backWidth, backHeight)
        ui.neonButton(backButton, UITheme.surfaceLight, UITheme.textSecondary, backHover * 0.4f)

        ui.endShapes()

        // Draw text
        ui.beginBatch()

        ui.textCentered("SELECT MODE", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Endless button text
        ui.textCentered("ENDLESS", endlessButton.x + endlessButton.width / 2,
            endlessButton.y + endlessButton.height / 2 + 20f * scale, UIFonts.heading, UITheme.textPrimary)
        ui.textCentered("Ride until you crash", endlessButton.x + endlessButton.width / 2,
            endlessButton.y + endlessButton.height / 2 - 25f * scale, UIFonts.caption, UITheme.textSecondary)

        // Time Trial button text
        ui.textCentered("TIME TRIAL", timeTrialButton.x + timeTrialButton.width / 2,
            timeTrialButton.y + timeTrialButton.height / 2 + 20f * scale, UIFonts.heading, UITheme.textPrimary)
        ui.textCentered("Beat the clock", timeTrialButton.x + timeTrialButton.width / 2,
            timeTrialButton.y + timeTrialButton.height / 2 - 25f * scale, UIFonts.caption, UITheme.textSecondary)

        // Back button text
        ui.textCentered("BACK", backButton.x + backButton.width / 2,
            backButton.y + backButton.height / 2, UIFonts.body, UITheme.textSecondary)

        ui.endBatch()

        // Handle input
        if (Gdx.input.justTouched()) {
            when {
                endlessButton.contains(touchX, touchY) -> {
                    UIFeedback.clickHeavy()
                    return Action.ENDLESS
                }
                timeTrialButton.contains(touchX, touchY) -> {
                    UIFeedback.clickHeavy()
                    return Action.TIME_TRIAL
                }
                backButton.contains(touchX, touchY) -> {
                    UIFeedback.click()
                    return Action.BACK
                }
            }
        }

        // Keyboard shortcuts
        when {
            Gdx.input.isKeyJustPressed(Input.Keys.NUM_1) || Gdx.input.isKeyJustPressed(Input.Keys.E) -> {
                UIFeedback.clickHeavy()
                return Action.ENDLESS
            }
            Gdx.input.isKeyJustPressed(Input.Keys.NUM_2) || Gdx.input.isKeyJustPressed(Input.Keys.T) -> {
                UIFeedback.clickHeavy()
                return Action.TIME_TRIAL
            }
            Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK) -> {
                UIFeedback.click()
                return Action.BACK
            }
        }

        return Action.NONE
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
        enterAnimProgress = 0f
    }

    fun recreate() {
        ui.recreate()
    }

    override fun dispose() {
        ui.dispose()
    }
}
