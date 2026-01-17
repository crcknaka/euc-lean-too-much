package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.state.SettingsManager

/**
 * Modern settings screen with large, touch-friendly controls.
 * Clean layout with clear visual hierarchy.
 */
class SettingsRenderer(private val settingsManager: SettingsManager) : Disposable {
    private val ui = UIRenderer()

    private val backButton = Rectangle()
    private val renderDistanceLeftButton = Rectangle()
    private val renderDistanceRightButton = Rectangle()
    private val fpsCheckbox = Rectangle()
    private val beepsCheckbox = Rectangle()
    private val avasLeftButton = Rectangle()
    private val avasRightButton = Rectangle()
    private val pwmWarningLeftButton = Rectangle()
    private val pwmWarningRightButton = Rectangle()

    private var backButtonHover = 0f
    private var leftButtonHover = 0f
    private var rightButtonHover = 0f
    private var fpsCheckboxHover = 0f
    private var beepsCheckboxHover = 0f
    private var avasLeftButtonHover = 0f
    private var avasRightButtonHover = 0f
    private var pwmLeftButtonHover = 0f
    private var pwmRightButtonHover = 0f
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
        enterAnimProgress = UITheme.Anim.ease(enterAnimProgress, 1f, 4f)

        // Check hover state
        val touchX = Gdx.input.x.toFloat()
        val touchY = sh - Gdx.input.y.toFloat()

        val backHovered = backButton.contains(touchX, touchY)
        val leftHovered = renderDistanceLeftButton.contains(touchX, touchY)
        val rightHovered = renderDistanceRightButton.contains(touchX, touchY)
        val fpsHovered = fpsCheckbox.contains(touchX, touchY)
        val beepsHovered = beepsCheckbox.contains(touchX, touchY)
        val avasLeftHovered = avasLeftButton.contains(touchX, touchY)
        val avasRightHovered = avasRightButton.contains(touchX, touchY)
        val pwmLeftHovered = pwmWarningLeftButton.contains(touchX, touchY)
        val pwmRightHovered = pwmWarningRightButton.contains(touchX, touchY)

        backButtonHover = UITheme.Anim.ease(backButtonHover, if (backHovered) 1f else 0f, 10f)
        leftButtonHover = UITheme.Anim.ease(leftButtonHover, if (leftHovered) 1f else 0f, 10f)
        rightButtonHover = UITheme.Anim.ease(rightButtonHover, if (rightHovered) 1f else 0f, 10f)
        fpsCheckboxHover = UITheme.Anim.ease(fpsCheckboxHover, if (fpsHovered) 1f else 0f, 10f)
        beepsCheckboxHover = UITheme.Anim.ease(beepsCheckboxHover, if (beepsHovered) 1f else 0f, 10f)
        avasLeftButtonHover = UITheme.Anim.ease(avasLeftButtonHover, if (avasLeftHovered) 1f else 0f, 10f)
        avasRightButtonHover = UITheme.Anim.ease(avasRightButtonHover, if (avasRightHovered) 1f else 0f, 10f)
        pwmLeftButtonHover = UITheme.Anim.ease(pwmLeftButtonHover, if (pwmLeftHovered) 1f else 0f, 10f)
        pwmRightButtonHover = UITheme.Anim.ease(pwmRightButtonHover, if (pwmRightHovered) 1f else 0f, 10f)

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

        // Main settings panel
        val panelWidth = 780f * scale * enterAnimProgress
        val panelHeight = 730f * scale * enterAnimProgress  // Increased for motor sound checkbox
        val panelX = centerX - panelWidth / 2
        val panelY = sh / 2 - panelHeight / 2 + 40f * scale

        ui.panel(panelX, panelY, panelWidth, panelHeight,
            radius = UITheme.Dimensions.panelRadius,
            backgroundColor = UITheme.surface)

        // Setting row dimensions
        val rowHeight = 100f * scale
        val arrowButtonSize = UITheme.Dimensions.arrowButtonSize
        val valueBoxWidth = 260f * scale
        val checkboxSize = UITheme.Dimensions.checkboxSize

        // === Render Distance Setting ===
        val renderDistanceY = panelY + panelHeight - 180f * scale

        renderDistanceLeftButton.set(
            centerX - valueBoxWidth / 2 - arrowButtonSize - 16f * scale,
            renderDistanceY - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(renderDistanceLeftButton, UITheme.secondary, glowIntensity = leftButtonHover * 0.6f)

        renderDistanceRightButton.set(
            centerX + valueBoxWidth / 2 + 16f * scale,
            renderDistanceY - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(renderDistanceRightButton, UITheme.secondary, glowIntensity = rightButtonHover * 0.6f)

        // Value box
        ui.panel(
            centerX - valueBoxWidth / 2,
            renderDistanceY - 40f * scale,
            valueBoxWidth,
            80f * scale,
            radius = 14f * scale,
            backgroundColor = UITheme.surfaceLight,
            shadowOffset = 0f
        )

        // === PWM Warning Setting ===
        val pwmSettingY = renderDistanceY - 130f * scale

        pwmWarningLeftButton.set(
            centerX - valueBoxWidth / 2 - arrowButtonSize - 16f * scale,
            pwmSettingY - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(pwmWarningLeftButton, UITheme.secondary, glowIntensity = pwmLeftButtonHover * 0.6f)

        pwmWarningRightButton.set(
            centerX + valueBoxWidth / 2 + 16f * scale,
            pwmSettingY - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(pwmWarningRightButton, UITheme.secondary, glowIntensity = pwmRightButtonHover * 0.6f)

        // PWM Value box
        ui.panel(
            centerX - valueBoxWidth / 2,
            pwmSettingY - 40f * scale,
            valueBoxWidth,
            80f * scale,
            radius = 14f * scale,
            backgroundColor = UITheme.surfaceLight,
            shadowOffset = 0f
        )

        // === AVAS Setting (selector with arrows) ===
        val avasSettingY = pwmSettingY - 130f * scale

        avasLeftButton.set(
            centerX - valueBoxWidth / 2 - arrowButtonSize - 16f * scale,
            avasSettingY - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(avasLeftButton, UITheme.secondary, glowIntensity = avasLeftButtonHover * 0.6f)

        avasRightButton.set(
            centerX + valueBoxWidth / 2 + 16f * scale,
            avasSettingY - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(avasRightButton, UITheme.secondary, glowIntensity = avasRightButtonHover * 0.6f)

        // AVAS Value box
        ui.panel(
            centerX - valueBoxWidth / 2,
            avasSettingY - 40f * scale,
            valueBoxWidth,
            80f * scale,
            radius = 14f * scale,
            backgroundColor = UITheme.surfaceLight,
            shadowOffset = 0f
        )

        // === Beeps Checkbox ===
        val beepsSettingY = avasSettingY - 100f * scale
        val checkboxX = centerX - 160f * scale

        beepsCheckbox.set(checkboxX, beepsSettingY - checkboxSize / 2, checkboxSize, checkboxSize)

        // Checkbox background with glow on hover
        if (beepsCheckboxHover > 0.1f) {
            ui.shapes.color = UITheme.withAlpha(UITheme.primary, beepsCheckboxHover * 0.25f)
            ui.roundedRect(beepsCheckbox.x - 5f, beepsCheckbox.y - 5f,
                beepsCheckbox.width + 10f, beepsCheckbox.height + 10f, 14f * scale, ui.shapes.color)
        }

        val beepsCheckboxColor = if (settingsManager.beepsEnabled) UITheme.primary else UITheme.surfaceLight
        ui.roundedRect(beepsCheckbox.x, beepsCheckbox.y, beepsCheckbox.width, beepsCheckbox.height,
            12f * scale, beepsCheckboxColor)

        // Checkmark
        if (settingsManager.beepsEnabled) {
            ui.shapes.color = UITheme.textPrimary
            val cx = beepsCheckbox.x + beepsCheckbox.width / 2
            val cy = beepsCheckbox.y + beepsCheckbox.height / 2
            val checkScale = checkboxSize / 52f
            ui.shapes.rectLine(cx - 14f * checkScale, cy, cx - 4f * checkScale, cy - 14f * checkScale, 4f * checkScale)
            ui.shapes.rectLine(cx - 4f * checkScale, cy - 14f * checkScale, cx + 16f * checkScale, cy + 10f * checkScale, 4f * checkScale)
        }

        // === FPS Checkbox ===
        val fpsSettingY = beepsSettingY - 100f * scale

        fpsCheckbox.set(checkboxX, fpsSettingY - checkboxSize / 2, checkboxSize, checkboxSize)

        // Checkbox background with glow on hover
        if (fpsCheckboxHover > 0.1f) {
            ui.shapes.color = UITheme.withAlpha(UITheme.primary, fpsCheckboxHover * 0.25f)
            ui.roundedRect(fpsCheckbox.x - 5f, fpsCheckbox.y - 5f,
                fpsCheckbox.width + 10f, fpsCheckbox.height + 10f, 14f * scale, ui.shapes.color)
        }

        val fpsCheckboxColor = if (settingsManager.showFps) UITheme.primary else UITheme.surfaceLight
        ui.roundedRect(fpsCheckbox.x, fpsCheckbox.y, fpsCheckbox.width, fpsCheckbox.height,
            12f * scale, fpsCheckboxColor)

        // Checkmark
        if (settingsManager.showFps) {
            ui.shapes.color = UITheme.textPrimary
            val cx = fpsCheckbox.x + fpsCheckbox.width / 2
            val cy = fpsCheckbox.y + fpsCheckbox.height / 2
            val checkScale = checkboxSize / 52f
            ui.shapes.rectLine(cx - 14f * checkScale, cy, cx - 4f * checkScale, cy - 14f * checkScale, 4f * checkScale)
            ui.shapes.rectLine(cx - 4f * checkScale, cy - 14f * checkScale, cx + 16f * checkScale, cy + 10f * checkScale, 4f * checkScale)
        }

        // Back button
        val buttonWidth = 340f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        backButton.set(centerX - buttonWidth / 2, panelY - buttonHeight - 36f * scale, buttonWidth, buttonHeight)
        ui.button(backButton, UITheme.primary, glowIntensity = backButtonHover * 0.8f)

        ui.endShapes()

        // === Draw Text ===
        ui.beginBatch()

        // Title
        val titleY = panelY + panelHeight - 55f * scale
        ui.textCentered("SETTINGS", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Render Distance label
        val rdLabelY = renderDistanceY + 70f * scale
        ui.textCentered("Render Distance", centerX, rdLabelY, UIFonts.body, UITheme.textSecondary)

        // Arrow symbols (larger)
        ui.textCentered("<",
            renderDistanceLeftButton.x + renderDistanceLeftButton.width / 2,
            renderDistanceLeftButton.y + renderDistanceLeftButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)

        ui.textCentered(">",
            renderDistanceRightButton.x + renderDistanceRightButton.width / 2,
            renderDistanceRightButton.y + renderDistanceRightButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)

        // Render Distance current value
        val currentIndex = settingsManager.getRenderDistanceIndex()
        val currentName = settingsManager.getRenderDistanceName()
        val currentDistance = settingsManager.renderDistance.toInt()
        ui.textCentered("$currentName (${currentDistance}m)", centerX, renderDistanceY, UIFonts.body, UITheme.accent)

        // PWM Warning label
        val pwmLabelY = pwmSettingY + 70f * scale
        ui.textCentered("PWM Warning", centerX, pwmLabelY, UIFonts.body, UITheme.textSecondary)

        // PWM Arrow symbols
        ui.textCentered("<",
            pwmWarningLeftButton.x + pwmWarningLeftButton.width / 2,
            pwmWarningLeftButton.y + pwmWarningLeftButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)

        ui.textCentered(">",
            pwmWarningRightButton.x + pwmWarningRightButton.width / 2,
            pwmWarningRightButton.y + pwmWarningRightButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)

        // PWM Current value
        val pwmCurrentName = settingsManager.getPwmWarningName()
        ui.textCentered(pwmCurrentName, centerX, pwmSettingY, UIFonts.body, UITheme.accent)

        // AVAS label
        val avasLabelY = avasSettingY + 70f * scale
        ui.textCentered("AVAS", centerX, avasLabelY, UIFonts.body, UITheme.textSecondary)

        // AVAS Arrow symbols
        ui.textCentered("<",
            avasLeftButton.x + avasLeftButton.width / 2,
            avasLeftButton.y + avasLeftButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)

        ui.textCentered(">",
            avasRightButton.x + avasRightButton.width / 2,
            avasRightButton.y + avasRightButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)

        // AVAS Current value
        val avasCurrentName = settingsManager.getAvasModeName()
        ui.textCentered(avasCurrentName, centerX, avasSettingY, UIFonts.body, UITheme.accent)

        // Beeps Checkbox label
        UIFonts.body.color = UITheme.textPrimary
        UIFonts.body.draw(ui.batch, "Beeps", beepsCheckbox.x + beepsCheckbox.width + 24f * scale,
            beepsCheckbox.y + beepsCheckbox.height / 2 + UIFonts.body.lineHeight / 3)

        // FPS Checkbox label
        UIFonts.body.color = UITheme.textPrimary
        UIFonts.body.draw(ui.batch, "Show FPS", fpsCheckbox.x + fpsCheckbox.width + 24f * scale,
            fpsCheckbox.y + fpsCheckbox.height / 2 + UIFonts.body.lineHeight / 3)

        // Back button text
        ui.textCentered("BACK", backButton.x + backButton.width / 2, backButton.y + backButton.height / 2,
            UIFonts.button, UITheme.textPrimary)

        // Hint at bottom
        ui.textCentered("Higher distance = better view, lower FPS", centerX, panelY + 50f * scale,
            UIFonts.caption, UITheme.textMuted)

        ui.endBatch()

        // === Handle Input ===
        val pwmCurrentIndex = settingsManager.getPwmWarningIndex()
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
            if (beepsCheckbox.contains(touchX, touchY)) {
                settingsManager.beepsEnabled = !settingsManager.beepsEnabled
            }
            val avasCurrentIndex = settingsManager.getAvasModeIndex()
            if (avasLeftButton.contains(touchX, touchY)) {
                val newIndex = (avasCurrentIndex - 1).coerceAtLeast(0)
                settingsManager.setAvasModeByIndex(newIndex)
            }
            if (avasRightButton.contains(touchX, touchY)) {
                val newIndex = (avasCurrentIndex + 1).coerceAtMost(SettingsManager.AVAS_OPTIONS.size - 1)
                settingsManager.setAvasModeByIndex(newIndex)
            }
            if (pwmWarningLeftButton.contains(touchX, touchY)) {
                val newIndex = (pwmCurrentIndex - 1).coerceAtLeast(0)
                settingsManager.setPwmWarningByIndex(newIndex)
            }
            if (pwmWarningRightButton.contains(touchX, touchY)) {
                val newIndex = (pwmCurrentIndex + 1).coerceAtMost(SettingsManager.PWM_WARNING_OPTIONS.size - 1)
                settingsManager.setPwmWarningByIndex(newIndex)
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
