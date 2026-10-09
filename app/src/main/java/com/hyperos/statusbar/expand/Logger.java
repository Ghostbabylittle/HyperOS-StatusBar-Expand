package com.hyperos.statusbar.expand;

import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

public class Logger {

    private static final String TAG = "StatusBarExpand";
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);

    private static final String[] LOG_PATHS = new String[] {
            "/sdcard/statusbar_expand.log",
            "/storage/emulated/0/statusbar_expand.log",
            "/data/local/tmp/statusbar_expand.log",
            "/data/data/com.android.systemui/cache/statusbar_expand.log"
    };

    public static synchronized void log(String message) {
        String timestamp = SDF.format(new Date());
        String line = String.format("[%s] [%s] %s", timestamp, Thread.currentThread().getName(), message);

        // 1. Android Logcat
        Log.i(TAG, line);

        // 2. LSPosed XposedBridge
        try {
            XposedBridge.log(TAG + ": " + line);
        } catch (Throwable ignored) {}

        // 3. Persistent Log Files
        for (String path : LOG_PATHS) {
            try {
                File file = new File(path);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (FileWriter fw = new FileWriter(file, true);
                     PrintWriter pw = new PrintWriter(fw)) {
                    pw.println(line);
                }
            } catch (Throwable ignored) {}
        }
    }

    public static void log(String message, Throwable t) {
        log(message + " - Exception: " + Log.getStackTraceString(t));
    }
}
