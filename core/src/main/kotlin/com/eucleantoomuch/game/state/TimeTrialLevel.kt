package com.eucleantoomuch.game.state

enum class TimeTrialLevel(
    val levelId: String,
    val displayName: String,
    val targetDistance: Float,  // meters
    val timeLimit: Float,       // seconds
    val voltReward: Int
) {
    LEVEL_1("level_1", "Easy Ride", 500f, 60f, 50),
    LEVEL_2("level_2", "City Run", 750f, 75f, 100),
    LEVEL_3("level_3", "Rush Hour", 1000f, 90f, 150),
    LEVEL_4("level_4", "Speed Demon", 1500f, 100f, 250),
    LEVEL_5("level_5", "Highway", 2000f, 120f, 350),
    LEVEL_6("level_6", "Night Run", 2500f, 140f, 450),
    LEVEL_7("level_7", "Storm Chase", 3000f, 155f, 600),
    LEVEL_8("level_8", "Extreme", 3500f, 170f, 750),
    LEVEL_9("level_9", "Insane", 4000f, 180f, 900),
    LEVEL_10("level_10", "Ultimate", 5000f, 200f, 1200);

    fun nextLevel(): TimeTrialLevel? = entries.getOrNull(ordinal + 1)

    companion object {
        fun fromId(id: String): TimeTrialLevel? = entries.find { it.levelId == id }
    }
}
