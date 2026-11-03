package io.jostx.hy300launcher.imagecontrol;

/** Verified on the HY300 Pro firmware; failure must propagate to the caller. */
public final class FirmwareProperties {
    private FirmwareProperties() {}
    public static String get(String name) throws Exception {
        return (String) Class.forName("android.os.SystemProperties")
                .getMethod("get", String.class).invoke(null, name);
    }
    public static void set(String name, String value) throws Exception {
        Class.forName("android.os.SystemProperties").getMethod("set", String.class, String.class)
                .invoke(null, name, value);
        if (!value.equals(get(name))) throw new IllegalStateException("No se confirmó " + name);
    }
}
