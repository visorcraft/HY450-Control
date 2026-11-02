package com.visorcraft.hy450control;

import android.content.Context;
import android.provider.Settings;
import java.io.File;
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

    interface ValueAccess {
        String getGlobal(String key) throws Exception;
        void putGlobal(String key, String value) throws Exception;
        String getProperty(String key) throws Exception;
        void putProperty(String key, String value) throws Exception;
    }

    static void save(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("firmware", android.os.Build.FINGERPRINT);
        root.put("values", captureValues(new ValueAccess() {
            @Override
            public String getGlobal(String key) {
                return Settings.Global.getString(context.getContentResolver(), key);
            }

            @Override
            public void putGlobal(String key, String value) {
                if (value == null) {
                    context.getContentResolver().delete(android.net.Uri.withAppendedPath(
                            Settings.Global.CONTENT_URI, key), null, null);
                } else {
                    Settings.Global.putString(context.getContentResolver(), key, value);
                }
            }

            @Override
            public String getProperty(String key) throws Exception {
                return property("get", key);
            }

            @Override
            public void putProperty(String key, String value) throws Exception {
                Class<?> type = Class.forName("android.os.SystemProperties");
                type.getMethod("set", String.class, String.class).invoke(null, key, value);
            }
        }));
        File target = new File(context.getFilesDir(), "projection-backup.json");
        File temporary = new File(context.getFilesDir(), "projection-backup.tmp");
        JsonFileStore.write(target, temporary, root.toString(2));
    }

    static void restore(Context context) throws Exception {
        File source = new File(context.getFilesDir(), "projection-backup.json");
        restoreValues(readBackup(source), new ValueAccess() {
            @Override
            public String getGlobal(String key) {
                return Settings.Global.getString(context.getContentResolver(), key);
            }

            @Override
            public void putGlobal(String key, String value) {
                if (value == null) {
                    context.getContentResolver().delete(android.net.Uri.withAppendedPath(
                            Settings.Global.CONTENT_URI, key), null, null);
                } else {
                    Settings.Global.putString(context.getContentResolver(), key, value);
                }
            }

            @Override
            public String getProperty(String key) throws Exception {
                return property("get", key);
            }

            @Override
            public void putProperty(String key, String value) throws Exception {
                Class<?> type = Class.forName("android.os.SystemProperties");
                type.getMethod("set", String.class, String.class).invoke(null, key, value);
            }
        });
    }

    static JSONObject captureValues(ValueAccess access) throws Exception {
        JSONObject values = new JSONObject();
        for (String key : GLOBAL) {
            String value = access.getGlobal(key);
            values.put("global:" + key, value == null ? JSONObject.NULL : value);
        }
        for (String key : PROPERTIES) values.put("property:" + key, access.getProperty(key));
        return values;
    }

    static void restoreValues(JSONObject values, ValueAccess access) throws Exception {
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String storedKey = keys.next();
            Object stored = values.get(storedKey);
            if (storedKey.startsWith("global:")) {
                String key = storedKey.substring(7);
                String expected = stored == JSONObject.NULL ? null : (String) stored;
                access.putGlobal(key, expected);
                String actual = access.getGlobal(key);
                if (expected == null ? actual != null : !expected.equals(actual)) {
                    throw new IllegalStateException("Readback failed: " + key);
                }
            } else {
                String key = storedKey.substring(9);
                if (stored == JSONObject.NULL) throw new IllegalStateException("Null property: " + key);
                String expected = (String) stored;
                access.putProperty(key, expected);
                String actual = access.getProperty(key);
                if (!expected.equals(actual)) throw new IllegalStateException("Readback failed: " + key);
            }
        }
    }

    static JSONObject readBackup(File source) throws Exception {
        JSONObject root = new JSONObject(JsonFileStore.read(source));
        if (root.getInt("schema") != 1) throw new IllegalStateException("Unsupported backup");
        if (!android.os.Build.FINGERPRINT.equals(root.getString("firmware"))) {
            throw new IllegalStateException("Firmware mismatch");
        }
        return root.getJSONObject("values");
    }

    private static String property(String method, String key) throws Exception {
        return (String) Class.forName("android.os.SystemProperties")
                .getMethod(method, String.class).invoke(null, key);
    }
}
