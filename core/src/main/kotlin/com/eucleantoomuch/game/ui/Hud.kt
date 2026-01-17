package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.ecs.components.EucComponent
import com.eucleantoomuch.game.state.GameSession
import com.eucleantoomuch.game.state.SettingsManager
import kotlin.math.sqrt

class Hud(private val settingsManager: SettingsManager) : Disposable {
    private val ui = UIRenderer()

    // Animation states
    private var scorePopScale = 1f
    private var lastScore = 0
    private var warningFlash = 0f
    private var speedBarSmooth = 0f

    fun render(session: GameSession, euc: EucComponent) {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight

        // Score pop animation
        if (session.score != lastScore) {
            scorePopScale = 1.3f
            lastScore = session.score
        }
        scorePopScale = UITheme.Anim.ease(scorePopScale, 1f, 8f)

        // Smooth speed bar
        val targetSpeed = (euc.speed / 25f).coerceIn(0f, 1f)
        speedBarSmooth = UITheme.Anim.ease(speedBarSmooth, targetSpeed, 5f)

        // Warning flash
        if (euc.isAboutToFall()) {
            warningFlash += Gdx.graphics.deltaTime * 8f
        } else {
            warningFlash = UITheme.Anim.ease(warningFlash, 0f, 5f)
        }

        val scale = UITheme.Dimensions.scale()

        ui.beginShapes()

        // === Top Score Panel ===
        val topPanelWidth = 280f * scale
        val topPanelHeight = 100f * scale
        val topPanelY = sh - topPanelHeight - 20f * scale
        ui.roundedRect(sw / 2 - topPanelWidth / 2, topPanelY, topPanelWidth, topPanelHeight,
            16f * scale, UITheme.withAlpha(UITheme.surface, 0.9f))

        // === Speed Panel (bottom left) ===
        drawSpeedPanel(euc)

        // === Lean Indicator (bottom right) ===
        drawLeanIndicator(euc)

        // === Warning Overlay ===
        if (warningFlash > 0.1f) {
            val flashAlpha = (MathUtils.sin(warningFlash * 3f) * 0.5f + 0.5f) * 0.15f
            ui.shapes.color = UITheme.withAlpha(UITheme.danger, flashAlpha)
            ui.shapes.rect(0f, 0f, sw, sh)

            // Red vignette at edges
            ui.shapes.color = UITheme.withAlpha(UITheme.danger, flashAlpha * 2)
            ui.shapes.rect(0f, 0f, 20f, sh)
            ui.shapes.rect(sw - 20f, 0f, 20f, sh)
            ui.shapes.rect(0f, 0f, sw, 20f)
            ui.shapes.rect(0f, sh - 20f, sw, 20f)
        }

        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        // Score
        val scoreLabelY = topPanelY + topPanelHeight - 25f * scale
        val scoreValueY = scoreLabelY - 40f * scale
        UIFonts.caption.color = UITheme.textSecondary
        ui.textCentered("SCORE", sw / 2, scoreLabelY, UIFonts.caption, UITheme.textSecondary)

        // Score value with pop effect
        val originalScale = UIFonts.title.data.scaleX
        UIFonts.title.data.setScale(originalScale * scorePopScale)
        ui.textCentered(session.score.toString(), sw / 2, scoreValueY, UIFonts.title, UITheme.textPrimary)
        UIFonts.title.data.setScale(originalScale)

        // Warnings
        if (euc.inPuddle) {
            drawWarningBadge("SLIPPERY!", UITheme.cyan, sh / 2 + 30)
        }

        if (euc.isAboutToFall()) {
            val dangerPulse = UITheme.Anim.pulse(6f, 0.7f, 1f)
            drawWarningBadge("!! DANGER !!", UITheme.lerp(UITheme.danger, UITheme.warningBright, dangerPulse), sh / 2 - 30)
        }

        // FPS counter (top-left, small)
        if (settingsManager.showFps) {
            val fps = Gdx.graphics.framesPerSecond
            val fpsColor = when {
                fps >= 55 -> UITheme.primary
                fps >= 30 -> UITheme.warning
                else -> UITheme.danger
            }
            UIFonts.caption.color = fpsColor
            UIFonts.caption.draw(ui.batch, "FPS: $fps", 10f * scale, sh - 10f * scale)
        }

        ui.endBatch()
    }

    private fun drawSpeedPanel(euc: EucComponent) {
        val scale = UITheme.Dimensions.scale()
        val panelWidth = 200f * scale
        val panelHeight = 110f * scale
        val panelX = 25f * scale
        val panelY = 25f * scale

        // Panel background
        ui.roundedRect(panelX, panelY, panelWidth, panelHeight, 14f, UITheme.withAlpha(UITheme.surface, 0.9f))

        // Speed bar background
        val barX = panelX + 12
        val barY = panelY + 12
        val barWidth = panelWidth - 24
        val barHeight = 8f

        ui.roundedRect(barX, barY, barWidth, barHeight, 4f, UITheme.surfaceLight)

        // Speed bar fill with color gradient
        val speedColor = when {
            speedBarSmooth < 0.4f -> UITheme.primary
            speedBarSmooth < 0.7f -> UITheme.warning
            else -> UITheme.danger
        }
        val fillWidth = (barWidth * speedBarSmooth).coerceAtLeast(8f)
        ui.roundedRect(barX, barY, fillWidth, barHeight, 4f, speedColor)

        // Glow effect at high speed
        if (speedBarSmooth > 0.7f) {
            val glowAlpha = (speedBarSmooth - 0.7f) / 0.3f * UITheme.Anim.pulse(4f, 0.3f, 0.6f)
            ui.roundedRect(barX, barY - 2, fillWidth, barHeight + 4, 5f, UITheme.withAlpha(speedColor, glowAlpha))
        }

        // Speed text
        ui.endShapes()
        ui.beginBatch()

        val speedKmh = (euc.speed * 3.6f).toInt()
        UIFonts.title.color = UITheme.textPrimary
        UIFonts.title.draw(ui.batch, "$speedKmh", panelX + 20, panelY + panelHeight - 18)

        ui.layout.setText(UIFonts.title, "$speedKmh")
        UIFonts.caption.color = UITheme.textSecondary
        UIFonts.caption.draw(ui.batch, "km/h", panelX + 25 + ui.layout.width, panelY + panelHeight - 28)

        ui.endBatch()
        ui.beginShapes()
    }

    private fun drawLeanIndicator(euc: EucComponent) {
        val scale = UITheme.Dimensions.scale()
        val indicatorSize = 130f * scale
        val indicatorX = ui.screenWidth - indicatorSize - 25f * scale
        val indicatorY = 25f * scale
        val centerX = indicatorX + indicatorSize / 2
        val centerY = indicatorY + indicatorSize / 2

        // Shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.3f)
        ui.shapes.circle(centerX + 2, centerY - 2, indicatorSize / 2 + 2)

        // Background with zones
        ui.shapes.color = UITheme.withAlpha(UITheme.surface, 0.95f)
        ui.shapes.circle(centerX, centerY, indicatorSize / 2)

        // Danger zone (outer red)
        ui.shapes.color = UITheme.withAlpha(UITheme.danger, 0.25f)
        ui.shapes.circle(centerX, centerY, indicatorSize / 2 - 3)

        // Warning zone (yellow)
        ui.shapes.color = UITheme.withAlpha(UITheme.warning, 0.2f)
        ui.shapes.circle(centerX, centerY, indicatorSize / 2 * 0.75f)

        // Safe zone (green center)
        ui.shapes.color = UITheme.withAlpha(UITheme.primary, 0.25f)
        ui.shapes.circle(centerX, centerY, indicatorSize / 2 * 0.5f)

        // Grid lines
        ui.shapes.color = UITheme.withAlpha(UITheme.textMuted, 0.2f)
        ui.shapes.rectLine(centerX - indicatorSize / 2 + 8, centerY,
            centerX + indicatorSize / 2 - 8, centerY, 1f)
        ui.shapes.rectLine(centerX, centerY - indicatorSize / 2 + 8,
            centerX, centerY + indicatorSize / 2 - 8, 1f)

        // Current lean position
        val totalLean = sqrt(euc.forwardLean * euc.forwardLean + euc.sideLean * euc.sideLean)
        val dotX = centerX + euc.sideLean * indicatorSize / 2 * 0.8f
        val dotY = centerY + euc.forwardLean * indicatorSize / 2 * 0.8f

        // Dot color based on danger
        val dotColor = when {
            totalLean > 0.85f -> UITheme.danger
            totalLean > 0.6f -> UITheme.warning
            else -> UITheme.textPrimary
        }

        // Dot shadow
        ui.shapes.color = UITheme.withAlpha(Color.BLACK, 0.4f)
        ui.shapes.circle(dotX + 1.5f, dotY - 1.5f, 10f)

        // Main dot
        ui.shapes.color = dotColor
        ui.shapes.circle(dotX, dotY, 9f)

        // Dot highlight
        ui.shapes.color = UITheme.withAlpha(Color.WHITE, 0.4f)
        ui.shapes.circle(dotX - 2.5f, dotY + 2.5f, 3f)

        // Danger glow when leaning too much
        if (totalLean > 0.7f) {
            val glowIntensity = (totalLean - 0.7f) / 0.3f * UITheme.Anim.pulse(5f, 0.3f, 0.7f)
            ui.shapes.color = UITheme.withAlpha(UITheme.danger, glowIntensity * 0.3f)
            ui.shapes.circle(dotX, dotY, 15f)
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

        ui.beginShapes()

        // Dark circle background
        ui.shapes.color = UITheme.withAlpha(UITheme.surface, 0.9f)
        ui.shapes.circle(sw / 2, sh / 2, 80f)

        // Ring
        val ringColor = if (seconds > 0) UITheme.warning else UITheme.primary
        ui.gauge(sw / 2, sh / 2, 70f, 1f, UITheme.surfaceLight, ringColor, 6f)

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
