package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern UI renderer with advanced drawing capabilities
 */
class UIRenderer : Disposable {
    var batch = SpriteBatch()
        private set
    var shapes = ShapeRenderer()
        private set
    val layout = GlyphLayout()

    var screenWidth = Gdx.graphics.width.toFloat()
        private set
    var screenHeight = Gdx.graphics.height.toFloat()
        private set

    fun resize(width: Int, height: Int) {
        screenWidth = width.toFloat()
        screenHeight = height.toFloat()
        batch.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapes.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
    }

    /** Force recreation of batch and shapes - call after GL context loss */
    @Suppress("TooGenericExceptionCaught")
    fun recreate() {
        Gdx.app.log("UIRenderer", "Recreating batch and shapes after context loss")
        try { batch.dispose() } catch (_: Exception) { /* ignore */ }
        try { shapes.dispose() } catch (_: Exception) { /* ignore */ }
        batch = SpriteBatch()
        shapes = ShapeRenderer()
        batch.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
        shapes.projectionMatrix.setToOrtho2D(0f, 0f, screenWidth, screenHeight)
    }

    fun beginShapes(type: ShapeRenderer.ShapeType = ShapeRenderer.ShapeType.Filled) {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.begin(type)
    }

    fun endShapes() {
        shapes.end()
    }

    fun beginBatch() {
        batch.begin()
    }

    fun endBatch() {
        batch.end()
    }

    // === Advanced Shape Drawing ===

    /** Draw a rounded rectangle */
    fun roundedRect(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
        shapes.color = color
        val r = radius.coerceAtMost(minOf(width, height) / 2f)

        // Main body
        shapes.rect(x + r, y, width - 2 * r, height)
        shapes.rect(x, y + r, width, height - 2 * r)

        // Corners
        shapes.circle(x + r, y + r, r)
        shapes.circle(x + width - r, y + r, r)
        shapes.circle(x + r, y + height - r, r)
        shapes.circle(x + width - r, y + height - r, r)
    }

    /** Draw a rounded rectangle with gradient (top to bottom) */
    @Suppress("unused")
    fun roundedRectGradient(
        x: Float, y: Float, width: Float, height: Float, radius: Float,
        topColor: Color, bottomColor: Color
    ) {
        val r = radius.coerceAtMost(minOf(width, height) / 2f)
        val segments = 16

        // Draw gradient using multiple horizontal strips
        val stripHeight = height / segments
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val stripY = y + i * stripHeight
            val color = UITheme.lerp(bottomColor, topColor, t)
            shapes.color = color

            when (i) {
                0 -> {
                    // Bottom strip with rounded corners
                    shapes.rect(x + r, stripY, width - 2 * r, stripHeight)
                    shapes.rect(x, stripY + r.coerceAtMost(stripHeight), width, (stripHeight - r).coerceAtLeast(0f))
                }
                segments - 1 -> {
                    // Top strip with rounded corners
                    shapes.rect(x + r, stripY, width - 2 * r, stripHeight)
                    shapes.rect(x, stripY, width, (stripHeight - r).coerceAtLeast(0f))
                }
                else -> {
                    shapes.rect(x, stripY, width, stripHeight)
                }
            }
        }

        // Corners
        shapes.color = bottomColor
        shapes.circle(x + r, y + r, r)
        shapes.circle(x + width - r, y + r, r)
        shapes.color = topColor
        shapes.circle(x + r, y + height - r, r)
        shapes.circle(x + width - r, y + height - r, r)
    }

    /** Draw a panel with shadow and optional border */
    fun panel(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float = UITheme.Dimensions.panelRadius,
        shadowOffset: Float = UITheme.Dimensions.shadowOffset,
        backgroundColor: Color = UITheme.surface,
        borderColor: Color? = null
    ) {
        // Shadow
        if (shadowOffset > 0) {
            roundedRect(
                x + shadowOffset, y - shadowOffset,
                width, height, radius,
                UITheme.withAlpha(Color.BLACK, 0.4f)
            )
        }

        // Background
        roundedRect(x, y, width, height, radius, backgroundColor)

        // Border
        if (borderColor != null) {
            shapes.end()
            shapes.begin(ShapeRenderer.ShapeType.Line)
            Gdx.gl.glLineWidth(2f)
            roundedRectOutline(x, y, width, height, radius, borderColor)
            shapes.end()
            shapes.begin(ShapeRenderer.ShapeType.Filled)
        }
    }

    /** Draw rounded rectangle outline */
    fun roundedRectOutline(x: Float, y: Float, width: Float, height: Float, radius: Float, color: Color) {
        shapes.color = color
        val r = radius.coerceAtMost(minOf(width, height) / 2f)

        // Lines
        shapes.line(x + r, y, x + width - r, y)
        shapes.line(x + r, y + height, x + width - r, y + height)
        shapes.line(x, y + r, x, y + height - r)
        shapes.line(x + width, y + r, x + width, y + height - r)

        // Corner arcs
        drawArc(x + r, y + r, r, 180f, 270f, color)
        drawArc(x + width - r, y + r, r, 270f, 360f, color)
        drawArc(x + width - r, y + height - r, r, 0f, 90f, color)
        drawArc(x + r, y + height - r, r, 90f, 180f, color)
    }

    private fun drawArc(cx: Float, cy: Float, radius: Float, startAngle: Float, endAngle: Float, color: Color) {
        shapes.color = color
        val segments = 8
        val angleStep = (endAngle - startAngle) / segments

        for (i in 0 until segments) {
            val angle1 = Math.toRadians((startAngle + i * angleStep).toDouble())
            val angle2 = Math.toRadians((startAngle + (i + 1) * angleStep).toDouble())
            shapes.line(
                cx + radius * cos(angle1).toFloat(),
                cy + radius * sin(angle1).toFloat(),
                cx + radius * cos(angle2).toFloat(),
                cy + radius * sin(angle2).toFloat()
            )
        }
    }

    /** Draw a modern button with 3D effect and smooth glow */
    fun button(
        rect: Rectangle,
        color: Color,
        pressedOffset: Float = 0f,
        glowIntensity: Float = 0f
    ) {
        val x = rect.x
        val y = rect.y - pressedOffset
        val radius = UITheme.Dimensions.buttonRadius
        val scale = UITheme.Dimensions.scale()

        // Enhanced glow effect for hover
        if (glowIntensity > 0) {
            val glowColor = UITheme.withAlpha(color, 0.35f * glowIntensity)
            for (i in 4 downTo 1) {
                roundedRect(
                    x - i * 3f * scale, y - i * 3f * scale,
                    rect.width + i * 6f * scale, rect.height + i * 6f * scale,
                    radius + i * 3f * scale,
                    UITheme.withAlpha(glowColor, glowIntensity * 0.12f * i)
                )
            }
        }

        // Deeper shadow for more depth
        val shadowOffset = (6f * scale - pressedOffset * 2).coerceAtLeast(0f)
        if (shadowOffset > 0) {
            roundedRect(
                x + 3f * scale, y - shadowOffset,
                rect.width, rect.height, radius,
                UITheme.withAlpha(Color.BLACK, 0.4f)
            )
        }

        // Button base (darker) for 3D depth
        roundedRect(x, y - 4f * scale + pressedOffset, rect.width, rect.height, radius, UITheme.darken(color, 0.18f))

        // Button main body
        roundedRect(x, y, rect.width, rect.height, radius, color)

        // Top highlight for glass effect
        shapes.color = UITheme.withAlpha(Color.WHITE, 0.12f)
        roundedRect(x + 6f * scale, y + rect.height - 16f * scale, rect.width - 12f * scale, 8f * scale, 4f * scale, shapes.color)
    }

    /** Draw a circular gauge/indicator */
    @Suppress("unused")
    fun gauge(
        cx: Float, cy: Float, radius: Float,
        value: Float, // 0-1
        backgroundColor: Color = UITheme.surfaceLight,
        fillColor: Color = UITheme.primary,
        thickness: Float = 8f
    ) {
        val segments = 32

        // Background arc
        shapes.color = backgroundColor
        drawThickArc(cx, cy, radius, 0f, 360f, thickness, segments)

        // Fill arc
        shapes.color = fillColor
        drawThickArc(cx, cy, radius, 90f, 90f - 360f * value, thickness, (segments * value).toInt().coerceAtLeast(2))
    }

    private fun drawThickArc(cx: Float, cy: Float, radius: Float, startAngle: Float, endAngle: Float, thickness: Float, segments: Int) {
        val innerRadius = radius - thickness / 2
        val outerRadius = radius + thickness / 2

        val start = Math.toRadians(startAngle.toDouble())
        val end = Math.toRadians(endAngle.toDouble())
        val step = (end - start) / segments

        for (i in 0 until segments) {
            val angle1 = start + i * step
            val angle2 = start + (i + 1) * step

            val x1i = cx + innerRadius * cos(angle1).toFloat()
            val y1i = cy + innerRadius * sin(angle1).toFloat()
            val x1o = cx + outerRadius * cos(angle1).toFloat()
            val y1o = cy + outerRadius * sin(angle1).toFloat()
            val x2i = cx + innerRadius * cos(angle2).toFloat()
            val y2i = cy + innerRadius * sin(angle2).toFloat()
            val x2o = cx + outerRadius * cos(angle2).toFloat()
            val y2o = cy + outerRadius * sin(angle2).toFloat()

            shapes.triangle(x1i, y1i, x1o, y1o, x2o, y2o)
            shapes.triangle(x1i, y1i, x2o, y2o, x2i, y2i)
        }
    }

    /** Draw a progress bar */
    @Suppress("unused")
    fun progressBar(
        x: Float, y: Float, width: Float, height: Float,
        progress: Float, // 0-1
        backgroundColor: Color = UITheme.surfaceLight,
        fillColor: Color = UITheme.primary,
        radius: Float = 4f
    ) {
        // Background
        roundedRect(x, y, width, height, radius, backgroundColor)

        // Fill
        val fillWidth = (width * progress).coerceAtLeast(radius * 2)
        if (progress > 0) {
            roundedRect(x, y, fillWidth, height, radius, fillColor)
        }
    }

    /** Draw centered text */
    fun textCentered(
        text: String,
        x: Float, y: Float,
        font: com.badlogic.gdx.graphics.g2d.BitmapFont,
        color: Color
    ) {
        font.color = color
        layout.setText(font, text)
        font.draw(batch, text, x - layout.width / 2, y + layout.height / 2)
    }

    /** Draw text with shadow */
    @Suppress("unused")
    fun textWithShadow(
        text: String,
        x: Float, y: Float,
        font: com.badlogic.gdx.graphics.g2d.BitmapFont,
        color: Color,
        shadowColor: Color = UITheme.withAlpha(Color.BLACK, 0.5f),
        shadowOffset: Float = 2f
    ) {
        layout.setText(font, text)
        font.color = shadowColor
        font.draw(batch, text, x + shadowOffset, y - shadowOffset)
        font.color = color
        font.draw(batch, text, x, y)
    }

    // === NEW: Neon Street Style Components ===

    /** DEPRECATED: Glow removed for clean look. Does nothing now. */
    @Suppress("UNUSED_PARAMETER")
    fun neonGlow(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float, glowColor: Color, intensity: Float = 1f, layers: Int = 4
    ) {
        // No glow - clean look
    }

    /** Draw a clean modern card with gradient - NO GLOW */
    fun card(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float = UITheme.Dimensions.panelRadius,
        backgroundColor: Color = UITheme.surfaceSolid,
        @Suppress("UNUSED_PARAMETER") glowColor: Color? = null,
        @Suppress("UNUSED_PARAMETER") glowIntensity: Float = 0.5f
    ) {
        val scale = UITheme.Dimensions.scale()

        // Clean shadow (no glow)
        roundedRect(
            x + 3f * scale, y - 6f * scale,
            width, height, radius,
            UITheme.withAlpha(Color.BLACK, 0.35f)
        )

        // Gradient background (darker at bottom, lighter at top)
        val bottomColor = UITheme.darken(backgroundColor, 0.06f)
        val topColor = UITheme.brighten(backgroundColor, 0.04f)
        val segments = 5
        val segHeight = height / segments
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val segColor = UITheme.lerp(bottomColor, topColor, t)
            val segY = y + i * segHeight
            if (i == 0 || i == segments - 1) {
                roundedRect(x, segY, width, segHeight + 1, radius, segColor)
            } else {
                shapes.color = segColor
                shapes.rect(x, segY, width, segHeight + 1)
            }
        }

        // Subtle top edge highlight for depth
        shapes.color = UITheme.withAlpha(Color.WHITE, 0.1f)
        roundedRect(x + radius, y + height - 2f * scale, width - radius * 2, 2f * scale, 1f, shapes.color)
    }

    /** Draw a clean modern panel with gradient - NO GLOW */
    fun glassPanel(
        x: Float, y: Float, width: Float, height: Float,
        radius: Float = UITheme.Dimensions.panelRadius,
        tintColor: Color = UITheme.surfaceGlass,
        @Suppress("UNUSED_PARAMETER") borderGlow: Color? = null
    ) {
        val scale = UITheme.Dimensions.scale()

        // Clean shadow (no glow)
        roundedRect(
            x + 4f * scale, y - 8f * scale,
            width, height, radius,
            UITheme.withAlpha(Color.BLACK, 0.4f)
        )

        // Panel gradient background (darker at bottom, lighter at top)
        val baseColor = if (tintColor.a < 0.5f) UITheme.surfaceSolid else tintColor
        val bottomColor = UITheme.darken(baseColor, 0.08f)
        val topColor = UITheme.brighten(baseColor, 0.04f)
        val segments = 6
        val segHeight = height / segments
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val segColor = UITheme.lerp(bottomColor, topColor, t)
            val segY = y + i * segHeight
            if (i == 0 || i == segments - 1) {
                roundedRect(x, segY, width, segHeight + 1, radius, segColor)
            } else {
                shapes.color = segColor
                shapes.rect(x, segY, width, segHeight + 1)
            }
        }

        // Subtle top edge highlight for depth
        shapes.color = UITheme.withAlpha(Color.WHITE, 0.1f)
        roundedRect(x + radius / 2, y + height - 3f * scale, width - radius, 3f * scale, 1.5f, shapes.color)

        // Clean border outline
        shapes.end()
        shapes.begin(ShapeRenderer.ShapeType.Line)
        Gdx.gl.glLineWidth(1.5f)
        roundedRectOutline(x, y, width, height, radius, UITheme.withAlpha(Color.WHITE, 0.08f))
        shapes.end()
        shapes.begin(ShapeRenderer.ShapeType.Filled)
    }

    /** Draw a clean modern button with gradient fill - NO GLOW */
    fun neonButton(
        rect: Rectangle,
        color: Color,
        @Suppress("UNUSED_PARAMETER") glowColor: Color = color,
        @Suppress("UNUSED_PARAMETER") glowIntensity: Float = 0f,
        pressedOffset: Float = 0f
    ) {
        val x = rect.x
        val y = rect.y - pressedOffset
        val radius = UITheme.Dimensions.buttonRadius
        val scale = UITheme.Dimensions.scale()

        // Clean shadow (no glow)
        val shadowOffset = (5f * scale - pressedOffset * 2).coerceAtLeast(0f)
        if (shadowOffset > 0) {
            roundedRect(
                x + 2f * scale, y - shadowOffset,
                rect.width, rect.height, radius,
                UITheme.withAlpha(Color.BLACK, 0.35f)
            )
        }

        // Button body with gradient (darker at bottom, brighter at top)
        val bottomColor = UITheme.darken(color, 0.12f)
        val topColor = UITheme.brighten(color, 0.08f)
        val gradientSegments = 6
        val segmentHeight = rect.height / gradientSegments
        for (i in 0 until gradientSegments) {
            val t = i.toFloat() / gradientSegments
            val segColor = UITheme.lerp(bottomColor, topColor, t)
            val segY = y + i * segmentHeight
            if (i == 0) {
                roundedRect(x, segY, rect.width, segmentHeight + 1, radius, segColor)
            } else if (i == gradientSegments - 1) {
                roundedRect(x, segY, rect.width, segmentHeight, radius, segColor)
            } else {
                shapes.color = segColor
                shapes.rect(x, segY, rect.width, segmentHeight + 1)
            }
        }

        // Subtle top edge highlight for depth
        shapes.color = UITheme.withAlpha(Color.WHITE, 0.15f)
        roundedRect(x + 4f * scale, y + rect.height - 6f * scale, rect.width - 8f * scale, 3f * scale, 1.5f * scale, shapes.color)
    }

    /** Draw a clean circular icon button - NO GLOW */
    fun iconButton(
        cx: Float, cy: Float, radius: Float,
        color: Color,
        @Suppress("UNUSED_PARAMETER") glowColor: Color = color,
        @Suppress("UNUSED_PARAMETER") glowIntensity: Float = 0f
    ) {
        val scale = UITheme.Dimensions.scale()

        // Clean shadow (no glow)
        shapes.color = UITheme.withAlpha(Color.BLACK, 0.3f)
        shapes.circle(cx + 2f * scale, cy - 3f * scale, radius)

        // Button with subtle gradient effect (darker at edges)
        shapes.color = UITheme.darken(color, 0.05f)
        shapes.circle(cx, cy, radius)

        // Brighter center for depth
        shapes.color = UITheme.brighten(color, 0.05f)
        shapes.circle(cx, cy, radius * 0.85f)

        // Subtle top highlight
        shapes.color = UITheme.withAlpha(Color.WHITE, 0.1f)
        shapes.circle(cx, cy + radius * 0.3f, radius * 0.5f)
    }

    /** Draw a clean progress bar - NO GLOW */
    fun neonBar(
        x: Float, y: Float, width: Float, height: Float,
        progress: Float, // 0-1
        backgroundColor: Color = UITheme.surfaceLight,
        fillColor: Color = UITheme.accent,
        @Suppress("UNUSED_PARAMETER") glowIntensity: Float = 0.5f
    ) {
        val radius = height / 2

        // Background
        roundedRect(x, y, width, height, radius, backgroundColor)

        // Fill with gradient (no glow)
        val fillWidth = (width * progress).coerceAtLeast(radius * 2)
        if (progress > 0) {
            val bottomFill = UITheme.darken(fillColor, 0.08f)
            val topFill = UITheme.brighten(fillColor, 0.05f)
            // Simple gradient by drawing two layers
            roundedRect(x, y, fillWidth, height, radius, bottomFill)
            roundedRect(x, y + height * 0.4f, fillWidth, height * 0.5f, radius * 0.6f, topFill)
        }
    }

    /** Draw a clean badge (small rounded label) - NO GLOW */
    fun badge(
        x: Float, y: Float, width: Float, height: Float,
        color: Color,
        @Suppress("UNUSED_PARAMETER") glowIntensity: Float = 0f
    ) {
        val radius = height / 2
        val scale = UITheme.Dimensions.scale()

        // Clean shadow (no glow)
        shapes.color = UITheme.withAlpha(Color.BLACK, 0.25f)
        roundedRect(x + 1f * scale, y - 2f * scale, width, height, radius, shapes.color)

        // Badge with subtle gradient
        val bottomColor = UITheme.darken(color, 0.05f)
        roundedRect(x, y, width, height, radius, bottomColor)
        roundedRect(x, y + height * 0.5f, width, height * 0.4f, radius * 0.7f, color)
    }

    /** Draw a full-screen gradient background */
    fun gradientBackground(topColor: Color = UITheme.Gradients.backgroundTop, bottomColor: Color = UITheme.Gradients.backgroundBottom) {
        val segments = 12
        val segHeight = screenHeight / segments
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val segColor = UITheme.lerp(bottomColor, topColor, t)
            shapes.color = segColor
            shapes.rect(0f, i * segHeight, screenWidth, segHeight + 1)
        }
    }

    /** Draw a stopwatch/timer icon ⏱️ */
    fun stopwatch(cx: Float, cy: Float, size: Float, color: Color) {
        shapes.color = color
        val s = size * 0.45f

        // Clock face - filled circle
        shapes.circle(cx, cy, s)

        // Top button
        val buttonW = s * 0.2f
        val buttonH = s * 0.3f
        shapes.rect(cx - buttonW / 2, cy + s, buttonW, buttonH)

        // Side button (start/stop)
        shapes.triangle(
            cx + s * 0.85f, cy + s * 0.6f,
            cx + s * 1.1f, cy + s * 0.8f,
            cx + s * 1.1f, cy + s * 0.4f
        )

        // Inner circle (clock face detail) - darker
        shapes.color = UITheme.darken(color, 0.3f)
        shapes.circle(cx, cy, s * 0.75f)

        // Clock hands
        shapes.color = color
        // Minute hand (pointing up-right)
        shapes.rect(cx - s * 0.04f, cy, s * 0.08f, s * 0.55f)
        // Second hand (pointing right)
        shapes.triangle(
            cx, cy + s * 0.05f,
            cx, cy - s * 0.05f,
            cx + s * 0.5f, cy
        )
    }

    /** Draw a modern lightning bolt icon ⚡ */
    fun lightning(cx: Float, cy: Float, size: Float, color: Color) {
        shapes.color = color
        val s = size * 0.5f

        // Modern sharp lightning bolt - single polygon shape
        // Points: top -> middle-right -> center -> bottom
        shapes.triangle(
            cx + s * 0.3f, cy + s,          // top right
            cx - s * 0.4f, cy + s,          // top left
            cx + s * 0.1f, cy + s * 0.05f   // middle
        )
        shapes.triangle(
            cx - s * 0.4f, cy + s,
            cx - s * 0.15f, cy + s * 0.05f,
            cx + s * 0.1f, cy + s * 0.05f
        )
        shapes.triangle(
            cx + s * 0.4f, cy - s * 0.05f,  // arrow point right
            cx - s * 0.15f, cy - s * 0.05f,
            cx - s * 0.3f, cy - s           // bottom point
        )
        shapes.triangle(
            cx + s * 0.4f, cy - s * 0.05f,
            cx - s * 0.3f, cy - s,
            cx + s * 0.15f, cy - s * 0.05f
        )
    }

    /** Draw a modern trophy cup icon 🏆 */
    fun trophy(cx: Float, cy: Float, size: Float, color: Color) {
        shapes.color = color
        val s = size * 0.5f

        // Cup bowl - trapezoid shape using triangles
        val bowlTopY = cy + s * 0.9f
        val bowlBottomY = cy + s * 0.1f
        val bowlTopHalfW = s * 0.7f
        val bowlBottomHalfW = s * 0.4f

        // Bowl left side
        shapes.triangle(
            cx - bowlTopHalfW, bowlTopY,
            cx - bowlBottomHalfW, bowlBottomY,
            cx, bowlBottomY
        )
        shapes.triangle(
            cx - bowlTopHalfW, bowlTopY,
            cx, bowlBottomY,
            cx, bowlTopY
        )
        // Bowl right side
        shapes.triangle(
            cx + bowlTopHalfW, bowlTopY,
            cx, bowlTopY,
            cx, bowlBottomY
        )
        shapes.triangle(
            cx + bowlTopHalfW, bowlTopY,
            cx, bowlBottomY,
            cx + bowlBottomHalfW, bowlBottomY
        )

        // Stem
        val stemW = s * 0.15f
        val stemTop = bowlBottomY
        val stemBottom = cy - s * 0.3f
        shapes.rect(cx - stemW, stemBottom, stemW * 2, stemTop - stemBottom)

        // Base
        val baseW = s * 0.5f
        val baseH = s * 0.15f
        shapes.rect(cx - baseW, cy - s * 0.5f, baseW * 2, baseH)

        // Handles - simple circles on sides
        val handleY = cy + s * 0.5f
        val handleR = s * 0.18f
        shapes.circle(cx - bowlTopHalfW - handleR * 0.5f, handleY, handleR)
        shapes.circle(cx + bowlTopHalfW + handleR * 0.5f, handleY, handleR)
    }

    /** Draw a lock icon 🔒 */
    fun lock(cx: Float, cy: Float, size: Float, color: Color) {
        shapes.color = color
        val s = size * 0.5f

        // Lock body (rounded rectangle)
        val bodyW = s * 1.2f
        val bodyH = s * 0.9f
        val bodyY = cy - s * 0.5f
        shapes.rect(cx - bodyW / 2, bodyY, bodyW, bodyH)

        // Lock shackle (arc at top) - using circles and rectangles
        val shackleW = s * 0.7f
        val shackleH = s * 0.6f
        val shackleY = bodyY + bodyH

        // Shackle outer arc (thick arc made with circles)
        val shackleThickness = s * 0.2f
        shapes.circle(cx - shackleW / 2 + shackleThickness / 2, shackleY, shackleThickness / 2)
        shapes.circle(cx + shackleW / 2 - shackleThickness / 2, shackleY, shackleThickness / 2)
        shapes.rect(cx - shackleW / 2, shackleY, shackleThickness, shackleH)
        shapes.rect(cx + shackleW / 2 - shackleThickness, shackleY, shackleThickness, shackleH)
        shapes.circle(cx, shackleY + shackleH, shackleW / 2)

        // Inner cutout for shackle (darker)
        shapes.color = UITheme.darken(color, 0.4f)
        shapes.circle(cx, shackleY + shackleH, shackleW / 2 - shackleThickness)

        // Keyhole on body
        shapes.color = UITheme.darken(color, 0.3f)
        val keyholeY = bodyY + bodyH * 0.5f
        shapes.circle(cx, keyholeY, s * 0.15f)
        shapes.triangle(
            cx - s * 0.08f, keyholeY,
            cx + s * 0.08f, keyholeY,
            cx, bodyY + s * 0.1f
        )
    }

    /** Draw a help icon (question mark in circle) */
    fun helpIcon(cx: Float, cy: Float, size: Float, color: Color) {
        shapes.color = color
        val radius = size * 0.5f

        // Outer circle
        shapes.circle(cx, cy, radius)

        // Inner circle (darker background)
        shapes.color = UITheme.darken(color, 0.3f)
        shapes.circle(cx, cy, radius * 0.85f)

        // Question mark
        shapes.color = color

        // Question mark curve (top part) - approximated with circles
        val qSize = radius * 0.5f
        val qTop = cy + qSize * 0.5f

        // Arc of the question mark (using small circles)
        val arcRadius = qSize * 0.5f
        val arcCx = cx
        val arcCy = qTop
        for (i in -30..180 step 15) {
            val angle = Math.toRadians(i.toDouble())
            val px = arcCx + cos(angle).toFloat() * arcRadius
            val py = arcCy + sin(angle).toFloat() * arcRadius
            shapes.circle(px, py, qSize * 0.18f)
        }

        // Stem of question mark (vertical part going down)
        shapes.rect(cx - qSize * 0.12f, cy - qSize * 0.3f, qSize * 0.24f, qSize * 0.5f)

        // Dot at the bottom
        shapes.circle(cx, cy - qSize * 0.7f, qSize * 0.2f)
    }

    /** Draw a separator line with gradient fade */
    fun separator(x: Float, y: Float, width: Float, color: Color = UITheme.surfaceBorder) {
        val scale = UITheme.Dimensions.scale()
        val height = 2f * scale

        // Draw faded edges
        val fadeWidth = width * 0.2f
        val segments = 10

        // Left fade
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val segX = x + fadeWidth * t / segments * i
            val segWidth = fadeWidth / segments
            shapes.color = UITheme.withAlpha(color, t * 0.6f)
            shapes.rect(segX, y, segWidth, height)
        }

        // Center solid
        shapes.color = UITheme.withAlpha(color, 0.6f)
        shapes.rect(x + fadeWidth, y, width - fadeWidth * 2, height)

        // Right fade
        for (i in 0 until segments) {
            val t = 1f - i.toFloat() / segments
            val segX = x + width - fadeWidth + fadeWidth * i / segments
            val segWidth = fadeWidth / segments
            shapes.color = UITheme.withAlpha(color, t * 0.6f)
            shapes.rect(segX, y, segWidth, height)
        }
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
    }
}
