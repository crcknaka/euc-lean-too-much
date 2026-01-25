package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.state.GameSession
import com.eucleantoomuch.game.state.TimeTrialLevel

/**
 * Modern game over screen with statistics and large action buttons.
 * Features celebratory effects for new high scores.
 */
class GameOverRenderer : Disposable {
    private val ui = UIRenderer()

    private val retryButton = Rectangle()
    private val menuButton = Rectangle()

    // Animation states
    private var overlayAlpha = 0f
    private var panelScale = 0f
    private var statsReveal = 0f
    private var newHighScoreAnim = 0f
    private var retryHover = 0f
    private var menuHover = 0f
    private var safetyTipAnim = 0f  // Animation timer for safety tip

    // Current safety tip (randomized on reset)
    private var currentSafetyTip = ""

    companion object {
        private val safetyTips = listOf(
            "Wear your gear. Always.",
            "Helmet on, worries off.",
            "Slow down, stay safe.",
            "Know your limits.",
            "Protect your head.",
            "Ride within your skill.",
            "Gear up before you roll.",
            "Safety first, tricks later.",
            "One fall can change everything.",
            "Your brain is irreplaceable.",
            "Pads today, ride tomorrow.",
            "Respect the wheel.",
            "Stay alert, stay alive.",
            "Don't push too hard.",
            "Learn to fall safely.",
            "Check your gear regularly.",
            "Watch for obstacles.",
            "Balance is everything.",
            "Practice makes perfect.",
            "Ride smart, ride long."
        )
    }

    enum class ButtonClicked {
        NONE, RETRY, MENU
    }

    // Time Trial result info
    data class TimeTrialResult(
        val completed: Boolean,
        val isNewBestTime: Boolean,
        val nextLevelUnlocked: TimeTrialLevel? = null,
        val completionTime: Float = 0f  // Frozen time at completion
    )

    fun render(
        session: GameSession,
        isNewHighScore: Boolean,
        sessionVolts: Int = 0,
        totalVolts: Int = 0,
        timeTrialResult: TimeTrialResult? = null
    ): ButtonClicked {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val centerY = sh / 2
        val scale = UITheme.Dimensions.scale()

        // Update animations
        overlayAlpha = UITheme.Anim.ease(overlayAlpha, 0.55f, 4f)
        panelScale = UITheme.Anim.ease(panelScale, 1f, 5f)
        statsReveal = UITheme.Anim.ease(statsReveal, 1f, 3f)

        if (isNewHighScore) {
            newHighScoreAnim += Gdx.graphics.deltaTime
        }

        // Check hover
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()
        retryHover = UITheme.Anim.ease(retryHover, if (retryButton.contains(touchX, touchY)) 1f else 0f, 10f)
        menuHover = UITheme.Anim.ease(menuHover, if (menuButton.contains(touchX, touchY)) 1f else 0f, 10f)

        ui.beginShapes()

        // Simple dark overlay (no gradient segments)
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, overlayAlpha)
        ui.shapes.rect(0f, 0f, sw, sh)

        // === HORIZONTAL LAYOUT ===
        // Left side: Title, safety tip, stats
        // Right side: Buttons

        val panelWidth = (sw * 0.9f).coerceAtMost(1250f * scale) * panelScale
        val panelHeight = (sh * 0.8f).coerceAtMost(600f * scale) * panelScale
        val panelX = centerX - panelWidth / 2
        val panelY = centerY - panelHeight / 2

        // Glass panel (clean, no glow)
        ui.glassPanel(panelX, panelY, panelWidth, panelHeight,
            tintColor = UITheme.surfaceSolid)

        // Layout sections
        val leftWidth = panelWidth * 0.6f
        val rightWidth = panelWidth * 0.4f
        val leftCenterX = panelX + leftWidth / 2
        val rightCenterX = panelX + leftWidth + rightWidth / 2
        val contentPadding = 40f * scale

        // === LEFT SIDE: Stats in cards ===
        val cardWidth = (leftWidth - contentPadding * 2) / 2 - 10f * scale
        val cardHeight = 80f * scale
        val cardGap = 12f * scale

        // Score card (top left) - positioned lower to make room for title and safety tip
        val scoreCardX = panelX + contentPadding
        val scoreCardY = panelY + panelHeight - 280f * scale
        ui.card(scoreCardX, scoreCardY, cardWidth, cardHeight,
            radius = 16f * scale,
            glowColor = UITheme.accent, glowIntensity = 0.3f * statsReveal)

        // Distance card (top right)
        val distCardX = scoreCardX + cardWidth + cardGap
        ui.card(distCardX, scoreCardY, cardWidth, cardHeight,
            radius = 16f * scale,
            glowColor = UITheme.primary, glowIntensity = 0.2f * statsReveal)

        // Speed card (bottom left)
        val speedCardY = scoreCardY - cardHeight - cardGap
        ui.card(scoreCardX, speedCardY, cardWidth, cardHeight,
            radius = 16f * scale,
            glowColor = UITheme.cyan, glowIntensity = 0.2f * statsReveal)

        // Near misses card (bottom right)
        val missCardX = scoreCardX + cardWidth + cardGap
        val missGlow = if (session.nearMisses > 0) UITheme.warning else null
        ui.card(missCardX, speedCardY, cardWidth, cardHeight,
            radius = 16f * scale,
            glowColor = missGlow, glowIntensity = 0.25f * statsReveal)

        // === RIGHT SIDE: Buttons ===
        val buttonWidth = rightWidth - contentPadding * 2
        val buttonHeight = 70f * scale
        val buttonGap = 18f * scale

        // Retry button (main action) - moved lower
        val retryY = panelY + panelHeight - 160f * scale
        retryButton.set(rightCenterX - buttonWidth / 2, retryY, buttonWidth, buttonHeight)
        ui.neonButton(retryButton, UITheme.accent, UITheme.accent, 0.4f + retryHover * 0.6f)

        // Menu button
        val menuY = retryY - buttonHeight - buttonGap
        menuButton.set(rightCenterX - buttonWidth / 2, menuY, buttonWidth, buttonHeight)
        ui.neonButton(menuButton, UITheme.surfaceLight, UITheme.textMuted, menuHover * 0.4f)

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        if (panelScale > 0.5f) {
            val titleY = panelY + panelHeight - 70f * scale
            val isTimeTrial = session.isTimeTrial

            if (isTimeTrial && timeTrialResult != null) {
                // Time Trial results
                if (timeTrialResult.completed) {
                    // Success!
                    val titleColor = UITheme.lerp(UITheme.accent, UITheme.accentBright, UITheme.Anim.pulse(2f, 0f, 1f))
                    ui.textCentered("LEVEL COMPLETE!", leftCenterX, titleY, UIFonts.title, titleColor)

                    // Level name
                    val levelName = session.timeTrialLevel?.displayName ?: ""
                    ui.textCentered(levelName, leftCenterX, titleY - 55f * scale, UIFonts.heading, UITheme.textSecondary)

                    // New best time badge
                    if (timeTrialResult.isNewBestTime) {
                        val badgePulse = UITheme.Anim.pulse(4f, 0.8f, 1f)
                        val badgeColor = UITheme.lerp(UITheme.cyan, UITheme.primary, badgePulse)
                        ui.textCentered("NEW BEST TIME!", leftCenterX, titleY - 95f * scale, UIFonts.body, badgeColor)
                    }

                    // Next level unlocked
                    if (timeTrialResult.nextLevelUnlocked != null) {
                        val unlockPulse = UITheme.Anim.pulse(3f, 0.7f, 1f)
                        val unlockColor = UITheme.withAlpha(UITheme.warning, unlockPulse)
                        ui.textCentered("${timeTrialResult.nextLevelUnlocked.displayName} UNLOCKED!",
                            leftCenterX, titleY - 130f * scale, UIFonts.caption, unlockColor)
                    }
                } else {
                    // Failed - time ran out or crashed
                    val failReason = if (session.timeRemaining <= 0f) "TIME'S UP!" else "CRASHED!"
                    ui.textCentered(failReason, leftCenterX, titleY, UIFonts.title, UITheme.danger)

                    // Progress info
                    val level = session.timeTrialLevel
                    if (level != null) {
                        val progressText = "${session.distanceTraveled.toInt()}m / ${level.targetDistance.toInt()}m"
                        ui.textCentered(progressText, leftCenterX, titleY - 55f * scale, UIFonts.heading, UITheme.textSecondary)
                    }

                    // Safety tip for crashes
                    if (session.timeRemaining > 0f && currentSafetyTip.isNotEmpty()) {
                        safetyTipAnim += Gdx.graphics.deltaTime
                        val glowIntensity = UITheme.Anim.pulse(2f, 0.85f, 1f)
                        val tipColor = UITheme.withAlpha(UITheme.warning, glowIntensity)
                        ui.textCentered(currentSafetyTip, leftCenterX, titleY - 100f * scale, UIFonts.body, tipColor)
                    }
                }
            } else {
                // Endless mode - original game over
                val titleColor = if (isNewHighScore) {
                    UITheme.lerp(UITheme.danger, UITheme.accent, UITheme.Anim.pulse(2f, 0f, 1f))
                } else {
                    UITheme.danger
                }
                ui.textCentered("GAME OVER", leftCenterX, titleY, UIFonts.title, titleColor)

                // Safety tip below title
                if (currentSafetyTip.isNotEmpty()) {
                    safetyTipAnim += Gdx.graphics.deltaTime
                    val glowIntensity = if (safetyTipAnim < 2f) {
                        UITheme.Anim.pulse(3f, 0.6f, 1f)
                    } else {
                        UITheme.Anim.pulse(2f, 0.85f, 1f)
                    }
                    val tipColor = UITheme.withAlpha(UITheme.warning, glowIntensity)
                    ui.textCentered(currentSafetyTip, leftCenterX, titleY - 60f * scale, UIFonts.heading, tipColor)
                }

                // New high score badge - below safety tip
                if (isNewHighScore) {
                    val badgePulse = UITheme.Anim.pulse(4f, 0.8f, 1f)
                    val badgeColor = UITheme.lerp(UITheme.accent, UITheme.accentBright, badgePulse)
                    ui.textCentered("NEW HIGH SCORE!", leftCenterX, titleY - 110f * scale, UIFonts.body, badgeColor)
                }
            }

            // Stats card content
            val statsAlpha = statsReveal
            val labelOffset = 18f * scale
            val valueOffset = -18f * scale

            // First card: Score (endless) or Time (time trial)
            if (session.isTimeTrial && timeTrialResult != null) {
                ui.textCentered("TIME", scoreCardX + cardWidth / 2, scoreCardY + cardHeight - labelOffset,
                    UIFonts.caption, UITheme.withAlpha(UITheme.textSecondary, statsAlpha))
                val timeText = formatTime(timeTrialResult.completionTime)
                ui.textCentered(timeText, scoreCardX + cardWidth / 2, scoreCardY + cardHeight / 2 + valueOffset,
                    UIFonts.heading, UITheme.withAlpha(UITheme.accent, statsAlpha))
            } else if (!session.isTimeTrial) {
                ui.textCentered("SCORE", scoreCardX + cardWidth / 2, scoreCardY + cardHeight - labelOffset,
                    UIFonts.caption, UITheme.withAlpha(UITheme.textSecondary, statsAlpha))
                ui.textCentered(session.score.toString(), scoreCardX + cardWidth / 2, scoreCardY + cardHeight / 2 + valueOffset,
                    UIFonts.heading, UITheme.withAlpha(UITheme.accent, statsAlpha))
            }

            // Distance
            ui.textCentered("DISTANCE", distCardX + cardWidth / 2, scoreCardY + cardHeight - labelOffset,
                UIFonts.caption, UITheme.withAlpha(UITheme.textSecondary, statsAlpha))
            ui.textCentered("${session.distanceTraveled.toInt()}m", distCardX + cardWidth / 2, scoreCardY + cardHeight / 2 + valueOffset,
                UIFonts.heading, UITheme.withAlpha(UITheme.primary, statsAlpha))

            // Speed
            ui.textCentered("TOP SPEED", scoreCardX + cardWidth / 2, speedCardY + cardHeight - labelOffset,
                UIFonts.caption, UITheme.withAlpha(UITheme.textSecondary, statsAlpha))
            ui.textCentered("${(session.maxSpeed * 3.6f).toInt()} km/h", scoreCardX + cardWidth / 2, speedCardY + cardHeight / 2 + valueOffset,
                UIFonts.heading, UITheme.withAlpha(UITheme.cyan, statsAlpha))

            // Near misses
            ui.textCentered("NEAR MISSES", missCardX + cardWidth / 2, speedCardY + cardHeight - labelOffset,
                UIFonts.caption, UITheme.withAlpha(UITheme.textSecondary, statsAlpha))
            val nearMissColor = if (session.nearMisses > 0) UITheme.warning else UITheme.textPrimary
            ui.textCentered(session.nearMisses.toString(), missCardX + cardWidth / 2, speedCardY + cardHeight / 2 + valueOffset,
                UIFonts.heading, UITheme.withAlpha(nearMissColor, statsAlpha))

            // Volts earned display (below stats cards)
            if (sessionVolts > 0) {
                val voltsY = speedCardY - 75f * scale
                val voltsColor = com.badlogic.gdx.graphics.Color(1f, 0.85f, 0.1f, 1f)  // Electric yellow
                val voltsPulse = UITheme.Anim.pulse(2f, 0.85f, 1f)
                ui.textCentered("+${sessionVolts}V", leftCenterX - 100f * scale, voltsY,
                    UIFonts.heading, UITheme.withAlpha(voltsColor, statsAlpha * voltsPulse))
                ui.textCentered("Total: ${totalVolts}V", leftCenterX + 120f * scale, voltsY,
                    UIFonts.caption, UITheme.withAlpha(UITheme.textMuted, statsAlpha))
            }

            // Button labels
            ui.textCentered("RETRY", retryButton.x + retryButton.width / 2, retryButton.y + retryButton.height / 2,
                UIFonts.button, UITheme.textPrimary)
            ui.textCentered("MENU", menuButton.x + menuButton.width / 2, menuButton.y + menuButton.height / 2,
                UIFonts.body, UITheme.textPrimary)
        }

        ui.endBatch()

        // === Input ===
        if (Gdx.input.justTouched()) {
            if (retryButton.contains(touchX, touchY)) {
                UIFeedback.clickHeavy()
                return ButtonClicked.RETRY
            }
            if (menuButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return ButtonClicked.MENU
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            UIFeedback.clickHeavy()
            return ButtonClicked.RETRY
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            UIFeedback.click()
            return ButtonClicked.MENU
        }

        return ButtonClicked.NONE
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

    fun reset() {
        overlayAlpha = 0f
        panelScale = 0f
        statsReveal = 0f
        newHighScoreAnim = 0f
        safetyTipAnim = 0f
        // Pick a random safety tip
        currentSafetyTip = safetyTips.random()
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
