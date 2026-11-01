package com.visorcraft.hy450control;

import android.content.Context;
import android.provider.Settings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import org.json.JSONObject;

final class ProjectionBackup {
    private static final String[] GLOBAL = {"GsInitXYZ", "JbCamX", "JbCamY", "PoCamX", "PoCamY"};
    private static final String[] PROPERTIES = {
        "persist.display.keystone_ltx", "persist.display.keystone_lty",
        "persist.display.keystone_rtx", "persist.display.keystone_rty",
        "persist.display.keystone_lbx", "persist.display.keystone_lby",
        "persist.display.keystone_rbx", "persist.display.keystone_rby",
        "persist.display.keystone_zoom_h", "persist.display.keystone_zoom_v"
    };

    static void save(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("firmware", android.os.Build.FINGERPRINT);
        JSONObject values = new JSONObject();
        for (String key : GLOBAL) values.put("global:" + key, Settings.Global.getString(
                context.getContentResolver(), key));
        for (String key : PROPERTIES) values.put("property:" + key, property("get", key));
        root.put("values", values);
        File target = new File(context.getFilesDir(), "projection-backup.json");
        File temporary = new File(context.getFilesDir(), "projection-backup.tmp");
        FileOutputStream output = new FileOutputStream(temporary);
        try {
            output.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } finally {
            output.close();
        }
        if (!temporary.renameTo(target)) throw new IllegalStateException("Backup rename failed");
    }

    static void restore(Context context) throws Exception {
        File source = new File(context.getFilesDir(), "projection-backup.json");
        byte[] data = new byte[(int) source.length()];
        FileInputStream input = new FileInputStream(source);
        try {
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) throw new IllegalStateException("Truncated backup");
                offset += count;
            }
        } finally {
            input.close();
        }
        JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
        if (root.getInt("schema") != 1) throw new IllegalStateException("Unsupported backup");
        if (!android.os.Build.FINGERPRINT.equals(root.getString("firmware"))) {
            throw new IllegalStateException("Firmware mismatch");
        }
        JSONObject values = root.getJSONObject("values");
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String storedKey = keys.next();
            String expected = values.getString(storedKey);
            if (storedKey.startsWith("global:")) {
                String key = storedKey.substring(7);
                Settings.Global.putString(context.getContentResolver(), key, expected);
                String actual = Settings.Global.getString(context.getContentResolver(), key);
                if (!expected.equals(actual)) throw new IllegalStateException("Readback failed: " + key);
            } else {
                String key = storedKey.substring(9);
                Class<?> type = Class.forName("android.os.SystemProperties");
                type.getMethod("set", String.class, String.class).invoke(null, key, expected);
                String actual = (String) type.getMethod("get", String.class).invoke(null, key);
                if (!expected.equals(actual)) throw new IllegalStateException("Readback failed: " + key);
            }
        }
    }

    private static String property(String method, String key) throws Exception {
        return (String) Class.forName("android.os.SystemProperties")
                .getMethod(method, String.class).invoke(null, key);
    }
}
