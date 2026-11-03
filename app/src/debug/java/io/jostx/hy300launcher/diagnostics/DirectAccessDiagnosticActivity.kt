package io.jostx.hy300launcher.diagnostics

import android.app.Activity
import android.os.Bundle
import android.os.IBinder
import android.os.Process
import android.os.Parcel
import android.util.Log
import android.widget.TextView
import java.io.File
import java.lang.reflect.InvocationTargetException
import io.jostx.hy300launcher.data.ProjectorOrientationBridge

/** Debug-only, non-exported probe running with the actual app UID and SELinux domain. */
class DirectAccessDiagnosticActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "Diagnóstico de acceso directo…"
            textSize = 24f
            setPadding(48, 48, 48, 48)
        }
        setContentView(status)
        val writes = savedInstanceState == null && intent.getBooleanExtra("writes", false)
        intent.removeExtra("writes")
        Thread {
            val report = StringBuilder()
            fun line(value: String) { report.appendLine(value); Log.i("HY300Direct", value) }
            fun probe(name: String, block: () -> Any?) {
                try { line("$name: OK ${block()}") }
                catch (error: Exception) {
                    var cause: Throwable = error
                    while (cause is InvocationTargetException && cause.cause != null) cause = cause.cause!!
                    line("$name: DENIED_OR_UNAVAILABLE ${cause.javaClass.simpleName}: ${cause.message}")
                }
            }
            line("uid=${Process.myUid()} pid=${Process.myPid()}")
            probe("context") { File("/proc/self/attr/current").readText().trim('\u0000', '\n') }
            probe("groups") { File("/proc/self/status").readLines().first { it.startsWith("Groups:") } }
            probe("property.read") {
                Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                    .invoke(null, "persist.sys.panelvalue")
            }
            probe("property.writeMethod") {
                Class.forName("android.os.SystemProperties").getMethod("set", String::class.java, String::class.java).toString()
            }
            probe("panel.read") {
                val bridge = ProjectorOrientationBridge::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                ProjectorOrientationBridge::class.java.getDeclaredMethod("read").apply { isAccessible = true }.invoke(bridge)
            }
            probe("surface.lookup") {
                val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                    .invoke(null, "SurfaceFlinger") as? IBinder ?: error("No service")
                binder.interfaceDescriptor
            }
            if (writes) {
                probe("panel.writeAndRestore") {
                    val cls = ProjectorOrientationBridge::class.java
                    val bridge = cls.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                    val read = cls.getDeclaredMethod("read").apply { isAccessible = true }
                    val apply = cls.getDeclaredMethod("apply", Int::class.javaPrimitiveType).apply { isAccessible = true }
                    val initial = read.invoke(bridge) as Int
                    val persisted = getProperty("persist.sys.panelvalue")
                    try {
                        val changed = initial xor 1
                        apply.invoke(bridge, changed)
                        check(read.invoke(bridge) == changed)
                        line("panel.changed=$initial->$changed uid=${Process.myUid()}")
                    } finally {
                        apply.invoke(bridge, initial)
                        setProperty("persist.sys.panelvalue", persisted)
                        check(read.invoke(bridge) == initial)
                        line("panel.restored=$initial")
                    }
                    "readback confirmed"
                }
                val properties = listOf(
                    "persist.sys.zoom_value", "persist.sys.zoom.value", "persist.vendor.overscan.main",
                    "persist.sys.tpryauto", "sys.temp_pass", "sys.auto_report_angle",
                    "persist.display.keystone_ltx", "persist.display.keystone_lty",
                    "persist.display.keystone_rtx", "persist.display.keystone_rty",
                    "persist.display.keystone_rbx", "persist.display.keystone_rby",
                    "persist.display.keystone_lbx", "persist.display.keystone_lby",
                )
                properties.forEach { name -> probe("property.writeSame.$name") {
                    val initial = getProperty(name)
                    if (initial.isEmpty()) "SKIPPED unset property"
                    else { setProperty(name, initial); "confirmed" }
                } }
                probe("surface.writeAndRestore") {
                    val matrixText = getProperty("persist.sys.zoom.value")
                    val matrix = matrixText.split(',').map { it.toInt() }
                    check(matrix.size == 8 && matrix.all { it in 0..1000 }) { "No saved matrix; probe refused" }
                    val xText = getProperty("persist.display.keystone_ltx")
                    check(matrix[2] == xText.toInt()) { "Matrix mismatch; probe refused" }
                    // First submit the unchanged matrix. Do not move a corner if access is rejected.
                    submitMatrix(matrix)
                    line("surface.sameMatrixAccepted=true")
                    val changed = matrix.toMutableList()
                    changed[2] += if (changed[2] < 500) 1 else -1
                    try {
                        setProperty("persist.display.keystone_ltx", changed[2].toString())
                        submitMatrix(changed)
                        setProperty("persist.sys.zoom.value", changed.joinToString(","))
                        line("surface.changedTL=${matrix[2]}->${changed[2]}")
                    } finally {
                        submitMatrix(matrix)
                        setProperty("persist.display.keystone_ltx", xText)
                        setProperty("persist.sys.zoom.value", matrixText)
                        line("surface.restored=true")
                    }
                    "transaction accepted; optical result not measured"
                }
            }
            line(if (writes) "COMPLETE_REVERSIBLE_WRITES" else "COMPLETE_READ_ONLY")
            File(filesDir, "direct-access-diagnostic.txt").writeText(report.toString())
            runOnUiThread { status.text = report.toString(); status.textSize = 16f }
        }.start()
    }

    private fun getProperty(name: String): String = Class.forName("android.os.SystemProperties")
        .getMethod("get", String::class.java).invoke(null, name) as String

    private fun setProperty(name: String, value: String) {
        Class.forName("android.os.SystemProperties")
            .getMethod("set", String::class.java, String::class.java).invoke(null, name, value)
        check(getProperty(name) == value) { "Property not confirmed: $name" }
    }

    private fun submitMatrix(matrix: List<Int>) {
        val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "SurfaceFlinger") as IBinder
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken("android.ui.ISurfaceComposer")
            matrix.forEach { request.writeFloat(it / 1000f) }
            check(binder.transact(1050, request, reply, 0)) { "Transaction unsupported" }
            if (reply.dataAvail() >= 4) reply.readException()
        } finally { request.recycle(); reply.recycle() }
    }
}
