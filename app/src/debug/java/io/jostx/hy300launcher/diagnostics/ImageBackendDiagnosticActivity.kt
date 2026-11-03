package io.jostx.hy300launcher.diagnostics

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.widget.TextView
import io.jostx.hy300launcher.data.ImageControlClient
import io.jostx.hy300launcher.data.ProjectorCorner
import io.jostx.hy300launcher.data.ProjectorImageRepository
import io.jostx.hy300launcher.data.ProjectorProjectionMode
import io.jostx.hy300launcher.imagecontrol.FirmwareProperties
import java.io.File

/** Debug-only integration check: the same repository and UID used by the image overlay. */
class ImageBackendDiagnosticActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply { textSize = 18f; setPadding(40, 40, 40, 40) }
        setContentView(view)
        val writes = savedInstanceState == null && intent.getBooleanExtra("writes", false)
        intent.removeExtra("writes")
        Thread {
            val report = StringBuilder()
            fun line(value: String) {
                report.appendLine(value)
                File(filesDir, "image-backend-diagnostic.txt").writeText(report.toString())
            }
            val repository = ProjectorImageRepository(this)
            try {
                line("uid=${Process.myUid()}")
                val state = repository.readState {}
                line("geometry=${state.supportsFourCorner}; zoom=${state.supportsZoom}; detail=${repository.geometryUnavailableReason}")
                check(state.supportsFourCorner && state.supportsZoom)
                val orientation = repository.readOrientation {}
                line("orientation=$orientation")
                try {
                    ImageControlClient(applicationContext).apply(List(8) { 501 })
                    error("Invalid geometry was accepted")
                } catch (expected: IllegalArgumentException) { line("invalidGeometry=rejected") }
                if (writes) {
                    val names = listOf("persist.sys.zoom_value", "persist.sys.zoom.value", "persist.vendor.overscan.main", "persist.sys.panelvalue") +
                        listOf("ltx", "lty", "rtx", "rty", "rbx", "rby", "lbx", "lby").map { "persist.display.keystone_$it" }
                    val initial = names.associateWith { FirmwareProperties.get(it) }
                    check(!state.autoKeystone) { "Disable automatic correction before this reversible diagnostic" }
                    File(filesDir, "image-backend-recovery.txt").writeText(initial.entries.joinToString("\n") { "${it.key}=${it.value}" })
                    try {
                        val point = state.corners.getValue(ProjectorCorner.TOP_LEFT)
                        val delta = if (point.x < 500) 1 else -1
                        val moved = repository.adjustCorner(ProjectorCorner.TOP_LEFT, delta, 0) {}
                        check(moved.corners.getValue(ProjectorCorner.TOP_LEFT).x == point.x + delta)
                        line("corner=applied")
                        check(repository.setCorners(state.corners) {}.corners == state.corners)
                        line("corner=restored")
                        val targetZoom = if (state.zoom < 20) state.zoom + 1 else state.zoom - 1
                        check(repository.setZoom(targetZoom) {}.zoom == targetZoom)
                        line("zoom=applied")
                        check(repository.setZoom(state.zoom) {}.zoom == state.zoom)
                        check(repository.setCorners(state.corners) {}.corners == state.corners)
                        line("zoom=restored")
                        val next = ProjectorProjectionMode.entries[(orientation.ordinal + 1) % 4]
                        check(repository.setOrientation(next) {} == next)
                        check(repository.readOrientation {} == next)
                        line("orientation=applied")
                    } finally {
                        repository.setOrientation(orientation) {}
                        repository.setZoom(state.zoom) {}
                        repository.setCorners(state.corners) {}
                        // Preserve exact original representations after exercising the production setters.
                        initial.forEach { (key, value) -> FirmwareProperties.set(key, value) }
                        check(names.associateWith { FirmwareProperties.get(it) } == initial)
                        check(repository.readOrientation {} == orientation)
                        File(filesDir, "image-backend-recovery.txt").delete()
                        line("all=restored")
                    }
                }
                line(if (writes) "PASS_RESTORED" else "PASS_READ_ONLY")
            } catch (error: Exception) { line("FAIL=${error.javaClass.simpleName}: ${error.message}") }
            finally { repository.close() }
            runOnUiThread { view.text = report.toString() }
        }.start()
    }
}
