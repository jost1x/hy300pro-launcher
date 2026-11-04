package io.jostx.hy300launcher.imagecontrol

/** Verified on the HY300 Pro firmware; failure must propagate to the caller. */
object FirmwareProperties {
    @JvmStatic
    fun get(name: String): String = Class.forName("android.os.SystemProperties")
        .getMethod("get", String::class.java).invoke(null, name) as String

    @JvmStatic
    fun set(name: String, value: String) {
        Class.forName("android.os.SystemProperties")
            .getMethod("set", String::class.java, String::class.java).invoke(null, name, value)
        check(value == get(name)) { "No se confirmó $name" }
    }
}
