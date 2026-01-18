package com.eucleantoomuch.game.model

import com.badlogic.gdx.graphics.Color

/**
 * Defines the two EUC wheel types with their respective physics characteristics.
 *
 * Street Runner (18"): Balanced - good all-around performance
 * Speed Demon (22"): Fast, stable (hard to cutout), very agile - difficulty from speed
 */
sealed class WheelType(
    val id: String,
    val displayName: String,
    val description: String,
    val wheelSizeInches: Int,

    // Physics parameters
    val maxSpeed: Float,           // m/s
    val acceleration: Float,       // m/s²
    val deceleration: Float,       // m/s²
    val criticalLean: Float,       // 0-1 threshold (higher = harder to fall)
    val pwmSensitivity: Float,     // Multiplier for PWM (lower = more stable)
    val turnResponsiveness: Float, // Turn rate multiplier (higher = more agile)

    // Visual parameters
    val wheelRadius: Float,        // meters
    val bodyColor: Color,          // Main body color
    val accentColor: Color         // Accent/trim color
) {
    // Standard wheel - 18" - balanced, maneuverable
    object Standard : WheelType(
        id = "standard",
        displayName = "Street Runner",
        description = "18\" wheel - Balanced & maneuverable",
        wheelSizeInches = 18,
        maxSpeed = 22.2f,           // ~80 km/h
        acceleration = 5f,
        deceleration = 8f,
        criticalLean = 0.95f,
        pwmSensitivity = 1.0f,
        turnResponsiveness = 5.0f,  // More agile
        wheelRadius = 0.2f,
        bodyColor = Color(0.25f, 0.25f, 0.25f, 1f),  // Dark gray
        accentColor = Color(0.4f, 0.4f, 0.4f, 1f)    // Gray
    )

    // Performance wheel - 22" - fast, stable
    object Performance : WheelType(
        id = "performance",
        displayName = "Speed Demon",
        description = "22\" wheel - Fast & agile, for experienced riders",
        wheelSizeInches = 22,
        maxSpeed = 31.9f,           // ~115 km/h
        acceleration = 7f,
        deceleration = 10f,
        criticalLean = 1.05f,       // Harder to cutout (more stable)
        pwmSensitivity = 0.95f,     // Lower sensitivity = more stable at high speeds
        turnResponsiveness = 4.0f,  // Agile, but less than Street Runner
        wheelRadius = 0.25f,
        bodyColor = Color(0.6f, 0.15f, 0.15f, 1f),  // Dark red
        accentColor = Color(1f, 0.3f, 0.1f, 1f)     // Orange accent
    )

    companion object {
        val ALL = listOf(Standard, Performance)

        fun fromId(id: String): WheelType = when (id) {
            "performance" -> Performance
            else -> Standard
        }
    }
}
