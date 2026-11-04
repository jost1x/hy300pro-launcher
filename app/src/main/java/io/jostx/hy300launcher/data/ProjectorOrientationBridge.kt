package io.jostx.hy300launcher.data

/** Direct HY300 Pro HAL access from the launcher process; no OEM APK or ADB. */
class ProjectorOrientationBridge {
    private val parcelClass = Class.forName("android.os.HwParcel")
    private val service: Any = Class.forName("android.os.HwBinder")
        .getMethod("getService", String::class.java, String::class.java, Boolean::class.javaPrimitiveType)
        .invoke(null, SERVICE, "default", false)
        ?: error("Servicio de orientación no disponible")

    private fun transact(code: Int, mode: Int? = null): Int {
        val request = parcelClass.getConstructor().newInstance()
        val reply = parcelClass.getConstructor().newInstance()
        try {
            parcelClass.getMethod("writeInterfaceToken", String::class.java).invoke(request, SERVICE)
            parcelClass.getMethod("writeInt32", Int::class.javaPrimitiveType).invoke(request, 3) // PANEL_CONFIG_MIRROR
            if (mode != null) {
                parcelClass.getMethod("writeInt32", Int::class.javaPrimitiveType).invoke(request, mode)
            }
            Class.forName("android.os.IHwBinder")
                .getMethod("transact", Int::class.javaPrimitiveType, parcelClass, parcelClass, Int::class.javaPrimitiveType)
                .invoke(service, code, request, reply, 0)
            parcelClass.getMethod("verifySuccess").invoke(reply)
            return parcelClass.getMethod("readInt32").invoke(reply) as Int
        } finally {
            parcelClass.getMethod("releaseTemporaryStorage").invoke(request)
            parcelClass.getMethod("release").invoke(reply)
            parcelClass.getMethod("release").invoke(request)
        }
    }

    fun read(): Int {
        val mode = transact(128)
        check(mode in 0..3) { "El panel no devolvió una orientación válida" }
        return mode
    }

    fun apply(mode: Int): Int {
        require(mode in 0..3) { "Modo fuera de rango" }
        val initial = read()
        val initialProperty = property(PROPERTY)
        try {
            // Match the OEM order, then verify both the HAL and the persisted value.
            persist(mode.toString())
            val result = transact(127, mode)
            if (result != 0 || read() != mode) error("El servicio no confirmó la orientación")
            return mode
        } catch (failure: Exception) {
            try {
                persist(initialProperty)
                if (transact(127, initial) != 0 || read() != initial) {
                    error("El panel no confirmó la restauración")
                }
            } catch (rollback: Exception) {
                throw IllegalStateException("No se pudo confirmar ni restaurar la orientación", rollback)
            }
            throw IllegalStateException("Cambio no aplicado; orientación inicial restaurada", failure)
        }
    }

    private fun property(name: String): String = Class.forName("android.os.SystemProperties")
        .getMethod("get", String::class.java).invoke(null, name) as String

    private fun persist(value: String) {
        Class.forName("android.os.SystemProperties")
            .getMethod("set", String::class.java, String::class.java).invoke(null, PROPERTY, value)
        check(property(PROPERTY) == value) { "No se guardó la orientación" }
    }

    private companion object {
        const val SERVICE = "vendor.aw.homlet.tvsystem.tvserver@1.0::ITvServer"
        const val PROPERTY = "persist.sys.panelvalue"
    }
}
