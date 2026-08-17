package com.eucleantoomuch.game.lwjgl3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

/**
 * The sounds that have no file behind them: PWM beeps, impacts, the motor voice and the
 * wobble rumble. Android builds these sample-by-sample with AudioTrack; this is the same idea
 * on libGDX's AudioDevice, whose write() blocks until the buffer is consumed - so every voice
 * gets its own thread and the render loop is never held up.
 *
 * Volumes deliberately follow the Android numbers, so a wheel sounds the same wherever it runs.
 */
class DesktopSynth {

    @Volatile private var motorRunning = false
    @Volatile private var motorSpeed = 0f
    @Volatile private var motorPwm = 0f
    @Volatile private var motorMode = 2

    @Volatile private var wobbleRunning = false
    @Volatile private var wobbleIntensity = 0f

    fun beep(frequencyHz: Int, durationMs: Int) = oneShot("euc-beep") { device ->
        val samples = ShortArray(SAMPLE_RATE * durationMs / 1000)
        val step = 2.0 * PI * frequencyHz / SAMPLE_RATE
        // Fade the ends in and out, otherwise the abrupt start and stop click audibly.
        val fade = (samples.size * 0.08f).toInt().coerceAtLeast(1)
        for (i in samples.indices) {
            val envelope = minOf(1f, i.toFloat() / fade, (samples.size - i).toFloat() / fade)
            samples[i] = (sin(step * i) * 0.28 * envelope * Short.MAX_VALUE).toInt().toShort()
        }
        device.writeSamples(samples, 0, samples.size)
    }

    fun crash(intensity: Float) = oneShot("euc-crash") { device ->
        val strength = intensity.coerceIn(0.3f, 1.5f)
        val durationMs = (150 + strength * 100).toInt().coerceIn(150, 300)
        val samples = ShortArray(SAMPLE_RATE * durationMs / 1000)
        val volume = (0.5f + strength * 0.3f).coerceIn(0.4f, 0.9f)

        var toneP = 0.0
        var lowpass = 0f
        for (i in samples.indices) {
            val t = i.toFloat() / samples.size
            val decay = (1f - t) * (1f - t)
            // Noise burst for the impact itself, a tone sliding down under it for weight
            val noise = (Math.random().toFloat() * 2f - 1f)
            lowpass += (noise - lowpass) * 0.35f
            toneP += 2.0 * PI * (180.0 - 130.0 * t) / SAMPLE_RATE
            val mixed = lowpass * 0.7f + sin(toneP).toFloat() * 0.5f
            samples[i] = (mixed * decay * volume * Short.MAX_VALUE).toInt().toShort()
        }
        device.writeSamples(samples, 0, samples.size)
    }

    fun startMotor(mode: Int) {
        if (mode == 0 || motorRunning) return
        motorMode = mode
        motorRunning = true
        thread(name = "euc-motor", isDaemon = true) {
            val device = openDevice() ?: run { motorRunning = false; return@thread }
            val chunk = ShortArray(SAMPLE_RATE / 30)
            var phase = 0.0
            var tyre = 0f
            try {
                while (motorRunning) {
                    val speedNorm = (motorSpeed / 24f).coerceIn(0f, 1f)
                    val pwm = motorPwm
                    val base: Double
                    val volume: Float
                    when (motorMode) {
                        ELECTRIC -> { base = 80.0 + speedNorm * 270.0; volume = 0.04f + speedNorm * 0.12f + pwm * 0.05f }
                        V8 -> { base = 35.0 + speedNorm * 145.0; volume = 0.20f + speedNorm * 0.35f + pwm * 0.15f }
                        else -> { base = 80.0 + speedNorm * 320.0; volume = 0.15f + speedNorm * 0.40f + pwm * 0.20f }
                    }
                    val level = volume.coerceIn(0f, 0.8f) * 0.5f
                    val step = 2.0 * PI * base / SAMPLE_RATE
                    for (i in chunk.indices) {
                        phase += step
                        if (phase > 2 * PI) phase -= 2 * PI
                        // Fundamental plus two harmonics gives the motor its edge; the
                        // electric mode keeps them low so it stays a muffled hum.
                        val h2 = if (motorMode == ELECTRIC) 0.05 else 0.30
                        val h3 = if (motorMode == ELECTRIC) 0.02 else 0.15
                        var s = sin(phase) + h2 * sin(2 * phase) + h3 * sin(3 * phase)
                        val noise = Math.random().toFloat() * 2f - 1f
                        tyre += (noise - tyre) * 0.25f
                        s += tyre * speedNorm * 0.25
                        chunk[i] = (s * level * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    device.writeSamples(chunk, 0, chunk.size)
                }
            } catch (t: Throwable) {
                Gdx.app?.error("DesktopSynth", "motor voice stopped: ${t.message}")
            } finally {
                device.dispose()
            }
        }
    }

    fun updateMotor(speed: Float, pwm: Float) {
        motorSpeed = speed
        motorPwm = pwm
    }

    fun stopMotor() {
        motorRunning = false
    }

    fun isMotorPlaying(): Boolean = motorRunning

    fun wobble(intensity: Float) {
        wobbleIntensity = intensity.coerceIn(0f, 1f)
        if (wobbleRunning) return
        wobbleRunning = true
        thread(name = "euc-wobble", isDaemon = true) {
            val device = openDevice() ?: run { wobbleRunning = false; return@thread }
            val chunk = ShortArray(SAMPLE_RATE / 30)
            var phase = 0.0
            var filtered = 0f
            try {
                while (wobbleRunning) {
                    val amount = wobbleIntensity
                    val step = 2.0 * PI * (55.0 + amount * 45.0) / SAMPLE_RATE
                    for (i in chunk.indices) {
                        phase += step
                        val noise = Math.random().toFloat() * 2f - 1f
                        filtered += (noise - filtered) * 0.12f
                        val s = sin(phase) * 0.6 + filtered * 0.8
                        chunk[i] = (s * amount * 0.45 * Short.MAX_VALUE).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                    device.writeSamples(chunk, 0, chunk.size)
                }
            } catch (t: Throwable) {
                Gdx.app?.error("DesktopSynth", "wobble voice stopped: ${t.message}")
            } finally {
                device.dispose()
            }
        }
    }

    fun stopWobble() {
        wobbleRunning = false
    }

    fun dispose() {
        stopMotor()
        stopWobble()
    }

    private fun openDevice(): AudioDevice? = try {
        Gdx.audio?.newAudioDevice(SAMPLE_RATE, true)
    } catch (t: Throwable) {
        Gdx.app?.error("DesktopSynth", "no audio device: ${t.message}")
        null
    }

    private fun oneShot(name: String, block: (AudioDevice) -> Unit) {
        thread(name = name, isDaemon = true) {
            val device = openDevice() ?: return@thread
            try {
                block(device)
            } catch (t: Throwable) {
                Gdx.app?.error("DesktopSynth", "$name failed: ${t.message}")
            } finally {
                device.dispose()
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44100
        const val ELECTRIC = 1
        const val V8 = 3
    }
}
