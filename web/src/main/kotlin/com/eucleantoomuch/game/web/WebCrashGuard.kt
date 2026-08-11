package com.eucleantoomuch.game.web

import com.badlogic.gdx.ApplicationListener

/**
 * Keeps one bad frame from ending the session.
 *
 * An exception thrown out of render() propagates into requestAnimationFrame, which stops the
 * loop for good: the player is left staring at a frozen canvas. The browser build has no
 * stack traces either (TeaVM strips them), so the throw would also tell nobody anything.
 * Reporting the first few occurrences per stage and carrying on is strictly better here.
 */
class WebCrashGuard(private val inner: ApplicationListener) : ApplicationListener {

    private var renderFailures = 0
    private var otherFailures = 0

    private inline fun guard(stage: String, isRender: Boolean, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            val count = if (isRender) ++renderFailures else ++otherFailures
            if (count <= MAX_REPORTS) {
                println("EUC: $stage failed (${t::class.java.name}: ${t.message})")
                if (count == MAX_REPORTS) println("EUC: further $stage failures will be silent")
            }
        }
    }

    override fun create() = guard("create", false) { inner.create() }
    override fun resize(width: Int, height: Int) = guard("resize", false) { inner.resize(width, height) }
    override fun render() = guard("render", true) { inner.render() }
    override fun pause() = guard("pause", false) { inner.pause() }
    override fun resume() = guard("resume", false) { inner.resume() }
    override fun dispose() = guard("dispose", false) { inner.dispose() }

    private companion object {
        const val MAX_REPORTS = 5
    }
}
