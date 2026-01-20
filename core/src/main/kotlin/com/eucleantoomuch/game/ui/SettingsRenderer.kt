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
            val musicHovered = musicCheckbox.contains(touchX, touchY)
            val beepsHovered = beepsCheckbox.contains(touchX, touchY)
            val noHudHovered = noHudCheckbox.contains(touchX, touchY)

            avasLeftButtonHover = UITheme.Anim.ease(avasLeftButtonHover, if (avasLeftHovered) 1f else 0f, 10f)
            avasRightButtonHover = UITheme.Anim.ease(avasRightButtonHover, if (avasRightHovered) 1f else 0f, 10f)
            musicCheckboxHover = UITheme.Anim.ease(musicCheckboxHover, if (musicHovered) 1f else 0f, 10f)
            beepsCheckboxHover = UITheme.Anim.ease(beepsCheckboxHover, if (beepsHovered) 1f else 0f, 10f)
            noHudCheckboxHover = UITheme.Anim.ease(noHudCheckboxHover, if (noHudHovered) 1f else 0f, 10f)
        }

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
        val panelWidth = 620f * scale * enterAnimProgress
        val panelHeight = 850f * scale * enterAnimProgress
        val panelX = centerX - panelWidth / 2
        val panelY = sh / 2 - panelHeight / 2 + 30f * scale

        ui.panel(panelX, panelY, panelWidth, panelHeight,
            radius = UITheme.Dimensions.panelRadius,
            backgroundColor = UITheme.surface)

        // === Tab Bar ===
        val tabBarY = panelY + panelHeight - 150f * scale
        val tabWidth = (panelWidth - 80f * scale) / 2
        val tabHeight = 54f * scale
        val tabStartX = panelX + 40f * scale

        // Graphics tab
        graphicsTabButton.set(tabStartX, tabBarY, tabWidth, tabHeight)
        val graphicsTabColor = if (currentTab == 0) UITheme.accent else UITheme.surfaceLight
        val graphicsTabGlow = if (currentTab == 0) 0.3f else graphicsTabHover * 0.2f
        ui.roundedRect(graphicsTabButton.x, graphicsTabButton.y, graphicsTabButton.width, graphicsTabButton.height,
            12f * scale, graphicsTabColor)
        if (graphicsTabGlow > 0.01f) {
            ui.shapes.color = UITheme.withAlpha(UITheme.accent, graphicsTabGlow)
            ui.roundedRect(graphicsTabButton.x - 2f, graphicsTabButton.y - 2f,
                graphicsTabButton.width + 4f, graphicsTabButton.height + 4f, 14f * scale, ui.shapes.color)
        }

        // Gameplay tab
        gameplayTabButton.set(tabStartX + tabWidth + 16f * scale, tabBarY, tabWidth, tabHeight)
        val gameplayTabColor = if (currentTab == 1) UITheme.accent else UITheme.surfaceLight
        val gameplayTabGlow = if (currentTab == 1) 0.3f else gameplayTabHover * 0.2f
        ui.roundedRect(gameplayTabButton.x, gameplayTabButton.y, gameplayTabButton.width, gameplayTabButton.height,
            12f * scale, gameplayTabColor)
        if (gameplayTabGlow > 0.01f) {
            ui.shapes.color = UITheme.withAlpha(UITheme.accent, gameplayTabGlow)
            ui.roundedRect(gameplayTabButton.x - 2f, gameplayTabButton.y - 2f,
                gameplayTabButton.width + 4f, gameplayTabButton.height + 4f, 14f * scale, ui.shapes.color)
        }

        // === Tab Content Area ===
        val contentStartY = tabBarY - 70f * scale
        val contentCenterX = panelX + panelWidth / 2
        val rowHeight = 130f * scale
        val arrowButtonSize = UITheme.Dimensions.arrowButtonSize
        val valueBoxWidth = 160f * scale
        val checkboxSize = UITheme.Dimensions.checkboxSize

        if (currentTab == 0) {
            // === GRAPHICS TAB ===

            // Graphics Preset (Normal / High)
            val graphicsPresetY = contentStartY - 50f * scale
            renderSelectorSetting(
                contentCenterX, graphicsPresetY, valueBoxWidth, arrowButtonSize,
                graphicsPresetLeftButton, graphicsPresetRightButton,
                presetLeftButtonHover, presetRightButtonHover
            )

            // Render Distance
            val renderDistanceY = graphicsPresetY - rowHeight
            renderSelectorSetting(
                contentCenterX, renderDistanceY, valueBoxWidth, arrowButtonSize,
                renderDistanceLeftButton, renderDistanceRightButton,
                renderLeftButtonHover, renderRightButtonHover
            )

            // Max FPS
            val maxFpsY = renderDistanceY - rowHeight
            renderSelectorSetting(
                contentCenterX, maxFpsY, valueBoxWidth, arrowButtonSize,
                maxFpsLeftButton, maxFpsRightButton,
                maxFpsLeftButtonHover, maxFpsRightButtonHover
            )

            // Shadows & Show FPS Checkboxes (same row)
            val checkboxRowY = maxFpsY - rowHeight
            val checkboxLeftX = panelX + 60f * scale  // Shadows on left
            val checkboxRightX = contentCenterX + 40f * scale  // Show FPS on right

            shadowsCheckbox.set(checkboxLeftX, checkboxRowY - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(shadowsCheckbox, shadowsCheckboxHover, settingsManager.shadowsEnabled)

            fpsCheckbox.set(checkboxRightX, checkboxRowY - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(fpsCheckbox, fpsCheckboxHover, settingsManager.showFps)

        } else {
            // === GAMEPLAY TAB ===

            // AVAS
            val avasY = contentStartY - 50f * scale
            renderSelectorSetting(
                contentCenterX, avasY, valueBoxWidth, arrowButtonSize,
                avasLeftButton, avasRightButton,
                avasLeftButtonHover, avasRightButtonHover
            )

            // Music & Beeps Checkboxes (same row)
            val checkboxRowY = avasY - rowHeight
            val checkboxLeftX = panelX + 60f * scale  // Music on left
            val checkboxRightX = contentCenterX + 40f * scale  // Beeps on right

            musicCheckbox.set(checkboxLeftX, checkboxRowY - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(musicCheckbox, musicCheckboxHover, settingsManager.musicEnabled)

            beepsCheckbox.set(checkboxRightX, checkboxRowY - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(beepsCheckbox, beepsCheckboxHover, settingsManager.beepsEnabled)

            // No HUD checkbox (separate row below Music/Beeps)
            val noHudRowY = checkboxRowY - rowHeight
            noHudCheckbox.set(checkboxLeftX, noHudRowY - checkboxSize / 2, checkboxSize, checkboxSize)
            renderCheckbox(noHudCheckbox, noHudCheckboxHover, settingsManager.noHud)
        }

        // === Footer Links (Privacy & Terms) ===
        val linkButtonHeight = 38f * scale
        val linkButtonY = panelY + 30f * scale
        val privacyButtonWidth = 180f * scale
        val termsButtonWidth = 130f * scale
        val linkSpacing = 16f * scale

        privacyPolicyButton.set(
            contentCenterX - privacyButtonWidth - linkSpacing / 2,
            linkButtonY,
            privacyButtonWidth,
            linkButtonHeight
        )
        val privacyBgColor = if (privacyButtonHover > 0.5f) UITheme.surfaceBorder else UITheme.surfaceLight
        ui.roundedRect(privacyPolicyButton.x, privacyPolicyButton.y,
            privacyPolicyButton.width, privacyPolicyButton.height, 8f * scale, privacyBgColor)

        termsButton.set(
            contentCenterX + linkSpacing / 2,
            linkButtonY,
            termsButtonWidth,
            linkButtonHeight
        )
        val termsBgColor = if (termsButtonHover > 0.5f) UITheme.surfaceBorder else UITheme.surfaceLight
        ui.roundedRect(termsButton.x, termsButton.y,
            termsButton.width, termsButton.height, 8f * scale, termsBgColor)

        // Back button (below panel)
        val buttonWidth = 300f * scale
        val buttonHeight = UITheme.Dimensions.buttonHeightSmall
        backButton.set(centerX - buttonWidth / 2, panelY - buttonHeight - 35f * scale, buttonWidth, buttonHeight)
        ui.button(backButton, UITheme.accent, glowIntensity = backButtonHover * 0.8f)

        ui.endShapes()

        // === Draw Text ===
        ui.beginBatch()

        // Title
        val titleY = panelY + panelHeight - 60f * scale
        ui.textCentered("SETTINGS", centerX, titleY, UIFonts.title, UITheme.textPrimary)

        // Tab labels
        val tabTextColor = UITheme.textPrimary
        ui.textCentered("Graphics", graphicsTabButton.x + graphicsTabButton.width / 2,
            graphicsTabButton.y + graphicsTabButton.height / 2, UIFonts.body, tabTextColor)
        ui.textCentered("Gameplay", gameplayTabButton.x + gameplayTabButton.width / 2,
            gameplayTabButton.y + gameplayTabButton.height / 2, UIFonts.body, tabTextColor)

        // Tab content text
        val checkboxLabelOffset = checkboxSize + 16f * scale

        if (currentTab == 0) {
            // === GRAPHICS TAB TEXT ===
            val graphicsPresetY = contentStartY - 50f * scale
            ui.textCentered("Graphics Quality", contentCenterX, graphicsPresetY + 50f * scale, UIFonts.body, UITheme.textSecondary)
            renderArrowText(graphicsPresetLeftButton, graphicsPresetRightButton)
            ui.textCentered(settingsManager.getGraphicsPresetName(), contentCenterX, graphicsPresetY, UIFonts.body, UITheme.accent)

            val renderDistanceY = graphicsPresetY - rowHeight
            ui.textCentered("Render Distance", contentCenterX, renderDistanceY + 50f * scale, UIFonts.body, UITheme.textSecondary)
            renderArrowText(renderDistanceLeftButton, renderDistanceRightButton)
            ui.textCentered(settingsManager.getRenderDistanceName(), contentCenterX, renderDistanceY, UIFonts.body, UITheme.accent)

            val maxFpsY = renderDistanceY - rowHeight
            ui.textCentered("Max FPS", contentCenterX, maxFpsY + 50f * scale, UIFonts.body, UITheme.textSecondary)
            renderArrowText(maxFpsLeftButton, maxFpsRightButton)
            ui.textCentered(settingsManager.getMaxFpsName(), contentCenterX, maxFpsY, UIFonts.body, UITheme.accent)

            // Shadows & Show FPS labels (same row)
            UIFonts.body.color = UITheme.textPrimary
            UIFonts.body.draw(ui.batch, "Shadows", shadowsCheckbox.x + checkboxLabelOffset,
                shadowsCheckbox.y + shadowsCheckbox.height / 2 + UIFonts.body.lineHeight / 3)
            UIFonts.body.draw(ui.batch, "Show FPS", fpsCheckbox.x + checkboxLabelOffset,
                fpsCheckbox.y + fpsCheckbox.height / 2 + UIFonts.body.lineHeight / 3)

        } else {
            // === GAMEPLAY TAB TEXT ===
            val avasY = contentStartY - 50f * scale
            ui.textCentered("AVAS Sound", contentCenterX, avasY + 50f * scale, UIFonts.body, UITheme.textSecondary)
            renderArrowText(avasLeftButton, avasRightButton)
            ui.textCentered(settingsManager.getAvasModeName(), contentCenterX, avasY, UIFonts.body, UITheme.accent)

            // Music & Beeps labels (same row)
            UIFonts.body.color = UITheme.textPrimary
            UIFonts.body.draw(ui.batch, "Music", musicCheckbox.x + checkboxLabelOffset,
                musicCheckbox.y + musicCheckbox.height / 2 + UIFonts.body.lineHeight / 3)
            UIFonts.body.draw(ui.batch, "Beeps", beepsCheckbox.x + checkboxLabelOffset,
                beepsCheckbox.y + beepsCheckbox.height / 2 + UIFonts.body.lineHeight / 3)

            // No HUD label
            UIFonts.body.draw(ui.batch, "No HUD", noHudCheckbox.x + checkboxLabelOffset,
                noHudCheckbox.y + noHudCheckbox.height / 2 + UIFonts.body.lineHeight / 3)
        }

        // Footer link text
        val linkTextColor = UITheme.textMuted
        ui.textCentered("Privacy Policy",
            privacyPolicyButton.x + privacyPolicyButton.width / 2,
            privacyPolicyButton.y + privacyPolicyButton.height / 2,
            UIFonts.caption, if (privacyButtonHover > 0.5f) UITheme.accent else linkTextColor)
        ui.textCentered("Terms",
            termsButton.x + termsButton.width / 2,
            termsButton.y + termsButton.height / 2,
            UIFonts.caption, if (termsButtonHover > 0.5f) UITheme.accent else linkTextColor)

        // Back button text
        ui.textCentered("BACK", backButton.x + backButton.width / 2, backButton.y + backButton.height / 2,
            UIFonts.button, UITheme.textPrimary)

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
            y - 32f * scale,
            valueBoxWidth,
            64f * scale,
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
