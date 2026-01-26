package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.state.HighScoreManager

/**
 * Game mode selection screen.
 * Allows player to choose between Endless, Time Trial, Hardcore, and Night Hardcore modes.
 */
class ModeSelectionRenderer(private val highScoreManager: HighScoreManager) : Disposable {
    private val ui = UIRenderer()

    private val endlessButton = Rectangle()
    private val timeTrialButton = Rectangle()
    private val hardcoreButton = Rectangle()
    private val nightHardcoreButton = Rectangle()
    private val backButton = Rectangle()

    private var endlessHover = 0f
    private var timeTrialHover = 0f
    private var hardcoreHover = 0f
    private var nightHardcoreHover = 0f
    private var backHover = 0f
    private var enterAnimProgress = 0f

    enum class Action {
        NONE, ENDLESS, TIME_TRIAL, HARDCORE, NIGHT_HARDCORE, BACK
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
        val hardcoreHovered = hardcoreButton.contains(touchX, touchY)
        val nightHardcoreHovered = nightHardcoreButton.contains(touchX, touchY)
        val backHovered = backButton.contains(touchX, touchY)

        endlessHover = UITheme.Anim.ease(endlessHover, if (endlessHovered) 1f else 0f, 10f)
        timeTrialHover = UITheme.Anim.ease(timeTrialHover, if (timeTrialHovered) 1f else 0f, 10f)
        hardcoreHover = UITheme.Anim.ease(hardcoreHover, if (hardcoreHovered) 1f else 0f, 10f)
        nightHardcoreHover = UITheme.Anim.ease(nightHardcoreHover, if (nightHardcoreHovered) 1f else 0f, 10f)
        backHover = UITheme.Anim.ease(backHover, if (backHovered) 1f else 0f, 10f)

        // Draw background
        ui.beginShapes()
        ui.gradientBackground()

        // Panel
        val panelMargin = 40f * scale
        val panelWidth = (sw - panelMargin * 2) * enterAnimProgress
        val panelHeight = (sh - panelMargin * 2) * enterAnimProgress
        val panelX = centerX - panelWidth / 2
        val panelY = panelMargin

        ui.glassPanel(panelX, panelY, panelWidth, panelHeight,
            radius = UITheme.Dimensions.panelRadius,
            tintColor = UITheme.surfaceSolid)

        // Title Y position
        val titleY = panelY + panelHeight - 60f * scale

        // 4 buttons in 1 row - adaptive sizing for different screen aspects
        val aspectRatio = sw / sh
        val isNarrowScreen = aspectRatio < 1.5f

        // Scale down cards on narrow/square screens
        val cardScale = if (isNarrowScreen) 0.7f else 1f
        val buttonWidth = 340f * scale * cardScale
        val buttonHeight = 460f * scale * cardScale
        val buttonGap = 28f * scale * cardScale
        val totalWidth = buttonWidth * 4 + buttonGap * 3
        val buttonsStartX = centerX - totalWidth / 2
        val buttonsY = panelY + (panelHeight - buttonHeight) / 2 - 10f * scale

        // ENDLESS button
        endlessButton.set(buttonsStartX, buttonsY, buttonWidth, buttonHeight)
        ui.neonButton(endlessButton, UITheme.accent, UITheme.accent, 0.3f + endlessHover * 0.5f)

        // TIME TRIAL button
        timeTrialButton.set(buttonsStartX + buttonWidth + buttonGap, buttonsY, buttonWidth, buttonHeight)
        ui.neonButton(timeTrialButton, UITheme.secondary, UITheme.secondary, 0.3f + timeTrialHover * 0.5f)

        // HARDCORE button
        hardcoreButton.set(buttonsStartX + (buttonWidth + buttonGap) * 2, buttonsY, buttonWidth, buttonHeight)
        ui.neonButton(hardcoreButton, UITheme.danger, UITheme.danger, 0.3f + hardcoreHover * 0.5f)

        // NIGHT HARDCORE button - dark purple color for night theme
        val nightPurple = com.badlogic.gdx.graphics.Color(0.4f, 0.1f, 0.6f, 1f)
        nightHardcoreButton.set(buttonsStartX + (buttonWidth + buttonGap) * 3, buttonsY, buttonWidth, buttonHeight)
        ui.neonButton(nightHardcoreButton, nightPurple, nightPurple, 0.3f + nightHardcoreHover * 0.5f)

        // Back button at bottom (same size as in Settings)
        val backWidth = 260f * scale
        val backHeight = 80f * scale
        backButton.set(centerX - backWidth / 2, panelY + 30f * scale, backWidth, backHeight)
        ui.neonButton(backButton, UITheme.surfaceLight, UITheme.textSecondary, backHover * 0.4f)

        // High score area positioning (no overlay)
        val scoreAreaHeight = 70f * scale * cardScale
        val scoreAreaY = buttonsY + 15f * scale * cardScale

        // Draw trophy icons for high scores
        val trophySize = 36f * scale * cardScale
        val trophyColor = com.badlogic.gdx.graphics.Color(1f, 0.85f, 0.2f, 1f)  // Bright gold
        val trophyY = scoreAreaY + scoreAreaHeight / 2

        if (highScoreManager.highScore > 0) {
            val endlessCenterX = endlessButton.x + endlessButton.width / 2
            ui.trophy(endlessCenterX - 75f * scale * cardScale, trophyY, trophySize, trophyColor)
        }
        if (highScoreManager.hardcoreHighScore > 0) {
            val hardcoreCenterX = hardcoreButton.x + hardcoreButton.width / 2
            ui.trophy(hardcoreCenterX - 75f * scale * cardScale, trophyY, trophySize, trophyColor)
        }
        if (highScoreManager.nightHardcoreHighScore > 0) {
            val nightHardcoreCenterX = nightHardcoreButton.x + nightHardcoreButton.width / 2
            ui.trophy(nightHardcoreCenterX - 75f * scale * cardScale, trophyY, trophySize, trophyColor)
        }

        // Time Trial card - stopwatch icon (larger)
        val timeTrialCenterXShape = timeTrialButton.x + timeTrialButton.width / 2
        val stopwatchSize = 110f * scale * cardScale
        val stopwatchY = timeTrialButton.y + buttonHeight / 2 - 140f * scale * cardScale
        ui.stopwatch(timeTrialCenterXShape, stopwatchY, stopwatchSize, UITheme.textPrimary)

        ui.endShapes()

        // Draw text
        ui.beginBatch()

        ui.textCentered("SELECT MODE", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Use smaller font for narrow screens
        val headingFont = if (isNarrowScreen) UIFonts.body else UIFonts.heading
        val bodyFont = if (isNarrowScreen) UIFonts.caption else UIFonts.body

        // Score area Y for text
        val scoreTextY = scoreAreaY + scoreAreaHeight / 2

        // Endless button text
        val endlessCenterX = endlessButton.x + endlessButton.width / 2
        ui.textCentered("ENDLESS", endlessCenterX,
            endlessButton.y + endlessButton.height - 55f * scale * cardScale, headingFont, UITheme.textPrimary)
        ui.textCentered("Ride until", endlessCenterX,
            endlessButton.y + endlessButton.height / 2 + 35f * scale * cardScale, bodyFont, UITheme.textPrimary)
        ui.textCentered("you crash", endlessCenterX,
            endlessButton.y + endlessButton.height / 2 + 5f * scale * cardScale, bodyFont, UITheme.textPrimary)
        // Endless high score
        val endlessHighScore = highScoreManager.highScore
        if (endlessHighScore > 0) {
            ui.textCentered("$endlessHighScore", endlessCenterX + 20f * scale * cardScale,
                scoreTextY, headingFont, UITheme.textPrimary)
        } else {
            ui.textCentered("---", endlessCenterX, scoreTextY, bodyFont, UITheme.textMuted)
        }

        // Time Trial button text
        val timeTrialCenterX = timeTrialButton.x + timeTrialButton.width / 2
        ui.textCentered("TIME", timeTrialCenterX,
            timeTrialButton.y + timeTrialButton.height - 55f * scale * cardScale, headingFont, UITheme.textPrimary)
        ui.textCentered("TRIAL", timeTrialCenterX,
            timeTrialButton.y + timeTrialButton.height - 100f * scale * cardScale, headingFont, UITheme.textPrimary)
        ui.textCentered("Beat the clock!", timeTrialCenterX,
            timeTrialButton.y + endlessButton.height / 2 + 20f * scale * cardScale, bodyFont, UITheme.textPrimary)

        // Hardcore button text
        val hardcoreCenterX = hardcoreButton.x + hardcoreButton.width / 2
        ui.textCentered("HARDCORE", hardcoreCenterX,
            hardcoreButton.y + hardcoreButton.height - 55f * scale * cardScale, headingFont, UITheme.textPrimary)
        ui.textCentered("Survive", hardcoreCenterX,
            hardcoreButton.y + hardcoreButton.height / 2 + 35f * scale * cardScale, bodyFont, UITheme.textPrimary)
        ui.textCentered("the chaos", hardcoreCenterX,
            hardcoreButton.y + hardcoreButton.height / 2 + 5f * scale * cardScale, bodyFont, UITheme.textPrimary)
        ui.textCentered("2x VOLTS!", hardcoreCenterX,
            hardcoreButton.y + hardcoreButton.height / 2 - 80f * scale * cardScale, headingFont, UITheme.warning)
        // Hardcore high score
        val hardcoreHighScore = highScoreManager.hardcoreHighScore
        if (hardcoreHighScore > 0) {
            ui.textCentered("$hardcoreHighScore", hardcoreCenterX + 20f * scale * cardScale,
                scoreTextY, headingFont, UITheme.textPrimary)
        } else {
            ui.textCentered("---", hardcoreCenterX, scoreTextY, bodyFont, UITheme.textMuted)
        }

        // Night Hardcore button text
        val nightPurpleText = com.badlogic.gdx.graphics.Color(0.85f, 0.7f, 1f, 1f)  // Brighter purple
        val nightHardcoreCenterX = nightHardcoreButton.x + nightHardcoreButton.width / 2
        ui.textCentered("NIGHT", nightHardcoreCenterX,
            nightHardcoreButton.y + nightHardcoreButton.height - 55f * scale * cardScale, headingFont, nightPurpleText)
        ui.textCentered("HARDCORE", nightHardcoreCenterX,
            nightHardcoreButton.y + nightHardcoreButton.height - 100f * scale * cardScale, headingFont, UITheme.textPrimary)
        ui.textCentered("Flickering lights", nightHardcoreCenterX,
            nightHardcoreButton.y + nightHardcoreButton.height / 2 + 35f * scale * cardScale, bodyFont, UITheme.textPrimary)
        ui.textCentered("pure darkness", nightHardcoreCenterX,
            nightHardcoreButton.y + nightHardcoreButton.height / 2 + 5f * scale * cardScale, bodyFont, UITheme.textPrimary)
        ui.textCentered("2.5x VOLTS!", nightHardcoreCenterX,
            nightHardcoreButton.y + nightHardcoreButton.height / 2 - 80f * scale * cardScale, headingFont, UITheme.warning)
        // Night Hardcore high score
        val nightHardcoreHighScore = highScoreManager.nightHardcoreHighScore
        if (nightHardcoreHighScore > 0) {
            ui.textCentered("$nightHardcoreHighScore", nightHardcoreCenterX + 20f * scale * cardScale,
                scoreTextY, headingFont, UITheme.textPrimary)
        } else {
            ui.textCentered("---", nightHardcoreCenterX, scoreTextY, bodyFont, UITheme.textMuted)
        }

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
                hardcoreButton.contains(touchX, touchY) -> {
                    UIFeedback.clickHeavy()
                    return Action.HARDCORE
                }
                nightHardcoreButton.contains(touchX, touchY) -> {
                    UIFeedback.clickHeavy()
                    return Action.NIGHT_HARDCORE
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
            Gdx.input.isKeyJustPressed(Input.Keys.NUM_3) || Gdx.input.isKeyJustPressed(Input.Keys.H) -> {
                UIFeedback.clickHeavy()
                return Action.HARDCORE
            }
            Gdx.input.isKeyJustPressed(Input.Keys.NUM_4) || Gdx.input.isKeyJustPressed(Input.Keys.N) -> {
                UIFeedback.clickHeavy()
                return Action.NIGHT_HARDCORE
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
