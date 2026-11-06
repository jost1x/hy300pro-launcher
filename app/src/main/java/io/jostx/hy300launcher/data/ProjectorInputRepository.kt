package io.jostx.hy300launcher.data

import android.content.Context
import android.media.tv.TvInputManager

class ProjectorInputRepository(context: Context) {
    private val tvInputManager = context.getSystemService(TvInputManager::class.java)

    fun hasHdmi1(): Boolean = try {
        tvInputManager?.tvInputList?.any { it.id == HDMI1_INPUT_ID } == true
    } catch (_: RuntimeException) {
        false
    }

    companion object {
        const val HDMI1_INPUT_ID = "com.softwinner.vis/.HdmiInputService/HW1"
    }
}
