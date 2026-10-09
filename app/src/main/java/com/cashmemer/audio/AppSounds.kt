package com.cashmemer.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.cashmemer.R

/** Short spooky sound effects. Turned on and off by the App sounds setting. */
object AppSounds {
    enum class Kind(val resId: Int) {
        WHOOSH(R.raw.ghost_whoosh),
        STING(R.raw.scare_sting),
        CHIME(R.raw.bat_chime),
    }

    @Volatile
    var enabled: Boolean = true

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Kind, Int>()

    /** The background track (the cellar stairs), looping quietly while the app is open. */
    private var music: MediaPlayer? = null

    fun startMusic(context: Context) {
        if (!enabled) return
        val player = music ?: MediaPlayer.create(context.applicationContext, R.raw.the_cellar_stairs)
            ?.also {
                it.isLooping = true
                it.setVolume(0.35f, 0.35f)
                music = it
            } ?: return
        if (!player.isPlaying) player.start()
    }

    fun pauseMusic() {
        music?.takeIf { it.isPlaying }?.pause()
    }

    fun resumeMusic() {
        if (enabled) music?.takeIf { !it.isPlaying }?.start()
    }

    fun stopMusic() {
        music?.let {
            it.stop()
            it.release()
        }
        music = null
    }

    fun play(context: Context, kind: Kind) {
        if (!enabled) return
        val current = pool ?: createPool(context).also { pool = it }
        ids[kind]?.let { current.play(it, 0.8f, 0.8f, 1, 0, 1f) }
    }

    private fun createPool(context: Context): SoundPool {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
        Kind.values().forEach { kind ->
            ids[kind] = created.load(context.applicationContext, kind.resId, 1)
        }
        return created
    }
}
