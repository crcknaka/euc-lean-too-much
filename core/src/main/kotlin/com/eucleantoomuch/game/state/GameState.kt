package com.eucleantoomuch.game.state

sealed class GameState {
    object Loading : GameState()
    object Menu : GameState()
    object Settings : GameState()
    object Calibrating : GameState()
    data class Countdown(val secondsLeft: Int) : GameState()
    data class Playing(val session: GameSession) : GameState()
    data class Paused(val session: GameSession) : GameState()
    data class GameOver(val session: GameSession) : GameState()
}
