package com.eucleantoomuch.game.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/**
 * A bare HTMLAudioElement, for streaming music from a URL.
 *
 * The backend's own Music builds a Howl from preloaded bytes, which forces every track into
 * the asset manifest and so into the first load - 9 MB of mp3 before the menu appears. An
 * audio element fetches its source on demand and starts playing from the first buffered
 * chunk, which is what background music wants anyway.
 *
 * Browsers refuse play() until the page has seen a user gesture. Rather than losing the menu
 * music to that, a refused play arms a one-shot retry on the first pointer, key or touch -
 * unless stop() arrived in the meantime, which __want tracks.
 */
public final class WebAudioElement {

    private WebAudioElement() {
    }

    @JSBody(params = {"url"}, script =
        "var a = new Audio(url); a.preload = 'auto'; a.__want = false; a.__armed = false; return a;")
    public static native JSObject create(String url);

    @JSBody(params = {"a"}, script =
        "a.__want = true;\n" +
        "var p = a.play();\n" +
        "if (p && p.catch) { p.catch(function () {\n" +
        "  if (a.__armed) return;\n" +
        "  a.__armed = true;\n" +
        "  var retry = function () {\n" +
        "    document.removeEventListener('pointerdown', retry, true);\n" +
        "    document.removeEventListener('keydown', retry, true);\n" +
        "    document.removeEventListener('touchend', retry, true);\n" +
        "    a.__armed = false;\n" +
        "    if (a.__want) { var q = a.play(); if (q && q.catch) { q.catch(function () {}); } }\n" +
        "  };\n" +
        "  document.addEventListener('pointerdown', retry, true);\n" +
        "  document.addEventListener('keydown', retry, true);\n" +
        "  document.addEventListener('touchend', retry, true);\n" +
        "}); }")
    public static native void play(JSObject a);

    @JSBody(params = {"a"}, script = "a.__want = false; a.pause();")
    public static native void pause(JSObject a);

    @JSBody(params = {"a"}, script =
        "a.__want = false; a.pause(); try { a.currentTime = 0; } catch (e) {}")
    public static native void stop(JSObject a);

    @JSBody(params = {"a"}, script = "return !a.paused && !a.ended;")
    public static native boolean isPlaying(JSObject a);

    @JSBody(params = {"a", "loop"}, script = "a.loop = loop;")
    public static native void setLoop(JSObject a, boolean loop);

    @JSBody(params = {"a"}, script = "return !!a.loop;")
    public static native boolean isLoop(JSObject a);

    @JSBody(params = {"a", "v"}, script = "a.volume = Math.max(0, Math.min(1, v));")
    public static native void setVolume(JSObject a, float v);

    @JSBody(params = {"a"}, script = "return a.volume;")
    public static native float getVolume(JSObject a);

    @JSBody(params = {"a"}, script = "return a.currentTime || 0;")
    public static native float getPosition(JSObject a);

    @JSBody(params = {"a", "s"}, script = "try { a.currentTime = s; } catch (e) {}")
    public static native void setPosition(JSObject a, float s);

    @JSBody(params = {"a"}, script =
        "a.__want = false; a.pause(); a.removeAttribute('src'); try { a.load(); } catch (e) {}")
    public static native void dispose(JSObject a);
}
