package com.cashmemer.audio

import android.content.Context
import android.media.AudioAttributes
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
