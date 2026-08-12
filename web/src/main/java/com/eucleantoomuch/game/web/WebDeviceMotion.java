package com.eucleantoomuch.game.web;

import org.teavm.jso.JSBody;

/**
 * Bridge to the browser's DeviceMotion sensor.
 *
 * libGDX's web backend leaves getAccelerometerX/Y/Z as stubs returning 0, so the readings are
 * taken straight from {@code devicemotion} events instead. {@code accelerationIncludingGravity}
 * is reported in m/s² in the device frame - the same quantity and units Android reports - so
 * the game's calibration and tilt limits carry over untouched.
 *
 * Two browser rules shape this:
 * <ul>
 *   <li>Motion sensors need a secure context. Over plain http (other than localhost) the
 *       events simply never fire, which is why {@link #isAvailable()} reports what actually
 *       arrived rather than what the API claims to support.</li>
 *   <li>iOS 13+ requires DeviceMotionEvent.requestPermission() from inside a user gesture.
 *       The page puts a button up for that; everywhere else listening starts on its own.</li>
 * </ul>
 *
 * The event frame is fixed to the device's natural orientation, so the readings are rotated by
 * screen.orientation.angle to reach the screen-aligned frame the game steers in.
 */
public final class WebDeviceMotion {

    private WebDeviceMotion() {
    }

    /**
     * Publishes {@code window.__eucTiltEnable()} and, where no permission is needed, calls it
     * straight away. On iOS the page's button calls it after the request is granted, because
     * the request itself only works inside a user gesture. Safe to call repeatedly.
     */
    @JSBody(script =
        "window.__eucTilt = window.__eucTilt || { x: 0, y: 0, live: false, started: false };\n" +
        "var t = window.__eucTilt;\n" +
        "var listen = function() {\n" +
        "  if (t.started) { return; }\n" +
        "  t.started = true;\n" +
        "  window.addEventListener('devicemotion', function(e) {\n" +
        "    var a = e.accelerationIncludingGravity;\n" +
        "    if (!a || (a.x === null && a.y === null)) { return; }\n" +
        "    var dx = a.x || 0, dy = a.y || 0;\n" +
        "    var angle = 0;\n" +
        "    if (screen.orientation && typeof screen.orientation.angle === 'number') {\n" +
        "      angle = screen.orientation.angle;\n" +
        "    } else if (typeof window.orientation === 'number') {\n" +
        "      angle = (window.orientation + 360) % 360;\n" +
        "    }\n" +
        "    var sx, sy;\n" +
        "    if (angle === 90)       { sx =  dy; sy = -dx; }\n" +
        "    else if (angle === 180) { sx = -dx; sy = -dy; }\n" +
        "    else if (angle === 270) { sx = -dy; sy =  dx; }\n" +
        "    else                    { sx =  dx; sy =  dy; }\n" +
        // The game's landscape frame calls the forward/back axis X, which is the screen's
        // vertical axis - the opposite of the horizontal one the rotation above produces.
        "    t.x = sy;\n" +
        "    t.y = sx;\n" +
        "    t.live = true;\n" +
        "  });\n" +
        "};\n" +
        "window.__eucTiltEnable = listen;\n" +
        "if (!(typeof DeviceMotionEvent !== 'undefined' &&\n" +
        "      typeof DeviceMotionEvent.requestPermission === 'function')) {\n" +
        "  listen();\n" +
        "}")
    public static native void enable();

    /** True only once real readings have arrived - permission granted and events actually firing. */
    @JSBody(script = "return !!(window.__eucTilt && window.__eucTilt.live);")
    public static native boolean isAvailable();

    /** Forward/back tilt, m/s². */
    @JSBody(script = "return window.__eucTilt ? window.__eucTilt.x : 0;")
    public static native float x();

    /** Left/right tilt, m/s². */
    @JSBody(script = "return window.__eucTilt ? window.__eucTilt.y : 0;")
    public static native float y();

    /** Whether this is a touch device at all, which libGDX's web backend never reports. */
    @JSBody(script =
        "return (navigator.maxTouchPoints || 0) > 0 || 'ontouchstart' in window;")
    public static native boolean hasTouchScreen();
}
