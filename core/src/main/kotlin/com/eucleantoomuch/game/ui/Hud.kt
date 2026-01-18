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

    // Speed effect state
    private var speedEffectIntensity = 0f
    private val speedLines = mutableListOf<SpeedLine>()
    private var speedLineTimer = 0f
    private var speedEffectTurnOffset = 0f  // Horizontal offset based on turning

    // Speed line data class - radial lines from edges toward center (tunnel effect)
    private data class SpeedLine(
        val edgeX: Float,    // Position on screen edge (0-1 normalized)
        val edgeY: Float,    // Position on screen edge (0-1 normalized)
        var progress: Float, // 0 = at edge, 1 = reached center area
        val speed: Float,    // How fast it moves (progress per second)
        val alpha: Float,    // Base transparency
        val side: Int        // 0=top, 1=right, 2=bottom, 3=left
    )

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

        // Speed effect - starts at 70 km/h, full intensity at 100+ km/h
        val speedKmh = euc.speed * 3.6f
        val targetIntensity = ((speedKmh - 70f) / 30f).coerceIn(0f, 1f)
        speedEffectIntensity = UITheme.Anim.ease(speedEffectIntensity, targetIntensity, 4f)

        // Speed effect turns with rider - sideLean affects center offset
        val targetTurnOffset = euc.sideLean * 0.15f  // Max 15% screen offset
        speedEffectTurnOffset = UITheme.Anim.ease(speedEffectTurnOffset, targetTurnOffset, 6f)

        // Update speed lines
        updateSpeedLines(sw, sh, scale)

        ui.beginShapes()

        // === Speed Lines Effect ===
        if (speedEffectIntensity > 0.01f) {
            drawSpeedLines(sw, sh, scale)
        }

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

        // Warnings - positioned lower to not obstruct view
        val warningBaseY = sh * 0.22f  // Lower on screen (22% from bottom)

        if (euc.inPuddle) {
            drawWarningBadge("SLIPPERY!", UITheme.cyan, warningBaseY + 70f * scale)
        }

        if (euc.isAboutToFall()) {
            val dangerPulse = UITheme.Anim.pulse(6f, 0.7f, 1f)
            drawWarningBadge("!! DANGER !!", UITheme.lerp(UITheme.danger, UITheme.warningBright, dangerPulse), warningBaseY)
        }

        // PWM warning indicator
        if (pwmWarningFlash > 0.1f) {
            val pwmPercent = euc.getPwmPercent()
            val warningPulse = MathUtils.sin(pwmWarningFlash * 8f) * 0.5f + 0.5f
            val warningColor = UITheme.lerp(UITheme.warning, UITheme.warningBright, warningPulse)
            drawWarningBadge("PWM $pwmPercent%", warningColor, warningBaseY + 140f * scale)
        }

        // FPS counter (top-left, visible but unobtrusive)
        if (settingsManager.showFps) {
            val fps = Gdx.graphics.framesPerSecond
            val fpsColor = when {
                fps >= 55 -> UITheme.accent
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

        // Safe zone (accent center)
        ui.shapes.color = UITheme.withAlpha(UITheme.accent, 0.22f)
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
        val scale = UITheme.Dimensions.scale()
        val centerX = ui.screenWidth / 2
        val pulse = UITheme.Anim.pulse(4f, 0.85f, 1f)
        val glowPulse = UITheme.Anim.pulse(3f, 0.4f, 0.8f)

        // Measure text width for badge sizing
        ui.layout.setText(UIFonts.heading, text)
        val textWidth = ui.layout.width
        val textHeight = ui.layout.height

        val badgeWidth = textWidth + 60f * scale
        val badgeHeight = textHeight + 30f * scale
        val badgeX = centerX - badgeWidth / 2
        val badgeY = y - badgeHeight / 2

        // End batch temporarily for shapes
        ui.endBatch()
        ui.beginShapes()

        // Outer glow
        for (i in 3 downTo 1) {
            ui.shapes.color = UITheme.withAlpha(color, glowPulse * 0.1f * i)
            ui.roundedRect(badgeX - i * 6f, badgeY - i * 6f,
                badgeWidth + i * 12f, badgeHeight + i * 12f, 20f * scale, ui.shapes.color)
        }

        // Badge background
        ui.shapes.color = UITheme.withAlpha(UITheme.surface, 0.92f)
        ui.roundedRect(badgeX, badgeY, badgeWidth, badgeHeight, 16f * scale, ui.shapes.color)

        // Accent border
        val borderThickness = 3f * scale
        ui.shapes.color = UITheme.withAlpha(color, pulse)
        // Top border
        ui.shapes.rect(badgeX + 16f * scale, badgeY + badgeHeight - borderThickness, badgeWidth - 32f * scale, borderThickness)
        // Bottom border
        ui.shapes.rect(badgeX + 16f * scale, badgeY, badgeWidth - 32f * scale, borderThickness)

        ui.endShapes()
        ui.beginBatch()

        // Warning text with pulse
        UIFonts.heading.color = UITheme.withAlpha(color, pulse)
        ui.textCentered(text, centerX, y, UIFonts.heading, UIFonts.heading.color)
    }

    fun renderCountdown(seconds: Int) {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val scale = UITheme.Dimensions.scale()
        val centerX = sw / 2
        val centerY = sh / 2

        // Pulsing animation
        val pulse = UITheme.Anim.pulse(3f, 0.85f, 1f)
        val glowPulse = UITheme.Anim.pulse(4f, 0.3f, 0.7f)

        ui.beginShapes()

        // Outer glow ring
        val mainColor = if (seconds > 0) UITheme.accent else UITheme.accent
        for (i in 4 downTo 1) {
            ui.shapes.color = UITheme.withAlpha(mainColor, glowPulse * 0.08f * i)
            ui.shapes.circle(centerX, centerY, (110f + i * 8f) * scale)
        }

        // Dark circle background
        ui.shapes.color = UITheme.withAlpha(UITheme.surface, 0.95f)
        ui.shapes.circle(centerX, centerY, 110f * scale)

        // Accent ring - thicker and more prominent
        ui.shapes.color = mainColor
        val ringRadius = 95f * scale
        val ringThickness = 10f * scale
        val segments = 48
        for (i in 0 until segments) {
            val angle1 = (i.toFloat() / segments) * com.badlogic.gdx.math.MathUtils.PI2
            val angle2 = ((i + 1).toFloat() / segments) * com.badlogic.gdx.math.MathUtils.PI2
            val innerR = ringRadius - ringThickness / 2
            val outerR = ringRadius + ringThickness / 2
            ui.shapes.rectLine(
                centerX + innerR * com.badlogic.gdx.math.MathUtils.cos(angle1),
                centerY + innerR * com.badlogic.gdx.math.MathUtils.sin(angle1),
                centerX + innerR * com.badlogic.gdx.math.MathUtils.cos(angle2),
                centerY + innerR * com.badlogic.gdx.math.MathUtils.sin(angle2),
                ringThickness
            )
        }

        // Inner decorative ring
        ui.shapes.color = UITheme.withAlpha(UITheme.surfaceLight, 0.5f)
        val innerRingRadius = 70f * scale
        for (i in 0 until segments) {
            val angle1 = (i.toFloat() / segments) * com.badlogic.gdx.math.MathUtils.PI2
            val angle2 = ((i + 1).toFloat() / segments) * com.badlogic.gdx.math.MathUtils.PI2
            ui.shapes.rectLine(
                centerX + innerRingRadius * com.badlogic.gdx.math.MathUtils.cos(angle1),
                centerY + innerRingRadius * com.badlogic.gdx.math.MathUtils.sin(angle1),
                centerX + innerRingRadius * com.badlogic.gdx.math.MathUtils.cos(angle2),
                centerY + innerRingRadius * com.badlogic.gdx.math.MathUtils.sin(angle2),
                3f * scale
            )
        }

        ui.endShapes()

        ui.beginBatch()

        // Countdown number or GO!
        val text = if (seconds > 0) seconds.toString() else "GO!"
        val textColor = mainColor

        // Scale text with pulse for emphasis
        val textScale = if (seconds > 0) pulse else 1.1f
        UIFonts.display.data.setScale(UIFonts.display.data.scaleX * textScale)
        ui.textCentered(text, centerX, centerY, UIFonts.display, textColor)
        UIFonts.display.data.setScale(UIFonts.display.data.scaleX / textScale)

        // "GET READY" label above countdown - larger and more visible
        if (seconds > 0) {
            ui.textCentered("GET READY", centerX, centerY + 160f * scale, UIFonts.heading, UITheme.textPrimary)
        }

        ui.endBatch()
    }

    private fun updateSpeedLines(sw: Float, sh: Float, scale: Float) {
        val delta = Gdx.graphics.deltaTime

        // Spawn new lines based on intensity
        if (speedEffectIntensity > 0.01f) {
            speedLineTimer += delta * speedEffectIntensity * 60f  // More lines at higher speed

            while (speedLineTimer > 1f) {
                speedLineTimer -= 1f

                // Spawn lines on all 4 edges, but more on left/right
                val side = when (MathUtils.random(0, 9)) {
                    in 0..3 -> 3  // Left (40%)
                    in 4..7 -> 1  // Right (40%)
                    8 -> 0        // Top (10%)
                    else -> 2     // Bottom (10%)
                }

                // Position along that edge (0-1)
                val edgePos = MathUtils.random(0.1f, 0.9f)

                // Calculate actual edge coordinates based on side
                val (edgeX, edgeY) = when (side) {
                    0 -> Pair(edgePos, 1f)  // Top edge
                    1 -> Pair(1f, edgePos)  // Right edge
                    2 -> Pair(edgePos, 0f)  // Bottom edge
                    else -> Pair(0f, edgePos)  // Left edge
                }

                speedLines.add(SpeedLine(
                    edgeX = edgeX,
                    edgeY = edgeY,
                    progress = 0f,
                    speed = MathUtils.random(0.8f, 1.5f) * (0.7f + speedEffectIntensity * 0.5f),
                    alpha = MathUtils.random(0.4f, 0.8f) * speedEffectIntensity,
                    side = side
                ))
            }
        }

        // Update existing lines - move toward center
        val iterator = speedLines.iterator()
        while (iterator.hasNext()) {
            val line = iterator.next()
            line.progress += line.speed * delta

            // Remove lines that reached center area
            if (line.progress > 0.7f) {
                iterator.remove()
            }
        }

        // Limit max lines
        while (speedLines.size > 100) {
            speedLines.removeAt(0)
        }
    }

    private fun drawSpeedLines(sw: Float, sh: Float, scale: Float) {
        // Center shifts based on turning direction
        val centerX = sw / 2f + sw * speedEffectTurnOffset
        val centerY = sh / 2f

        // Lines start at 40% from center (not from center itself)
        val startOffset = 0.4f

        for (line in speedLines) {
            // Convert edge position to screen coordinates
            val edgeX = line.edgeX * sw
            val edgeY = line.edgeY * sh

            // Calculate direction from center toward edge
            val dirX = edgeX - centerX
            val dirY = edgeY - centerY

            // Remap progress: 0 = at startOffset, 1 = at edge
            val mappedProgress = startOffset + line.progress * (1f - startOffset)
            val mappedTailProgress = startOffset + (line.progress - 0.15f).coerceAtLeast(0f) * (1f - startOffset)

            // Current position along the path
            val currentX = centerX + dirX * mappedProgress
            val currentY = centerY + dirY * mappedProgress

            // Line tail is behind
            val tailX = centerX + dirX * mappedTailProgress
            val tailY = centerY + dirY * mappedTailProgress

            // Fade: brighten as approaching edge
            val fadeAlpha = line.progress / 0.7f
            val alpha = line.alpha * fadeAlpha

            // Line gets thicker as it approaches edge (perspective effect)
            val thickness = (1.5f + line.progress * 4f) * scale

            ui.shapes.color = UITheme.withAlpha(Color.WHITE, alpha)
            ui.shapes.rectLine(tailX, tailY, currentX, currentY, thickness)
        }
    }

    fun reset() {
        scorePopScale = 1f
        lastScore = 0
        warningFlash = 0f
        speedBarSmooth = 0f
        pwmWarningFlash = 0f
        speedEffectIntensity = 0f
        speedLines.clear()
        speedLineTimer = 0f
        speedEffectTurnOffset = 0f
        pwmSmooth = 0f
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
