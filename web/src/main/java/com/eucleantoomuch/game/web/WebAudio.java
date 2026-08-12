package com.eucleantoomuch.game.web;

import org.teavm.jso.JSBody;

/**
 * Web Audio synthesis for the sounds Android generates sample-by-sample.
 *
 * Beeps, the motor whine and the wobble rumble have no sound file behind them - Android builds
 * them with AudioTrack on a dedicated thread. A browser cannot write raw PCM from the game
 * loop without stuttering, so the same character is reproduced with oscillators whose frequency
 * and gain are simply retargeted each frame; the audio graph itself runs on the browser's own
 * audio thread.
 *
 * The context starts suspended until the player interacts with the page, which browsers require;
 * every entry point resumes it, so the first menu tap is enough to bring the sound up.
 */
public final class WebAudio {

    private WebAudio() {
    }

    /** Shared context plus the persistent motor/wobble voices. Idempotent. */
    @JSBody(script =
        "if (window.__eucAudio) { return; }\n" +
        "var Ctx = window.AudioContext || window.webkitAudioContext;\n" +
        "if (!Ctx) { window.__eucAudio = { dead: true }; return; }\n" +
        "var ctx = new Ctx();\n" +
        // One second of white noise, looped: the tyre roll and the crash burst are both
        // shaped out of this rather than allocating buffers per hit.
        "var noiseBuf = ctx.createBuffer(1, ctx.sampleRate, ctx.sampleRate);\n" +
        "var nd = noiseBuf.getChannelData(0);\n" +
        "for (var i = 0; i < nd.length; i++) { nd[i] = Math.random() * 2 - 1; }\n" +
        "window.__eucAudio = { ctx: ctx, noise: noiseBuf, motor: null, wobble: null };")
    public static native void init();

    @JSBody(script =
        "var a = window.__eucAudio;\n" +
        "if (a && a.ctx && a.ctx.state === 'suspended') { a.ctx.resume(); }")
    public static native void resume();

    /** Short sine beep - the PWM warning and the UI click. */
    @JSBody(params = {"freq", "ms"}, script =
        "var a = window.__eucAudio; if (!a || !a.ctx) { return; }\n" +
        "var ctx = a.ctx, now = ctx.currentTime, dur = ms / 1000;\n" +
        "var osc = ctx.createOscillator(), gain = ctx.createGain();\n" +
        "osc.type = 'sine'; osc.frequency.value = freq;\n" +
        // Ramped rather than switched, so the beep does not click at either end.
        "gain.gain.setValueAtTime(0, now);\n" +
        "gain.gain.linearRampToValueAtTime(0.22, now + 0.008);\n" +
        "gain.gain.setValueAtTime(0.22, now + Math.max(dur - 0.02, 0.01));\n" +
        "gain.gain.linearRampToValueAtTime(0, now + dur);\n" +
        "osc.connect(gain); gain.connect(ctx.destination);\n" +
        "osc.start(now); osc.stop(now + dur + 0.02);")
    public static native void beep(int freq, int ms);

    /** Impact: a noise burst under a falling tone, louder and longer with intensity. */
    @JSBody(params = {"intensity"}, script =
        "var a = window.__eucAudio; if (!a || !a.ctx) { return; }\n" +
        "var ctx = a.ctx, now = ctx.currentTime;\n" +
        "var dur = Math.min(0.3, 0.15 + intensity * 0.1);\n" +
        "var vol = Math.min(0.9, 0.5 + intensity * 0.3);\n" +
        "var src = ctx.createBufferSource(); src.buffer = a.noise;\n" +
        "var band = ctx.createBiquadFilter(); band.type = 'lowpass';\n" +
        "band.frequency.setValueAtTime(2200, now);\n" +
        "band.frequency.exponentialRampToValueAtTime(300, now + dur);\n" +
        "var ng = ctx.createGain();\n" +
        "ng.gain.setValueAtTime(vol, now);\n" +
        "ng.gain.exponentialRampToValueAtTime(0.001, now + dur);\n" +
        "src.connect(band); band.connect(ng); ng.connect(ctx.destination);\n" +
        "src.start(now); src.stop(now + dur);\n" +
        "var osc = ctx.createOscillator(), og = ctx.createGain();\n" +
        "osc.type = 'triangle';\n" +
        "osc.frequency.setValueAtTime(180, now);\n" +
        "osc.frequency.exponentialRampToValueAtTime(50, now + dur);\n" +
        "og.gain.setValueAtTime(vol * 0.6, now);\n" +
        "og.gain.exponentialRampToValueAtTime(0.001, now + dur);\n" +
        "osc.connect(og); og.connect(ctx.destination);\n" +
        "osc.start(now); osc.stop(now + dur);")
    public static native void crash(float intensity);

    /**
     * Motor + tyre voice. Mirrors the Android synthesis: a sawtooth carries the harmonics the
     * hand-written version sums explicitly, and a lowpass stands in for its muffling, with
     * mode 1 electric (quiet, dull), mode 3 a V8 rumble and everything else a motorcycle.
     */
    @JSBody(params = {"mode"}, script =
        "var a = window.__eucAudio; if (!a || !a.ctx || a.motor) { return; }\n" +
        "var ctx = a.ctx, now = ctx.currentTime;\n" +
        "var osc = ctx.createOscillator(); osc.type = (mode === 1) ? 'triangle' : 'sawtooth';\n" +
        "var lp = ctx.createBiquadFilter(); lp.type = 'lowpass';\n" +
        "lp.frequency.value = (mode === 1) ? 700 : 2000;\n" +
        "var gain = ctx.createGain(); gain.gain.value = 0;\n" +
        "osc.connect(lp); lp.connect(gain); gain.connect(ctx.destination);\n" +
        "var tyre = ctx.createBufferSource(); tyre.buffer = a.noise; tyre.loop = true;\n" +
        "var tf = ctx.createBiquadFilter(); tf.type = 'bandpass'; tf.frequency.value = 900;\n" +
        "var tg = ctx.createGain(); tg.gain.value = 0;\n" +
        "tyre.connect(tf); tf.connect(tg); tg.connect(ctx.destination);\n" +
        "osc.frequency.value = 80; osc.start(now); tyre.start(now);\n" +
        "a.motor = { osc: osc, gain: gain, tyreGain: tg, mode: mode };")
    public static native void motorStart(int mode);

    @JSBody(params = {"speed", "pwm"}, script =
        "var a = window.__eucAudio; if (!a || !a.motor) { return; }\n" +
        "var m = a.motor, ctx = a.ctx, t = ctx.currentTime + 0.02;\n" +
        "var norm = Math.max(0, Math.min(1, speed / 24));\n" +
        "var base, vol;\n" +
        "if (m.mode === 1)      { base = 80 + norm * 270; vol = 0.04 + norm * 0.12 + pwm * 0.05; }\n" +
        "else if (m.mode === 3) { base = 35 + norm * 145; vol = 0.20 + norm * 0.35 + pwm * 0.15; }\n" +
        "else                   { base = 80 + norm * 320; vol = 0.15 + norm * 0.40 + pwm * 0.20; }\n" +
        // Ramped, not set: stepping the frequency once a frame would sound like a zipper.
        "m.osc.frequency.linearRampToValueAtTime(base, t);\n" +
        "m.gain.gain.linearRampToValueAtTime(Math.min(0.8, vol) * 0.5, t);\n" +
        "m.tyreGain.gain.linearRampToValueAtTime(norm * 0.05, t);")
    public static native void motorUpdate(float speed, float pwm);

    @JSBody(script =
        "var a = window.__eucAudio; if (!a || !a.motor) { return; }\n" +
        "var m = a.motor, now = a.ctx.currentTime;\n" +
        "m.gain.gain.linearRampToValueAtTime(0, now + 0.08);\n" +
        "m.tyreGain.gain.linearRampToValueAtTime(0, now + 0.08);\n" +
        "try { m.osc.stop(now + 0.1); } catch (e) {}\n" +
        "a.motor = null;")
    public static native void motorStop();

    /** Low rattling rumble while the wheel wobbles; intensity retargets it live. */
    @JSBody(params = {"intensity"}, script =
        "var a = window.__eucAudio; if (!a || !a.ctx) { return; }\n" +
        "var ctx = a.ctx, now = ctx.currentTime;\n" +
        "if (!a.wobble) {\n" +
        "  var src = ctx.createBufferSource(); src.buffer = a.noise; src.loop = true;\n" +
        "  var bp = ctx.createBiquadFilter(); bp.type = 'bandpass';\n" +
        "  bp.frequency.value = 70; bp.Q.value = 6;\n" +
        "  var g = ctx.createGain(); g.gain.value = 0;\n" +
        "  src.connect(bp); bp.connect(g); g.connect(ctx.destination);\n" +
        "  src.start(now);\n" +
        "  a.wobble = { src: src, filter: bp, gain: g };\n" +
        "}\n" +
        "var w = a.wobble, t = now + 0.05;\n" +
        "w.filter.frequency.linearRampToValueAtTime(60 + intensity * 60, t);\n" +
        "w.gain.gain.linearRampToValueAtTime(intensity * 0.5, t);")
    public static native void wobble(float intensity);

    @JSBody(script =
        "var a = window.__eucAudio; if (!a || !a.wobble) { return; }\n" +
        "var w = a.wobble, now = a.ctx.currentTime;\n" +
        "w.gain.gain.linearRampToValueAtTime(0, now + 0.12);\n" +
        "try { w.src.stop(now + 0.15); } catch (e) {}\n" +
        "a.wobble = null;")
    public static native void wobbleStop();
}
