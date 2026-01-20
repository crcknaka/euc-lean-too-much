package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.utils.Disposable

/**
 * Modern font system using FreeType for crisp rendering at any size.
 * Falls back to default BitmapFont if FreeType fails.
 * Increased font sizes for better readability on modern displays.
 */
object UIFonts : Disposable {
    private val fonts = mutableMapOf<FontStyle, BitmapFont>()
    private var initialized = false
    private var generator: FreeTypeFontGenerator? = null
    private var lastGlContext = 0  // Track GL context changes

    enum class FontStyle {
        DISPLAY,     // Extra large - Big titles, countdown
        TITLE,       // Large - Screen titles
        HEADING,     // Medium-large - Section headings, values
        BUTTON,      // Medium - Button text
        BODY,        // Regular - Body text, labels
        CAPTION,     // Small - Hints, secondary info
        TINY         // Extra small - Debug info
    }

    // Base sizes for 1080p screen (in points, will be scaled for screen)
    // Sized to look good on large UI elements (buttons 100-120px)
    private val baseFontSizes = mapOf(
        FontStyle.DISPLAY to 96,    // Big titles, countdown
        FontStyle.TITLE to 64,      // Screen titles (GAME OVER, PAUSED, SETTINGS)
        FontStyle.HEADING to 48,    // Section headings, values
        FontStyle.BUTTON to 40,     // Button text (fits 120px buttons)
        FontStyle.BODY to 32,       // Body text, labels
        FontStyle.CAPTION to 26,    // Hints, secondary info
        FontStyle.TINY to 20        // Debug info, FPS
    )

    // Characters to include in font
    private const val CHARS = FreeTypeFontGenerator.DEFAULT_CHARS +
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"

    fun initialize() {
        // Check if GL context was recreated (happens on Android resume)
        // We detect this by checking if any font texture became invalid
        val contextLost = initialized && fonts.isNotEmpty() &&
            fonts.values.any { !it.region.texture.isManaged || it.region.texture.textureObjectHandle == 0 }

        if (contextLost) {
            Gdx.app.log("UIFonts", "GL context lost, reinitializing fonts")
            disposeInternal()
        }

        if (initialized) return
        initialized = true

        val density = Gdx.graphics.density.coerceAtLeast(1f)
        // Scale based on screen height (base is 1080p)
        val screenScale = (Gdx.graphics.height / 1080f).coerceIn(0.5f, 2.5f)

        // Try to use custom font file
        generator = try {
            // Load Roboto font (download from fonts.google.com and place in assets/)
            val fontFile = Gdx.files.internal("Roboto-Medium.ttf")
            if (fontFile.exists()) {
                FreeTypeFontGenerator(fontFile)
            } else {
                Gdx.app.log("UIFonts", "Font file not found: Roboto-Medium.ttf - using default bitmap font")
                null
            }
        } catch (e: Exception) {
            Gdx.app.log("UIFonts", "Failed to load font: ${e.message}")
            null
        }

        // Create fonts for each style
        FontStyle.entries.forEach { style ->
            val baseSize = baseFontSizes[style] ?: 24
            fonts[style] = createFont(baseSize, density, screenScale)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun createFont(baseSize: Int, density: Float, screenScale: Float): BitmapFont {
        return if (generator != null) {
            // FreeType: scale based on screen size only (density is handled by screen resolution)
            val scaledSize = (baseSize * screenScale).toInt().coerceAtLeast(10)
            createFreeTypeFont(scaledSize)
        } else {
            // Fallback bitmap font: needs different scaling approach
            createScaledBitmapFont(baseSize * screenScale * 0.06f)
        }
    }

    private fun createFreeTypeFont(size: Int): BitmapFont {
        val param = FreeTypeFontGenerator.FreeTypeFontParameter().apply {
            this.size = size
            this.color = Color.WHITE
            this.borderWidth = 0f
            this.shadowOffsetX = 0
            this.shadowOffsetY = 0
            this.minFilter = Texture.TextureFilter.Linear
            this.magFilter = Texture.TextureFilter.Linear
            this.characters = CHARS
            this.kerning = true
            this.genMipMaps = true
        }
        return generator!!.generateFont(param)
    }

    private fun createScaledBitmapFont(scale: Float): BitmapFont {
        return BitmapFont().apply {
            data.setScale(scale)
            setUseIntegerPositions(false)
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    fun get(style: FontStyle): BitmapFont {
        if (!initialized) initialize()
        return fonts[style] ?: fonts[FontStyle.BODY]!!
    }

    // Convenience accessors
    val display: BitmapFont get() = get(FontStyle.DISPLAY)
    val title: BitmapFont get() = get(FontStyle.TITLE)
    val heading: BitmapFont get() = get(FontStyle.HEADING)
    val button: BitmapFont get() = get(FontStyle.BUTTON)
    val body: BitmapFont get() = get(FontStyle.BODY)
    val caption: BitmapFont get() = get(FontStyle.CAPTION)
    val tiny: BitmapFont get() = get(FontStyle.TINY)

    private fun disposeInternal() {
        fonts.values.forEach {
            try { it.dispose() } catch (e: Exception) { /* ignore */ }
        }
        fonts.clear()
        generator?.let {
            try { it.dispose() } catch (e: Exception) { /* ignore */ }
        }
        generator = null
        initialized = false
    }

    override fun dispose() {
        disposeInternal()
    }
}
