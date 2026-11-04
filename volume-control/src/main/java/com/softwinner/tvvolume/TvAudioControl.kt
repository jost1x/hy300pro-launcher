package com.softwinner.tvvolume

import android.content.Context
import android.media.AudioManager

/** Small adapter for the volume parameters used by the HY300 Pro firmware. */
internal class TvAudioControl(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioManagerEx: Any by lazy {
        val type = Class.forName("android.media.AudioManagerEx")
        type.getConstructor(Context::class.java).newInstance(appContext)
    }

    fun getVolume(): Int {
        val raw = audioManagerEx.javaClass
            .getMethod("getAudioParameters", String::class.java)
            .invoke(audioManagerEx, "speaker_volume") as? String
            ?: error("El firmware no devolvió el nivel de volumen")
        return raw.toIntOrNull()?.coerceIn(0, MAX_VOLUME)
            ?: error("El firmware devolvió un nivel de volumen no válido")
    }

    fun isMuted(): Boolean {
        val raw = audioManagerEx.javaClass
            .getMethod("getAudioParameters", String::class.java)
            .invoke(audioManagerEx, "spk_mute") as? String
        return raw?.toIntOrNull()?.let { it != 0 } ?: audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
    }

    fun setVolume(value: Int) {
        require(value in 0..MAX_VOLUME) { "Nivel de volumen fuera de rango" }
        setParameter("spk_mute", "0")
        setParameter("set_volume", value.toString())
    }

    fun setMuted(muted: Boolean) {
        setParameter("spk_mute", if (muted) "1" else "0")
    }

    private fun setParameter(name: String, value: String) {
        AudioManager::class.java
            .getMethod("setParameter", String::class.java, String::class.java)
            .invoke(audioManager, name, value)
    }

    companion object {
        const val MAX_VOLUME = 100
    }
}
