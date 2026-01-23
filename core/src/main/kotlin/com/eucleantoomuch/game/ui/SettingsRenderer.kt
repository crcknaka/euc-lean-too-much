package com.eucleantoomuch.game.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.utils.Disposable
import com.eucleantoomuch.game.state.SettingsManager

/**
 * Modern settings screen with tab-based navigation.
 * Graphics tab: Render Distance, Max FPS, Show FPS
 * Gameplay tab: AVAS, Music, Beeps
 */
class SettingsRenderer(private val settingsManager: SettingsManager) : Disposable {
    private val ui = UIRenderer()

    // Tabs
    private var currentTab = 0  // 0 = Graphics, 1 = Gameplay
    private val graphicsTabButton = Rectangle()
    private val gameplayTabButton = Rectangle()

    // Back button
    private val backButton = Rectangle()

    // Graphics tab controls
    private val graphicsPresetLeftButton = Rectangle()
    private val graphicsPresetRightButton = Rectangle()
    private val renderDistanceLeftButton = Rectangle()
    private val renderDistanceRightButton = Rectangle()
    private val maxFpsLeftButton = Rectangle()
    private val maxFpsRightButton = Rectangle()
    private val shadowsCheckbox = Rectangle()
    private val fpsCheckbox = Rectangle()

    // Gameplay tab controls
    private val avasLeftButton = Rectangle()
    private val avasRightButton = Rectangle()
    private val pwmWarningLeftButton = Rectangle()
    private val pwmWarningRightButton = Rectangle()
    private val musicCheckbox = Rectangle()
    private val beepsCheckbox = Rectangle()
    private val noHudCheckbox = Rectangle()

    // Footer links
    private val privacyPolicyButton = Rectangle()
    private val termsButton = Rectangle()

    // Hover states
    private var backButtonHover = 0f
    private var graphicsTabHover = 0f
    private var gameplayTabHover = 0f

    // Graphics tab hovers
    private var presetLeftButtonHover = 0f
    private var presetRightButtonHover = 0f
    private var renderLeftButtonHover = 0f
    private var renderRightButtonHover = 0f
    private var maxFpsLeftButtonHover = 0f
    private var maxFpsRightButtonHover = 0f
    private var shadowsCheckboxHover = 0f
    private var fpsCheckboxHover = 0f

    // Gameplay tab hovers
    private var avasLeftButtonHover = 0f
    private var avasRightButtonHover = 0f
    private var pwmWarningLeftButtonHover = 0f
    private var pwmWarningRightButtonHover = 0f
    private var musicCheckboxHover = 0f
    private var beepsCheckboxHover = 0f
    private var noHudCheckboxHover = 0f

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

        // Update hover states
        val backHovered = backButton.contains(touchX, touchY)
        val graphicsTabHovered = graphicsTabButton.contains(touchX, touchY)
        val gameplayTabHovered = gameplayTabButton.contains(touchX, touchY)
        val privacyHovered = privacyPolicyButton.contains(touchX, touchY)
        val termsHovered = termsButton.contains(touchX, touchY)

        backButtonHover = UITheme.Anim.ease(backButtonHover, if (backHovered) 1f else 0f, 10f)
        graphicsTabHover = UITheme.Anim.ease(graphicsTabHover, if (graphicsTabHovered) 1f else 0f, 10f)
        gameplayTabHover = UITheme.Anim.ease(gameplayTabHover, if (gameplayTabHovered) 1f else 0f, 10f)
        privacyButtonHover = UITheme.Anim.ease(privacyButtonHover, if (privacyHovered) 1f else 0f, 10f)
        termsButtonHover = UITheme.Anim.ease(termsButtonHover, if (termsHovered) 1f else 0f, 10f)

        // Tab-specific hover updates
        if (currentTab == 0) {
            val presetLeftHovered = graphicsPresetLeftButton.contains(touchX, touchY)
            val presetRightHovered = graphicsPresetRightButton.contains(touchX, touchY)
            val renderLeftHovered = renderDistanceLeftButton.contains(touchX, touchY)
            val renderRightHovered = renderDistanceRightButton.contains(touchX, touchY)
            val maxFpsLeftHovered = maxFpsLeftButton.contains(touchX, touchY)
            val maxFpsRightHovered = maxFpsRightButton.contains(touchX, touchY)
            val shadowsHovered = shadowsCheckbox.contains(touchX, touchY)
            val fpsHovered = fpsCheckbox.contains(touchX, touchY)

            presetLeftButtonHover = UITheme.Anim.ease(presetLeftButtonHover, if (presetLeftHovered) 1f else 0f, 10f)
            presetRightButtonHover = UITheme.Anim.ease(presetRightButtonHover, if (presetRightHovered) 1f else 0f, 10f)
            renderLeftButtonHover = UITheme.Anim.ease(renderLeftButtonHover, if (renderLeftHovered) 1f else 0f, 10f)
            renderRightButtonHover = UITheme.Anim.ease(renderRightButtonHover, if (renderRightHovered) 1f else 0f, 10f)
            maxFpsLeftButtonHover = UITheme.Anim.ease(maxFpsLeftButtonHover, if (maxFpsLeftHovered) 1f else 0f, 10f)
            maxFpsRightButtonHover = UITheme.Anim.ease(maxFpsRightButtonHover, if (maxFpsRightHovered) 1f else 0f, 10f)
            shadowsCheckboxHover = UITheme.Anim.ease(shadowsCheckboxHover, if (shadowsHovered) 1f else 0f, 10f)
            fpsCheckboxHover = UITheme.Anim.ease(fpsCheckboxHover, if (fpsHovered) 1f else 0f, 10f)
        } else {
            val avasLeftHovered = avasLeftButton.contains(touchX, touchY)
            val avasRightHovered = avasRightButton.contains(touchX, touchY)
            val pwmLeftHovered = pwmWarningLeftButton.contains(touchX, touchY)
            val pwmRightHovered = pwmWarningRightButton.contains(touchX, touchY)
            val musicHovered = musicCheckbox.contains(touchX, touchY)
            val beepsHovered = beepsCheckbox.contains(touchX, touchY)
            val noHudHovered = noHudCheckbox.contains(touchX, touchY)

            avasLeftButtonHover = UITheme.Anim.ease(avasLeftButtonHover, if (avasLeftHovered) 1f else 0f, 10f)
            avasRightButtonHover = UITheme.Anim.ease(avasRightButtonHover, if (avasRightHovered) 1f else 0f, 10f)
            pwmWarningLeftButtonHover = UITheme.Anim.ease(pwmWarningLeftButtonHover, if (pwmLeftHovered) 1f else 0f, 10f)
            pwmWarningRightButtonHover = UITheme.Anim.ease(pwmWarningRightButtonHover, if (pwmRightHovered) 1f else 0f, 10f)
            musicCheckboxHover = UITheme.Anim.ease(musicCheckboxHover, if (musicHovered) 1f else 0f, 10f)
            beepsCheckboxHover = UITheme.Anim.ease(beepsCheckboxHover, if (beepsHovered) 1f else 0f, 10f)
            noHudCheckboxHover = UITheme.Anim.ease(noHudCheckboxHover, if (noHudHovered) 1f else 0f, 10f)
        }

        // === Draw Background ===
        ui.beginShapes()

        // Full gradient background
        ui.gradientBackground()

        // === CENTERED COMPACT LAYOUT with breathing room ===
        // Panel is narrower, centered, with lots of space on sides
        val panelMarginY = 35f * scale
        val maxPanelWidth = 980f * scale  // Max width for comfortable reading
        val panelWidth = (sw * 0.82f).coerceAtMost(maxPanelWidth) * enterAnimProgress
        val panelHeight = (sh - panelMarginY * 2) * enterAnimProgress
        val panelX = centerX - panelWidth / 2
        val panelY = panelMarginY

        // Main glass panel with accent glow
        ui.glassPanel(panelX, panelY, panelWidth, panelHeight,
            radius = UITheme.Dimensions.panelRadius,
            tintColor = UITheme.surfaceSolid,
            borderGlow = UITheme.accent)

        // Title at top center with good padding
        val titleY = panelY + panelHeight - 55f * scale

        // === TABS - horizontal at top, below title ===
        val tabWidth = 210f * scale
        val tabHeight = 68f * scale
        val tabGap = 18f * scale
        val tabsY = titleY - 115f * scale  // Moved down

        // Graphics tab (left) - using neonButton
        graphicsTabButton.set(centerX - tabWidth - tabGap / 2, tabsY, tabWidth, tabHeight)
        val graphicsTabColor = if (currentTab == 0) UITheme.accent else UITheme.surfaceLight
        val graphicsTabGlow = if (currentTab == 0) 0.6f else graphicsTabHover * 0.4f
        ui.neonButton(graphicsTabButton, graphicsTabColor, UITheme.accent, graphicsTabGlow)

        // Gameplay tab (right) - using neonButton
        gameplayTabButton.set(centerX + tabGap / 2, tabsY, tabWidth, tabHeight)
        val gameplayTabColor = if (currentTab == 1) UITheme.accent else UITheme.surfaceLight
        val gameplayTabGlow = if (currentTab == 1) 0.6f else gameplayTabHover * 0.4f
        ui.neonButton(gameplayTabButton, gameplayTabColor, UITheme.accent, gameplayTabGlow)

        // === CONTENT AREA - Two columns ===
        val contentStartY = tabsY - 90f * scale
        val contentWidth = panelWidth - 100f * scale
        val contentX = panelX + 50f * scale
        val leftColCenterX = contentX + contentWidth * 0.32f
        val rightColCenterX = contentX + contentWidth * 0.75f
        val rowHeight = 110f * scale
        val arrowButtonSize = 64f * scale
        val valueBoxWidth = 170f * scale
        val checkboxSize = 64f * scale

        if (currentTab == 0) {
            // === GRAPHICS TAB ===
            // Row 1: Graphics Quality (left) | Shadows (right)
            val row1Y = contentStartY - 25f * scale
            renderSelectorSettingLarge(
                leftColCenterX, row1Y, valueBoxWidth, arrowButtonSize,
                graphicsPresetLeftButton, graphicsPresetRightButton,
                presetLeftButtonHover, presetRightButtonHover
            )
            shadowsCheckbox.set(rightColCenterX - checkboxSize / 2, row1Y - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(shadowsCheckbox, shadowsCheckboxHover, settingsManager.shadowsEnabled)

            // Row 2: Render Distance (left) | Show FPS (right)
            val row2Y = row1Y - rowHeight
            renderSelectorSettingLarge(
                leftColCenterX, row2Y, valueBoxWidth, arrowButtonSize,
                renderDistanceLeftButton, renderDistanceRightButton,
                renderLeftButtonHover, renderRightButtonHover
            )
            fpsCheckbox.set(rightColCenterX - checkboxSize / 2, row2Y - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(fpsCheckbox, fpsCheckboxHover, settingsManager.showFps)

            // Row 3: Max FPS (left only)
            val row3Y = row2Y - rowHeight
            renderSelectorSettingLarge(
                leftColCenterX, row3Y, valueBoxWidth, arrowButtonSize,
                maxFpsLeftButton, maxFpsRightButton,
                maxFpsLeftButtonHover, maxFpsRightButtonHover
            )

        } else {
            // === GAMEPLAY TAB ===
            // Row 1: AVAS (left) | Music (right)
            val row1Y = contentStartY - 25f * scale
            renderSelectorSettingLarge(
                leftColCenterX, row1Y, valueBoxWidth, arrowButtonSize,
                avasLeftButton, avasRightButton,
                avasLeftButtonHover, avasRightButtonHover
            )
            musicCheckbox.set(rightColCenterX - checkboxSize / 2, row1Y - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(musicCheckbox, musicCheckboxHover, settingsManager.musicEnabled)

            // Row 2: PWM Warning (left) | Beeps (right)
            val row2Y = row1Y - rowHeight
            renderSelectorSettingLarge(
                leftColCenterX, row2Y, valueBoxWidth, arrowButtonSize,
                pwmWarningLeftButton, pwmWarningRightButton,
                pwmWarningLeftButtonHover, pwmWarningRightButtonHover
            )
            beepsCheckbox.set(rightColCenterX - checkboxSize / 2, row2Y - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(beepsCheckbox, beepsCheckboxHover, settingsManager.beepsEnabled)

            // Row 3: empty (left) | No HUD (right)
            val row3Y = row2Y - rowHeight
            noHudCheckbox.set(rightColCenterX - checkboxSize / 2, row3Y - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(noHudCheckbox, noHudCheckboxHover, settingsManager.noHud)
        }

        // === BOTTOM: Back button and links ===
        val buttonWidth = 260f * scale
        val buttonHeight = 80f * scale
        val bottomY = panelY + 30f * scale
        backButton.set(centerX - buttonWidth / 2, bottomY, buttonWidth, buttonHeight)
        ui.neonButton(backButton, UITheme.surfaceLight, UITheme.cyan, backButtonHover * 0.6f)

        // Footer links - on sides of back button
        val linkButtonHeight = 50f * scale
        val linkButtonWidth = 140f * scale
        val linkGap = 18f * scale

        // Privacy on the left of Back
        privacyPolicyButton.set(backButton.x - linkButtonWidth - linkGap, bottomY + (buttonHeight - linkButtonHeight) / 2, linkButtonWidth, linkButtonHeight)
        val privacyGlow = privacyButtonHover * 0.4f
        ui.neonButton(privacyPolicyButton, UITheme.surfaceLight, UITheme.cyan, privacyGlow)

        // Terms on the right of Back
        termsButton.set(backButton.x + buttonWidth + linkGap, bottomY + (buttonHeight - linkButtonHeight) / 2, linkButtonWidth, linkButtonHeight)
        val termsGlow = termsButtonHover * 0.4f
        ui.neonButton(termsButton, UITheme.surfaceLight, UITheme.cyan, termsGlow)

        ui.endShapes()

        // === Draw Text ===
        ui.beginBatch()

        // Title at top
        ui.textCentered("SETTINGS", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Tab labels
        val tabTextColor = UITheme.textPrimary
        ui.textCentered("Graphics", graphicsTabButton.x + graphicsTabButton.width / 2,
            graphicsTabButton.y + graphicsTabButton.height / 2, UIFonts.button, tabTextColor)
        ui.textCentered("Gameplay", gameplayTabButton.x + gameplayTabButton.width / 2,
            gameplayTabButton.y + gameplayTabButton.height / 2, UIFonts.button, tabTextColor)

        // Back button text
        ui.textCentered("BACK", backButton.x + backButton.width / 2, backButton.y + backButton.height / 2,
            UIFonts.button, UITheme.textSecondary)

        // Footer link text
        val linkTextColor = UITheme.textMuted
        ui.textCentered("Privacy",
            privacyPolicyButton.x + privacyPolicyButton.width / 2,
            privacyPolicyButton.y + privacyPolicyButton.height / 2,
            UIFonts.caption, if (privacyButtonHover > 0.5f) UITheme.accent else linkTextColor)
        ui.textCentered("Terms",
            termsButton.x + termsButton.width / 2,
            termsButton.y + termsButton.height / 2,
            UIFonts.caption, if (termsButtonHover > 0.5f) UITheme.accent else linkTextColor)

        // Tab content text - two columns
        val checkboxLabelOffsetRight = checkboxSize / 2 + 35f * scale

        if (currentTab == 0) {
            // === GRAPHICS TAB TEXT ===
            val row1Y = contentStartY - 25f * scale
            ui.textCentered("Graphics Quality", leftColCenterX, row1Y + 52f * scale, UIFonts.body, UITheme.textSecondary)
            renderArrowText(graphicsPresetLeftButton, graphicsPresetRightButton)
            ui.textCentered(settingsManager.getGraphicsPresetName(), leftColCenterX, row1Y, UIFonts.body, UITheme.accent)
            UIFonts.body.color = UITheme.textPrimary
            UIFonts.body.draw(ui.batch, "Shadows", rightColCenterX + checkboxLabelOffsetRight,
                row1Y + UIFonts.body.lineHeight / 3)

            val row2Y = row1Y - rowHeight
            ui.textCentered("Render Distance", leftColCenterX, row2Y + 52f * scale, UIFonts.body, UITheme.textSecondary)
            renderArrowText(renderDistanceLeftButton, renderDistanceRightButton)
            ui.textCentered(settingsManager.getRenderDistanceName(), leftColCenterX, row2Y, UIFonts.body, UITheme.accent)
            UIFonts.body.color = UITheme.textPrimary
            UIFonts.body.draw(ui.batch, "Show FPS", rightColCenterX + checkboxLabelOffsetRight,
                row2Y + UIFonts.body.lineHeight / 3)

            val row3Y = row2Y - rowHeight
            ui.textCentered("Max FPS", leftColCenterX, row3Y + 52f * scale, UIFonts.body, UITheme.textSecondary)
            renderArrowText(maxFpsLeftButton, maxFpsRightButton)
            ui.textCentered(settingsManager.getMaxFpsName(), leftColCenterX, row3Y, UIFonts.body, UITheme.accent)

        } else {
            // === GAMEPLAY TAB TEXT ===
            val row1Y = contentStartY - 25f * scale
            ui.textCentered("AVAS Sound", leftColCenterX, row1Y + 52f * scale, UIFonts.body, UITheme.textSecondary)
            renderArrowText(avasLeftButton, avasRightButton)
            ui.textCentered(settingsManager.getAvasModeName(), leftColCenterX, row1Y, UIFonts.body, UITheme.accent)
            UIFonts.body.color = UITheme.textPrimary
            UIFonts.body.draw(ui.batch, "Music", rightColCenterX + checkboxLabelOffsetRight,
                row1Y + UIFonts.body.lineHeight / 3)

            val row2Y = row1Y - rowHeight
            ui.textCentered("PWM Beeps", leftColCenterX, row2Y + 52f * scale, UIFonts.body, UITheme.textSecondary)
            renderArrowText(pwmWarningLeftButton, pwmWarningRightButton)
            val pwmColor = if (settingsManager.pwmWarning >= 90) UITheme.danger else UITheme.accent
            ui.textCentered(settingsManager.getPwmWarningName(), leftColCenterX, row2Y, UIFonts.body, pwmColor)
            UIFonts.body.color = UITheme.textPrimary
            UIFonts.body.draw(ui.batch, "Beeps", rightColCenterX + checkboxLabelOffsetRight,
                row2Y + UIFonts.body.lineHeight / 3)

            val row3Y = row2Y - rowHeight
            UIFonts.body.color = UITheme.textPrimary
            UIFonts.body.draw(ui.batch, "No HUD", rightColCenterX + checkboxLabelOffsetRight,
                row3Y + UIFonts.body.lineHeight / 3)
        }

        ui.endBatch()

        // === Handle Input ===
        if (Gdx.input.justTouched()) {
            // Tab switching
            if (graphicsTabButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                currentTab = 0
            }
            if (gameplayTabButton.contains(touchX, touchY)) {
                UIFeedback.tap()
                currentTab = 1
            }

            // Back button
            if (backButton.contains(touchX, touchY)) {
                UIFeedback.click()
                return Action.BACK
            }

            // Tab-specific input
            if (currentTab == 0) {
                // Graphics tab

                // Graphics Preset
                val presetIndex = settingsManager.getGraphicsPresetIndex()
                if (graphicsPresetLeftButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setGraphicsPresetByIndex((presetIndex - 1).coerceAtLeast(0))
                }
                if (graphicsPresetRightButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setGraphicsPresetByIndex((presetIndex + 1).coerceAtMost(SettingsManager.GRAPHICS_PRESET_OPTIONS.size - 1))
                }

                // Render Distance
                val rdIndex = settingsManager.getRenderDistanceIndex()
                if (renderDistanceLeftButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setRenderDistanceByIndex((rdIndex - 1).coerceAtLeast(0))
                }
                if (renderDistanceRightButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setRenderDistanceByIndex((rdIndex + 1).coerceAtMost(SettingsManager.RENDER_DISTANCE_OPTIONS.size - 1))
                }

                // Max FPS
                val fpsIndex = settingsManager.getMaxFpsIndex()
                if (maxFpsLeftButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setMaxFpsByIndex((fpsIndex - 1).coerceAtLeast(0))
                }
                if (maxFpsRightButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setMaxFpsByIndex((fpsIndex + 1).coerceAtMost(SettingsManager.MAX_FPS_OPTIONS.size - 1))
                }

                if (shadowsCheckbox.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.shadowsEnabled = !settingsManager.shadowsEnabled
                }

                if (fpsCheckbox.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.showFps = !settingsManager.showFps
                }

            } else {
                // Gameplay tab
                val avasIndex = settingsManager.getAvasModeIndex()
                if (avasLeftButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setAvasModeByIndex((avasIndex - 1).coerceAtLeast(0))
                }
                if (avasRightButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setAvasModeByIndex((avasIndex + 1).coerceAtMost(SettingsManager.AVAS_OPTIONS.size - 1))
                }

                val pwmIndex = settingsManager.getPwmWarningIndex()
                if (pwmWarningLeftButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setPwmWarningByIndex((pwmIndex - 1).coerceAtLeast(0))
                }
                if (pwmWarningRightButton.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.setPwmWarningByIndex((pwmIndex + 1).coerceAtMost(SettingsManager.PWM_WARNING_OPTIONS.size - 1))
                }

                if (musicCheckbox.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.musicEnabled = !settingsManager.musicEnabled
                }
                if (beepsCheckbox.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.beepsEnabled = !settingsManager.beepsEnabled
                }
                if (noHudCheckbox.contains(touchX, touchY)) {
                    UIFeedback.tap()
                    settingsManager.noHud = !settingsManager.noHud
                }
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

        // Tab switching with keyboard
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            UIFeedback.tap()
            currentTab = (currentTab + 1) % 2
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
        ui.neonButton(leftButton, UITheme.secondary, UITheme.secondary, 0.2f + leftHover * 0.6f)

        rightButton.set(
            centerX + valueBoxWidth / 2 + 12f * scale,
            y - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.neonButton(rightButton, UITheme.secondary, UITheme.secondary, 0.2f + rightHover * 0.6f)

        // Value box with gradient card style
        ui.card(
            centerX - valueBoxWidth / 2,
            y - 32f * scale,
            valueBoxWidth,
            64f * scale,
            radius = 12f * scale,
            backgroundColor = UITheme.surfaceLight,
            glowColor = UITheme.accent,
            glowIntensity = 0.15f
        )
    }

    private fun renderSelectorSettingLarge(
        centerX: Float, y: Float, valueBoxWidth: Float, arrowButtonSize: Float,
        leftButton: Rectangle, rightButton: Rectangle,
        leftHover: Float, rightHover: Float
    ) {
        val scale = UITheme.Dimensions.scale()

        leftButton.set(
            centerX - valueBoxWidth / 2 - arrowButtonSize - 14f * scale,
            y - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.neonButton(leftButton, UITheme.secondary, UITheme.secondary, 0.25f + leftHover * 0.6f)

        rightButton.set(
            centerX + valueBoxWidth / 2 + 14f * scale,
            y - arrowButtonSize / 2,
            arrowButtonSize,
            arrowButtonSize
        )
        ui.neonButton(rightButton, UITheme.secondary, UITheme.secondary, 0.25f + rightHover * 0.6f)

        // Bigger value box with gradient card style
        val boxHeight = 64f * scale
        ui.card(
            centerX - valueBoxWidth / 2,
            y - boxHeight / 2,
            valueBoxWidth,
            boxHeight,
            radius = 14f * scale,
            backgroundColor = UITheme.surfaceLight,
            glowColor = UITheme.accent,
            glowIntensity = 0.2f
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

    private fun renderCheckbox(checkbox: Rectangle, @Suppress("UNUSED_PARAMETER") hoverAnim: Float, checked: Boolean) {
        val scale = UITheme.Dimensions.scale()

        // Clean shadow (no glow)
        ui.shapes.color = UITheme.withAlpha(com.badlogic.gdx.graphics.Color.BLACK, 0.3f)
        ui.roundedRect(checkbox.x + 2f * scale, checkbox.y - 3f * scale, checkbox.width, checkbox.height, 12f * scale, ui.shapes.color)

        // Checkbox background with gradient
        val checkboxColor = if (checked) UITheme.accent else UITheme.surfaceLight
        val bottomColor = UITheme.darken(checkboxColor, 0.08f)
        val topColor = UITheme.brighten(checkboxColor, 0.04f)

        // Draw gradient
        val segments = 4
        val segHeight = checkbox.height / segments
        for (i in 0 until segments) {
            val t = i.toFloat() / segments
            val segColor = UITheme.lerp(bottomColor, topColor, t)
            val segY = checkbox.y + i * segHeight
            if (i == 0 || i == segments - 1) {
                ui.roundedRect(checkbox.x, segY, checkbox.width, segHeight + 1, 12f * scale, segColor)
            } else {
                ui.shapes.color = segColor
                ui.shapes.rect(checkbox.x, segY, checkbox.width, segHeight + 1)
            }
        }

        // Subtle top highlight
        ui.shapes.color = UITheme.withAlpha(com.badlogic.gdx.graphics.Color.WHITE, 0.12f)
        ui.roundedRect(checkbox.x + 4f * scale, checkbox.y + checkbox.height - 5f * scale,
            checkbox.width - 8f * scale, 3f * scale, 1.5f * scale, ui.shapes.color)

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
