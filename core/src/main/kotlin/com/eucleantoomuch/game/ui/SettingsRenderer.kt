package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.state.SettingsManager

/**
 * Modern settings screen with large, touch-friendly controls.
 * Two-column layout for better space usage.
 */
class SettingsRenderer(private val settingsManager: SettingsManager) : Disposable {
    private val ui = UIRenderer()

    private val backButton = Rectangle()

    // Left column controls
    private val renderDistanceLeftButton = Rectangle()
    private val renderDistanceRightButton = Rectangle()
    private val maxFpsLeftButton = Rectangle()
    private val maxFpsRightButton = Rectangle()
    private val pwmWarningLeftButton = Rectangle()
    private val pwmWarningRightButton = Rectangle()

    // Right column controls
    private val avasLeftButton = Rectangle()
    private val avasRightButton = Rectangle()
    private val beepsCheckbox = Rectangle()
    private val musicCheckbox = Rectangle()
    private val fpsCheckbox = Rectangle()

    private val privacyPolicyButton = Rectangle()
    private val termsButton = Rectangle()

    // Hover states - left column
    private var backButtonHover = 0f
    private var renderLeftButtonHover = 0f
    private var renderRightButtonHover = 0f
    private var maxFpsLeftButtonHover = 0f
    private var maxFpsRightButtonHover = 0f
    private var pwmLeftButtonHover = 0f
    private var pwmRightButtonHover = 0f

    // Hover states - right column
    private var avasLeftButtonHover = 0f
    private var avasRightButtonHover = 0f
    private var beepsCheckboxHover = 0f
    private var musicCheckboxHover = 0f
    private var fpsCheckboxHover = 0f

    private var privacyButtonHover = 0f
    private var termsButtonHover = 0f
    private var enterAnimProgress = 0f

    companion object {
        private const val PRIVACY_POLICY_URL = "https://crcknaka.github.io/euc-lean-too-much/privacy-policy.html"
        private const val TERMS_URL = "https://crcknaka.github.io/euc-lean-too-much/terms-of-service.html"
    }

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

        // Update all hover states
        val backHovered = backButton.contains(touchX, touchY)
        val renderLeftHovered = renderDistanceLeftButton.contains(touchX, touchY)
        val renderRightHovered = renderDistanceRightButton.contains(touchX, touchY)
        val maxFpsLeftHovered = maxFpsLeftButton.contains(touchX, touchY)
        val maxFpsRightHovered = maxFpsRightButton.contains(touchX, touchY)
        val pwmLeftHovered = pwmWarningLeftButton.contains(touchX, touchY)
        val pwmRightHovered = pwmWarningRightButton.contains(touchX, touchY)
        val avasLeftHovered = avasLeftButton.contains(touchX, touchY)
        val avasRightHovered = avasRightButton.contains(touchX, touchY)
        val beepsHovered = beepsCheckbox.contains(touchX, touchY)
        val musicHovered = musicCheckbox.contains(touchX, touchY)
        val fpsHovered = fpsCheckbox.contains(touchX, touchY)
        val privacyHovered = privacyPolicyButton.contains(touchX, touchY)
        val termsHovered = termsButton.contains(touchX, touchY)

        backButtonHover = UITheme.Anim.ease(backButtonHover, if (backHovered) 1f else 0f, 10f)
        renderLeftButtonHover = UITheme.Anim.ease(renderLeftButtonHover, if (renderLeftHovered) 1f else 0f, 10f)
        renderRightButtonHover = UITheme.Anim.ease(renderRightButtonHover, if (renderRightHovered) 1f else 0f, 10f)
        maxFpsLeftButtonHover = UITheme.Anim.ease(maxFpsLeftButtonHover, if (maxFpsLeftHovered) 1f else 0f, 10f)
        maxFpsRightButtonHover = UITheme.Anim.ease(maxFpsRightButtonHover, if (maxFpsRightHovered) 1f else 0f, 10f)
        pwmLeftButtonHover = UITheme.Anim.ease(pwmLeftButtonHover, if (pwmLeftHovered) 1f else 0f, 10f)
        pwmRightButtonHover = UITheme.Anim.ease(pwmRightButtonHover, if (pwmRightHovered) 1f else 0f, 10f)
        avasLeftButtonHover = UITheme.Anim.ease(avasLeftButtonHover, if (avasLeftHovered) 1f else 0f, 10f)
        avasRightButtonHover = UITheme.Anim.ease(avasRightButtonHover, if (avasRightHovered) 1f else 0f, 10f)
        beepsCheckboxHover = UITheme.Anim.ease(beepsCheckboxHover, if (beepsHovered) 1f else 0f, 10f)
        musicCheckboxHover = UITheme.Anim.ease(musicCheckboxHover, if (musicHovered) 1f else 0f, 10f)
        fpsCheckboxHover = UITheme.Anim.ease(fpsCheckboxHover, if (fpsHovered) 1f else 0f, 10f)
        privacyButtonHover = UITheme.Anim.ease(privacyButtonHover, if (privacyHovered) 1f else 0f, 10f)
        termsButtonHover = UITheme.Anim.ease(termsButtonHover, if (termsHovered) 1f else 0f, 10f)

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

        // Main settings panel - wider for two columns
        val panelWidth = 900f * scale * enterAnimProgress
        val panelHeight = 580f * scale * enterAnimProgress
        val panelX = centerX - panelWidth / 2
        val panelY = sh / 2 - panelHeight / 2 + 40f * scale

        ui.panel(panelX, panelY, panelWidth, panelHeight,
            radius = UITheme.Dimensions.panelRadius,
            backgroundColor = UITheme.surface)

        // Column dimensions
        val columnWidth = panelWidth / 2 - 30f * scale
        val leftColumnX = panelX + 30f * scale
        val rightColumnX = centerX + 15f * scale
        val rowHeight = 110f * scale
        val arrowButtonSize = UITheme.Dimensions.arrowButtonSize
        val valueBoxWidth = 180f * scale
        val checkboxSize = UITheme.Dimensions.checkboxSize

        // === LEFT COLUMN ===
        val leftColCenterX = leftColumnX + columnWidth / 2

        // Render Distance Setting
        val renderDistanceY = panelY + panelHeight - 160f * scale
        renderSelectorSetting(
            leftColCenterX, renderDistanceY, valueBoxWidth, arrowButtonSize,
            renderDistanceLeftButton, renderDistanceRightButton,
            renderLeftButtonHover, renderRightButtonHover
        )

        // Max FPS Setting
        val maxFpsY = renderDistanceY - rowHeight
        renderSelectorSetting(
            leftColCenterX, maxFpsY, valueBoxWidth, arrowButtonSize,
            maxFpsLeftButton, maxFpsRightButton,
            maxFpsLeftButtonHover, maxFpsRightButtonHover
        )

        // PWM Warning Setting
        val pwmY = maxFpsY - rowHeight
        renderSelectorSetting(
            leftColCenterX, pwmY, valueBoxWidth, arrowButtonSize,
            pwmWarningLeftButton, pwmWarningRightButton,
            pwmLeftButtonHover, pwmRightButtonHover
        )

        // === RIGHT COLUMN ===
        val rightColCenterX = rightColumnX + columnWidth / 2

        // AVAS Setting
        val avasY = panelY + panelHeight - 160f * scale
        renderSelectorSetting(
            rightColCenterX, avasY, valueBoxWidth, arrowButtonSize,
            avasLeftButton, avasRightButton,
            avasLeftButtonHover, avasRightButtonHover
        )

        // Checkboxes row positions
        val checkboxRowY = avasY - rowHeight
        val checkboxLabelOffset = checkboxSize + 16f * scale

        // Music Checkbox
        val musicCheckboxX = rightColumnX + 20f * scale
        musicCheckbox.set(musicCheckboxX, checkboxRowY - checkboxSize / 2, checkboxSize, checkboxSize)
        renderCheckbox(musicCheckbox, musicCheckboxHover, settingsManager.musicEnabled)

        // Beeps Checkbox
        val beepsCheckboxX = rightColumnX + 20f * scale
        val beepsY = checkboxRowY - 70f * scale
        beepsCheckbox.set(beepsCheckboxX, beepsY - checkboxSize / 2, checkboxSize, checkboxSize)
        renderCheckbox(beepsCheckbox, beepsCheckboxHover, settingsManager.beepsEnabled)

        // FPS Checkbox
        val fpsY = beepsY - 70f * scale
        fpsCheckbox.set(beepsCheckboxX, fpsY - checkboxSize / 2, checkboxSize, checkboxSize)
        renderCheckbox(fpsCheckbox, fpsCheckboxHover, settingsManager.showFps)

        // Back button
        val buttonWidth = 340f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        backButton.set(centerX - buttonWidth / 2, panelY - buttonHeight - 36f * scale, buttonWidth, buttonHeight)
        ui.button(backButton, UITheme.accent, glowIntensity = backButtonHover * 0.8f)

        // Privacy Policy and Terms buttons
        val linkButtonWidth = 160f * scale
        val linkButtonHeight = 40f * scale
        val linkButtonY = panelY + 15f * scale
        val linkButtonSpacing = 20f * scale

        privacyPolicyButton.set(
            centerX - linkButtonWidth - linkButtonSpacing / 2,
            linkButtonY,
            linkButtonWidth,
            linkButtonHeight
        )
        ui.panel(
            privacyPolicyButton.x, privacyPolicyButton.y,
            privacyPolicyButton.width, privacyPolicyButton.height,
            radius = 8f * scale,
            backgroundColor = UITheme.surfaceLight,
            shadowOffset = 0f
        )

        termsButton.set(
            centerX + linkButtonSpacing / 2,
            linkButtonY,
            linkButtonWidth,
            linkButtonHeight
        )
        ui.panel(
            termsButton.x, termsButton.y,
            termsButton.width, termsButton.height,
            radius = 8f * scale,
            backgroundColor = UITheme.surfaceLight,
            shadowOffset = 0f
        )

        ui.endShapes()

        // === Draw Text ===
        ui.beginBatch()

        // Title
        val titleY = panelY + panelHeight - 55f * scale
        ui.textCentered("SETTINGS", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // === LEFT COLUMN TEXT ===

        // Render Distance
        ui.textCentered("Render Distance", leftColCenterX, renderDistanceY + 55f * scale, UIFonts.body, UITheme.textSecondary)
        renderArrowText(renderDistanceLeftButton, renderDistanceRightButton)
        val rdName = settingsManager.getRenderDistanceName()
        val rdDist = settingsManager.renderDistance.toInt()
        ui.textCentered("$rdName (${rdDist}m)", leftColCenterX, renderDistanceY, UIFonts.body, UITheme.accent)

        // Max FPS
        ui.textCentered("Max FPS", leftColCenterX, maxFpsY + 55f * scale, UIFonts.body, UITheme.textSecondary)
        renderArrowText(maxFpsLeftButton, maxFpsRightButton)
        ui.textCentered(settingsManager.getMaxFpsName(), leftColCenterX, maxFpsY, UIFonts.body, UITheme.accent)

        // PWM Warning
        ui.textCentered("PWM Warning", leftColCenterX, pwmY + 55f * scale, UIFonts.body, UITheme.textSecondary)
        renderArrowText(pwmWarningLeftButton, pwmWarningRightButton)
        val pwmTextColor = if (settingsManager.pwmWarning == 95) UITheme.danger else UITheme.accent
        ui.textCentered(settingsManager.getPwmWarningName(), leftColCenterX, pwmY, UIFonts.body, pwmTextColor)

        // === RIGHT COLUMN TEXT ===

        // AVAS
        ui.textCentered("AVAS", rightColCenterX, avasY + 55f * scale, UIFonts.body, UITheme.textSecondary)
        renderArrowText(avasLeftButton, avasRightButton)
        ui.textCentered(settingsManager.getAvasModeName(), rightColCenterX, avasY, UIFonts.body, UITheme.accent)

        // Checkbox labels
        UIFonts.body.color = UITheme.textPrimary
        UIFonts.body.draw(ui.batch, "Music", musicCheckbox.x + checkboxLabelOffset,
            musicCheckbox.y + musicCheckbox.height / 2 + UIFonts.body.lineHeight / 3)

        UIFonts.body.draw(ui.batch, "Beeps", beepsCheckbox.x + checkboxLabelOffset,
            beepsCheckbox.y + beepsCheckbox.height / 2 + UIFonts.body.lineHeight / 3)

        UIFonts.body.draw(ui.batch, "Show FPS", fpsCheckbox.x + checkboxLabelOffset,
            fpsCheckbox.y + fpsCheckbox.height / 2 + UIFonts.body.lineHeight / 3)

        // Back button text
        ui.textCentered("BACK", backButton.x + backButton.width / 2, backButton.y + backButton.height / 2,
            UIFonts.button, UITheme.textPrimary)

        // Privacy Policy and Terms link text
        val linkTextColor = if (privacyButtonHover > 0.5f) UITheme.accent else UITheme.textMuted
        val termsTextColor = if (termsButtonHover > 0.5f) UITheme.accent else UITheme.textMuted
        ui.textCentered("Privacy Policy",
            privacyPolicyButton.x + privacyPolicyButton.width / 2,
            privacyPolicyButton.y + privacyPolicyButton.height / 2,
            UIFonts.caption, linkTextColor)
        ui.textCentered("Terms",
            termsButton.x + termsButton.width / 2,
            termsButton.y + termsButton.height / 2,
            UIFonts.caption, termsTextColor)

        ui.endBatch()

        // === Handle Input ===
        if (Gdx.input.justTouched()) {
            if (backButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return Action.BACK
            }

            // Left column - Render Distance
            val rdIndex = settingsManager.getRenderDistanceIndex()
            if (renderDistanceLeftButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.setRenderDistanceByIndex((rdIndex - 1).coerceAtLeast(0))
            }
            if (renderDistanceRightButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.setRenderDistanceByIndex((rdIndex + 1).coerceAtMost(SettingsManager.RENDER_DISTANCE_OPTIONS.size - 1))
            }

            // Left column - Max FPS
            val fpsIndex = settingsManager.getMaxFpsIndex()
            if (maxFpsLeftButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.setMaxFpsByIndex((fpsIndex - 1).coerceAtLeast(0))
            }
            if (maxFpsRightButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.setMaxFpsByIndex((fpsIndex + 1).coerceAtMost(SettingsManager.MAX_FPS_OPTIONS.size - 1))
            }

            // Left column - PWM Warning
            val pwmIndex = settingsManager.getPwmWarningIndex()
            if (pwmWarningLeftButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.setPwmWarningByIndex((pwmIndex - 1).coerceAtLeast(0))
            }
            if (pwmWarningRightButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.setPwmWarningByIndex((pwmIndex + 1).coerceAtMost(SettingsManager.PWM_WARNING_OPTIONS.size - 1))
            }

            // Right column - AVAS
            val avasIndex = settingsManager.getAvasModeIndex()
            if (avasLeftButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.setAvasModeByIndex((avasIndex - 1).coerceAtLeast(0))
            }
            if (avasRightButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.setAvasModeByIndex((avasIndex + 1).coerceAtMost(SettingsManager.AVAS_OPTIONS.size - 1))
            }

            // Right column - Checkboxes
            if (musicCheckbox.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.musicEnabled = !settingsManager.musicEnabled
            }
            if (beepsCheckbox.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.beepsEnabled = !settingsManager.beepsEnabled
            }
            if (fpsCheckbox.contains(touchX, touchY)) {
                UIFeedback.tap()
                settingsManager.showFps = !settingsManager.showFps
            }

            // Links
            if (privacyPolicyButton.contains(touchX, touchY)) {
                UIFeedback.click()
                Gdx.net.openURI(PRIVACY_POLICY_URL)
            }
            if (termsButton.contains(touchX, touchY)) {
                UIFeedback.click()
                Gdx.net.openURI(TERMS_URL)
            }
        }

        // Keyboard shortcuts
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACK)) {
            UIFeedback.click()
            return Action.BACK
        }

        return Action.NONE
    }

    private fun renderSelectorSetting(
        centerX: Float, y: Float, valueBoxWidth: Float, arrowButtonSize: Float,
        leftButton: Rectangle, rightButton: Rectangle,
        leftHover: Float, rightHover: Float
    ) {
        val scale = UITheme.Dimensions.scale()

        leftButton.set(
            centerX - valueBoxWidth / 2 - arrowButtonSize - 12f * scale,
            y - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(leftButton, UITheme.secondary, glowIntensity = leftHover * 0.6f)

        rightButton.set(
            centerX + valueBoxWidth / 2 + 12f * scale,
            y - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.button(rightButton, UITheme.secondary, glowIntensity = rightHover * 0.6f)

        // Value box
        ui.panel(
            centerX - valueBoxWidth / 2,
            y - 35f * scale,
            valueBoxWidth,
            70f * scale,
            radius = 12f * scale,
            backgroundColor = UITheme.surfaceLight,
            shadowOffset = 0f
        )
    }

    private fun renderArrowText(leftButton: Rectangle, rightButton: Rectangle) {
        ui.textCentered("<",
            leftButton.x + leftButton.width / 2,
            leftButton.y + leftButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)
        ui.textCentered(">",
            rightButton.x + rightButton.width / 2,
            rightButton.y + rightButton.height / 2,
            UIFonts.heading, UITheme.textPrimary)
    }

    private fun renderCheckbox(checkbox: Rectangle, hoverAnim: Float, checked: Boolean) {
        val scale = UITheme.Dimensions.scale()

        // Glow on hover
        if (hoverAnim > 0.1f) {
            ui.shapes.color = UITheme.withAlpha(UITheme.accent, hoverAnim * 0.25f)
            ui.roundedRect(checkbox.x - 5f, checkbox.y - 5f,
                checkbox.width + 10f, checkbox.height + 10f, 14f * scale, ui.shapes.color)
        }

        val checkboxColor = if (checked) UITheme.accent else UITheme.surfaceLight
        ui.roundedRect(checkbox.x, checkbox.y, checkbox.width, checkbox.height, 12f * scale, checkboxColor)

        // Checkmark
        if (checked) {
            ui.shapes.color = UITheme.textPrimary
            val cx = checkbox.x + checkbox.width / 2
            val cy = checkbox.y + checkbox.height / 2
            val checkScale = checkbox.width / 52f
            ui.shapes.rectLine(cx - 14f * checkScale, cy, cx - 4f * checkScale, cy - 14f * checkScale, 4f * checkScale)
            ui.shapes.rectLine(cx - 4f * checkScale, cy - 14f * checkScale, cx + 16f * checkScale, cy + 10f * checkScale, 4f * checkScale)
        }
    }

    fun resize(width: Int, height: Int) {
        ui.resize(width, height)
        enterAnimProgress = 0f
    }

    fun recreate() {
        ui.recreate()
    }

    override fun dispose() {
        ui.dispose()
    }
}
