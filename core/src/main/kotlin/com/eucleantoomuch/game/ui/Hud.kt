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

    // Near miss notification state
    private var nearMissTimer = 0f
    private val nearMissDisplayDuration = 0.7f  // How long to show "Near miss!" text

    // Wobble screen shake state
    private var wobbleShakeX = 0f
    private var wobbleShakeY = 0f
    private var wobbleShakePhase = 0f

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

        // Update wobble screen shake
        if (euc.isWobbling && euc.wobbleIntensity > 0.01f) {
            wobbleShakePhase += Gdx.graphics.deltaTime * 25f  // Fast shake
            val shakeIntensity = euc.wobbleIntensity * 8f * scale  // Max 8 pixels shake
            wobbleShakeX = MathUtils.sin(wobbleShakePhase * 1.3f) * shakeIntensity
            wobbleShakeY = MathUtils.sin(wobbleShakePhase * 1.7f) * shakeIntensity * 0.7f
        } else {
            // Decay shake when not wobbling
            wobbleShakeX *= 0.85f
            wobbleShakeY *= 0.85f
        }

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

        // No HUD mode - skip most UI rendering, but still show FPS if enabled
        if (settingsManager.noHud) {
            // Only render FPS counter in No HUD mode if showFps is enabled
            if (settingsManager.showFps) {
                ui.beginBatch()
                val fps = Gdx.graphics.framesPerSecond
                val fpsColor = when {
                    fps >= 55 -> UITheme.accent
                    fps >= 30 -> UITheme.warning
                    else -> UITheme.danger
                }
                UIFonts.caption.color = fpsColor
                UIFonts.caption.draw(ui.batch, "FPS: $fps", 14f * scale, sh - 14f * scale)
                ui.endBatch()
            }
            return
        }

        ui.beginShapes()

        // === Speed Lines Effect ===
        if (speedEffectIntensity > 0.01f) {
            drawSpeedLines(sw, sh, scale)
        }

        // === Top Score Badge (minimalist) ===
        val scoreBadgeWidth = 180f * scale
        val scoreBadgeHeight = 70f * scale
        val scoreBadgeY = sh - scoreBadgeHeight - 20f * scale
        val scoreBadgeX = sw / 2 - scoreBadgeWidth / 2

        // Subtle glow when score changes
        if (scorePopScale > 1.01f) {
            ui.neonGlow(scoreBadgeX, scoreBadgeY, scoreBadgeWidth, scoreBadgeHeight,
                16f * scale, UITheme.accent, (scorePopScale - 1f) * 3f, 3)
        }
        ui.glassPanel(scoreBadgeX, scoreBadgeY, scoreBadgeWidth, scoreBadgeHeight,
            radius = 16f * scale, tintColor = UITheme.withAlpha(UITheme.surfaceSolid, 0.7f))

        // === Speed Panel (bottom left) ===
        drawSpeedPanel(euc)

        // === PWM Panel (above speed panel) ===
        drawPwmPanel(euc)

        // === Lean Indicator (bottom right) ===
        drawLeanIndicator(euc)


        ui.endShapes()

        // === Text ===
        ui.beginBatch()

        // Score - centered in badge, compact
        val scoreY = scoreBadgeY + scoreBadgeHeight / 2
        val originalScale = UIFonts.heading.data.scaleX
        UIFonts.heading.data.setScale(originalScale * scorePopScale)
        ui.textCentered(session.score.toString(), sw / 2, scoreY, UIFonts.heading, UITheme.accent)
        UIFonts.heading.data.setScale(originalScale)

        // Warnings - positioned lower to not obstruct view
        val warningBaseY = sh * 0.22f  // Lower on screen (22% from bottom)

        if (euc.inPuddle) {
            drawWarningBadge("SLIPPERY!", UITheme.cyan, warningBaseY + 70f * scale)
        }


        // PWM warning indicator
        if (pwmWarningFlash > 0.1f) {
            val pwmPercent = euc.getPwmPercent()
            val warningPulse = MathUtils.sin(pwmWarningFlash * 8f) * 0.5f + 0.5f
            val warningColor = UITheme.lerp(UITheme.warning, UITheme.warningBright, warningPulse)
            drawWarningBadge("PWM $pwmPercent%", warningColor, warningBaseY + 140f * scale)
        }

        // Near miss notification
        if (nearMissTimer > 0f) {
            nearMissTimer -= Gdx.graphics.deltaTime
            val alpha = (nearMissTimer / nearMissDisplayDuration).coerceIn(0f, 1f)
            val pulse = UITheme.Anim.pulse(8f, 0.8f, 1f)
            val nearMissColor = UITheme.withAlpha(UITheme.accent, alpha * pulse)
            drawWarningBadge("NEAR MISS!", nearMissColor, warningBaseY + 210f * scale)
        }

        // Wobbling warning - show when wobbling is active (at top position where DANGER was)
        if (euc.isWobbling) {
            val wobbleProgress = (euc.wobbleTimer / 3f).coerceIn(0f, 1f)  // 0-1 over 3 seconds
            val urgencyPulse = UITheme.Anim.pulse(6f + wobbleProgress * 8f, 0.7f, 1f)  // Faster pulse as time runs out
            // Color shifts from warning yellow to danger red as time runs out
            val wobbleColor = UITheme.lerp(UITheme.warning, UITheme.danger, wobbleProgress)
            val timeLeft = (3f - euc.wobbleTimer).coerceAtLeast(0f)
            val displayText = if (timeLeft < 1f) "WOBBLING!" else "WOBBLING"
            drawWarningBadge(displayText, UITheme.withAlpha(wobbleColor, urgencyPulse), warningBaseY)
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
        val panelWidth = 200f * scale
        val panelHeight = 110f * scale
        val panelX = 25f * scale
        val panelY = 25f * scale

        // Speed color based on level
        val speedColor = when {
            speedBarSmooth < 0.4f -> UITheme.primary
            speedBarSmooth < 0.7f -> UITheme.warning
            else -> UITheme.danger
        }

        // Glass panel with subtle glow at high speed
        val glowIntensity = if (speedBarSmooth > 0.7f) (speedBarSmooth - 0.7f) / 0.3f * 0.5f else 0f
        if (glowIntensity > 0) {
            ui.neonGlow(panelX, panelY, panelWidth, panelHeight, 16f * scale, speedColor, glowIntensity, 2)
        }
        ui.glassPanel(panelX, panelY, panelWidth, panelHeight,
            radius = 16f * scale, tintColor = UITheme.withAlpha(UITheme.surfaceSolid, 0.7f))

        // Speed bar with neon effect
        val barX = panelX + 14f * scale
        val barY = panelY + 14f * scale
        val barWidth = panelWidth - 28f * scale
        val barHeight = 10f * scale

        ui.neonBar(barX, barY, barWidth, barHeight, speedBarSmooth,
            backgroundColor = UITheme.withAlpha(UITheme.surfaceLight, 0.6f),
            fillColor = speedColor,
            glowIntensity = if (speedBarSmooth > 0.7f) 0.6f else 0.2f)

        // Speed text
        ui.endShapes()
        ui.beginBatch()

        val speedKmh = (euc.speed * 3.6f).toInt()
        UIFonts.heading.color = speedColor
        UIFonts.heading.draw(ui.batch, "$speedKmh", panelX + 18f * scale, panelY + panelHeight - 18f * scale)

        ui.layout.setText(UIFonts.heading, "$speedKmh")
        UIFonts.caption.color = UITheme.textMuted
        UIFonts.caption.draw(ui.batch, "km/h", panelX + 24f * scale + ui.layout.width, panelY + panelHeight - 26f * scale)

        ui.endBatch()
        ui.beginShapes()
    }

    private fun drawPwmPanel(euc: EucComponent) {
        val scale = UITheme.Dimensions.scale()
        val panelWidth = 200f * scale
        val panelHeight = 85f * scale
        val panelX = 25f * scale
        val panelY = 145f * scale  // Above speed panel

        // PWM color based on level
        val pwmColor = when {
            pwmSmooth < 0.7f -> UITheme.primary
            pwmSmooth < 0.9f -> UITheme.warning
            pwmSmooth <= 1.0f -> UITheme.lerp(UITheme.warning, UITheme.danger, (pwmSmooth - 0.9f) / 0.1f)
            else -> UITheme.danger
        }

        // Glass panel with danger glow at high PWM
        val glowIntensity = if (pwmSmooth > 0.9f) {
            ((pwmSmooth - 0.9f) / 0.1f) * UITheme.Anim.pulse(6f, 0.4f, 0.8f)
        } else 0f
        if (glowIntensity > 0) {
            ui.neonGlow(panelX, panelY, panelWidth, panelHeight, 14f * scale, pwmColor, glowIntensity, 3)
        }
        ui.glassPanel(panelX, panelY, panelWidth, panelHeight,
            radius = 14f * scale, tintColor = UITheme.withAlpha(UITheme.surfaceSolid, 0.7f))

        // PWM bar (shows up to 110%)
        val barX = panelX + 14f * scale
        val barY = panelY + 12f * scale
        val barWidth = panelWidth - 28f * scale
        val barHeight = 12f * scale

        val displayPwm = (pwmSmooth / 1.1f).coerceIn(0f, 1f)
        ui.neonBar(barX, barY, barWidth, barHeight, displayPwm,
            backgroundColor = UITheme.withAlpha(UITheme.surfaceLight, 0.6f),
            fillColor = pwmColor,
            glowIntensity = if (pwmSmooth > 0.9f) 0.7f else 0.2f)

        // Markers
        val marker90X = barX + barWidth * (0.9f / 1.1f)
        ui.shapes.color = UITheme.withAlpha(UITheme.warning, 0.6f)
        ui.shapes.rectLine(marker90X, barY - 2f, marker90X, barY + barHeight + 2f, 2f * scale)

        val marker100X = barX + barWidth * (1.0f / 1.1f)
        ui.shapes.color = UITheme.withAlpha(UITheme.danger, 0.8f)
        ui.shapes.rectLine(marker100X, barY - 3f, marker100X, barY + barHeight + 3f, 2f * scale)

        // PWM text
        ui.endShapes()
        ui.beginBatch()

        val pwmPercent = euc.getPwmPercent()
        UIFonts.body.color = pwmColor
        UIFonts.body.draw(ui.batch, "$pwmPercent%", panelX + 16f * scale, panelY + panelHeight - 14f * scale)

        ui.layout.setText(UIFonts.body, "$pwmPercent%")
        UIFonts.caption.color = UITheme.textMuted
        UIFonts.caption.draw(ui.batch, "PWM", panelX + 22f * scale + ui.layout.width, panelY + panelHeight - 18f * scale)

        ui.endBatch()
        ui.beginShapes()
    }

    private fun drawLeanIndicator(euc: EucComponent) {
        val scale = UITheme.Dimensions.scale()
        val indicatorSize = 130f * scale  // Slightly smaller
        val indicatorX = ui.screenWidth - indicatorSize - 25f * scale
        val indicatorY = 25f * scale
        val centerX = indicatorX + indicatorSize / 2
        val centerY = indicatorY + indicatorSize / 2
        val radius = indicatorSize / 2

        val totalLean = sqrt(euc.forwardLean * euc.forwardLean + euc.sideLean * euc.sideLean)

        // Danger glow around indicator when leaning too much
        if (totalLean > 0.7f) {
            val glowIntensity = (totalLean - 0.7f) / 0.3f * UITheme.Anim.pulse(5f, 0.4f, 0.8f)
            for (i in 3 downTo 1) {
                ui.shapes.color = UITheme.withAlpha(UITheme.danger, glowIntensity * 0.12f * i)
                ui.shapes.circle(centerX, centerY, radius + i * 5f * scale)
            }
        }

        // Glass background
        ui.shapes.color = UITheme.withAlpha(UITheme.surfaceSolid, 0.7f)
        ui.shapes.circle(centerX, centerY, radius)

        // Zone rings (from outside in)
        // Danger zone ring
        ui.shapes.color = UITheme.withAlpha(UITheme.danger, 0.2f)
        ui.shapes.circle(centerX, centerY, radius * 0.95f)

        // Warning zone
        ui.shapes.color = UITheme.withAlpha(UITheme.warning, 0.15f)
        ui.shapes.circle(centerX, centerY, radius * 0.7f)

        // Safe zone
        ui.shapes.color = UITheme.withAlpha(UITheme.primary, 0.15f)
        ui.shapes.circle(centerX, centerY, radius * 0.4f)

        // Cross-hair lines
        ui.shapes.color = UITheme.withAlpha(UITheme.textMuted, 0.2f)
        ui.shapes.rectLine(centerX - radius + 10f * scale, centerY,
            centerX + radius - 10f * scale, centerY, 1f * scale)
        ui.shapes.rectLine(centerX, centerY - radius + 10f * scale,
            centerX, centerY + radius - 10f * scale, 1f * scale)

        // Current lean position dot
        val dotX = centerX + euc.sideLean * radius * 0.8f
        val dotY = centerY + euc.forwardLean * radius * 0.8f

        // Dot color based on danger
        val dotColor = when {
            totalLean > 0.85f -> UITheme.danger
            totalLean > 0.6f -> UITheme.warning
            else -> UITheme.accent
        }

        // Dot with neon glow effect
        if (totalLean > 0.5f) {
            val dotGlow = (totalLean - 0.5f) / 0.5f * 0.6f
            for (i in 2 downTo 1) {
                ui.shapes.color = UITheme.withAlpha(dotColor, dotGlow * 0.3f * i)
                ui.shapes.circle(dotX, dotY, (10f + i * 4f) * scale)
            }
        }

        // Main dot
        ui.shapes.color = dotColor
        ui.shapes.circle(dotX, dotY, 10f * scale)

        // Dot highlight
        ui.shapes.color = UITheme.withAlpha(Color.WHITE, 0.5f)
        ui.shapes.circle(dotX - 3f * scale, dotY + 3f * scale, 3f * scale)

        // Border ring
        ui.shapes.end()
        ui.shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line)
        Gdx.gl.glLineWidth(2f)
        ui.shapes.color = UITheme.withAlpha(UITheme.surfaceBorder, 0.5f)
        ui.shapes.circle(centerX, centerY, radius)
        ui.shapes.end()
        ui.shapes.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled)
    }

    private fun drawWarningBadge(text: String, color: Color, y: Float) {
        val scale = UITheme.Dimensions.scale()
        val centerX = ui.screenWidth / 2
        val pulse = UITheme.Anim.pulse(4f, 0.85f, 1f)
        val glowPulse = UITheme.Anim.pulse(3f, 0.5f, 1f)

        // Measure text width for badge sizing
        ui.layout.setText(UIFonts.body, text)
        val textWidth = ui.layout.width
        val textHeight = ui.layout.height

        val badgeWidth = textWidth + 50f * scale
        val badgeHeight = textHeight + 24f * scale
        val badgeX = centerX - badgeWidth / 2
        val badgeY = y - badgeHeight / 2

        // End batch temporarily for shapes
        ui.endBatch()
        ui.beginShapes()

        // Neon glow effect
        ui.neonGlow(badgeX, badgeY, badgeWidth, badgeHeight, badgeHeight / 2, color, glowPulse * 0.7f, 3)

        // Badge with glass effect
        ui.glassPanel(badgeX, badgeY, badgeWidth, badgeHeight,
            radius = badgeHeight / 2,
            tintColor = UITheme.withAlpha(UITheme.surfaceSolid, 0.85f),
            borderGlow = color)

        ui.endShapes()
        ui.beginBatch()

        // Warning text with pulse
        UIFonts.body.color = UITheme.withAlpha(color, pulse)
        ui.textCentered(text, centerX, y, UIFonts.body, UIFonts.body.color)
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
        nearMissTimer = 0f
        wobbleShakeX = 0f
        wobbleShakeY = 0f
        wobbleShakePhase = 0f
    }

    /**
     * Trigger near miss notification when player passes close to pedestrian.
     */
    fun triggerNearMiss() {
        nearMissTimer = nearMissDisplayDuration
    }

    /**
     * Get current screen shake offset for camera.
     * Returns Pair(x, y) offset in screen pixels.
     */
    fun getScreenShake(): Pair<Float, Float> = Pair(wobbleShakeX, wobbleShakeY)

    /**
     * Render camera mode indicator when switching views.
     * @param modeName Name of the camera mode ("Normal", "Close", "First Person")
     * @param alpha Fade alpha (1 = full, 0 = invisible)
     */
    fun renderCameraMode(modeName: String, alpha: Float) {
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val scale = UITheme.Dimensions.scale()

        // Position at bottom center of screen
        val y = sh * 0.15f

        ui.beginShapes()

        // Semi-transparent background pill
        ui.layout.setText(UIFonts.heading, modeName)
        val textWidth = ui.layout.width
        val pillWidth = textWidth + 60f * scale
        val pillHeight = 50f * scale
        val pillX = sw / 2 - pillWidth / 2
        val pillY = y - pillHeight / 2

        ui.roundedRect(pillX, pillY, pillWidth, pillHeight, 25f * scale,
            UITheme.withAlpha(UITheme.surface, 0.85f * alpha))

        ui.endShapes()

        ui.beginBatch()

        // Camera mode text
        ui.textCentered(modeName, sw / 2, y, UIFonts.heading, UITheme.withAlpha(UITheme.textPrimary, alpha))

        ui.endBatch()
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
