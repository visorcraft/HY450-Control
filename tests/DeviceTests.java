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
        int globalWrites;
        int propertyWrites;

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
            globalWrites++;
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
            propertyWrites++;
            if (value == null) {
                properties.remove(key);
            } else {
                properties.put(key, value);
            }
        }
    }

    private static final class FakePortableAdapter implements ControlAdapter {
        private int value;
        int writes;

        FakePortableAdapter(int value) {
            this.value = value;
        }

        @Override
        public int read() {
            return value;
        }

        @Override
        public void write(int value) {
            writes++;
            this.value = value;
        }

        @Override
        public int min() {
            return 0;
        }

        @Override
        public int max() {
            return 100;
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

        testPortableBackup();
        testDiagnosticAllowlist();
        testAmbientScenes();
        testBootScheduling();
        testProjectionValidationRejectsBeforeWrites();
        testReadBackup();
        testBackupStoreDoesNotOverwrite();
        testRoutineStore();
        testOwnerBackupStore();
        testRestoreEngine();
        testRoutineBatchLinkedControls();
        testOwnerAssistantUiState();
        testOwnerAssistantControllers();
        System.out.println("Device tests passed");
    }

    private static void testOwnerBackupStore() throws Exception {
        File directory = new File("/data/local/tmp",
                "hy450-owner-backup-store-" + System.nanoTime());
        check(directory.mkdirs(), "owner-backup test directory must be created");
        try {
            FakePortableAdapter backlight = new FakePortableAdapter(25);
            Map<String, java.util.List<Control>> families = portableFamilies(
                    backlight, new FakePortableAdapter(20));
            FakeAccess access = new FakeAccess(new HashMap<>(), new HashMap<>());
            JSONObject root = PortableBackup.capture(
                    families, access, "1.6.0", "test-fingerprint", "2026-11-03T00:00:00Z");
            OwnerBackupStore store = new OwnerBackupStore(directory);
            check(store.readSafety() == null, "absent safety copy must return null");
            store.saveSafety(root);
            File target = new File(directory, "pre-restore-backup.json");
            String original = JsonFileStore.read(target);
            check(root.toString().equals(new OwnerBackupStore(directory).readSafety().toString()),
                    "safety JSON must survive store recreation");
            check(!new File(directory, "pre-restore-backup.tmp").exists(),
                    "atomic safety rename must consume temporary file");
            check(OwnerBackupStore.class.getConstructor(android.content.Context.class) != null,
                    "Context safety-store constructor must exist");

            JSONObject replacement = new JSONObject(root.toString());
            replacement.put("createdUtc", "2026-11-03T00:01:00Z");
            File temporary = new File(directory, "pre-restore-backup.tmp");
            check(temporary.mkdir(), "temporary-path collision must be created");
            boolean failed = false;
            try {
                store.saveSafety(replacement);
            } catch (Exception expected) {
                failed = true;
            } finally {
                temporary.delete();
            }
            check(failed, "failed second safety save must throw");
            check(original.equals(JsonFileStore.read(target))
                            && root.toString().equals(store.readSafety().toString()),
                    "failed second safety save must preserve the first file");

            JSONObject invalid = new JSONObject(replacement.toString());
            invalid.put("schema", 2);
            failed = false;
            try {
                store.saveSafety(invalid);
            } catch (Exception expected) {
                failed = true;
            }
            check(failed && original.equals(JsonFileStore.read(target)),
                    "schema rejection must preserve the prior safety copy");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static final class RestoreAdapter implements ControlAdapter {
        private final String family;
        private final java.util.List<String> order;
        private int value;
        int writes;
        Exception failure;

        RestoreAdapter(String family, int value, java.util.List<String> order) {
            this.family = family;
            this.value = value;
            this.order = order;
        }

        @Override
        public int read() {
            return value;
        }

        @Override
        public void write(int value) throws Exception {
            writes++;
            order.add(family);
            if (failure != null) throw failure;
            this.value = value;
        }

        @Override
        public int min() {
            return 0;
        }

        @Override
        public int max() {
            return 100;
        }
    }

    private static void testRestoreEngine() throws Exception {
        java.util.List<String> order = new java.util.ArrayList<>();
        RestoreAdapter picture = new RestoreAdapter("picture", 10, order);
        RestoreAdapter audio = new RestoreAdapter("audio", 20, order);
        RestoreAdapter source = new RestoreAdapter("source", 30, order);
        Map<String, java.util.List<Control>> families = new java.util.LinkedHashMap<>();
        families.put("source", java.util.Collections.singletonList(
                new Control("source", "Source", source)));
        families.put("audio", java.util.Collections.singletonList(
                new Control("audio", "Audio", audio)));
        families.put("picture", java.util.Collections.singletonList(
                new Control("picture", "Picture", picture)));
        Map<String, String> globals = new HashMap<>();
        globals.put("JbCamX", "before");
        Map<String, String> properties = new HashMap<>();
        properties.put("persist.display.keystone_ltx", "12.5");
        FakeAccess access = new FakeAccess(globals, properties);
        JSONObject root = PortableBackup.capture(families, access,
                "1.6.0", "test-fingerprint", "2026-11-03T00:00:00Z");
        globals.put("JbCamX", "changed");
        properties.put("persist.display.keystone_ltx", "changed");
        String longError = new String(new char[240]).replace('\0', 'x');
        audio.failure = new Exception(longError);
        int[] projectionCalls = {0};
        RestoreEngine.Result result = RestoreEngine.restore(
                root, families, "test-fingerprint", projection -> {
                    projectionCalls[0]++;
                    order.add("projection");
                    ProjectionBackup.restoreValues(projection, access);
                });
        check(order.equals(java.util.Arrays.asList("picture", "audio", "source", "projection")),
                "restore order must continue past a family failure");
        check(result.applied == 2 && result.failures.size() == 1,
                "partial restore failure must be visible");
        check(result.failures.get(0).length() <= 160,
                "family restore failure must be bounded");
        check(projectionCalls[0] == 1 && picture.writes == 1 && audio.writes == 1
                        && source.writes == 1,
                "restore must not automatically retry families or projection");
        check("before".equals(globals.get("JbCamX"))
                        && "12.5".equals(properties.get("persist.display.keystone_ltx")),
                "projection restore must receive validated values");
        boolean immutable = false;
        try {
            result.failures.add("unexpected");
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "restore failures must be immutable");

        order.clear();
        picture.writes = 0;
        audio.writes = 0;
        source.writes = 0;
        audio.failure = null;
        projectionCalls[0] = 0;
        RestoreEngine.Result projectionFailure = RestoreEngine.restore(
                root, families, "test-fingerprint", projection -> {
                    projectionCalls[0]++;
                    throw new Exception(longError);
                });
        check(projectionFailure.applied == 3 && projectionFailure.failures.size() == 1
                        && projectionFailure.failures.get(0).startsWith("Projection: ")
                        && projectionFailure.failures.get(0).length() <= 160,
                "projection exception must be reported once with bounded text");
        check(projectionCalls[0] == 1 && picture.writes == 1 && audio.writes == 1
                        && source.writes == 1,
                "projection failure must not trigger another restore");

        JSONObject malformed = new JSONObject(root.toString());
        malformed.put("extra", true);
        expectRestoreInvalid(malformed, families, "test-fingerprint",
                new RestoreAdapter[] {picture, audio, source}, projectionCalls,
                "malformed restore root");
        expectRestoreInvalid(root, families, "other-fingerprint",
                new RestoreAdapter[] {picture, audio, source}, projectionCalls,
                "wrong-fingerprint restore");
    }

    private static void expectRestoreInvalid(JSONObject root,
            Map<String, java.util.List<Control>> families, String fingerprint,
            RestoreAdapter[] adapters, int[] projectionCalls, String message) throws Exception {
        for (RestoreAdapter adapter : adapters) adapter.writes = 0;
        projectionCalls[0] = 0;
        boolean thrown = false;
        try {
            RestoreEngine.restore(root, families, fingerprint, projection -> {
                projectionCalls[0]++;
            });
        } catch (Exception expected) {
            thrown = true;
        }
        check(thrown, message + " must be rejected");
        for (RestoreAdapter adapter : adapters) {
            check(adapter.writes == 0, message + " must not write families");
        }
        check(projectionCalls[0] == 0, message + " must not write projection");
    }

    private static void testRoutineBatchLinkedControls() throws Exception {
        class LinkedAdapter implements ControlAdapter {
            private final String title;
            private final java.util.function.IntSupplier getter;
            private final java.util.function.IntConsumer setter;
            private final java.util.List<String> order;

            LinkedAdapter(String title, java.util.function.IntSupplier getter,
                    java.util.function.IntConsumer setter, java.util.List<String> order) {
                this.title = title;
                this.getter = getter;
                this.setter = setter;
                this.order = order;
            }

            @Override
            public int read() {
                return getter.getAsInt();
            }

            @Override
            public void write(int value) {
                order.add(title);
                setter.accept(value);
            }

            @Override
            public int min() { return 0; }

            @Override
            public int max() { return 255; }
        }

        int[] mode = {21};
        int[] backlight = {100};
        int[] temperature = {0};
        java.util.List<String> order = new java.util.ArrayList<>();
        Map<String, java.util.List<Control>> families = new java.util.LinkedHashMap<>();
        families.put("picture", java.util.Arrays.asList(
                new Control("display.backlight", "Backlight", new LinkedAdapter("Backlight",
                        () -> backlight[0], value -> {
                            backlight[0] = value;
                            mode[0] = 21;
                        }, order)),
                new Control("display.pictureMode", "Picture mode", new LinkedAdapter("Picture mode",
                        () -> mode[0], value -> {
                            mode[0] = value;
                            backlight[0] = 100;
                        }, order)),
                new Control("display.colorTemp", "Colour temperature", new LinkedAdapter(
                        "Colour temperature", () -> temperature[0], value -> {
                            temperature[0] = value;
                            mode[0] = 21;
                        }, order))));
        Map<String, Integer> requested = new HashMap<>();
        requested.put("picture:display.backlight", 100);
        requested.put("picture:display.pictureMode", 5);
        requested.put("picture:display.colorTemp", 0);
        RoutineEngine.Result linked = RoutineEngine.apply(families, requested);
        check(order.equals(java.util.Arrays.asList(
                        "Backlight", "Colour temperature", "Picture mode"))
                        && mode[0] == 5 && backlight[0] == 100
                        && linked.applied == 3 && linked.failures.isEmpty(),
                "Android linked adapters must apply picture mode last");

        order.clear();
        requested.put("picture:display.backlight", 99);
        RoutineEngine.Result mismatch = RoutineEngine.apply(families, requested);
        check(mismatch.applied == 2 && mismatch.failures.size() == 1
                        && mismatch.failures.get(0).equals(
                                "Backlight: Final readback expected 99 but got 100"),
                "Android final mismatch must be visible with corrected counts");

        java.util.List<String> readOrder = new java.util.ArrayList<>();
        int[] successful = {20};
        Map<String, java.util.List<Control>> readFamilies = new java.util.LinkedHashMap<>();
        readFamilies.put("picture", java.util.Arrays.asList(
                new Control("broken", "Broken read", new LinkedAdapter("Broken read", () -> {
                    throw new IllegalStateException("final read failed");
                }, value -> { }, readOrder)),
                new Control("successful", "Successful", new LinkedAdapter("Successful",
                        () -> successful[0], value -> successful[0] = value, readOrder))));
        Map<String, Integer> readValues = new HashMap<>();
        readValues.put("picture:broken", 11);
        readValues.put("picture:successful", 21);
        RoutineEngine.Result readFailure = RoutineEngine.apply(readFamilies, readValues);
        check(readFailure.applied == 1 && readFailure.failures.size() == 1
                        && readFailure.failures.get(0).equals(
                                "Broken read: Final readback: final read failed")
                        && successful[0] == 21
                        && readOrder.equals(java.util.Arrays.asList("Broken read", "Successful")),
                "Android final read failure must decrement only its own write");
    }

    private static void testOwnerAssistantUiState() throws Exception {
        File directory = new File("/data/local/tmp",
                "hy450-owner-ui-state-" + System.nanoTime());
        check(directory.mkdirs(), "owner UI-state test directory must be created");
        try {
            FakePortableAdapter backlight = new FakePortableAdapter(100);
            FakePortableAdapter vendor = new FakePortableAdapter(20);
            Map<String, java.util.List<Control>> families = portableFamilies(backlight, vendor);
            FakeAccess access = new FakeAccess(new HashMap<>(), new HashMap<>());
            JSONObject root = PortableBackup.capture(families, access,
                    "1.6.0", "test-fingerprint", "2026-11-03T00:00:00Z");

            RoutineStore routines = new RoutineStore(new File(directory, "routines"));
            RoutinesActivity.UiState empty = RoutinesActivity.uiState(null, null);
            check(!empty.applyEnabled && !empty.undoEnabled,
                    "empty slot and absent undo must disable writes");
            routines.saveUndo(routineCapture(90, "undo"));
            RoutinesActivity.UiState recreated = RoutinesActivity.uiState(
                    null, new RoutineStore(new File(directory, "routines")).readUndo());
            check(!recreated.applyEnabled && recreated.undoEnabled,
                    "persisted undo must survive recreation without enabling an empty slot");
            routines.saveSlot("movie", routineCapture(10, "slot"));
            check(RoutinesActivity.uiState(routines.readSlot("movie"), null).applyEnabled,
                    "populated slot must enable Apply");

            BackupRecoveryActivity.ImportState valid = BackupRecoveryActivity.inspect(
                    root, families, "test-fingerprint");
            check(valid.restoreEnabled && valid.preview != null && valid.error == null,
                    "valid matching import must enable Restore");
            JSONObject malformed = new JSONObject(root.toString());
            malformed.put("extra", true);
            BackupRecoveryActivity.ImportState invalid = BackupRecoveryActivity.inspect(
                    malformed, families, "test-fingerprint");
            check(!invalid.restoreEnabled && invalid.error != null,
                    "invalid import must disable Restore");
            JSONObject mismatch = new JSONObject(root.toString());
            mismatch.put("firmware", "other-fingerprint");
            BackupRecoveryActivity.ImportState mismatched = BackupRecoveryActivity.inspect(
                    mismatch, families, "test-fingerprint");
            check(!mismatched.restoreEnabled && mismatched.preview != null
                            && !mismatched.preview.firmwareMatches,
                    "firmware mismatch must remain previewable but disable Restore");

            OwnerBackupStore safety = new OwnerBackupStore(new File(directory, "safety"));
            safety.saveSafety(root);
            BackupRecoveryActivity.ImportState recovered = BackupRecoveryActivity.inspect(
                    new OwnerBackupStore(new File(directory, "safety")).readSafety(),
                    families, "test-fingerprint");
            check(recovered.restoreEnabled,
                    "persisted safety document must enable recovery after recreation");
            int[] importCalls = {0};
            BackupRecoveryActivity.ImportController imports =
                    new BackupRecoveryActivity.ImportController();
            BackupRecoveryActivity.ImportState cancelled = imports.onActivityResult(
                    android.app.Activity.RESULT_CANCELED, null, uri -> {
                        importCalls[0]++;
                        return valid;
                    });
            BackupRecoveryActivity.ImportState missingData = imports.onActivityResult(
                    android.app.Activity.RESULT_OK, null, uri -> {
                        importCalls[0]++;
                        return valid;
                    });
            check(!cancelled.restoreEnabled && cancelled.root == null
                            && !missingData.restoreEnabled && importCalls[0] == 0,
                    "SAF cancellation/BACK must use the Activity path without reading or writing");

            check(valid.preview.summary.length() > 0,
                    "import preview must contain a non-empty summary");
            check(backlight.writes == 0 && vendor.writes == 0 && access.writes == 0
                            && access.globalWrites == 0 && access.propertyWrites == 0,
                    "preview, cancellation, and recreated recovery state must not write hardware");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static void testOwnerAssistantControllers() throws Exception {
        FakePortableAdapter backlight = new FakePortableAdapter(10);
        FakePortableAdapter vendor = new FakePortableAdapter(20);
        Map<String, java.util.List<Control>> families = portableFamilies(backlight, vendor);
        RoutineEngine.Capture routine = new RoutineEngine.Capture(
                java.util.Collections.singletonMap("picture:backlight", 50),
                java.util.Collections.emptyList());
        RoutineEngine.Capture undo = new RoutineEngine.Capture(
                java.util.Collections.singletonMap("picture:backlight", 10),
                java.util.Collections.emptyList());
        java.util.List<String> order = new java.util.ArrayList<>();
        int[] persistentWrites = {0};
        RoutinesActivity.ApplyController apply = new RoutinesActivity.ApplyController(
                capture -> {
                    persistentWrites[0]++;
                    order.add("undo");
                },
                (familyValues, values) -> {
                    order.add("apply");
                    return RoutineEngine.apply(familyValues, values);
                });
        check(apply.onConfirmation(false, families, routine, undo) == null,
                "negative Apply must not produce a result");
        check(apply.onConfirmation(false, families, routine, undo) == null,
                "BACK Apply must not produce a result");
        check(persistentWrites[0] == 0 && backlight.writes == 0 && vendor.writes == 0
                        && order.isEmpty(),
                "negative/BACK Apply must leave persistent and hardware writes at zero");
        RoutineEngine.Result applied = apply.onConfirmation(true, families, routine, undo);
        check(applied.applied == 1 && order.equals(java.util.Arrays.asList("undo", "apply"))
                        && persistentWrites[0] == 1,
                "positive Apply must save undo before RoutineEngine.apply");

        FakeAccess access = new FakeAccess(new HashMap<>(), new HashMap<>());
        JSONObject root = PortableBackup.capture(families, access,
                "1.6.0", "test-fingerprint", "2026-11-03T00:00:00Z");
        persistentWrites[0] = 0;
        backlight.writes = 0;
        vendor.writes = 0;
        order.clear();
        BackupRecoveryActivity.RestoreController restore =
                new BackupRecoveryActivity.RestoreController(
                        () -> {
                            order.add("capture");
                            return root;
                        },
                        safety -> {
                            persistentWrites[0]++;
                            order.add("safety");
                        },
                        document -> {
                            order.add("restore");
                            return RestoreEngine.restore(document, families,
                                    "test-fingerprint", projection -> { });
                        });
        check(restore.onConfirmation(false, root) == null,
                "negative Restore must not produce a result");
        check(restore.onConfirmation(false, root) == null,
                "BACK Restore must not produce a result");
        check(persistentWrites[0] == 0 && backlight.writes == 0 && vendor.writes == 0
                        && access.writes == 0 && order.isEmpty(),
                "negative/BACK Restore must leave persistent and hardware writes at zero");
        RestoreEngine.Result restored = restore.onConfirmation(true, root);
        check(restored.failures.isEmpty()
                        && order.equals(java.util.Arrays.asList("capture", "safety", "restore"))
                        && persistentWrites[0] == 1,
                "positive Restore must save safety before RestoreEngine.restore");
    }

    private static void testPortableBackup() throws Exception {
        FakePortableAdapter backlight = new FakePortableAdapter(100);
        FakePortableAdapter vendor = new FakePortableAdapter(105);
        Map<String, java.util.List<Control>> families = portableFamilies(backlight, vendor);
        Map<String, String> globals = new HashMap<>();
        globals.put("JbCamX", "");
        globals.put("PoCamX", "12.5");
        Map<String, String> properties = new HashMap<>();
        properties.put("persist.display.keystone_ltx", "17.5");
        FakeAccess access = new FakeAccess(globals, properties);
        String createdUtc = "2026-11-03T00:00:00Z";
        JSONObject root = PortableBackup.capture(
                families, access, "1.6.0", "test-fingerprint", createdUtc);

        check(root.length() == 8, "portable root allowlist");
        check("hy450-control-backup".equals(root.getString("type"))
                        && root.getInt("schema") == 1,
                "portable type and schema");
        check(createdUtc.equals(root.getString("createdUtc"))
                        && "1.6.0".equals(root.getString("appVersion"))
                        && "test-fingerprint".equals(root.getString("firmware")),
                "portable metadata");
        JSONObject picture = root.getJSONObject("families").getJSONObject("picture");
        check(picture.getInt("backlight") == 100, "portable capture value");
        check(!picture.has("vendor") && root.getJSONArray("warnings").length() == 1,
                "out-of-domain vendor getter must be omitted with a warning");

        PortableBackup.Preview preview = PortableBackup.preview(root, "test-fingerprint");
        check(preview.summary.contains("hy450-control-backup")
                        && preview.summary.contains("schema 1")
                        && preview.summary.contains(createdUtc),
                "preview summary must include type, schema, and time");
        check(preview.firmwareMatches && preview.warnings.size() == 1,
                "preview compatibility and warnings");
        check(backlight.writes == 0 && vendor.writes == 0 && access.writes == 0,
                "capture and preview must not write");

        PortableBackup.Validated valid = PortableBackup.validate(
                root, families, "test-fingerprint");
        check(valid.values.get("picture:backlight") == 100, "portable value");
        check(valid.projection.isNull("global:GsInitXYZ"), "absent global must survive");
        check("".equals(valid.projection.getString("global:JbCamX")),
                "empty global must survive validation");
        check("12.5".equals(valid.projection.getString("global:PoCamX")),
                "non-empty global must survive validation");
        check(createdUtc.equals(valid.createdUtc) && "1.6.0".equals(valid.appVersion)
                        && valid.warnings.size() == 1,
                "validated metadata and warnings");
        check(backlight.writes == 0 && vendor.writes == 0 && access.writes == 0,
                "validation must not write");

        backlight.value = 10;
        globals.put("GsInitXYZ", "created-later");
        globals.put("JbCamX", "changed");
        globals.put("PoCamX", "changed");
        properties.put("persist.display.keystone_ltx", "changed");
        RoutineEngine.Result applied = RoutineEngine.apply(families, valid.values);
        ProjectionBackup.restoreValues(valid.projection, access);
        check(applied.applied == 1 && backlight.value == 100,
                "portable routine must round trip");
        check(!globals.containsKey("GsInitXYZ") && "".equals(globals.get("JbCamX"))
                        && "12.5".equals(globals.get("PoCamX"))
                        && "17.5".equals(properties.get("persist.display.keystone_ltx")),
                "portable projection must round trip all global states");

        JSONObject mismatch = new JSONObject(root.toString());
        mismatch.put("firmware", "other-fingerprint");
        PortableBackup.Preview mismatchPreview = PortableBackup.preview(
                mismatch, "test-fingerprint");
        check(!mismatchPreview.firmwareMatches, "preview must report firmware mismatch");
        expectPortableInvalid(mismatch, "firmware mismatch");

        JSONObject wrongType = new JSONObject(root.toString());
        wrongType.put("type", "other");
        expectPortableInvalid(wrongType, "wrong portable type");
        JSONObject wrongSchema = new JSONObject(root.toString());
        wrongSchema.put("schema", 2);
        expectPortableInvalid(wrongSchema, "wrong portable schema");
        JSONObject extraRoot = new JSONObject(root.toString());
        extraRoot.put("extra", true);
        expectPortableInvalid(extraRoot, "unknown portable root key");
        JSONObject missingRoot = new JSONObject(root.toString());
        missingRoot.remove("createdUtc");
        expectPortableInvalid(missingRoot, "missing portable root key");
        JSONObject badMetadata = new JSONObject(root.toString());
        badMetadata.put("appVersion", 16);
        expectPortableInvalid(badMetadata, "non-string portable metadata");

        JSONObject unknownFamily = new JSONObject(root.toString());
        unknownFamily.getJSONObject("families").put("unknown", new JSONObject());
        expectPortableInvalid(unknownFamily, "unknown portable family");
        JSONObject unknownControl = new JSONObject(root.toString());
        unknownControl.getJSONObject("families").getJSONObject("picture")
                .put("unknown", 10);
        expectPortableInvalid(unknownControl, "unknown portable control");
        JSONObject nonInteger = new JSONObject(root.toString());
        nonInteger.getJSONObject("families").getJSONObject("picture")
                .put("backlight", "100");
        expectPortableInvalid(nonInteger, "non-integer portable value");
        JSONObject outOfDomain = new JSONObject(root.toString());
        outOfDomain.getJSONObject("families").getJSONObject("picture")
                .put("backlight", 105);
        expectPortableInvalid(outOfDomain, "out-of-domain portable value");
        JSONObject badFamilies = new JSONObject(root.toString());
        badFamilies.put("families", new org.json.JSONArray());
        expectPortableInvalid(badFamilies, "non-object portable families");
        JSONObject badFamily = new JSONObject(root.toString());
        badFamily.getJSONObject("families").put("picture", new org.json.JSONArray());
        expectPortableInvalid(badFamily, "non-object portable family");
        JSONObject badWarnings = new JSONObject(root.toString());
        badWarnings.getJSONArray("warnings").put(1);
        expectPortableInvalid(badWarnings, "non-string portable warning");

        JSONObject malformedProjection = new JSONObject(root.toString());
        malformedProjection.getJSONObject("projection").put("malformed", "value");
        expectPortableInvalid(malformedProjection, "malformed projection key");
        JSONObject badProjection = new JSONObject(root.toString());
        badProjection.put("projection", new org.json.JSONArray());
        expectPortableInvalid(badProjection, "non-object projection");

        FakePortableAdapter oversizedAdapter = new FakePortableAdapter(10);
        FakeAccess oversizedAccess = new FakeAccess(new HashMap<>(), new HashMap<>());
        boolean oversized = false;
        try {
            BoundedDocument.read(new java.io.ByteArrayInputStream(new byte[1024 * 1024 + 1]));
        } catch (IllegalStateException expected) {
            oversized = "Backup exceeds 1 MiB".equals(expected.getMessage());
        }
        check(oversized, "oversized portable input");
        check(oversizedAdapter.writes == 0 && oversizedAccess.globalWrites == 0
                        && oversizedAccess.propertyWrites == 0,
                "oversized portable input must not write");
        check(ProjectionBackup.class.getDeclaredMethod(
                        "restoreValues", android.content.Context.class, JSONObject.class) != null,
                "Android projection restore wrapper must exist");
    }

    private static Map<String, java.util.List<Control>> portableFamilies(
            ControlAdapter backlight, ControlAdapter vendor) {
        Map<String, java.util.List<Control>> families = new java.util.LinkedHashMap<>();
        families.put("picture", java.util.Arrays.asList(
                new Control("backlight", "Backlight", backlight),
                new Control("vendor", "Vendor", vendor)));
        return families;
    }

    private static void expectPortableInvalid(JSONObject root, String message) throws Exception {
        FakePortableAdapter backlight = new FakePortableAdapter(10);
        FakePortableAdapter vendor = new FakePortableAdapter(20);
        Map<String, java.util.List<Control>> families = portableFamilies(backlight, vendor);
        FakeAccess access = new FakeAccess(new HashMap<>(), new HashMap<>());
        boolean thrown = false;
        try {
            PortableBackup.validate(root, families, "test-fingerprint");
        } catch (Exception expected) {
            thrown = true;
        }
        check(thrown, message);
        check(backlight.writes == 0 && vendor.writes == 0 && access.globalWrites == 0
                        && access.propertyWrites == 0,
                message + " with zero adapter/global/property writes");
    }

    private static void testDiagnosticAllowlist() throws Exception {
        ControlAdapter adapter = new ControlAdapter() {
            @Override
            public int read() { return 42; }

            @Override
            public void write(int value) {
                throw new AssertionError("diagnostic fixture must not write");
            }

            @Override
            public int min() { return 0; }

            @Override
            public int max() { return 100; }
        };
        Map<String, java.util.List<Control>> families = new java.util.LinkedHashMap<>();
        families.put("picture", java.util.Collections.singletonList(
                new Control("brightness", "Brightness", adapter)));
        CapabilityReport report = CapabilityReport.scan(families);
        JSONObject json = report.toDiagnosticJson(
                "1.6.0", "test-fingerprint", 34, "2026-11-03T00:00:00Z");
        check("hy450-control-diagnostic".equals(json.getString("type")), "diagnostic type");
        check(json.getInt("schema") == 1, "diagnostic schema");
        check("1.6.0".equals(json.getString("appVersion")), "diagnostic app version");
        check("test-fingerprint".equals(json.getString("fingerprint")), "diagnostic firmware");
        check(json.getInt("sdk") == 34, "diagnostic SDK");
        check("2026-11-03T00:00:00Z".equals(json.getString("createdUtc")),
                "diagnostic timestamp");
        check(json.getJSONArray("controls").length() == 1, "diagnostic control entries");
        JSONObject control = json.getJSONArray("controls").getJSONObject(0);
        check("picture".equals(control.getString("family"))
                        && "brightness".equals(control.getString("id"))
                        && "Brightness".equals(control.getString("title"))
                        && control.getBoolean("writable") && control.getBoolean("readable")
                        && "42".equals(control.getString("value")),
                "diagnostic control fields");
        String encoded = json.toString();
        String[] forbidden = {"serial", "ip", "mac", "accounts", "packages", "logcat", "properties"};
        for (String key : forbidden) {
            check(!encoded.contains("\"" + key + "\""), "diagnostic allowlist: " + key);
        }
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

    private static void testRoutineStore() throws Exception {
        File directory = new File("/data/local/tmp",
                "hy450-routine-store-" + System.nanoTime());
        check(directory.mkdirs(), "routine-store test directory must be created");
        try {
            RoutineStore store = new RoutineStore(directory);
            check(store.readSlot("movie") == null && store.readUndo() == null,
                    "absent routine state must return null");
            String[] slots = {"movie", "day", "night", "custom"};
            for (int i = 0; i < slots.length; i++) {
                store.saveSlot(slots[i], routineCapture(10 + i, "slot " + slots[i]));
            }
            for (int i = 0; i < slots.length; i++) {
                RoutineEngine.Capture restored = store.readSlot(slots[i]);
                check(restored.values.get("picture:brightness") == 10 + i
                                && restored.warnings.equals(
                                        java.util.Collections.singletonList("slot " + slots[i])),
                        "routine slot round trip: " + slots[i]);
            }
            check(!new File(directory, "routines.tmp").exists(),
                    "atomic routine rename must consume temporary file");

            RoutineEngine.Capture undo = routineCapture(90, "undo");
            store.saveUndo(undo);
            check(store.readUndo().values.equals(undo.values)
                            && store.readUndo().warnings.equals(undo.warnings),
                    "independent undo round trip");
            store.saveSlot("movie", routineCapture(20, "replacement"));
            check(store.readUndo().values.equals(undo.values),
                    "saving a slot must not modify undo");
            check(store.readSlot("day").values.get("picture:brightness") == 11,
                    "atomic slot replacement must preserve other slots");
            RoutineEngine.Capture replacementUndo = routineCapture(91, "new undo");
            store.saveUndo(replacementUndo);
            check(store.readSlot("movie").values.get("picture:brightness") == 20,
                    "saving undo must not modify a slot");
            check(store.readUndo().values.equals(replacementUndo.values),
                    "undo replacement must round trip");

            File slotsFile = new File(directory, "routines.json");
            String original = JsonFileStore.read(slotsFile);
            boolean rejected = false;
            try {
                store.saveSlot("other", undo);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "fifth routine slot must be rejected");
            check(original.equals(JsonFileStore.read(slotsFile)),
                    "rejected slot must leave existing JSON unchanged");
            expectRoutineSlotFailure(store, "other", "unknown slot read must fail");

            File undoFile = new File(directory, "routine-undo.json");
            write(undoFile, "{not-json");
            expectRoutineUndoFailure(store, "malformed undo JSON must fail");
            check(store.readSlot("movie").values.get("picture:brightness") == 20,
                    "malformed undo must not damage slots");
            write(undoFile, "{\"schema\":1,\"values\":{},\"warnings\":[],\"extra\":true}");
            expectRoutineUndoFailure(store, "unknown undo key must fail");

            write(slotsFile, "{not-json");
            expectRoutineSlotFailure(store, "movie", "malformed slot JSON must fail");
            write(slotsFile, "{\"schema\":2,\"slots\":{}}");
            expectRoutineSlotFailure(store, "movie", "unsupported schema must fail");
            write(slotsFile, "{\"schema\":1,\"slots\":{},\"extra\":true}");
            expectRoutineSlotFailure(store, "movie", "unknown root key must fail");
            write(slotsFile, "{\"schema\":1,\"slots\":{\"other\":"
                    + "{\"values\":{},\"warnings\":[]}}}");
            expectRoutineSlotFailure(store, "movie", "unknown stored slot must fail");
            write(slotsFile, "{\"schema\":1,\"slots\":{\"movie\":"
                    + "{\"values\":{\"picture:brightness\":\"20\"},\"warnings\":[]}}}");
            expectRoutineSlotFailure(store, "movie", "non-integer routine value must fail");
            write(slotsFile, "{\"schema\":1,\"slots\":{\"movie\":"
                    + "{\"values\":{},\"warnings\":[],\"extra\":true}}}");
            expectRoutineSlotFailure(store, "movie", "unknown capture key must fail");
            write(slotsFile, "{\"schema\":1,\"slots\":{\"movie\":"
                    + "{\"values\":{},\"warnings\":[1]}}}");
            expectRoutineSlotFailure(store, "movie", "non-string warning must fail");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static RoutineEngine.Capture routineCapture(int value, String warning) {
        Map<String, Integer> values = new HashMap<>();
        values.put("picture:brightness", value);
        return new RoutineEngine.Capture(values, java.util.Collections.singletonList(warning));
    }

    private static void expectRoutineSlotFailure(RoutineStore store, String slot, String message)
            throws Exception {
        boolean thrown = false;
        try {
            store.readSlot(slot);
        } catch (Exception expected) {
            thrown = true;
        }
        check(thrown, message);
    }

    private static void expectRoutineUndoFailure(RoutineStore store, String message)
            throws Exception {
        boolean thrown = false;
        try {
            store.readUndo();
        } catch (Exception expected) {
            thrown = true;
        }
        check(thrown, message);
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
