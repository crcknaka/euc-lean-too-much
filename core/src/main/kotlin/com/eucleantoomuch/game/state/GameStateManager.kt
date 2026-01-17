package com.eucleantoomuch.game.state

class GameStateManager {
    private var currentState: GameState = GameState.Loading
    private val listeners = mutableListOf<(GameState, GameState) -> Unit>()

    fun current(): GameState = currentState

    fun transition(newState: GameState) {
        val oldState = currentState
        currentState = newState
        listeners.forEach { it(oldState, newState) }
    }

    fun addListener(listener: (GameState, GameState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (GameState, GameState) -> Unit) {
        listeners.remove(listener)
    }

    fun isPlaying(): Boolean = currentState is GameState.Playing
    fun isPaused(): Boolean = currentState is GameState.Paused
    fun isGameOver(): Boolean = currentState is GameState.GameOver
}
