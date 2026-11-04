package com.visorcraft.hy450control;

import android.content.Context;
import android.provider.Settings;
import java.io.File;
import java.util.Iterator;
import org.json.JSONObject;

final class ProjectionBackup {
    private static final String GLOBAL_PREFIX = "global:";
    private static final String PROPERTY_PREFIX = "property:";
    private static final String[] GLOBAL = {"GsInitXYZ", "JbCamX", "JbCamY", "PoCamX", "PoCamY"};

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
        root.put("values", captureValues(androidAccess(context)));
        File target = new File(context.getFilesDir(), "projection-backup.json");
        File temporary = new File(context.getFilesDir(), "projection-backup.tmp");
        JsonFileStore.write(target, temporary, root.toString(2));
    }

    static void restore(Context context) throws Exception {
        File source = new File(context.getFilesDir(), "projection-backup.json");
        restoreValues(readBackup(source), androidAccess(context));
    }

    static void restoreValues(Context context, JSONObject values) throws Exception {
        restoreValues(values, androidAccess(context));
    }

    static JSONObject captureValues(ValueAccess access) throws Exception {
        JSONObject values = new JSONObject();
        for (String key : GLOBAL) {
            String value = access.getGlobal(key);
            values.put(GLOBAL_PREFIX + key, value == null ? JSONObject.NULL : value);
        }
        for (String key : Keystone.DISPLAY) {
            values.put(PROPERTY_PREFIX + key, access.getProperty(key));
        }
        values.put(PROPERTY_PREFIX + Keystone.ZOOM_H, access.getProperty(Keystone.ZOOM_H));
        values.put(PROPERTY_PREFIX + Keystone.ZOOM_V, access.getProperty(Keystone.ZOOM_V));
        return values;
    }

    static void restoreValues(JSONObject values, ValueAccess access) throws Exception {
        validateValues(values);
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String storedKey = keys.next();
            Object stored = values.get(storedKey);
            if (storedKey.startsWith(GLOBAL_PREFIX)) {
                String key = storedKey.substring(GLOBAL_PREFIX.length());
                String expected = stored == JSONObject.NULL ? null : (String) stored;
                access.putGlobal(key, expected);
                String actual = access.getGlobal(key);
                if (expected == null ? actual != null : !expected.equals(actual)) {
                    throw new IllegalStateException("Readback failed: " + key);
                }
            } else {
                String key = storedKey.substring(PROPERTY_PREFIX.length());
                String expected = (String) stored;
                access.putProperty(key, expected);
                String actual = access.getProperty(key);
                if (!expected.equals(actual)) throw new IllegalStateException("Readback failed: " + key);
            }
        }
    }

    static void validateValues(JSONObject values) throws Exception {
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            validateEntry(key, values.get(key), JSONObject.NULL);
        }
    }

    static void validateEntry(String storedKey, Object stored, Object nullValue) {
        if (storedKey.startsWith(GLOBAL_PREFIX)) {
            String key = storedKey.substring(GLOBAL_PREFIX.length());
            if (!contains(GLOBAL, key)) throw new IllegalStateException("Unknown global: " + key);
            if (stored != nullValue && !(stored instanceof String)) {
                throw new IllegalStateException("Invalid global: " + key);
            }
        } else if (storedKey.startsWith(PROPERTY_PREFIX)) {
            String key = storedKey.substring(PROPERTY_PREFIX.length());
            if (!isKeystoneProperty(key)) {
                throw new IllegalStateException("Unknown property: " + key);
            }
            if (!(stored instanceof String)) {
                throw new IllegalStateException("Invalid property: " + key);
            }
        } else {
            throw new IllegalStateException("Unknown backup key: " + storedKey);
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

    private static ValueAccess androidAccess(Context context) {
        return new ValueAccess() {
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
        };
    }

    private static boolean isKeystoneProperty(String key) {
        return contains(Keystone.DISPLAY, key)
                || Keystone.ZOOM_H.equals(key) || Keystone.ZOOM_V.equals(key);
    }

    private static boolean contains(String[] values, String expected) {
        for (String value : values) {
            if (value.equals(expected)) return true;
        }
        return false;
    }

    private static String property(String method, String key) throws Exception {
        return (String) Class.forName("android.os.SystemProperties")
                .getMethod(method, String.class).invoke(null, key);
    }
}
