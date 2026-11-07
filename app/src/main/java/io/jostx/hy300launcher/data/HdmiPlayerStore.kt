package io.jostx.hy300launcher.data

import android.content.Context

enum class HdmiPlayer(val label: String, val description: String) {
    LAUNCHER("Reproductor propio", "Experimental · cliente TIF del launcher"),
    OEM("AwLiveTv de fábrica", "Comparación · reproductor original"),
}

class HdmiPlayerStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): HdmiPlayer = when (preferences.getString(KEY_HDMI_PLAYER, null)) {
        HdmiPlayer.OEM.name -> HdmiPlayer.OEM
        else -> HdmiPlayer.LAUNCHER
    }

    fun save(player: HdmiPlayer) {
        preferences.edit().putString(KEY_HDMI_PLAYER, player.name).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "launcher"
        private const val KEY_HDMI_PLAYER = "hdmi_player"
    }
}
