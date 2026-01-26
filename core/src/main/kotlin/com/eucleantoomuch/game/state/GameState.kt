package com.eucleantoomuch.game.state

sealed class GameState {
    object Loading : GameState()
    object Menu : GameState()
    object ModeSelection : GameState()
    object TimeTrialLevelSelection : GameState()
    object WheelSelection : GameState()
    data class Settings(val returnTo: GameState) : GameState()
    object Credits : GameState()
    object Help : GameState()
    data class Calibrating(val returnTo: GameState? = null) : GameState()
    data class Countdown(val secondsLeft: Int) : GameState()
    data class Playing(val session: GameSession) : GameState()
    data class Paused(val session: GameSession) : GameState()
    data class Falling(val session: GameSession) : GameState()
    data class GameOver(val session: GameSession) : GameState()
}
