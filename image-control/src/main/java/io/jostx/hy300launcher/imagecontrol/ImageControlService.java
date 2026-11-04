package io.jostx.hy300launcher.imagecontrol;

import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Properties;

/** Narrow, authenticated firmware bridge. No shell, network, root or OEM APK dependency. */
public final class ImageControlService extends Service {
    private static final String LAUNCHER = "io.jostx.hy300launcher";
    private static final String MATRIX = "persist.sys.zoom.value";
    private static final String ZOOM = "persist.sys.zoom_value";
    private static final String OVERSCAN = "persist.vendor.overscan.main";
    private static final String[] CORNERS = {"lbx", "lby", "ltx", "lty", "rtx", "rty", "rbx", "rby"};
    private AtomicFile journal;
    private final Object lock = new Object();

    @Override public void onCreate() {
        super.onCreate();
        journal = new AtomicFile(new File(getFilesDir(), "pending-geometry.xml"));
    }

    private final IImageControl.Stub binder = new IImageControl.Stub() {
        @Override public String status() {
            authenticate();
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (lock) { prepare(); }
                return "";
            } catch (Exception error) { return detail(error); }
            finally { Binder.restoreCallingIdentity(identity); }
        }
        @Override public String shutdownStatus() {
            authenticate();
            if (checkSelfPermission("android.permission.SHUTDOWN") != PackageManager.PERMISSION_GRANTED)
                return "El servicio no tiene permiso para abrir el apagado del sistema";
            return "";
        }
        @Override public String requestShutdown() {
            authenticate();
            if (checkSelfPermission("android.permission.SHUTDOWN") != PackageManager.PERMISSION_GRANTED)
                return "El servicio no tiene permiso para abrir el apagado del sistema";
            long identity = Binder.clearCallingIdentity();
            try {
                Intent shutdown = new Intent("com.android.internal.intent.action.REQUEST_SHUTDOWN")
                        .setComponent(new android.content.ComponentName("android", "com.android.internal.app.ShutdownActivity"))
                        .putExtra("android.intent.extra.KEY_CONFIRM", true)
                        .putExtra("android.intent.extra.USER_REQUESTED_SHUTDOWN", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(shutdown);
                return "";
            } catch (Exception error) { return detail(error); }
            finally { Binder.restoreCallingIdentity(identity); }
        }
        @Override public String applyGeometry(int[] matrix, int zoom) {
            authenticate();
            if (matrix == null || matrix.length != 8 || zoom < -1 || zoom > 20)
                throw new IllegalArgumentException("Ajuste fuera de rango");
            for (int value : matrix) if (value < 0 || value > 500)
                throw new IllegalArgumentException("Esquina fuera de rango");
            long identity = Binder.clearCallingIdentity();
            try {
                synchronized (lock) { prepare(); apply(matrix, zoom); }
                return "";
            } catch (Exception error) { return detail(error); }
            finally { Binder.restoreCallingIdentity(identity); }
        }
    };

    @Override public IBinder onBind(Intent intent) { return binder; }

    private void authenticate() {
        try {
            int uid = Binder.getCallingUid();
            String[] packages = getPackageManager().getPackagesForUid(uid);
            // Refuse shared UIDs: another package must not inherit the launcher's authority.
            if (packages == null || packages.length != 1 || !LAUNCHER.equals(packages[0]))
                throw new SecurityException("Cliente no autorizado");
            PackageInfo info = getPackageManager().getPackageInfo(LAUNCHER, PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.applicationInfo.uid != uid || info.signingInfo == null ||
                    info.signingInfo.getApkContentsSigners().length != 1)
                throw new SecurityException("Firma del launcher no válida");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    info.signingInfo.getApkContentsSigners()[0].toByteArray());
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            if (!BuildConfig.LAUNCHER_CERTIFICATE.equals(hex.toString()))
                throw new SecurityException("Firma del launcher no autorizada");
        } catch (SecurityException error) { throw error; }
        catch (Exception error) { throw new SecurityException("No se pudo verificar el launcher", error); }
    }

    private void prepare() throws Exception {
        if (checkSelfPermission("android.permission.HARDWARE_TEST") != PackageManager.PERMISSION_GRANTED)
            throw new IllegalStateException("El servicio de imagen no tiene el permiso del firmware");
        // A process death during a write is recovered before accepting another operation.
        if (journal.getBaseFile().exists() || new File(journal.getBaseFile() + ".bak").exists()) {
            Properties previous = new Properties();
            try (java.io.FileInputStream input = journal.openRead()) { previous.loadFromXML(input); }
            restore(previous);
            journal.delete();
        }
    }

    private Properties snapshot() throws Exception {
        Properties values = new Properties();
        for (String suffix : CORNERS) {
            String name = "persist.display.keystone_" + suffix;
            values.setProperty(name, FirmwareProperties.get(name));
        }
        for (String name : Arrays.asList(MATRIX, ZOOM, OVERSCAN))
            values.setProperty(name, FirmwareProperties.get(name));
        String savedMatrix = values.getProperty(MATRIX);
        if (savedMatrix.isEmpty()) {
            int[] corners = new int[8];
            for (int i = 0; i < 8; i++) {
                String raw = values.getProperty("persist.display.keystone_" + CORNERS[i]);
                corners[i] = raw.isEmpty() ? 0 : Integer.parseInt(raw);
            }
            // Same legacy neutral zoom representation used by the launcher.
            if (Arrays.stream(corners).allMatch(value -> value == 0)) {
                String rawZoom = values.getProperty(ZOOM);
                int inset = Math.round((rawZoom.isEmpty() ? 20 : Integer.parseInt(rawZoom)) * (1000f / 120f));
                Arrays.fill(corners, inset);
            }
            savedMatrix = join(corners);
        }
        parse(savedMatrix); // Refuse to write without a valid recovery matrix.
        values.setProperty("recovery.matrix", savedMatrix);
        return values;
    }

    private void apply(int[] matrix, int zoom) throws Exception {
        Properties previous = snapshot();
        FileOutputStream output = null;
        try {
            output = journal.startWrite();
            previous.storeToXML(output, "HY300 geometry recovery");
            journal.finishWrite(output);
        } catch (Exception error) {
            if (output != null) journal.failWrite(output);
            throw error;
        }
        try {
            if (zoom >= 0) {
                FirmwareProperties.set(ZOOM, Integer.toString(20 - zoom));
                int inset = 80 + zoom;
                FirmwareProperties.set(OVERSCAN, "overscan " + inset + "," + inset + "," + inset + "," + inset);
            }
            for (int i = 0; i < 8; i++)
                FirmwareProperties.set("persist.display.keystone_" + CORNERS[i], Integer.toString(matrix[i]));
            submit(matrix);
            FirmwareProperties.set(MATRIX, join(matrix));
            journal.delete();
        } catch (Exception error) {
            try { restore(previous); journal.delete(); }
            catch (Exception rollback) {
                throw new IllegalStateException("Cambio no confirmado; no se pudo restaurar la imagen. Vuelve a abrir los ajustes para reintentar", rollback);
            }
            throw new IllegalStateException("Cambio no aplicado; imagen anterior restaurada", error);
        }
    }

    private void restore(Properties previous) throws Exception {
        // Restore only the fixed allowlist, never property names taken from an IPC caller.
        for (String name : Arrays.asList(ZOOM, OVERSCAN))
            FirmwareProperties.set(name, previous.getProperty(name));
        for (String suffix : CORNERS) {
            String name = "persist.display.keystone_" + suffix;
            FirmwareProperties.set(name, previous.getProperty(name));
        }
        submit(parse(previous.getProperty("recovery.matrix")));
        FirmwareProperties.set(MATRIX, previous.getProperty(MATRIX));
    }

    private static int[] parse(String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length != 8) throw new IllegalStateException("No hay una matriz inicial válida para restaurar");
        int[] matrix = new int[8];
        for (int i = 0; i < 8; i++) {
            matrix[i] = Integer.parseInt(parts[i]);
            if (matrix[i] < 0 || matrix[i] > 500) throw new IllegalStateException("Matriz inicial fuera de rango");
        }
        return matrix;
    }

    private static String join(int[] values) {
        StringBuilder result = new StringBuilder();
        for (int value : values) { if (result.length() > 0) result.append(','); result.append(value); }
        return result.toString();
    }

    private static void submit(int[] matrix) throws Exception {
        IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "SurfaceFlinger");
        if (service == null) throw new IllegalStateException("El control de geometría no está disponible");
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken("android.ui.ISurfaceComposer");
            for (int value : matrix) request.writeFloat(value / 1000f);
            if (!service.transact(1050, request, reply, 0)) throw new IllegalStateException("El firmware no admite esta corrección");
            if (reply.dataAvail() >= 4) reply.readException();
        } finally { request.recycle(); reply.recycle(); }
    }

    private static String detail(Exception error) {
        return error.getMessage() == null ? "No se confirmó el ajuste de imagen" : error.getMessage();
    }
}
