package com.eucleantoomuch.game.util

object Constants {
    // Screen
    const val VIRTUAL_WIDTH = 1280f
    const val VIRTUAL_HEIGHT = 720f

    // EUC Physics
    const val MAX_LEAN_ANGLE = 45f          // degrees (increased for more range)
    const val CRITICAL_LEAN = 0.95f         // 95% of max lean = instant fall (more forgiving)
    const val MIN_SPEED = 2f                // m/s (always moving forward)
    const val MAX_SPEED = 23.6f             // m/s (85 km/h)
    const val ACCELERATION = 5f             // m/s²
    const val DECELERATION = 8f             // m/s² (braking)
    const val MAX_TURN_RATE = 90f           // degrees/second
    const val TURN_RESPONSIVENESS = 3f
    const val WHEEL_RADIUS = 0.2f           // 20cm wheel

    // Puddle effect
    const val PUDDLE_DURATION = 3f          // seconds of control loss
    const val PUDDLE_CONTROL_FACTOR = 0.3f  // 30% control when in puddle

    // Input
    const val INPUT_SMOOTHING = 0.2f
    const val DEAD_ZONE = 0.05f
    const val MAX_TILT = 5f                 // m/s² max accelerometer offset

    // World Generation
    const val CHUNK_LENGTH = 50f            // meters
    const val RENDER_DISTANCE = 150f        // 3 chunks ahead
    const val DESPAWN_DISTANCE = -30f       // Behind player

    // Road layout (X coordinates)
    const val ROAD_WIDTH = 8f
    const val SIDEWALK_WIDTH = 3f
    const val LANE_WIDTH = 3f
    const val ROAD_LEFT = -ROAD_WIDTH / 2
    const val ROAD_RIGHT = ROAD_WIDTH / 2
    const val SIDEWALK_LEFT_X = ROAD_LEFT - SIDEWALK_WIDTH / 2
    const val SIDEWALK_RIGHT_X = ROAD_RIGHT + SIDEWALK_WIDTH / 2

    // Difficulty
    const val EASY_DISTANCE = 100f          // First 100m is easy
    const val HARD_DISTANCE = 1000f         // Full difficulty at 1km
    const val MIN_OBSTACLE_SPACING = 3f     // meters between obstacles
    const val MAX_OBSTACLE_SPACING = 8f

    // Camera
    const val CAMERA_OFFSET_Y = 3.5f        // Above player (higher to see obstacles)
    const val CAMERA_OFFSET_Z = -4.5f       // Behind player (closer)
    const val CAMERA_LOOK_AHEAD = 8f        // Look ahead distance
    const val CAMERA_SMOOTHNESS = 5f

    // Building generation
    const val BUILDING_MIN_HEIGHT = 18f
    const val BUILDING_MAX_HEIGHT = 50f
    const val BUILDING_WIDTH = 6f
    const val BUILDING_DEPTH = 8f
    const val BUILDING_OFFSET_X = 14f       // Distance from road center (leaves room for grass)

    // Obstacle dimensions
    const val MANHOLE_RADIUS = 0.7f
    const val PUDDLE_WIDTH = 1.5f
    const val PUDDLE_LENGTH = 2f
    const val CURB_HEIGHT = 0.15f
    const val POTHOLE_RADIUS = 0.5f
    const val PEDESTRIAN_HEIGHT = 1.7f
    const val PEDESTRIAN_WIDTH = 0.5f
    const val CAR_LENGTH = 5.5f
    const val CAR_WIDTH = 2.2f
    const val CAR_HEIGHT = 1.8f

    // AI speeds
    const val PEDESTRIAN_MIN_SPEED = 1f     // m/s
    const val PEDESTRIAN_MAX_SPEED = 2.5f
    const val CAR_MIN_SPEED = 5f
    const val CAR_MAX_SPEED = 15f

    // Scoring
    const val POINTS_PER_METER = 10
}
