package io.jostx.hy300launcher.imageprobe

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.widget.TextView
import io.jostx.hy300launcher.imagecontrol.FirmwareProperties
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/** Isolated probe: no ADB client, shell commands, root requests or OEM app calls. */
class ProbeActivity : Activity() {
    private val report = StringBuilder()
    private lateinit var reportFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = TextView(this).apply {
            setPadding(48, 32, 48, 32)
            textSize = 16f
            text = "Comprobando permiso de geometría…"
        }
        setContentView(view)
        val writes = savedInstanceState == null && intent.getBooleanExtra("writes", false)
        intent.removeExtra("writes")
        reportFile = File(filesDir, "result.txt")
        Thread({
            try {
                line("uid=${Process.myUid()} pid=${Process.myPid()}")
                line("context=${File("/proc/self/attr/current").readText().trim()}")
                val granted = checkSelfPermission("android.permission.HARDWARE_TEST") == PackageManager.PERMISSION_GRANTED
                line("HARDWARE_TEST=$granted")
                if (!granted) throw SecurityException("Required signature permission not granted")
                if (writes) exercise()
                line(if (writes) "PASS_RESTORED" else "PASS_PERMISSION_ONLY")
            } catch (error: Exception) {
                line("FAIL=${error.javaClass.simpleName}: ${error.message}")
            }
            runOnUiThread { view.text = report.toString() }
        }, "image-permission-probe").start()
    }

    private fun exercise() {
        val recovery = File(filesDir, "pending-restore.txt")
        check(!recovery.exists()) { "An unfinished probe requires recovery first" }
        val initial = snapshot()
        val parts = initial.getValue(MATRIX).split(',')
        check(parts.size == 8) { "No complete saved matrix; refusing probe" }
        val matrix = IntArray(8) { index ->
            parts[index].toInt().also { value ->
                check(value in 0..500 && value == initial.getValue("persist.display.keystone_${CORNERS[index]}").toInt()) {
                    "Saved matrix and corners disagree; refusing probe"
                }
            }
        }
        val backup = initial.entries.joinToString(separator = "", postfix = "") { (key, value) -> "$key=$value\n" }
        save(recovery, backup)
        // This must succeed before attempting any modification.
        try {
            submit(matrix)
            line("surface.sameMatrixAccepted=true")
        } catch (error: Exception) {
            // Unchanged matrix only: keep recovery information for review, do not attempt a move.
            throw error
        }
        val changed = matrix.clone()
        changed[2] += if (changed[2] < 500) 1 else -1
        try {
            set(TLX, changed[2].toString())
            submit(changed)
            set(MATRIX, changed.joinToString(","))
            line("surface.changedTL=${matrix[2]}->${changed[2]}")
        } finally {
            // Keep the recovery file unless both service submission and property restoration succeed.
            var restoreError: Exception? = null
            try {
                submit(matrix)
            } catch (error: Exception) {
                restoreError = error
            }
            for (property in listOf(TLX, MATRIX)) {
                try {
                    set(property, initial.getValue(property))
                } catch (error: Exception) {
                    restoreError = error
                }
            }
            if (restoreError != null) throw IllegalStateException("RESTORE_NOT_CONFIRMED; see pending-restore.txt", restoreError)
            check(snapshot() == initial) { "Initial properties not restored" }
            line("surface.originalMatrixAccepted=true")
            line("properties.allRestored=true")
            check(recovery.delete()) { "Could not clear recovery marker" }
        }
    }

    private fun snapshot(): Map<String, String> {
        val names = listOf(
            "persist.sys.panelvalue", MATRIX, "persist.sys.zoom_value", "persist.vendor.overscan.main",
            "persist.sys.tpryauto", "sys.temp_pass", "sys.auto_report_angle",
        ) + CORNERS.map { "persist.display.keystone_$it" }
        return names.associateWith(FirmwareProperties::get)
    }

    private fun set(key: String, value: String) = FirmwareProperties.set(key, value)

    private fun submit(matrix: IntArray) {
        val service = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java).invoke(null, "SurfaceFlinger") as? IBinder
            ?: error("SurfaceFlinger unavailable")
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken("android.ui.ISurfaceComposer")
            matrix.forEach { request.writeFloat(it / 1000f) }
            check(service.transact(1050, request, reply, 0)) { "Unsupported transaction" }
            if (reply.dataAvail() >= 4) reply.readException()
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    private fun line(value: String) {
        report.append(value).append('\n')
        android.util.Log.i("HY300SignedProbe", value)
        try {
            save(reportFile, report.toString())
        } catch (error: Exception) {
            throw IllegalStateException("Cannot persist probe report", error)
        }
    }

    private fun save(file: File, value: String) {
        FileOutputStream(file).use { output ->
            output.write(value.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
        }
    }

    private companion object {
        const val MATRIX = "persist.sys.zoom.value"
        const val TLX = "persist.display.keystone_ltx"
        val CORNERS = arrayOf("lbx", "lby", "ltx", "lty", "rtx", "rty", "rbx", "rby")
    }
}
