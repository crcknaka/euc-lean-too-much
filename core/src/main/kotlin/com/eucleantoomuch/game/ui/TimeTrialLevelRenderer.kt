package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.state.TimeTrialLevel
import com.eucleantoomuch.game.state.TimeTrialManager

/**
 * Time Trial level selection screen.
 * Shows 5 levels with progress, unlock status, and best times.
 */
class TimeTrialLevelRenderer(
    private val timeTrialManager: TimeTrialManager
) : Disposable {
    private val ui = UIRenderer()

    private val levelButtons = Array(10) { Rectangle() }
    private val backButton = Rectangle()

    private val levelHovers = FloatArray(10)
    private var backHover = 0f
    private var enterAnimProgress = 0f

    enum class Action {
        NONE, SELECT_LEVEL, BACK
    }

    var selectedLevel: TimeTrialLevel? = null
        private set

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

        // Update hover states
        for (i in 0 until 10) {
            val hovered = levelButtons[i].contains(touchX, touchY)
            levelHovers[i] = UITheme.Anim.ease(levelHovers[i], if (hovered) 1f else 0f, 10f)
        }
        val backHovered = backButton.contains(touchX, touchY)
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

        val titleY = panelY + panelHeight - 50f * scale

        // Adaptive sizing for different screen aspects
        val aspectRatio = sw / sh
        val isNarrowScreen = aspectRatio < 1.5f

        // Scale down cards on narrow/square screens
        val cardScale = if (isNarrowScreen) 0.65f else 1f

        // Level cards - 2 rows of 5
        val cardWidth = 260f * scale * cardScale
        val cardHeight = 300f * scale * cardScale
        val cardGap = 20f * scale * cardScale
        val rowGap = 20f * scale * cardScale
        val totalCardsWidth = cardWidth * 5 + cardGap * 4
        val cardsStartX = centerX - totalCardsWidth / 2
        val totalHeight = cardHeight * 2 + rowGap
        val topRowY = panelY + (panelHeight - totalHeight) / 2 + cardHeight + rowGap + 20f * scale
        val bottomRowY = topRowY - cardHeight - rowGap

        val levels = TimeTrialLevel.entries

        for (i in levels.indices) {
            val level = levels[i]
            val row = i / 5  // 0 for first 5, 1 for last 5
            val col = i % 5
            val cardX = cardsStartX + col * (cardWidth + cardGap)
            val cardsY = if (row == 0) topRowY else bottomRowY
            val isUnlocked = timeTrialManager.isUnlocked(level)
            val isCompleted = timeTrialManager.isCompleted(level)
            val bestTime = timeTrialManager.getBestTime(level)

            levelButtons[i].set(cardX, cardsY, cardWidth, cardHeight)

            // Card color based on status
            val bgColor = when {
                !isUnlocked -> UITheme.surface
                isCompleted -> UITheme.primary
                else -> UITheme.secondary
            }
            val glowColor = when {
                !isUnlocked -> UITheme.textMuted
                isCompleted -> UITheme.primary
                else -> UITheme.secondary
            }
            val glowIntensity = if (isUnlocked) 0.2f + levelHovers[i] * 0.4f else 0.1f

            ui.neonButton(levelButtons[i], bgColor, glowColor, glowIntensity)
        }

        // Back button (same size as in Settings)
        val backWidth = 260f * scale
        val backHeight = 80f * scale
        backButton.set(centerX - backWidth / 2, panelY + 20f * scale, backWidth, backHeight)
        ui.neonButton(backButton, UITheme.surfaceLight, UITheme.textSecondary, backHover * 0.4f)

        ui.endShapes()

        // Draw text
        ui.beginBatch()

        ui.textCentered("TIME TRIAL", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Use smaller font for narrow screens
        val headingFont = if (isNarrowScreen) UIFonts.body else UIFonts.heading
        val bodyFont = if (isNarrowScreen) UIFonts.caption else UIFonts.body

        for (i in levels.indices) {
            val level = levels[i]
            val row = i / 5
            val col = i % 5
            val cardX = cardsStartX + col * (cardWidth + cardGap)
            val cardsY = if (row == 0) topRowY else bottomRowY
            val cardCenterX = cardX + cardWidth / 2
            val isUnlocked = timeTrialManager.isUnlocked(level)
            val isCompleted = timeTrialManager.isCompleted(level)
            val bestTime = timeTrialManager.getBestTime(level)

            val textColor = if (isUnlocked) UITheme.textPrimary else UITheme.textMuted

            // Level number and name - wider spacing on narrow screens
            val levelNumOffset = if (isNarrowScreen) 15f else 30f
            val levelNameOffset = if (isNarrowScreen) 55f else 60f

            ui.textCentered("LEVEL ${i + 1}", cardCenterX, cardsY + cardHeight - levelNumOffset * scale * cardScale,
                bodyFont, textColor)

            // Level name
            ui.textCentered(level.displayName, cardCenterX, cardsY + cardHeight - levelNameOffset * scale * cardScale,
                UIFonts.caption, textColor)

            if (isUnlocked) {
                // Distance requirement
                ui.textCentered("${level.targetDistance.toInt()}m", cardCenterX,
                    cardsY + cardHeight / 2 + 20f * scale * cardScale, headingFont, UITheme.accent)

                // Time limit
                ui.textCentered("${level.timeLimit.toInt()}s", cardCenterX,
                    cardsY + cardHeight / 2 - 20f * scale * cardScale, bodyFont, UITheme.textSecondary)

                // Best time or "Not completed"
                if (isCompleted && bestTime != null) {
                    val timeStr = formatTime(bestTime)
                    ui.textCentered("BEST: $timeStr", cardCenterX, cardsY + 65f * scale * cardScale,
                        UIFonts.caption, UITheme.surface)
                } else {
                    ui.textCentered("--:--", cardCenterX, cardsY + 65f * scale * cardScale,
                        UIFonts.caption, UITheme.textMuted)
                }

                // Volt reward
                ui.textCentered("+${level.voltReward}V", cardCenterX, cardsY + 25f * scale * cardScale,
                    UIFonts.caption, UITheme.warning)
            } else {
                // Locked indicator
                ui.textCentered("LOCKED", cardCenterX, cardsY + cardHeight / 2,
                    bodyFont, UITheme.textMuted)

                // Volt reward (show even for locked levels)
                ui.textCentered("+${level.voltReward}V", cardCenterX, cardsY + 25f * scale * cardScale,
                    UIFonts.caption, UITheme.textMuted)
            }
        }

        // Back button text
        ui.textCentered("BACK", backButton.x + backButton.width / 2,
            backButton.y + backButton.height / 2, UIFonts.body, UITheme.textSecondary)

        ui.endBatch()

        // Handle input
        if (Gdx.input.justTouched()) {
            for (i in levels.indices) {
                if (levelButtons[i].contains(touchX, touchY) && timeTrialManager.isUnlocked(levels[i])) {
                    UIFeedback.clickHeavy()
                    selectedLevel = levels[i]
                    timeTrialManager.selectLevel(levels[i])
                    return Action.SELECT_LEVEL
                }
            }
            if (backButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return Action.BACK
            }
        }

        // Keyboard shortcuts (1-9 for levels 1-9, 0 for level 10)
        for (i in 1..9) {
            val key = Input.Keys.NUM_1 + i - 1
            if (Gdx.input.isKeyJustPressed(key) && i <= levels.size) {
                val level = levels[i - 1]
                if (timeTrialManager.isUnlocked(level)) {
                    UIFeedback.clickHeavy()
                    selectedLevel = level
                    timeTrialManager.selectLevel(level)
                    return Action.SELECT_LEVEL
                }
            }
        }
        // 0 key for level 10
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0) && levels.size >= 10) {
            val level = levels[9]
            if (timeTrialManager.isUnlocked(level)) {
                UIFeedback.clickHeavy()
                selectedLevel = level
                timeTrialManager.selectLevel(level)
                return Action.SELECT_LEVEL
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            UIFeedback.click()
            return Action.BACK
        }

        return Action.NONE
    }

    private fun formatTime(seconds: Float): String {
        val mins = (seconds / 60).toInt()
        val secs = (seconds % 60).toInt()
        val millis = ((seconds % 1) * 100).toInt()
        return if (mins > 0) {
            "$mins:${secs.toString().padStart(2, '0')}.${millis.toString().padStart(2, '0')}"
        } else {
            "$secs.${millis.toString().padStart(2, '0')}s"
        }
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
