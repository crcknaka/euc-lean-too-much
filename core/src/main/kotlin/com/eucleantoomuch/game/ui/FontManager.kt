package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.utils.Disposable

object FontManager : Disposable {
    private val fonts = mutableMapOf<String, BitmapFont>()
    private var initialized = false

    val titleFont: BitmapFont get() = fonts["title"] ?: createDefaultFont(4f)
    val largeFont: BitmapFont get() = fonts["large"] ?: createDefaultFont(3f)
    val mediumFont: BitmapFont get() = fonts["medium"] ?: createDefaultFont(2.2f)
    val smallFont: BitmapFont get() = fonts["small"] ?: createDefaultFont(1.6f)
    val tinyFont: BitmapFont get() = fonts["tiny"] ?: createDefaultFont(1.2f)

    fun initialize() {
        if (initialized) return
        initialized = true

        val density = Gdx.graphics.density.coerceAtLeast(1f)

        // Create fonts with linear filtering for smooth scaling
        fonts["title"] = createSmoothFont(4.5f * density)
        fonts["large"] = createSmoothFont(3.2f * density)
        fonts["medium"] = createSmoothFont(2.4f * density)
        fonts["small"] = createSmoothFont(1.8f * density)
        fonts["tiny"] = createSmoothFont(1.3f * density)
    }

    private fun createSmoothFont(scale: Float): BitmapFont {
        return BitmapFont().apply {
            data.setScale(scale)
            setUseIntegerPositions(false)
            // Apply linear filtering for smooth edges
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    private fun createDefaultFont(scale: Float): BitmapFont {
        return BitmapFont().apply {
            data.setScale(scale)
            setUseIntegerPositions(false)
            region.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        }
    }

    override fun dispose() {
        fonts.values.forEach { it.dispose() }
        fonts.clear()
        initialized = false
    }
}
