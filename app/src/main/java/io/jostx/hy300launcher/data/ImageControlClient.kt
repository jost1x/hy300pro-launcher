package io.jostx.hy300launcher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Looper
import android.os.Build
import io.jostx.hy300launcher.imagecontrol.IImageControl
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Explicit, bounded Binder connection. Never falls back to ADB or replays an uncertain write. */
internal class ImageControlClient(private val context: Context) {
    fun check() = call { it.status() }
    fun apply(matrix: List<Int>, zoom: Int = -1) = call { it.applyGeometry(matrix.toIntArray(), zoom) }

    private fun call(operation: (IImageControl) -> String?) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "El control de imagen debe ejecutarse fuera del hilo de interfaz" }
        if (Build.VERSION.SDK_INT < 30) throw IOException("Este firmware no admite el servicio de imagen")
        try {
            val info = context.packageManager.getPackageInfo(PACKAGE, PackageManager.GET_SIGNING_CERTIFICATES)
            val signers = info.signingInfo?.apkContentsSigners
            val digest = signers?.singleOrNull()?.let {
                MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
            }
            if (digest != PLATFORM_CERTIFICATE) throw IOException("La firma del servicio de imagen no es válida")
        } catch (error: PackageManager.NameNotFoundException) {
            throw IOException("Falta instalar el servicio Control de imagen HY300 Pro", error)
        }
        val ready = CountDownLatch(1)
        val service = AtomicReference<IBinder?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service.set(binder)
                ready.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { service.set(null); ready.countDown() }
            override fun onNullBinding(name: ComponentName) { ready.countDown() }
            override fun onBindingDied(name: ComponentName) { service.set(null); ready.countDown() }
        }
        val intent = Intent().setComponent(ComponentName(PACKAGE, "$PACKAGE.ImageControlService"))
        if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
            throw IOException("No se pudo abrir el servicio de imagen")
        try {
            if (!ready.await(4, TimeUnit.SECONDS)) throw IOException("El servicio de imagen no responde")
            val binder = service.get() ?: throw IOException("Se perdió la conexión con el servicio de imagen")
            val error = operation(IImageControl.Stub.asInterface(binder))
            if (!error.isNullOrEmpty()) throw IOException(error)
        } catch (error: android.os.RemoteException) {
            throw IOException("Se perdió la conexión; el cambio no se pudo confirmar. Vuelve a abrir los ajustes", error)
        } finally {
            context.unbindService(connection)
        }
    }

    companion object {
        private const val PACKAGE = "io.jostx.hy300launcher.imagecontrol"
        private const val PLATFORM_CERTIFICATE = "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"
    }
}
