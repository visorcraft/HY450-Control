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
        int writes;

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
            writes++;
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
            writes++;
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

        globals.put("GsInitXYZ", "12.5,-7.25,3");
        values = ProjectionBackup.captureValues(access);
        check("12.5,-7.25,3".equals(values.getString("global:GsInitXYZ")),
                "non-empty global string must be captured");
        globals.put("GsInitXYZ", "changed");
        ProjectionBackup.restoreValues(values, access);
        check("12.5,-7.25,3".equals(globals.get("GsInitXYZ")),
                "non-empty global string must survive round trip");

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

        testAmbientScenes();
        testBootScheduling();
        testProjectionValidationRejectsBeforeWrites();
        testReadBackup();
        testBackupStoreDoesNotOverwrite();
        System.out.println("Device tests passed");
    }

    private static void testAmbientScenes() {
        android.graphics.Bitmap blank = android.graphics.Bitmap.createBitmap(
                320, 180, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            AmbientScene[] scenes = AmbientScenes.create();
            String[] expected = {"Starfield", "Aurora", "Fireplace", "Ocean waves"};
            check(scenes.length == expected.length, "four scenes in menu order");
            for (int i = 0; i < scenes.length; i++) {
                check(expected[i].equals(scenes[i].title()), "scene title/order");
                android.graphics.Bitmap first = android.graphics.Bitmap.createBitmap(
                        320, 180, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Bitmap second = android.graphics.Bitmap.createBitmap(
                        320, 180, android.graphics.Bitmap.Config.ARGB_8888);
                try {
                    android.graphics.Paint paint = new android.graphics.Paint(
                            android.graphics.Paint.ANTI_ALIAS_FLAG);
                    scenes[i].draw(new android.graphics.Canvas(first), paint, 320, 180, 0f);
                    scenes[i].draw(new android.graphics.Canvas(second), paint, 320, 180, 1f);
                    check(!first.sameAs(second), expected[i] + " must animate");
                    check(!first.sameAs(blank), expected[i] + " must draw nonblank output");
                } finally {
                    first.recycle();
                    second.recycle();
                }
            }
        } finally {
            blank.recycle();
        }
    }

    private static void testBootScheduling() {
        FakeJobScheduler scheduler = new FakeJobScheduler();
        android.content.Context context = new android.content.ContextWrapper(null) {
            @Override
            public String getPackageName() {
                return "com.visorcraft.hy450control";
            }

            @Override
            public Object getSystemService(String name) {
                return JOB_SCHEDULER_SERVICE.equals(name) ? scheduler : null;
            }
        };
        BootReceiver receiver = new BootReceiver();
        receiver.onReceive(context, new android.content.Intent("unrelated"));
        check(scheduler.scheduled == null, "unrelated broadcast must not schedule");
        receiver.onReceive(context,
                new android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED));
        check(scheduler.scheduled != null, "boot must schedule durable delayed work");
        check(scheduler.scheduled.getId() == 1001, "boot job ID must remain stable");
        check(scheduler.scheduled.getMinLatencyMillis() == 20_000L,
                "boot launch delay must remain 20 seconds");
        check(BootLaunchService.class.getName().equals(
                scheduler.scheduled.getService().getClassName()),
                "boot job must target the launch service");
    }

    private static final class FakeJobScheduler extends android.app.job.JobScheduler {
        android.app.job.JobInfo scheduled;

        @Override
        public int schedule(android.app.job.JobInfo job) {
            scheduled = job;
            return RESULT_SUCCESS;
        }

        @Override
        public int enqueue(android.app.job.JobInfo job, android.app.job.JobWorkItem work) {
            return schedule(job);
        }

        @Override
        public void cancel(int jobId) {
            if (scheduled != null && scheduled.getId() == jobId) scheduled = null;
        }

        @Override
        public void cancelAll() {
            scheduled = null;
        }

        @Override
        public java.util.List<android.app.job.JobInfo> getAllPendingJobs() {
            return scheduled == null ? java.util.Collections.emptyList()
                    : java.util.Collections.singletonList(scheduled);
        }

        @Override
        public android.app.job.JobInfo getPendingJob(int jobId) {
            return scheduled != null && scheduled.getId() == jobId ? scheduled : null;
        }
    }

    private static void testProjectionValidationRejectsBeforeWrites() throws Exception {
        JSONObject valid = new JSONObject();
        valid.put("global:GsInitXYZ", JSONObject.NULL);
        valid.put("property:persist.display.keystone_ltx", "12.5");
        FakeAccess validAccess = new FakeAccess(new HashMap<>(), new HashMap<>());
        ProjectionBackup.restoreValues(valid, validAccess);
        check(validAccess.writes == 2, "valid partial backup must still restore");

        JSONObject unknownGlobal = new JSONObject(valid.toString());
        unknownGlobal.put("global:Unknown", "value");
        expectInvalidBeforeWrites(unknownGlobal, "unknown global must fail before writes");

        JSONObject unknownProperty = new JSONObject(valid.toString());
        unknownProperty.put("property:persist.display.unknown", "value");
        expectInvalidBeforeWrites(unknownProperty, "unknown property must fail before writes");

        JSONObject malformed = new JSONObject(valid.toString());
        malformed.put("malformed", "value");
        expectInvalidBeforeWrites(malformed, "malformed key must fail before writes");

        JSONObject numericGlobal = new JSONObject(valid.toString());
        numericGlobal.put("global:JbCamX", 12);
        expectInvalidBeforeWrites(numericGlobal, "numeric global must fail before writes");

        JSONObject numericProperty = new JSONObject(valid.toString());
        numericProperty.put("property:persist.display.keystone_zoom_h", 100);
        expectInvalidBeforeWrites(numericProperty, "numeric property must fail before writes");

        JSONObject nullProperty = new JSONObject(valid.toString());
        nullProperty.put("property:persist.display.keystone_zoom_v", JSONObject.NULL);
        expectInvalidBeforeWrites(nullProperty, "null property must fail before writes");
    }

    private static void expectInvalidBeforeWrites(JSONObject values, String message) {
        FakeAccess access = new FakeAccess(new HashMap<>(), new HashMap<>());
        boolean thrown = false;
        try {
            ProjectionBackup.restoreValues(values, access);
        } catch (Exception expected) {
            thrown = true;
        }
        check(thrown, message);
        check(access.writes == 0, message + " with zero writes");
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
        File legacy = File.createTempFile("projection-backup-legacy-", ".json", directory);
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

            JSONObject legacyValues = new JSONObject();
            legacyValues.put("global:JbCamX", "legacy-camera-x");
            legacyValues.put("property:persist.display.keystone_ltx", "17.5");
            root.put("values", legacyValues);
            write(legacy, root.toString());
            JSONObject restoredValues = ProjectionBackup.readBackup(legacy);
            Map<String, String> legacyGlobals = new HashMap<>();
            Map<String, String> legacyProperties = new HashMap<>();
            legacyGlobals.put("JbCamX", "changed");
            legacyProperties.put("persist.display.keystone_ltx", "changed");
            ProjectionBackup.restoreValues(restoredValues,
                    new FakeAccess(legacyGlobals, legacyProperties));
            check("legacy-camera-x".equals(legacyGlobals.get("JbCamX")),
                    "legacy schema-1 global string must restore");
            check("17.5".equals(legacyProperties.get("persist.display.keystone_ltx")),
                    "legacy schema-1 property string must restore");

            write(malformed, "{not-json");
            expectReadBackupFailure(malformed, "malformed JSON must throw");

            root.put("firmware", Build.FINGERPRINT + "-different");
            write(mismatch, root.toString());
            expectReadBackupFailure(mismatch, "firmware mismatch must throw");
        } finally {
            valid.delete();
            legacy.delete();
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
