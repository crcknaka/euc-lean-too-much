package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.state.GameSession
import com.eucleantoomuch.game.state.SettingsManager
import kotlin.math.sqrt

/**
 * Modern in-game HUD with clean, readable panels.
 * Designed for quick glances during gameplay with clear visual hierarchy.
 */
class Hud(private val settingsManager: SettingsManager) : Disposable {
    private val ui = UIRenderer()

    // Animation states
    private var scorePopScale = 1f
    private var lastScore = 0
    private var warningFlash = 0f
    private var speedBarSmooth = 0f
    private var pwmWarningFlash = 0f
    private var pwmSmooth = 0f

    fun render(session: GameSession, euc: EucComponent, pwmWarningActive: Boolean = false) {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val scale = UITheme.Dimensions.scale()

        // Score pop animation
        if (session.score != lastScore) {
            scorePopScale = 1.25f
            lastScore = session.score
        }
        scorePopScale = UITheme.Anim.ease(scorePopScale, 1f, 8f)

        // Smooth speed bar
        val targetSpeed = (euc.speed / 25f).coerceIn(0f, 1f)
        speedBarSmooth = UITheme.Anim.ease(speedBarSmooth, targetSpeed, 5f)

        // Smooth PWM
        pwmSmooth = UITheme.Anim.ease(pwmSmooth, euc.pwm, 8f)

        // Warning flash (about to fall)
        if (euc.isAboutToFall()) {
            warningFlash += Gdx.graphics.deltaTime * 8f
        } else {
            warningFlash = UITheme.Anim.ease(warningFlash, 0f, 5f)
        }

        // PWM warning flash
        if (pwmWarningActive) {
            pwmWarningFlash += Gdx.graphics.deltaTime * 10f
        } else {
            pwmWarningFlash = UITheme.Anim.ease(pwmWarningFlash, 0f, 5f)
        }

        ui.beginShapes()

        // === Top Score Panel ===
        val topPanelWidth = 320f * scale
        val topPanelHeight = 120f * scale
        val topPanelY = sh - topPanelHeight - 28f * scale
        ui.roundedRect(sw / 2 - topPanelWidth / 2, topPanelY, topPanelWidth, topPanelHeight,
            20f * scale, UITheme.withAlpha(UITheme.surface, 0.92f))

        // === Speed Panel (bottom left) ===
        drawSpeedPanel(euc)

        // === PWM Panel (above speed panel) ===
        drawPwmPanel(euc)

        // === Lean Indicator (bottom right) ===
        drawLeanIndicator(euc)

        // === Warning Overlay ===
        if (warningFlash > 0.1f) {
            val flashAlpha = (MathUtils.sin(warningFlash * 3f) * 0.5f + 0.5f) * 0.18f
            ui.shapes.color = UITheme.withAlpha(UITheme.danger, flashAlpha)
            ui.shapes.rect(0f, 0f, sw, sh)

            // Red vignette at edges
            val vignetteWidth = 28f * scale
            ui.shapes.color = UITheme.withAlpha(UITheme.danger, flashAlpha * 2.2f)
            ui.shapes.rect(0f, 0f, vignetteWidth, sh)
            ui.shapes.rect(sw - vignetteWidth, 0f, vignetteWidth, sh)
            ui.shapes.rect(0f, 0f, sw, vignetteWidth)
            ui.shapes.rect(0f, sh - vignetteWidth, sw, vignetteWidth)
        }

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        // Score
        val scoreLabelY = topPanelY + topPanelHeight - 30f * scale
        val scoreValueY = scoreLabelY - 48f * scale
        ui.textCentered("SCORE", sw / 2, scoreLabelY, UIFonts.caption, UITheme.textSecondary)

        // Score value with pop effect
        val originalScale = UIFonts.title.data.scaleX
        UIFonts.title.data.setScale(originalScale * scorePopScale)
        ui.textCentered(session.score.toString(), sw / 2, scoreValueY, UIFonts.title, UITheme.textPrimary)
        UIFonts.title.data.setScale(originalScale)

        // Warnings - centered on screen
        if (euc.inPuddle) {
            drawWarningBadge("SLIPPERY!", UITheme.cyan, sh / 2 + 40f * scale)
        }

        if (euc.isAboutToFall()) {
            val dangerPulse = UITheme.Anim.pulse(6f, 0.7f, 1f)
            drawWarningBadge("!! DANGER !!", UITheme.lerp(UITheme.danger, UITheme.warningBright, dangerPulse), sh / 2 - 40f * scale)
        }

        // PWM warning indicator
        if (pwmWarningFlash > 0.1f) {
            val pwmPercent = euc.getPwmPercent()
            val warningPulse = MathUtils.sin(pwmWarningFlash * 8f) * 0.5f + 0.5f
            val warningColor = UITheme.lerp(UITheme.warning, UITheme.warningBright, warningPulse)
            drawWarningBadge("PWM $pwmPercent%", warningColor, sh / 2 + 100f * scale)
        }

        // FPS counter (top-left, visible but unobtrusive)
        if (settingsManager.showFps) {
            val fps = Gdx.graphics.framesPerSecond
            val fpsColor = when {
                fps >= 55 -> UITheme.primary
                fps >= 30 -> UITheme.warning
                else -> UITheme.danger
            }
            UIFonts.caption.color = fpsColor
            UIFonts.caption.draw(ui.batch, "FPS: $fps", 14f * scale, sh - 14f * scale)
        }

        ui.endBatch()
    }

    private fun drawSpeedPanel(euc: EucComponent) {
        val scale = UITheme.Dimensions.scale()
        val panelWidth = 240f * scale
        val panelHeight = 130f * scale
        val panelX = 30f * scale
        val panelY = 30f * scale

        // Panel background
        ui.roundedRect(panelX, panelY, panelWidth, panelHeight, 18f * scale, UITheme.withAlpha(UITheme.surface, 0.92f))

        // Speed bar background
        val barX = panelX + 16f * scale
        val barY = panelY + 16f * scale
        val barWidth = panelWidth - 32f * scale
        val barHeight = 12f * scale

        ui.roundedRect(barX, barY, barWidth, barHeight, 6f * scale, UITheme.surfaceLight)

        // Speed bar fill with color gradient
        val speedColor = when {
            speedBarSmooth < 0.4f -> UITheme.primary
            speedBarSmooth < 0.7f -> UITheme.warning
            else -> UITheme.danger
        }
        val fillWidth = (barWidth * speedBarSmooth).coerceAtLeast(12f * scale)
        ui.roundedRect(barX, barY, fillWidth, barHeight, 6f * scale, speedColor)

        // Glow effect at high speed
        if (speedBarSmooth > 0.7f) {
            val glowAlpha = (speedBarSmooth - 0.7f) / 0.3f * UITheme.Anim.pulse(4f, 0.35f, 0.65f)
            ui.roundedRect(barX, barY - 3f, fillWidth, barHeight + 6f, 8f * scale, UITheme.withAlpha(speedColor, glowAlpha))
        }

        // Speed text
        ui.endShapes()
        ui.beginBatch()

        val speedKmh = (euc.speed * 3.6f).toInt()
        UIFonts.title.color = UITheme.textPrimary
        UIFonts.title.draw(ui.batch, "$speedKmh", panelX + 24f * scale, panelY + panelHeight - 22f * scale)

        ui.layout.setText(UIFonts.title, "$speedKmh")
        UIFonts.caption.color = UITheme.textSecondary
        UIFonts.caption.draw(ui.batch, "km/h", panelX + 30f * scale + ui.layout.width, panelY + panelHeight - 32f * scale)

        ui.endBatch()
        ui.beginShapes()
    }

    private fun drawPwmPanel(euc: EucComponent) {
        val scale = UITheme.Dimensions.scale()
        val panelWidth = 240f * scale
        val panelHeight = 100f * scale
        val panelX = 30f * scale
        val panelY = 170f * scale  // Above speed panel

        // Panel background
        ui.roundedRect(panelX, panelY, panelWidth, panelHeight, 18f * scale, UITheme.withAlpha(UITheme.surface, 0.92f))

        // PWM bar background
        val barX = panelX + 16f * scale
        val barY = panelY + 16f * scale
        val barWidth = panelWidth - 32f * scale
        val barHeight = 16f * scale

        ui.roundedRect(barX, barY, barWidth, barHeight, 8f * scale, UITheme.surfaceLight)

        // PWM bar fill with color based on level
        val pwmColor = when {
            pwmSmooth < 0.7f -> UITheme.primary
            pwmSmooth < 0.9f -> UITheme.warning
            pwmSmooth <= 1.0f -> UITheme.lerp(UITheme.warning, UITheme.danger, (pwmSmooth - 0.9f) / 0.1f)
            else -> UITheme.danger
        }

        // Bar shows up to 110%
        val displayPwm = (pwmSmooth / 1.1f).coerceIn(0f, 1f)
        val fillWidth = (barWidth * displayPwm).coerceAtLeast(12f * scale)
        ui.roundedRect(barX, barY, fillWidth, barHeight, 8f * scale, pwmColor)

        // Marker at 90%
        val marker90X = barX + barWidth * (0.9f / 1.1f)
        ui.shapes.color = UITheme.withAlpha(UITheme.warning, 0.55f)
        ui.shapes.rectLine(marker90X, barY - 3f, marker90X, barY + barHeight + 3f, 2.5f * scale)

        // Marker at 100%
        val marker100X = barX + barWidth * (1.0f / 1.1f)
        ui.shapes.color = UITheme.withAlpha(UITheme.danger, 0.75f)
        ui.shapes.rectLine(marker100X, barY - 4f, marker100X, barY + barHeight + 4f, 2.5f * scale)

        // Pulsing glow when PWM > 90%
        if (pwmSmooth > 0.9f) {
            val glowIntensity = ((pwmSmooth - 0.9f) / 0.1f).coerceIn(0f, 1f) * UITheme.Anim.pulse(6f, 0.4f, 0.85f)
            ui.roundedRect(barX - 3f, barY - 3f, fillWidth + 6f, barHeight + 6f, 10f * scale, UITheme.withAlpha(pwmColor, glowIntensity * 0.55f))
        }

        // PWM text
        ui.endShapes()
        ui.beginBatch()

        val pwmPercent = euc.getPwmPercent()
        UIFonts.heading.color = pwmColor
        UIFonts.heading.draw(ui.batch, "$pwmPercent%", panelX + 24f * scale, panelY + panelHeight - 16f * scale)

        ui.layout.setText(UIFonts.heading, "$pwmPercent%")
        UIFonts.caption.color = UITheme.textSecondary
        UIFonts.caption.draw(ui.batch, "PWM", panelX + 32f * scale + ui.layout.width, panelY + panelHeight - 22f * scale)

        ui.endBatch()
        ui.beginShapes()
    }

    private fun drawLeanIndicator(euc: EucComponent) {
        val scale = UITheme.Dimensions.scale()
        val indicatorSize = 160f * scale
        val indicatorX = ui.screenWidth - indicatorSize - 30f * scale
        val indicatorY = 30f * scale
        val centerX = indicatorX + indicatorSize / 2
        val centerY = indicatorY + indicatorSize / 2

        // Shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.35f)
        ui.shapes.circle(centerX + 3f, centerY - 3f, indicatorSize / 2 + 3f)

        // Background with zones
        ui.shapes.color = UITheme.withAlpha(UITheme.surface, 0.95f)
        ui.shapes.circle(centerX, centerY, indicatorSize / 2)

        // Danger zone (outer red)
        ui.shapes.color = UITheme.withAlpha(UITheme.danger, 0.22f)
        ui.shapes.circle(centerX, centerY, indicatorSize / 2 - 4f)

        // Warning zone (yellow)
        ui.shapes.color = UITheme.withAlpha(UITheme.warning, 0.18f)
        ui.shapes.circle(centerX, centerY, indicatorSize / 2 * 0.72f)

        // Safe zone (green center)
        ui.shapes.color = UITheme.withAlpha(UITheme.primary, 0.22f)
        ui.shapes.circle(centerX, centerY, indicatorSize / 2 * 0.45f)

        // Grid lines
        ui.shapes.color = UITheme.withAlpha(UITheme.textMuted, 0.18f)
        ui.shapes.rectLine(centerX - indicatorSize / 2 + 12f, centerY,
            centerX + indicatorSize / 2 - 12f, centerY, 1.5f * scale)
        ui.shapes.rectLine(centerX, centerY - indicatorSize / 2 + 12f,
            centerX, centerY + indicatorSize / 2 - 12f, 1.5f * scale)

        // Current lean position
        val totalLean = sqrt(euc.forwardLean * euc.forwardLean + euc.sideLean * euc.sideLean)
        val dotX = centerX + euc.sideLean * indicatorSize / 2 * 0.78f
        val dotY = centerY + euc.forwardLean * indicatorSize / 2 * 0.78f

        // Dot color based on danger
        val dotColor = when {
            totalLean > 0.85f -> UITheme.danger
            totalLean > 0.6f -> UITheme.warning
            else -> UITheme.textPrimary
        }

        // Dot shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.45f)
        ui.shapes.circle(dotX + 2f, dotY - 2f, 13f * scale)

        // Main dot
        ui.shapes.color = dotColor
        ui.shapes.circle(dotX, dotY, 12f * scale)

        // Dot highlight
        ui.shapes.color = UITheme.withAlpha(Color.WHITE, 0.45f)
        ui.shapes.circle(dotX - 3.5f * scale, dotY + 3.5f * scale, 4f * scale)

        // Danger glow when leaning too much
        if (totalLean > 0.7f) {
            val glowIntensity = (totalLean - 0.7f) / 0.3f * UITheme.Anim.pulse(5f, 0.35f, 0.75f)
            ui.shapes.color = UITheme.withAlpha(UITheme.danger, glowIntensity * 0.35f)
            ui.shapes.circle(dotX, dotY, 20f * scale)
        }
    }

    private fun drawWarningBadge(text: String, color: Color, y: Float) {
        val pulse = UITheme.Anim.pulse(4f, 0.8f, 1f)
        UIFonts.heading.color = UITheme.withAlpha(color, pulse)
        ui.textCentered(text, ui.screenWidth / 2, y, UIFonts.heading, UIFonts.heading.color)
    }

    fun renderCountdown(seconds: Int) {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val scale = UITheme.Dimensions.scale()

        ui.beginShapes()

        // Dark circle background - larger
        ui.shapes.color = UITheme.withAlpha(UITheme.surface, 0.92f)
        ui.shapes.circle(sw / 2, sh / 2, 100f * scale)

        // Ring
        val ringColor = if (seconds > 0) UITheme.warning else UITheme.primary
        ui.gauge(sw / 2, sh / 2, 85f * scale, 1f, UITheme.surfaceLight, ringColor, 8f * scale)

        ui.endShapes()

        ui.beginBatch()

        val text = if (seconds > 0) seconds.toString() else "GO!"
        val textColor = if (seconds > 0) UITheme.warning else UITheme.primary
        ui.textCentered(text, sw / 2, sh / 2, UIFonts.display, textColor)

        ui.endBatch()
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
    }

    override fun dispose() {
        ui.dispose()
    }
}
