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
 */
object UIFonts : Disposable {
    private val fonts = mutableMapOf<FontStyle, BitmapFont>()
    private var initialized = false
    private var generator: FreeTypeFontGenerator? = null

    enum class FontStyle {
        DISPLAY,     // 72px - Big titles
        TITLE,       // 48px - Section titles
        HEADING,     // 36px - Headings
        BUTTON,      // 28px - Button text
        BODY,        // 22px - Regular text
        CAPTION,     // 18px - Small labels
        TINY         // 14px - Very small text
    }

    // Base sizes for 1080p screen
    private val baseFontSizes = mapOf(
        FontStyle.DISPLAY to 140,
        FontStyle.TITLE to 100,
        FontStyle.HEADING to 75,
        FontStyle.BUTTON to 60,
        FontStyle.BODY to 48,
        FontStyle.CAPTION to 40,
        FontStyle.TINY to 32
    )

    // Characters to include in font
    private const val CHARS = FreeTypeFontGenerator.DEFAULT_CHARS +
            "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"

    fun initialize() {
        if (initialized) return
        initialized = true

        val density = Gdx.graphics.density.coerceAtLeast(1f)
        // Scale based on screen height (base is 1080p)
        val screenScale = (Gdx.graphics.height / 1080f).coerceIn(0.5f, 2.5f)

        // Try to use system fonts or fallback
        generator = try {
            // Try loading a clean sans-serif font bundled with the app
            // For now we'll use the default scaled bitmap font with better settings
            null
        } catch (e: Exception) {
            null
        }

        // Create fonts for each style
        FontStyle.entries.forEach { style ->
            val baseSize = baseFontSizes[style] ?: 24
            fonts[style] = createFont(baseSize, density, screenScale)
        }
    }

    private fun createFont(baseSize: Int, density: Float, screenScale: Float): BitmapFont {
        val scaledSize = (baseSize * density * screenScale * 0.5f).toInt().coerceAtLeast(12)

        return if (generator != null) {
            createFreeTypeFont(scaledSize)
        } else {
            // Scale factor for bitmap fonts, adjusted for screen size
            createScaledBitmapFont(baseSize * screenScale * 0.04f)
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

    override fun dispose() {
        fonts.values.forEach { it.dispose() }
        fonts.clear()
        generator?.dispose()
        generator = null
        initialized = false
    }
}
