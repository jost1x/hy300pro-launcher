package io.jostx.hy300launcher.imagecontrol

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.AtomicFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Arrays
import java.util.Properties
import kotlin.math.roundToInt

/** Narrow, authenticated firmware bridge. No shell, network, root or OEM APK dependency. */
class ImageControlService : Service() {
    private lateinit var journal: AtomicFile
    private val lock = Any()

    override fun onCreate() {
        super.onCreate()
        journal = AtomicFile(File(filesDir, "pending-geometry.xml"))
    }

    private val binder = object : IImageControl.Stub() {
        override fun status(): String {
            authenticate()
            val identity = Binder.clearCallingIdentity()
            return try {
                synchronized(lock) { prepare() }
                ""
            } catch (error: Exception) {
                detail(error)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }

        override fun shutdownStatus(): String {
            authenticate()
            return if (checkSelfPermission("android.permission.SHUTDOWN") != PackageManager.PERMISSION_GRANTED) {
                "El servicio no tiene permiso para abrir el apagado del sistema"
            } else ""
        }

        override fun requestShutdown(): String {
            authenticate()
            if (checkSelfPermission("android.permission.SHUTDOWN") != PackageManager.PERMISSION_GRANTED) {
                return "El servicio no tiene permiso para abrir el apagado del sistema"
            }
            val identity = Binder.clearCallingIdentity()
            return try {
                val shutdown = Intent("com.android.internal.intent.action.REQUEST_SHUTDOWN")
                    .setComponent(ComponentName("android", "com.android.internal.app.ShutdownActivity"))
                    .putExtra("android.intent.extra.KEY_CONFIRM", true)
                    .putExtra("android.intent.extra.USER_REQUESTED_SHUTDOWN", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(shutdown)
                ""
            } catch (error: Exception) {
                detail(error)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }

        override fun applyGeometry(matrix: IntArray?, zoom: Int): String {
            authenticate()
            require(matrix != null && matrix.size == 8 && zoom in -1..20) { "Ajuste fuera de rango" }
            require(matrix.all { it in 0..500 }) { "Esquina fuera de rango" }
            val identity = Binder.clearCallingIdentity()
            return try {
                synchronized(lock) {
                    prepare()
                    apply(matrix, zoom)
                }
                ""
            } catch (error: Exception) {
                detail(error)
            } finally {
                Binder.restoreCallingIdentity(identity)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun authenticate() {
        try {
            val uid = Binder.getCallingUid()
            val packages = packageManager.getPackagesForUid(uid)
            // Refuse shared UIDs: another package must not inherit the launcher's authority.
            if (packages == null || packages.size != 1 || LAUNCHER != packages[0]) {
                throw SecurityException("Cliente no autorizado")
            }
            val info: PackageInfo = packageManager.getPackageInfo(LAUNCHER, PackageManager.GET_SIGNING_CERTIFICATES)
            val signingInfo = info.signingInfo
            if (info.applicationInfo?.uid != uid || signingInfo == null || signingInfo.apkContentsSigners.size != 1) {
                throw SecurityException("Firma del launcher no válida")
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(signingInfo.apkContentsSigners[0].toByteArray())
            val hex = digest.joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 0xff) }
            if (BuildConfig.LAUNCHER_CERTIFICATE != hex) throw SecurityException("Firma del launcher no autorizada")
        } catch (error: SecurityException) {
            throw error
        } catch (error: Exception) {
            throw SecurityException("No se pudo verificar el launcher", error)
        }
    }

    private fun prepare() {
        check(checkSelfPermission("android.permission.HARDWARE_TEST") == PackageManager.PERMISSION_GRANTED) {
            "El servicio de imagen no tiene el permiso del firmware"
        }
        // A process death during a write is recovered before accepting another operation.
        if (journal.baseFile.exists() || File("${journal.baseFile}.bak").exists()) {
            val previous = Properties()
            FileInputStream(journal.baseFile).use(previous::loadFromXML)
            restore(previous)
            journal.delete()
        }
    }

    private fun snapshot(): Properties {
        val values = Properties()
        for (suffix in CORNERS) {
            val name = "persist.display.keystone_$suffix"
            values.setProperty(name, FirmwareProperties.get(name))
        }
        for (name in listOf(MATRIX, ZOOM, OVERSCAN)) values.setProperty(name, FirmwareProperties.get(name))
        var savedMatrix = values.getProperty(MATRIX)
        if (savedMatrix.isEmpty()) {
            val corners = IntArray(8) { index ->
                values.getProperty("persist.display.keystone_${CORNERS[index]}").let { if (it.isEmpty()) 0 else it.toInt() }
            }
            // Same legacy neutral zoom representation used by the launcher.
            if (corners.all { it == 0 }) {
                val rawZoom = values.getProperty(ZOOM)
                val inset = ((if (rawZoom.isEmpty()) 20 else rawZoom.toInt()) * (1000f / 120f)).roundToInt()
                corners.fill(inset)
            }
            savedMatrix = join(corners)
        }
        parse(savedMatrix) // Refuse to write without a valid recovery matrix.
        values.setProperty("recovery.matrix", savedMatrix)
        return values
    }

    private fun apply(matrix: IntArray, zoom: Int) {
        val previous = snapshot()
        var output: FileOutputStream? = null
        try {
            output = journal.startWrite()
            previous.storeToXML(output, "HY300 geometry recovery")
            journal.finishWrite(output)
        } catch (error: Exception) {
            output?.let(journal::failWrite)
            throw error
        }
        try {
            if (zoom >= 0) {
                FirmwareProperties.set(ZOOM, (20 - zoom).toString())
                val inset = 80 + zoom
                FirmwareProperties.set(OVERSCAN, "overscan $inset,$inset,$inset,$inset")
            }
            for (i in 0..7) FirmwareProperties.set("persist.display.keystone_${CORNERS[i]}", matrix[i].toString())
            submit(matrix)
            FirmwareProperties.set(MATRIX, join(matrix))
            journal.delete()
        } catch (error: Exception) {
            try {
                restore(previous)
                journal.delete()
            } catch (rollback: Exception) {
                throw IllegalStateException(
                    "Cambio no confirmado; no se pudo restaurar la imagen. Vuelve a abrir los ajustes para reintentar",
                    rollback,
                )
            }
            throw IllegalStateException("Cambio no aplicado; imagen anterior restaurada", error)
        }
    }

    private fun restore(previous: Properties) {
        // Restore only the fixed allowlist, never property names taken from an IPC caller.
        for (name in listOf(ZOOM, OVERSCAN)) FirmwareProperties.set(name, previous.getProperty(name))
        for (suffix in CORNERS) {
            val name = "persist.display.keystone_$suffix"
            FirmwareProperties.set(name, previous.getProperty(name))
        }
        submit(parse(previous.getProperty("recovery.matrix")))
        FirmwareProperties.set(MATRIX, previous.getProperty(MATRIX))
    }

    private fun parse(raw: String?): IntArray {
        val parts = raw?.split(',') ?: emptyList()
        check(parts.size == 8) { "No hay una matriz inicial válida para restaurar" }
        return IntArray(8) { index ->
            parts[index].toInt().also { check(it in 0..500) { "Matriz inicial fuera de rango" } }
        }
    }

    private fun join(values: IntArray): String = values.joinToString(",")

    private fun submit(matrix: IntArray) {
        val service = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java).invoke(null, "SurfaceFlinger") as? IBinder
            ?: error("El control de geometría no está disponible")
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            request.writeInterfaceToken("android.ui.ISurfaceComposer")
            matrix.forEach { request.writeFloat(it / 1000f) }
            check(service.transact(1050, request, reply, 0)) { "El firmware no admite esta corrección" }
            if (reply.dataAvail() >= 4) reply.readException()
        } finally {
            request.recycle()
            reply.recycle()
        }
    }

    private fun detail(error: Exception): String = error.message ?: "No se confirmó el ajuste de imagen"

    private companion object {
        const val LAUNCHER = "io.jostx.hy300launcher"
        const val MATRIX = "persist.sys.zoom.value"
        const val ZOOM = "persist.sys.zoom_value"
        const val OVERSCAN = "persist.vendor.overscan.main"
        val CORNERS = arrayOf("lbx", "lby", "ltx", "lty", "rtx", "rty", "rbx", "rby")
    }
}
