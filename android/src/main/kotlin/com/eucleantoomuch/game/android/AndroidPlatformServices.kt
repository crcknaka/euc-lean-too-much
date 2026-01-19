package com.eucleantoomuch.game.android

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.eucleantoomuch.game.R
import com.eucleantoomuch.game.platform.PlatformServices
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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

    // === Motor Sound Synthesis ===
    private var motorSoundThread: Thread? = null
    private var motorAudioTrack: AudioTrack? = null
    private val isMotorPlaying = AtomicBoolean(false)

    // Motor sound parameters (updated from game thread, read by audio thread)
    @Volatile private var motorSpeed: Float = 0f      // m/s
    @Volatile private var motorPwm: Float = 0f        // 0-1.5
    @Volatile private var motorAccel: Float = 0f      // m/s²
    @Volatile private var avasMode: Int = 1           // 1 = electric, 2 = motorcycle

    // === SoundPool for short sound effects ===
    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    // Sound IDs
    private val pigeonWingsSound: Int = soundPool.load(context, R.raw.pigeon_wings, 1)

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

    // === Motor/Tire Sound Implementation ===

    override fun startMotorSound(mode: Int) {
        if (isMotorPlaying.get()) return
        if (mode == 0) return  // AVAS off

        avasMode = mode
        isMotorPlaying.set(true)
        motorSoundThread = Thread {
            runMotorSoundLoop()
        }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    override fun stopMotorSound() {
        isMotorPlaying.set(false)
        motorSoundThread?.join(500)
        motorSoundThread = null
    }

    override fun updateMotorSound(speed: Float, pwm: Float, acceleration: Float) {
        motorSpeed = speed
        motorPwm = pwm
        motorAccel = acceleration
    }

    override fun isMotorSoundPlaying(): Boolean = isMotorPlaying.get()

    /**
     * Main audio synthesis loop running on dedicated thread.
     * Generates real-time motor + tire sound based on current parameters.
     */
    private fun runMotorSoundLoop() {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            // Use larger buffer for smoother playback
            val actualBufferSize = maxOf(bufferSize, sampleRate / 10) // At least 100ms buffer

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
                    .setBufferSizeInBytes(actualBufferSize * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    actualBufferSize * 2,
                    AudioTrack.MODE_STREAM
                )
            }

            motorAudioTrack = track
            track.play()

            // Audio generation state
            var motorPhase = 0.0
            var motor2Phase = 0.0
            var motor3Phase = 0.0
            var tirePhase = 0.0
            var lastFilteredNoise = 0f

            // Generate audio in chunks
            val chunkSize = sampleRate / 60  // ~16ms chunks (60 FPS equivalent)
            val samples = ShortArray(chunkSize)

            while (isMotorPlaying.get()) {
                // Snapshot current parameters
                val speed = motorSpeed
                val pwm = motorPwm
                val mode = avasMode

                // Calculate sound parameters from physics
                val speedNorm = (speed / 24f).coerceIn(0f, 1f)  // Normalize to 0-1

                // Mode-dependent parameters
                val isElectric = (mode == 1)
                val isV8 = (mode == 3)

                // === BASE FREQUENCY ===
                val motorBaseFreq = when {
                    isElectric -> {
                        // Electric: 80 Hz at idle, up to 350 Hz at max speed (lower, more muffled)
                        80.0 + speedNorm * 270.0
                    }
                    isV8 -> {
                        // V8: Lower rumble, 35 Hz idle (idle burble), up to 180 Hz at max
                        // V8 fires 4 times per revolution, so lower fundamental
                        35.0 + speedNorm * 145.0
                    }
                    else -> {
                        // Motorcycle: 80 Hz at idle, up to 400 Hz at max speed
                        80.0 + speedNorm * 320.0
                    }
                }

                // === VOLUME ===
                val baseVolume = when {
                    isElectric -> 0.04f  // Quieter base
                    isV8 -> 0.2f  // V8 is loud
                    else -> 0.15f
                }
                val speedVolume = when {
                    isElectric -> 0.12f  // Less volume increase with speed
                    isV8 -> 0.35f
                    else -> 0.4f
                }
                val pwmVolume = when {
                    isElectric -> 0.05f  // Less PWM-related volume
                    isV8 -> 0.15f
                    else -> 0.2f
                }
                val motorVolume = (baseVolume + speedNorm * speedVolume + pwm * pwmVolume).coerceIn(0f, 0.8f)

                // === HARMONICS ===
                val harmonic2Freq = motorBaseFreq * 2.0
                val harmonic3Freq = motorBaseFreq * 3.0
                val harmonic4Freq = motorBaseFreq * 4.0  // Extra for V8
                val harmonic5Freq = motorBaseFreq * 5.0  // Extra for V8

                val harmonic2Vol = when {
                    isElectric -> motorVolume * 0.05f  // Reduced harmonics for softer sound
                    isV8 -> motorVolume * 0.5f  // V8 has strong 2nd harmonic
                    else -> motorVolume * 0.3f
                }
                val harmonic3Vol = when {
                    isElectric -> motorVolume * 0.02f  // Almost no high harmonics - more muffled
                    isV8 -> motorVolume * 0.35f  // V8 rich harmonics
                    else -> motorVolume * 0.15f * (0.5f + pwm * 0.5f)
                }
                // V8 specific harmonics for that characteristic growl
                val harmonic4Vol = if (isV8) motorVolume * 0.25f else 0f
                val harmonic5Vol = if (isV8) motorVolume * 0.15f else 0f

                // === PWM STRAIN / ACCELERATION SOUND ===
                val strainVol = when {
                    isElectric -> (pwm - 0.7f).coerceIn(0f, 0.3f) * 0.08f  // Minimal strain sound
                    isV8 -> (pwm - 0.4f).coerceIn(0f, 0.6f) * 0.2f  // V8 growls under load
                    else -> (pwm - 0.5f).coerceIn(0f, 0.5f) * 0.4f
                }

                // === TIRE/EXHAUST NOISE ===
                val tireVolume = when {
                    isElectric -> 0f  // No tire noise for electric
                    isV8 -> speedNorm * 0.25f + 0.1f  // V8 exhaust rumble always present
                    else -> speedNorm * 0.35f
                }
                val tireFreq = if (isV8) {
                    20.0 + speedNorm * 40.0  // Lower frequency exhaust rumble for V8
                } else {
                    30.0 + speedNorm * 70.0  // 30-100 Hz rumble
                }

                // Generate samples
                for (i in 0 until chunkSize) {
                    // Motor fundamental
                    val motor1 = sin(motorPhase) * motorVolume

                    // Harmonics
                    val motor2 = sin(motor2Phase) * harmonic2Vol
                    val motor3 = sin(motor3Phase) * harmonic3Vol

                    // V8 extra harmonics for rich sound
                    val motor4 = if (isV8) sin(motorPhase * 4.0) * harmonic4Vol else 0.0
                    val motor5 = if (isV8) sin(motorPhase * 5.0) * harmonic5Vol else 0.0

                    // PWM strain / V8 growl
                    val strain = if (strainVol > 0.01f) {
                        if (isV8) {
                            // V8 growl: lower frequency modulation
                            sin(motorPhase * 0.5 + pwm) * strainVol + sin(motorPhase * 1.5) * strainVol * 0.5
                        } else {
                            sin(motorPhase * 1.5 + pwm) * strainVol
                        }
                    } else 0.0

                    // Tire/exhaust noise
                    val rawNoise = Random.nextFloat() * 2f - 1f
                    lastFilteredNoise = lastFilteredNoise * 0.85f + rawNoise * 0.15f
                    val tireNoise = lastFilteredNoise * tireVolume
                    val tireMod = sin(tirePhase) * 0.3 + 0.7
                    val tire = tireNoise * tireMod

                    // Mix all components
                    val mixed = (motor1 + motor2 + motor3 + motor4 + motor5 + strain + tire).toFloat()

                    // Soft clip to prevent harsh distortion
                    val clipped = softClip(mixed)

                    // Convert to 16-bit PCM
                    val masterVolume = when {
                        isElectric -> 0.35f  // Quieter, softer sound
                        isV8 -> 0.7f  // V8 is louder
                        else -> 0.6f
                    }
                    samples[i] = (clipped * Short.MAX_VALUE * masterVolume).toInt().coerceIn(-32768, 32767).toShort()

                    // Advance phases
                    val phaseStep = 2.0 * Math.PI / sampleRate
                    motorPhase += motorBaseFreq * phaseStep
                    motor2Phase += harmonic2Freq * phaseStep
                    motor3Phase += harmonic3Freq * phaseStep
                    tirePhase += tireFreq * phaseStep

                    // Keep phases in range to prevent precision loss
                    if (motorPhase > 2.0 * Math.PI) motorPhase -= 2.0 * Math.PI
                    if (motor2Phase > 2.0 * Math.PI) motor2Phase -= 2.0 * Math.PI
                    if (motor3Phase > 2.0 * Math.PI) motor3Phase -= 2.0 * Math.PI
                    if (tirePhase > 2.0 * Math.PI) tirePhase -= 2.0 * Math.PI
                }

                // Write to audio track (blocking call)
                track.write(samples, 0, chunkSize)
            }

            // Cleanup
            track.stop()
            track.release()
            motorAudioTrack = null

        } catch (e: Exception) {
            // Audio errors are not critical
            isMotorPlaying.set(false)
        }
    }

    /**
     * Soft clipping function to prevent harsh digital distortion.
     * Uses tanh-like curve for smooth saturation.
     */
    private fun softClip(x: Float): Float {
        return when {
            x > 1f -> 1f - 1f / (1f + x)
            x < -1f -> -1f + 1f / (1f - x)
            else -> x
        }
    }

    // === Crash Sound Implementation ===

    override fun playCrashSound(intensity: Float) {
        Thread {
            try {
                // Duration: 150-250ms based on intensity
                val durationMs = (150 + intensity * 100).toInt().coerceIn(150, 300)
                val numSamples = sampleRate * durationMs / 1000
                val samples = ShortArray(numSamples)

                // Volume based on intensity
                val volume = (0.5f + intensity * 0.3f).coerceIn(0.4f, 0.9f)

                // Generate crash sound: noise burst + descending tone
                var noiseEnvelope = 1f
                var tonePhase = 0.0
                var toneFreq = 800.0 + intensity * 400.0  // Start frequency (higher with more intensity)
                val freqDecay = 0.9997  // How fast frequency drops

                for (i in 0 until numSamples) {
                    val t = i.toFloat() / numSamples

                    // Envelope: sharp attack, quick decay
                    val envelope = when {
                        t < 0.02f -> t / 0.02f  // Quick attack
                        t < 0.15f -> 1f - (t - 0.02f) / 0.13f * 0.4f  // Initial drop
                        else -> 0.6f * (1f - (t - 0.15f) / 0.85f)  // Long decay
                    }

                    // Noise component (crack/crunch)
                    noiseEnvelope *= 0.9985f  // Noise decays faster
                    val noise = (Random.nextFloat() * 2f - 1f) * noiseEnvelope

                    // Filtered noise for body impact sound
                    val filteredNoise = noise * 0.7f

                    // Descending tone component (thud)
                    toneFreq *= freqDecay
                    val tone = sin(tonePhase) * (1f - t * 0.8f)  // Tone fades
                    tonePhase += 2.0 * Math.PI * toneFreq / sampleRate

                    // Mix: more noise at start, more tone as it settles
                    val noiseMix = (1f - t * 0.6f).coerceIn(0.3f, 1f)
                    val toneMix = (t * 1.5f).coerceIn(0.2f, 0.7f)

                    val mixed = (filteredNoise * noiseMix + tone.toFloat() * toneMix) * envelope * volume

                    // Add some low frequency thump
                    val thump = sin(2.0 * Math.PI * (60.0 + t * 20.0) * i / sampleRate).toFloat()
                    val thumpEnvelope = if (t < 0.1f) t / 0.1f else (1f - (t - 0.1f) / 0.3f).coerceIn(0f, 1f)
                    val finalMix = mixed + thump * thumpEnvelope * 0.3f * volume

                    samples[i] = (finalMix * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
                }

                // Apply fade out to last 10% to avoid click
                val fadeStart = (numSamples * 0.9f).toInt()
                for (i in fadeStart until numSamples) {
                    val fade = 1f - (i - fadeStart).toFloat() / (numSamples - fadeStart)
                    samples[i] = (samples[i] * fade).toInt().toShort()
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

                // Wait for playback, then release
                Thread.sleep(durationMs.toLong() + 50)
                track.stop()
                track.release()

            } catch (e: Exception) {
                // Audio errors not critical
            }
        }.start()
    }

    // === Whoosh Sound Implementation ===

    override fun playWhooshSound() {
        Thread {
            try {
                // Short whoosh: 100-150ms
                val durationMs = 120
                val numSamples = sampleRate * durationMs / 1000
                val samples = ShortArray(numSamples)

                val volume = 0.6f

                // Whoosh: filtered noise with frequency sweep
                // Start with higher frequency, sweep down quickly
                var filterState = 0f
                var filterState2 = 0f

                for (i in 0 until numSamples) {
                    val t = i.toFloat() / numSamples

                    // Envelope: quick rise, sustained, quick fall
                    val envelope = when {
                        t < 0.1f -> t / 0.1f  // Quick attack
                        t < 0.6f -> 1f  // Sustain
                        else -> 1f - (t - 0.6f) / 0.4f  // Fade out
                    }

                    // Raw noise
                    val noise = Random.nextFloat() * 2f - 1f

                    // Bandpass filter with sweeping center frequency
                    // Start high (~3000 Hz), sweep down to ~500 Hz
                    val centerFreq = 3000f - t * 2500f
                    val filterCoeff = (centerFreq / sampleRate).coerceIn(0.01f, 0.3f)

                    // Simple resonant filter
                    filterState += (noise - filterState) * filterCoeff
                    filterState2 += (filterState - filterState2) * filterCoeff * 0.8f

                    // Add some stereo-like depth with phase shift
                    val doppler = sin(2.0 * Math.PI * (200.0 + t * 100.0) * i / sampleRate).toFloat() * 0.15f

                    val filtered = (filterState - filterState2 * 0.5f + doppler) * envelope * volume

                    samples[i] = (filtered * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
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

                // Wait for playback, then release
                Thread.sleep(durationMs.toLong() + 50)
                track.stop()
                track.release()

            } catch (e: Exception) {
                // Audio errors not critical
            }
        }.start()
    }

    // === Pigeon Fly-Off Sound Implementation ===

    override fun playPigeonFlyOffSound() {
        // Play pre-loaded sound from SoundPool
        soundPool.play(pigeonWingsSound, 1f, 1f, 1, 0, 1f)
    }
}
