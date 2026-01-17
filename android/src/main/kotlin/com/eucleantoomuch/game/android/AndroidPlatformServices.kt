package com.eucleantoomuch.game.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.eucleantoomuch.game.platform.PlatformServices
import kotlin.math.sin

/**
 * Android implementation of platform services for vibration and beep sounds.
 */
class AndroidPlatformServices(private val context: Context) : PlatformServices {

    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    // Audio track for beep generation
    private var audioTrack: AudioTrack? = null
    private val sampleRate = 44100

    override fun vibrate(durationMs: Long, amplitude: Int) {
        if (!hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (amplitude in 1..255) {
                VibrationEffect.createOneShot(durationMs, amplitude)
            } else {
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    override fun playBeep(frequencyHz: Int, durationMs: Int) {
        // Generate and play beep on a background thread to avoid blocking
        Thread {
            try {
                val numSamples = (sampleRate * durationMs / 1000)
                val samples = ShortArray(numSamples)

                // Generate sine wave
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i * frequencyHz / sampleRate
                    samples[i] = (sin(angle) * Short.MAX_VALUE * 0.7).toInt().toShort()
                }

                // Apply quick fade in/out to avoid clicks
                val fadeLength = minOf(100, numSamples / 4)
                for (i in 0 until fadeLength) {
                    val factor = i.toFloat() / fadeLength
                    samples[i] = (samples[i] * factor).toInt().toShort()
                    samples[numSamples - 1 - i] = (samples[numSamples - 1 - i] * factor).toInt().toShort()
                }

                val bufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build()
                        )
                        .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 2))
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        maxOf(bufferSize, samples.size * 2),
                        AudioTrack.MODE_STATIC
                    )
                }

                track.write(samples, 0, samples.size)
                track.play()

                // Wait for playback to complete, then release
                Thread.sleep(durationMs.toLong() + 50)
                track.stop()
                track.release()
            } catch (e: Exception) {
                // Ignore audio errors - not critical
            }
        }.start()
    }

    override fun hasVibrator(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            vibrator.hasVibrator()
        } else {
            true // Assume vibrator exists on old devices
        }
    }

    override fun cancelVibration() {
        vibrator.cancel()
    }
}
