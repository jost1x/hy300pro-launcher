package io.jostx.hy300launcher.data;

import java.lang.reflect.InvocationTargetException;

/** Standalone root app_process entry point; uses only the firmware HAL, never the OEM APK. */
public final class ProjectorOrientationBridge {
    private static final String SERVICE = "vendor.aw.homlet.tvsystem.tvserver@1.0::ITvServer";
    private static final String PROPERTY = "persist.sys.panelvalue";
    private final Class<?> parcelClass = Class.forName("android.os.HwParcel");
    private final Object service;

    private ProjectorOrientationBridge() throws Exception {
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

    private int read() throws Exception {
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

    private int apply(int mode) throws Exception {
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

    public static void main(String[] args) {
        try {
            if (android.os.Process.myUid() != 0) throw new IllegalStateException("Se requiere el puente de sistema");
            if (args.length == 0 || !(args[0].equals("read") || args[0].equals("set"))) {
                throw new IllegalArgumentException("Operación de orientación inválida");
            }
            ProjectorOrientationBridge bridge = new ProjectorOrientationBridge();
            int mode;
            if (args[0].equals("set")) {
                if (args.length != 2) throw new IllegalArgumentException("Falta el modo");
                int requested = Integer.parseInt(args[1]);
                if (requested < 0 || requested > 3) throw new IllegalArgumentException("Modo fuera de rango");
                mode = bridge.apply(requested);
            } else {
                mode = bridge.read();
            }
            System.out.println("PANEL_MODE=" + mode);
            System.out.println("PANEL_TYPE=" + property("persist.sys.prj.type"));
        } catch (Exception error) {
            Throwable cause = error instanceof InvocationTargetException ? error.getCause() : error;
            System.out.println("PANEL_ERROR=" + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            System.exit(1);
        }
    }
}
