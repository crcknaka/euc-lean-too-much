package com.eucleantoomuch.game.web;

import org.teavm.jso.JSBody;

/**
 * Browser Vibration API bridge.
 *
 * Written in Java on purpose: TeaVM only accepts {@code @JSBody} on genuinely static methods,
 * and Kotlin's {@code companion object} + {@code @JvmStatic} still emits instance methods on
 * the synthetic Companion class, which the compiler rejects.
 */
public final class WebVibration {

    private WebVibration() {
    }

    @JSBody(script = "return typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function';")
    public static native boolean isSupported();

    /** Duration only - the web API has no amplitude control. Pass 0 to cancel. */
    @JSBody(params = {"ms"}, script = "try { navigator.vibrate(ms); } catch (e) {}")
    public static native void vibrate(int ms);
}
