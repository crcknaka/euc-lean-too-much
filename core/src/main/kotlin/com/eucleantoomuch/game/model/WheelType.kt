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
    val batteryCapacity: Int,      // mAh - affects how far you can ride

    // Visual parameters
    val wheelRadius: Float,        // meters
    val bodyColor: Color,          // Main body color
    val accentColor: Color         // Accent/trim color
) {
    // Simple wheel - 16" - slow, beginner friendly, very agile
    object Simple : WheelType(
        id = "simple",
        displayName = "Simple Wheel",
        description = "16\" wheel - Slow & easy for beginners",
        wheelSizeInches = 16,
        maxSpeed = 20.0f,           // ~72 km/h (+20%)
        acceleration = 3.5f,        // Slower than Street Runner
        deceleration = 6f,
        criticalLean = 0.90f,
        pwmSensitivity = 1.1f,
        turnResponsiveness = 6.0f,  // Most agile
        batteryCapacity = 1800,     // mAh - shortest range
        wheelRadius = 0.18f,
        bodyColor = Color(0.2f, 0.3f, 0.35f, 1f),  // Blue-gray
        accentColor = Color(0.3f, 0.5f, 0.6f, 1f)  // Teal accent
    )

    // Standard wheel - 18" - balanced, maneuverable
    object Standard : WheelType(
        id = "standard",
        displayName = "Street Runner",
        description = "18\" wheel - Balanced & maneuverable",
        wheelSizeInches = 18,
        maxSpeed = 26.6f,           // ~96 km/h (+20%)
        acceleration = 5f,
        deceleration = 8f,
        criticalLean = 0.95f,
        pwmSensitivity = 1.0f,
        turnResponsiveness = 5.0f,  // Medium agility
        batteryCapacity = 2400,     // mAh - medium range
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
        maxSpeed = 38.3f,           // ~138 km/h (+20%)
        acceleration = 7f,
        deceleration = 10f,
        criticalLean = 1.05f,       // Harder to cutout (more stable)
        pwmSensitivity = 0.95f,     // Lower sensitivity = more stable at high speeds
        turnResponsiveness = 3.5f,  // Less agile than others
        batteryCapacity = 4000,     // mAh - longest range
        wheelRadius = 0.25f,
        bodyColor = Color(0.6f, 0.15f, 0.15f, 1f),  // Dark red
        accentColor = Color(1f, 0.3f, 0.1f, 1f)     // Orange accent
    )

    companion object {
        val ALL = listOf(Simple, Standard, Performance)

        fun fromId(id: String): WheelType = when (id) {
            "simple" -> Simple
            "performance" -> Performance
            else -> Standard
        }
    }
}
