package io.jostx.hy300launcher.imageprobe;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Isolated probe: no ADB client, shell commands, root requests or OEM app calls. */
public final class ProbeActivity extends Activity {
    private static final String MATRIX = "persist.sys.zoom.value";
    private static final String TLX = "persist.display.keystone_ltx";
    private static final String[] CORNERS = {"lbx", "lby", "ltx", "lty", "rtx", "rty", "rbx", "rby"};
    private final StringBuilder report = new StringBuilder();
    private File reportFile;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView view = new TextView(this);
        view.setPadding(48, 32, 48, 32);
        view.setTextSize(16);
        view.setText("Comprobando permiso de geometría…");
        setContentView(view);
        boolean writes = savedInstanceState == null && getIntent().getBooleanExtra("writes", false);
        getIntent().removeExtra("writes");
        reportFile = new File(getFilesDir(), "result.txt");
        new Thread(() -> {
            try {
                line("uid=" + Process.myUid() + " pid=" + Process.myPid());
                line("context=" + new String(Files.readAllBytes(new File("/proc/self/attr/current").toPath()), StandardCharsets.UTF_8).trim());
                boolean granted = checkSelfPermission("android.permission.HARDWARE_TEST") == PackageManager.PERMISSION_GRANTED;
                line("HARDWARE_TEST=" + granted);
                if (!granted) throw new SecurityException("Required signature permission not granted");
                if (writes) exercise();
                line(writes ? "PASS_RESTORED" : "PASS_PERMISSION_ONLY");
            } catch (Exception error) {
                line("FAIL=" + error.getClass().getSimpleName() + ": " + error.getMessage());
            }
            runOnUiThread(() -> view.setText(report.toString()));
        }, "image-permission-probe").start();
    }

    private void exercise() throws Exception {
        File recovery = new File(getFilesDir(), "pending-restore.txt");
        if (recovery.exists()) throw new IllegalStateException("An unfinished probe requires recovery first");
        Map<String, String> initial = snapshot();
        String[] parts = initial.get(MATRIX).split(",");
        if (parts.length != 8) throw new IllegalStateException("No complete saved matrix; refusing probe");
        int[] matrix = new int[8];
        for (int i = 0; i < 8; i++) {
            matrix[i] = Integer.parseInt(parts[i]);
            if (matrix[i] < 0 || matrix[i] > 500 ||
                    matrix[i] != Integer.parseInt(initial.get("persist.display.keystone_" + CORNERS[i]))) {
                throw new IllegalStateException("Saved matrix and corners disagree; refusing probe");
            }
        }
        StringBuilder backup = new StringBuilder();
        initial.forEach((key, value) -> backup.append(key).append('=').append(value).append('\n'));
        save(recovery, backup.toString());
        // This must succeed before attempting any modification.
        try {
            submit(matrix);
            line("surface.sameMatrixAccepted=true");
        } catch (Exception error) {
            // Unchanged matrix only: keep recovery information for review, do not attempt a move.
            throw error;
        }
        int[] changed = matrix.clone();
        changed[2] += changed[2] < 500 ? 1 : -1;
        try {
            set(TLX, Integer.toString(changed[2]));
            submit(changed);
            set(MATRIX, join(changed));
            line("surface.changedTL=" + matrix[2] + "->" + changed[2]);
        } finally {
            // Keep the recovery file unless both service submission and property restoration succeed.
            Exception restoreError = null;
            try { submit(matrix); } catch (Exception error) { restoreError = error; }
            for (String property : Arrays.asList(TLX, MATRIX)) {
                try { set(property, initial.get(property)); }
                catch (Exception error) { restoreError = error; }
            }
            if (restoreError != null) throw new IllegalStateException("RESTORE_NOT_CONFIRMED; see pending-restore.txt", restoreError);
            if (!snapshot().equals(initial)) throw new IllegalStateException("Initial properties not restored");
            line("surface.originalMatrixAccepted=true");
            line("properties.allRestored=true");
            if (!recovery.delete()) throw new IllegalStateException("Could not clear recovery marker");
        }
    }

    private Map<String, String> snapshot() throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : Arrays.asList("persist.sys.panelvalue", MATRIX, "persist.sys.zoom_value",
                "persist.vendor.overscan.main", "persist.sys.tpryauto", "sys.temp_pass", "sys.auto_report_angle")) {
            values.put(name, get(name));
        }
        for (String suffix : CORNERS) values.put("persist.display.keystone_" + suffix, get("persist.display.keystone_" + suffix));
        return values;
    }

    private String get(String key) throws Exception {
        return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, key);
    }

    private void set(String key, String value) throws Exception {
        Class.forName("android.os.SystemProperties").getMethod("set", String.class, String.class).invoke(null, key, value);
        if (!value.equals(get(key))) throw new IllegalStateException("Property not confirmed: " + key);
    }

    private String join(int[] values) {
        StringBuilder result = new StringBuilder();
        for (int value : values) { if (result.length() > 0) result.append(','); result.append(value); }
        return result.toString();
    }

    private void submit(int[] matrix) throws Exception {
        IBinder service = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "SurfaceFlinger");
        if (service == null) throw new IllegalStateException("SurfaceFlinger unavailable");
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken("android.ui.ISurfaceComposer");
            for (int value : matrix) request.writeFloat(value / 1000f);
            if (!service.transact(1050, request, reply, 0)) throw new IllegalStateException("Unsupported transaction");
            if (reply.dataAvail() >= 4) reply.readException();
        } finally { request.recycle(); reply.recycle(); }
    }

    private void line(String value) {
        report.append(value).append('\n');
        android.util.Log.i("HY300SignedProbe", value);
        try { save(reportFile, report.toString()); }
        catch (Exception error) { throw new IllegalStateException("Cannot persist probe report", error); }
    }

    private void save(File file, String value) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
    }
}
