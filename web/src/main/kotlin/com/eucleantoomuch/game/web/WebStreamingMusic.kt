package com.eucleantoomuch.game.web

import com.badlogic.gdx.audio.Music

/**
 * Music streamed from a URL instead of decoded from preloaded bytes - see [WebAudioElement]
 * for why. Both of the game's tracks loop, so the completion listener is accepted and kept
 * but never fired; wiring it would need a JS functor for no current caller.
 */
class WebStreamingMusic(url: String) : Music {

    private val element = WebAudioElement.create(url)
    private var completion: Music.OnCompletionListener? = null

    override fun play() = WebAudioElement.play(element)
    override fun pause() = WebAudioElement.pause(element)
    override fun stop() = WebAudioElement.stop(element)
    override fun isPlaying(): Boolean = WebAudioElement.isPlaying(element)
    override fun setLooping(isLooping: Boolean) = WebAudioElement.setLoop(element, isLooping)
    override fun isLooping(): Boolean = WebAudioElement.isLoop(element)
    override fun setVolume(volume: Float) = WebAudioElement.setVolume(element, volume)
    override fun getVolume(): Float = WebAudioElement.getVolume(element)
    override fun setPan(pan: Float, volume: Float) = setVolume(volume)
    override fun setPosition(position: Float) = WebAudioElement.setPosition(element, position)
    override fun getPosition(): Float = WebAudioElement.getPosition(element)
    override fun dispose() = WebAudioElement.dispose(element)

    override fun setOnCompletionListener(listener: Music.OnCompletionListener?) {
        completion = listener
    }
}
