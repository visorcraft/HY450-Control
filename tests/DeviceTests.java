package com.visorcraft.hy450control;

import android.os.Build;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public final class DeviceTests {
    private static final class FakeAccess implements ProjectionBackup.ValueAccess {
        private final Map<String, String> globals;
        private final Map<String, String> properties;

        FakeAccess(Map<String, String> globals, Map<String, String> properties) {
            this.globals = globals;
            this.properties = properties;
        }

        @Override
        public String getGlobal(String key) {
            return globals.get(key);
        }

        @Override
        public void putGlobal(String key, String value) {
            if (value == null) {
                globals.remove(key);
            } else {
                globals.put(key, value);
            }
        }

        @Override
        public String getProperty(String key) {
            return properties.get(key);
        }

        @Override
        public void putProperty(String key, String value) {
            if (value == null) {
                properties.remove(key);
            } else {
                properties.put(key, value);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> globals = new HashMap<>();
        Map<String, String> properties = new HashMap<>();
        FakeAccess access = new FakeAccess(globals, properties);
        JSONObject values = ProjectionBackup.captureValues(access);
        check(values.has("global:GsInitXYZ"), "absent key must remain present");
        check(values.isNull("global:GsInitXYZ"), "absence must be JSON null");
        globals.put("GsInitXYZ", "created-later");
        ProjectionBackup.restoreValues(values, access);
        check(!globals.containsKey("GsInitXYZ"), "restore must clear a newly created global");
        globals.put("JbCamX", "");
        values = ProjectionBackup.captureValues(access);
        globals.put("JbCamX", "changed");
        ProjectionBackup.restoreValues(values, access);
        check("".equals(globals.get("JbCamX")), "empty string must survive round trip");

        String property = "persist.display.keystone_ltx";
        properties.put(property, "12.5");
        values = ProjectionBackup.captureValues(access);
        check("12.5".equals(values.getString("property:" + property)),
                "property string must be captured");
        properties.put(property, "changed");
        ProjectionBackup.restoreValues(values, access);
        check("12.5".equals(properties.get(property)), "property string must survive round trip");

        JSONObject nullProperty = new JSONObject();
        nullProperty.put("property:" + property, JSONObject.NULL);
        boolean rejected = false;
        try {
            ProjectionBackup.restoreValues(nullProperty, access);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(rejected, "JSON null property must be rejected");
        check("12.5".equals(properties.get(property)), "rejected property must not be written");

        testReadBackup();
        testBackupStoreDoesNotOverwrite();
        System.out.println("Device tests passed");
    }

    private static void testBackupStoreDoesNotOverwrite() throws Exception {
        File directory = new File("/data/local/tmp",
                "hy450-backup-store-" + System.nanoTime());
        check(directory.mkdirs(), "backup-store test directory must be created");
        try {
            BackupStore store = new BackupStore(directory);
            Map<String, Integer> values = new HashMap<>();
            values.put("keystone", 12);
            store.snapshot("keystone", values);
            File snapshot = new File(directory, "keystone.json");
            String original = JsonFileStore.read(snapshot);

            values.put("keystone", 99);
            boolean rejected = false;
            try {
                store.snapshot("keystone", values);
            } catch (IllegalStateException expected) {
                rejected = "Original snapshot already exists".equals(expected.getMessage());
            }
            check(rejected, "second snapshot for a family must be rejected");
            check(original.equals(JsonFileStore.read(snapshot)),
                    "rejected snapshot must leave original JSON unchanged");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static void testReadBackup() throws Exception {
        File directory = new File("/data/local/tmp");
        File valid = File.createTempFile("projection-backup-valid-", ".json", directory);
        File malformed = File.createTempFile("projection-backup-malformed-", ".json", directory);
        File mismatch = File.createTempFile("projection-backup-mismatch-", ".json", directory);
        try {
            JSONObject root = new JSONObject();
            root.put("schema", 1);
            root.put("firmware", Build.FINGERPRINT);
            JSONObject values = new JSONObject();
            values.put("global:GsInitXYZ", JSONObject.NULL);
            root.put("values", values);
            write(valid, root.toString());
            check(ProjectionBackup.readBackup(valid).isNull("global:GsInitXYZ"),
                    "schema-1 backup values must be returned");

            write(malformed, "{not-json");
            expectReadBackupFailure(malformed, "malformed JSON must throw");

            root.put("firmware", Build.FINGERPRINT + "-different");
            write(mismatch, root.toString());
            expectReadBackupFailure(mismatch, "firmware mismatch must throw");
        } finally {
            valid.delete();
            malformed.delete();
            mismatch.delete();
        }
    }

    private static void expectReadBackupFailure(File source, String message) throws Exception {
        boolean thrown = false;
        try {
            ProjectionBackup.readBackup(source);
        } catch (Exception expected) {
            thrown = true;
        }
        check(thrown, message);
    }

    private static void write(File target, String text) throws Exception {
        FileOutputStream output = new FileOutputStream(target);
        try {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
