package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.MathUtils

/**
 * Modern UI Theme with colors, animations, and utilities
 */
object UITheme {
    // === Primary Colors ===
    val primary = Color(0x4ADE80FF.toInt())        // Vibrant green
    val primaryDark = Color(0x22C55EFF.toInt())    // Darker green
    val primaryGlow = Color(0x4ADE8066.toInt())    // Green with alpha

    // === Secondary Colors ===
    val secondary = Color(0x3B82F6FF.toInt())      // Blue
    val secondaryDark = Color(0x2563EBFF.toInt())  // Darker blue

    // === Accent Colors ===
    val accent = Color(0xFF6B00FF.toInt())         // Orange (#ff6b00)
    val accentBright = Color(0xFF8C33FF.toInt())   // Bright orange

    // === Danger/Warning Colors ===
    val danger = Color(0xEF4444FF.toInt())         // Red
    val dangerDark = Color(0xDC2626FF.toInt())     // Dark red
    val warning = Color(0xF59E0BFF.toInt())        // Amber
    val warningBright = Color(0xFBBF24FF.toInt())  // Bright amber

    // === Neutral Colors ===
    val background = Color(0x0F0F17FF.toInt())     // Very dark blue-gray
    val backgroundLight = Color(0x1A1A25FF.toInt()) // Slightly lighter
    val surface = Color(0x1E1E2EFF.toInt())        // Card/panel surface
    val surfaceLight = Color(0x2A2A3EFF.toInt())   // Elevated surface
    val surfaceBorder = Color(0x3A3A4EFF.toInt())  // Border color

    // === Text Colors ===
    val textPrimary = Color(0xF8FAFCFF.toInt())    // White-ish
    val textSecondary = Color(0x94A3B8FF.toInt())  // Gray
    val textMuted = Color(0x64748BFF.toInt())      // Muted gray
    val textDisabled = Color(0x475569FF.toInt())   // Disabled

    // === Special Colors ===
    val cyan = Color(0x22D3EEFF.toInt())           // Cyan for special effects
    val purple = Color(0xA855F7FF.toInt())         // Purple accent

    // === Dimensions (base values, multiply by scale()) ===
    // Modern, larger UI elements for better touch targets and visibility
    object Dimensions {
        // Call scale() to get screen-relative size
        private var cachedScale = 1f
        private var lastScreenHeight = 0f

        fun scale(): Float {
            val screenHeight = com.badlogic.gdx.Gdx.graphics.height.toFloat()
            if (screenHeight != lastScreenHeight) {
                lastScreenHeight = screenHeight
                // Base design is for 1080p (1920x1080), scale proportionally
                cachedScale = (screenHeight / 1080f).coerceIn(0.5f, 2.5f)
            }
            return cachedScale
        }

        // Larger buttons for better touch targets
        val buttonHeight: Float get() = 120f * scale()
        val buttonHeightSmall: Float get() = 100f * scale()
        val buttonHeightMedium: Float get() = 110f * scale()

        // More rounded corners for modern look
        val buttonRadius: Float get() = 24f * scale()
        val panelRadius: Float get() = 28f * scale()

        // Generous padding
        val padding: Float get() = 40f * scale()
        val paddingSmall: Float get() = 28f * scale()
        val paddingTiny: Float get() = 14f * scale()

        // More pronounced shadows
        val shadowOffset: Float get() = 8f * scale()
        val borderWidth: Float get() = 3f * scale()

        // UI element sizes
        val iconSize: Float get() = 48f * scale()
        val checkboxSize: Float get() = 52f * scale()
        val arrowButtonSize: Float get() = 72f * scale()
        val hudPanelPadding: Float get() = 20f * scale()
    }

    // === Animation Utilities ===
    object Anim {
        private var globalTime = 0f

        fun update(delta: Float) {
            globalTime += delta
        }

        /** Returns a value that pulses between min and max */
        fun pulse(speed: Float = 2f, min: Float = 0.7f, max: Float = 1f): Float {
            return MathUtils.lerp(min, max, (MathUtils.sin(globalTime * speed) + 1f) / 2f)
        }

        /** Returns a looping value 0-1 */
        fun loop(duration: Float = 1f): Float {
            return (globalTime % duration) / duration
        }

        /** Smooth easing for hover/press effects */
        fun ease(current: Float, target: Float, speed: Float = 10f): Float {
            val delta = Gdx.graphics.deltaTime
            return MathUtils.lerp(current, target, (speed * delta).coerceAtMost(1f))
        }

        /** Bounce interpolation */
        fun bounce(progress: Float): Float {
            return Interpolation.bounceOut.apply(progress)
        }

        /** Elastic interpolation */
        fun elastic(progress: Float): Float {
            return Interpolation.elasticOut.apply(progress)
        }

        /** Smooth step (ease in-out) */
        fun smooth(progress: Float): Float {
            return Interpolation.smooth.apply(progress)
        }

        /** Get current time for custom animations */
        fun time(): Float = globalTime
    }

    // === Color Utilities ===
    fun lerp(a: Color, b: Color, t: Float): Color {
        return Color(
            MathUtils.lerp(a.r, b.r, t),
            MathUtils.lerp(a.g, b.g, t),
            MathUtils.lerp(a.b, b.b, t),
            MathUtils.lerp(a.a, b.a, t)
        )
    }

    fun withAlpha(color: Color, alpha: Float): Color {
        return Color(color.r, color.g, color.b, alpha)
    }

    fun brighten(color: Color, amount: Float = 0.2f): Color {
        return Color(
            (color.r + amount).coerceAtMost(1f),
            (color.g + amount).coerceAtMost(1f),
            (color.b + amount).coerceAtMost(1f),
            color.a
        )
    }

    fun darken(color: Color, amount: Float = 0.2f): Color {
        return Color(
            (color.r - amount).coerceAtLeast(0f),
            (color.g - amount).coerceAtLeast(0f),
            (color.b - amount).coerceAtLeast(0f),
            color.a
        )
    }
}
