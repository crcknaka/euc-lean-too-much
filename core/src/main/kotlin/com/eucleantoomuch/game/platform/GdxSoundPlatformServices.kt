package com.eucleantoomuch.game.platform

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound

/**
 * Sound effects played from the shared .ogg files in assets/sounds through libGDX's own audio.
 *
 * Android plays those same files through SoundPool and has its own implementation; desktop and
 * the browser both get them from here, so the two do not carry a copy of the same code each.
 * Only the sounds Android synthesises by hand - beeps, the motor voice, the wobble rumble -
 * are left to the platform, since there is no portable way to generate them.
 */
open class GdxSoundPlatformServices : DefaultPlatformServices() {

    /**
     * Loaded on first use rather than up front: platform services are constructed before
     * libGDX has installed Gdx.audio. A name that fails to load is remembered as absent, so a
     * missing file cannot retry on every single hit.
     */
    private val sounds = HashMap<String, Sound?>()

    /** Hook for platforms that must wake their audio stack before a sound will be heard. */
    protected open fun onBeforePlay() {}

    protected fun play(name: String, volume: Float = 1f) {
        val sound = sounds.getOrPut(name) {
            try {
                Gdx.audio?.newSound(Gdx.files.internal("sounds/$name.ogg"))
            } catch (t: Throwable) {
                Gdx.app?.error("SoundPlatform", "sound $name failed to load: ${t.message}")
                null
            }
        } ?: return

        onBeforePlay()
        sound.play(volume.coerceIn(0f, 1f))
    }

    override fun playPowerupSound() = play("powerup")
    override fun playWhooshSound() = play("swoosh")
    override fun playPigeonFlyOffSound() = play("pigeon_wings")
    override fun playManholeSound() = play("manhole")
    override fun playWaterSplashSound() = play("water_splash")
    override fun playStreetLightImpactSound(volume: Float) = play("impact_street_light", volume)
    override fun playRecycleBinImpactSound(volume: Float) = play("impact_recycle", volume)
    override fun playPersonImpactSound(volume: Float) = play("personimpact", volume)
    override fun playGenericHitSound(volume: Float) = play("hit1", volume)
    override fun playCarCrashSound(volume: Float) = play("carcrash", volume)
    override fun playBenchImpactSound(volume: Float) = play("bench", volume)

    override fun releaseAudio() {
        sounds.values.forEach { it?.dispose() }
        sounds.clear()
    }
}
