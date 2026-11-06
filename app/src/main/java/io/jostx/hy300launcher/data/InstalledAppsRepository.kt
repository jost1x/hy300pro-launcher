package io.jostx.hy300launcher.data

import android.content.Intent
import android.content.pm.PackageManager
import io.jostx.hy300launcher.model.TvApp

class InstalledAppsRepository(
    private val packageManager: PackageManager,
    private val ownPackageName: String,
) {
    companion object {
        const val INPUTS_SHORTCUT_ID = "io.jostx.hy300launcher.shortcut.inputs"
        const val INPUTS_SHORTCUT_LABEL = "HDMI 1"
        private const val INPUTS_PACKAGE = "com.softwinner.awsource"
        private const val INPUTS_ACTIVITY = "com.softwinner.awsource.MainActivity"
    }

    fun getLaunchableApps(): List<TvApp> {
        val launcherQueries = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
        )
        val found = linkedMapOf<String, TvApp>()

        launcherQueries.forEach { query ->
            val activities = try {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(query, 0)
            } catch (_: Exception) {
                emptyList()
            }
            activities.forEach { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
                if (packageName == ownPackageName) return@forEach
                val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim()
                    .orEmpty().ifBlank { packageName }
                found.putIfAbsent(packageName, TvApp(packageName, label))
            }
        }
        if (hasProjectorSource()) {
            found[INPUTS_SHORTCUT_ID] = TvApp(INPUTS_SHORTCUT_ID, INPUTS_SHORTCUT_LABEL, isInputShortcut = true)
        }
        return found.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    fun getLaunchIntent(packageName: String): Intent? =
        packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)

    private fun hasProjectorSource(): Boolean = try {
        val info = packageManager.getActivityInfo(android.content.ComponentName(INPUTS_PACKAGE, INPUTS_ACTIVITY), 0)
        info.enabled && info.applicationInfo.enabled
    } catch (_: Exception) {
        false
    }
}
