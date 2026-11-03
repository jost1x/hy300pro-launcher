package io.jostx.hy300launcher.data;


/** Direct HY300 Pro HAL access from the launcher process; no OEM APK or ADB. */
public final class ProjectorOrientationBridge {
    private static final String SERVICE = "vendor.aw.homlet.tvsystem.tvserver@1.0::ITvServer";
    private static final String PROPERTY = "persist.sys.panelvalue";
    private final Class<?> parcelClass = Class.forName("android.os.HwParcel");
    private final Object service;

    public ProjectorOrientationBridge() throws Exception {
        service = Class.forName("android.os.HwBinder")
                .getMethod("getService", String.class, String.class, boolean.class)
                .invoke(null, SERVICE, "default", false);
        if (service == null) throw new IllegalStateException("Servicio de orientación no disponible");
    }

    private int transact(int code, Integer mode) throws Exception {
        Object request = parcelClass.getConstructor().newInstance();
        Object reply = parcelClass.getConstructor().newInstance();
        try {
            parcelClass.getMethod("writeInterfaceToken", String.class).invoke(request, SERVICE);
            parcelClass.getMethod("writeInt32", int.class).invoke(request, 3); // PANEL_CONFIG_MIRROR
            if (mode != null) parcelClass.getMethod("writeInt32", int.class).invoke(request, mode);
            Class.forName("android.os.IHwBinder")
                    .getMethod("transact", int.class, parcelClass, parcelClass, int.class)
                    .invoke(service, code, request, reply, 0);
            parcelClass.getMethod("verifySuccess").invoke(reply);
            return (Integer) parcelClass.getMethod("readInt32").invoke(reply);
        } finally {
            parcelClass.getMethod("releaseTemporaryStorage").invoke(request);
            parcelClass.getMethod("release").invoke(reply);
            parcelClass.getMethod("release").invoke(request);
        }
    }

    public int read() throws Exception {
        int mode = transact(128, null);
        if (mode < 0 || mode > 3) throw new IllegalStateException("El panel no devolvió una orientación válida");
        return mode;
    }

    private static String property(String name) throws Exception {
        return (String) Class.forName("android.os.SystemProperties")
                .getMethod("get", String.class).invoke(null, name);
    }

    private static void persist(String value) throws Exception {
        Class.forName("android.os.SystemProperties").getMethod("set", String.class, String.class)
                .invoke(null, PROPERTY, value);
        if (!property(PROPERTY).equals(value)) throw new IllegalStateException("No se guardó la orientación");
    }

    public int apply(int mode) throws Exception {
        if (mode < 0 || mode > 3) throw new IllegalArgumentException("Modo fuera de rango");
        int initial = read();
        String initialProperty = property(PROPERTY);
        try {
            // Match the OEM order, then verify both the HAL and the persisted value.
            persist(Integer.toString(mode));
            int result = transact(127, mode);
            if (result != 0 || read() != mode) {
                throw new IllegalStateException("El servicio no confirmó la orientación");
            }
            return mode;
        } catch (Exception error) {
            try {
                persist(initialProperty);
                if (transact(127, initial) != 0 || read() != initial) {
                    throw new IllegalStateException("El panel no confirmó la restauración");
                }
            } catch (Exception rollback) {
                throw new IllegalStateException("No se pudo confirmar ni restaurar la orientación", rollback);
            }
            throw new IllegalStateException("Cambio no aplicado; orientación inicial restaurada", error);
        }
    }

}
