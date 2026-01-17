package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.state.SettingsManager

class SettingsRenderer(private val settingsManager: SettingsManager) : Disposable {
    private val ui = UIRenderer()

    private val backButton = Rectangle()
    private val renderDistanceLeftButton = Rectangle()
    private val renderDistanceRightButton = Rectangle()
    private val fpsCheckbox = Rectangle()

    private var backButtonHover = 0f
    private var leftButtonHover = 0f
    private var rightButtonHover = 0f
    private var fpsCheckboxHover = 0f
    private var enterAnimProgress = 0f

    enum class Action {
        NONE, BACK
    }

    fun render(): Action {
        UITheme.Anim.update(Gdx.graphics.deltaTime)
        UIFonts.initialize()

        val sw = ui.screenWidth
        val sh = ui.screenHeight
        val centerX = sw / 2
        val scale = UITheme.Dimensions.scale()

        // Update animations
        enterAnimProgress = UITheme.Anim.ease(enterAnimProgress, 1f, 3f)

        // Check hover state
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()

        val backHovered = backButton.contains(touchX, touchY)
        val leftHovered = renderDistanceLeftButton.contains(touchX, touchY)
        val rightHovered = renderDistanceRightButton.contains(touchX, touchY)
        val fpsHovered = fpsCheckbox.contains(touchX, touchY)

        backButtonHover = UITheme.Anim.ease(backButtonHover, if (backHovered) 1f else 0f, 8f)
        leftButtonHover = UITheme.Anim.ease(leftButtonHover, if (leftHovered) 1f else 0f, 8f)
        rightButtonHover = UITheme.Anim.ease(rightButtonHover, if (rightHovered) 1f else 0f, 8f)
        fpsCheckboxHover = UITheme.Anim.ease(fpsCheckboxHover, if (fpsHovered) 1f else 0f, 8f)

        // === Draw Background ===
        ui.beginShapes()

        // Gradient background
        val bgTop = UITheme.backgroundLight
        val bgBottom = UITheme.background
        for (i in 0 until 20) {
            val t = i / 20f
            val stripY = sh * t
            val stripHeight = sh / 20f + 1
            ui.shapes.color = UITheme.lerp(bgBottom, bgTop, t)
            ui.shapes.rect(0f, stripY, sw, stripHeight)
        }

        // Settings panel
        val panelWidth = 700f * scale
        val panelHeight = 480f * scale
        val panelX = centerX - panelWidth / 2
        val panelY = sh / 2 - panelHeight / 2 + 50f * scale

        ui.panel(panelX, panelY, panelWidth, panelHeight,
            radius = UITheme.Dimensions.panelRadius,
            backgroundColor = UITheme.surface)

        // === Render Distance Setting ===
        val settingY = panelY + panelHeight - 120f * scale

        // === FPS Counter Checkbox ===
        val fpsSettingY = settingY - 120f * scale
        val checkboxSize = 40f * scale
        val checkboxX = centerX - 120f * scale

        fpsCheckbox.set(checkboxX, fpsSettingY - checkboxSize / 2, checkboxSize, checkboxSize)

        // Checkbox background
        val checkboxColor = if (settingsManager.showFps) UITheme.primary else UITheme.surfaceLight
        ui.roundedRect(fpsCheckbox.x, fpsCheckbox.y, fpsCheckbox.width, fpsCheckbox.height,
            8f * scale, checkboxColor)

        // Checkbox border/glow
        if (fpsCheckboxHover > 0.1f) {
            ui.shapes.color = UITheme.withAlpha(UITheme.primary, fpsCheckboxHover * 0.3f)
            ui.roundedRect(fpsCheckbox.x - 3f, fpsCheckbox.y - 3f,
                fpsCheckbox.width + 6f, fpsCheckbox.height + 6f, 10f * scale, ui.shapes.color)
        }

        // Checkmark
        if (settingsManager.showFps) {
            ui.shapes.color = UITheme.textPrimary
            val cx = fpsCheckbox.x + fpsCheckbox.width / 2
            val cy = fpsCheckbox.y + fpsCheckbox.height / 2
            // Draw checkmark as two lines
            ui.shapes.rectLine(cx - 10f * scale, cy, cx - 2f * scale, cy - 10f * scale, 3f * scale)
            ui.shapes.rectLine(cx - 2f * scale, cy - 10f * scale, cx + 12f * scale, cy + 8f * scale, 3f * scale)
        }
        val arrowButtonSize = 60f * scale
        val valueBoxWidth = 200f * scale

        // Left arrow button
        renderDistanceLeftButton.set(
            centerX - valueBoxWidth / 2 - arrowButtonSize - 20f * scale,
            settingY - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(renderDistanceLeftButton, UITheme.secondary, glowIntensity = leftButtonHover * 0.5f)

        // Right arrow button
        renderDistanceRightButton.set(
            centerX + valueBoxWidth / 2 + 20f * scale,
            settingY - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(renderDistanceRightButton, UITheme.secondary, glowIntensity = rightButtonHover * 0.5f)

        // Value box
        ui.panel(
            centerX - valueBoxWidth / 2,
            settingY - 30f * scale,
            valueBoxWidth,
            60f * scale,
            radius = 10f * scale,
            backgroundColor = UITheme.surfaceLight
        )

        // Back button
        val buttonWidth = 300f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        backButton.set(centerX - buttonWidth / 2, panelY - buttonHeight - 30f * scale, buttonWidth, buttonHeight)
        ui.button(backButton, UITheme.primary, glowIntensity = backButtonHover * 0.8f)

        ui.endShapes()

        // === Draw Text ===
        ui.beginBatch()

        // Title
        val titleY = panelY + panelHeight - 40f * scale
        ui.textCentered("SETTINGS", centerX, titleY, UIFonts.heading, UITheme.textPrimary)

        // Render Distance label
        val labelY = settingY + 60f * scale
        ui.textCentered("Render Distance", centerX, labelY, UIFonts.body, UITheme.textSecondary)

        // Arrow symbols
        ui.textCentered("<",
            renderDistanceLeftButton.x + renderDistanceLeftButton.width / 2,
            renderDistanceLeftButton.y + renderDistanceLeftButton.height / 2,
            UIFonts.button, UITheme.textPrimary)

        ui.textCentered(">",
            renderDistanceRightButton.x + renderDistanceRightButton.width / 2,
            renderDistanceRightButton.y + renderDistanceRightButton.height / 2,
            UIFonts.button, UITheme.textPrimary)

        // Current value
        val currentIndex = settingsManager.getRenderDistanceIndex()
        val currentName = settingsManager.getRenderDistanceName()
        val currentDistance = settingsManager.renderDistance.toInt()
        ui.textCentered("$currentName (${currentDistance}m)", centerX, settingY, UIFonts.body, UITheme.accent)

        // FPS Checkbox label
        UIFonts.body.draw(ui.batch, "Show FPS Counter", fpsCheckbox.x + fpsCheckbox.width + 20f * scale,
            fpsCheckbox.y + fpsCheckbox.height / 2 + UIFonts.body.lineHeight / 3)

        // Back button text
        ui.textCentered("BACK", backButton.x + backButton.width / 2, backButton.y + backButton.height / 2, UIFonts.button, UITheme.textPrimary)

        // Hint at bottom
        ui.textCentered("Higher distance = better view, lower FPS", centerX, panelY + 40f * scale, UIFonts.caption, UITheme.textMuted)

        ui.endBatch()

        // === Handle Input ===
        if (Gdx.input.justTouched()) {
            if (backButton.contains(touchX, touchY)) {
                return Action.BACK
            }
            if (renderDistanceLeftButton.contains(touchX, touchY)) {
                val newIndex = (currentIndex - 1).coerceAtLeast(0)
                settingsManager.setRenderDistanceByIndex(newIndex)
            }
            if (renderDistanceRightButton.contains(touchX, touchY)) {
                val newIndex = (currentIndex + 1).coerceAtMost(SettingsManager.RENDER_DISTANCE_OPTIONS.size - 1)
                settingsManager.setRenderDistanceByIndex(newIndex)
            }
            if (fpsCheckbox.contains(touchX, touchY)) {
                settingsManager.showFps = !settingsManager.showFps
            }
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            return Action.BACK
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            val newIndex = (currentIndex - 1).coerceAtLeast(0)
            settingsManager.setRenderDistanceByIndex(newIndex)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            val newIndex = (currentIndex + 1).coerceAtMost(SettingsManager.RENDER_DISTANCE_OPTIONS.size - 1)
            settingsManager.setRenderDistanceByIndex(newIndex)
        }

        return Action.NONE
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
        enterAnimProgress = 0f
    }

    override fun dispose() {
        ui.dispose()
    }
}
