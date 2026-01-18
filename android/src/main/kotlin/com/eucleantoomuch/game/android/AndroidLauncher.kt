package com.eucleantoomuch.game.android

import android.os.Build
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.eucleantoomuch.game.EucGame

class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on while playing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enable edge-to-edge display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        // Request high refresh rate (120Hz) on supported devices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val highRefreshMode = getHighRefreshRateMode()
            window.attributes = window.attributes.apply {
                preferredDisplayModeId = highRefreshMode
            }
        }

        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = true
            useCompass = false
            useGyroscope = false
            useImmersiveMode = true
            numSamples = 2
            // Disable foreground FPS limit to allow 120Hz
            // (libGDX defaults to 60fps in foreground on some devices)
        }

        // Create platform services for Android (vibration, beep sounds)
        val platformServices = AndroidPlatformServices(this)

        initialize(EucGame(platformServices), config)

        // Request 120Hz frame rate on the surface (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Find the SurfaceView and set frame rate
            window.decorView.post {
                requestHighFrameRate()
            }
        }

        // Hide system bars for full immersive experience
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // Legacy approach for older Android versions
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    /**
     * Request high frame rate (120Hz) on the GL surface
     * This tells the system we want consistent 120fps rendering
     */
    private fun requestHighFrameRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Find SurfaceView in the view hierarchy
            fun findSurfaceView(view: View): SurfaceView? {
                if (view is SurfaceView) return view
                if (view is android.view.ViewGroup) {
                    for (i in 0 until view.childCount) {
                        val found = findSurfaceView(view.getChildAt(i))
                        if (found != null) return found
                    }
                }
                return null
            }

            val surfaceView = findSurfaceView(window.decorView)
            surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    // Set frame rate when surface is actually ready
                    // Use FIXED_SOURCE to force high refresh rate
                    holder.surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    // Also try setting when surface changes
                    holder.surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                }
                override fun surfaceDestroyed(holder: SurfaceHolder) {}
            })

            // Also try immediate set if surface already exists
            surfaceView?.holder?.surface?.let { surface ->
                if (surface.isValid) {
                    surface.setFrameRate(120f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                }
            }
        }
    }

    /**
     * Find display mode with CURRENT system resolution and highest refresh rate.
     * This respects the user's system resolution setting while enabling high refresh rate.
     */
    @Suppress("DEPRECATION")
    private fun getHighRefreshRateMode(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Use new API on Android R+, fallback to deprecated API on older versions
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                windowManager.defaultDisplay
            } ?: return 0

            val supportedModes = display.supportedModes
            val currentMode = display.mode

            // Use current system resolution (respects user's display settings)
            val currentWidth = currentMode.physicalWidth
            val currentHeight = currentMode.physicalHeight

            // Find mode with SAME resolution as current but highest refresh rate
            var bestMode = currentMode
            var maxRefreshRate = currentMode.refreshRate

            for (mode in supportedModes) {
                // Only consider modes with same resolution as current system setting
                if (mode.physicalWidth == currentWidth && mode.physicalHeight == currentHeight) {
                    if (mode.refreshRate > maxRefreshRate) {
                        maxRefreshRate = mode.refreshRate
                        bestMode = mode
                    }
                }
            }

            return bestMode.modeId
        }
        return 0
    }
}
