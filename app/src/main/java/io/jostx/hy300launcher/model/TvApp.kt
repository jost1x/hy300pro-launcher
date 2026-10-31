package io.jostx.hy300launcher.model

data class TvApp(
    val packageName: String,
    val label: String,
    val isInputShortcut: Boolean = false,
)
