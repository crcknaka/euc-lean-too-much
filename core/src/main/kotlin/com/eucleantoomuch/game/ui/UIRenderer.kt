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

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
    }
}
