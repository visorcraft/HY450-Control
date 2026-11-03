package com.visorcraft.hy450control;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HostTests {
    private static final String[] INSET_KEYS = {
        "persist.display.keystone_ltx", "persist.display.keystone_lty",
        "persist.display.keystone_rtx", "persist.display.keystone_rty",
        "persist.display.keystone_lbx", "persist.display.keystone_lby",
        "persist.display.keystone_rbx", "persist.display.keystone_rby"
    };

    public static void main(String[] args) throws Exception {
        class FakeControl implements ControlAdapter {
            private final boolean writable;
            private final Exception failure;
            private int lastWritten;
            private int writeCalls;

            FakeControl(boolean writable, Exception failure) {
                this.writable = writable;
                this.failure = failure;
            }

            @Override
            public int read() { return lastWritten; }

            @Override
            public void write(int value) throws Exception {
                writeCalls++;
                if (failure != null) throw failure;
                lastWritten = value;
            }

            @Override
            public int min() { return 0; }

            @Override
            public int max() { return 100; }

            @Override
            public boolean isWritable() { return writable; }
        }

        FakeControl firstAdapter = new FakeControl(true, null);
        FakeControl middleAdapter = new FakeControl(true, new Exception("Out of range"));
        FakeControl lastAdapter = new FakeControl(true, null);
        FakeControl readOnlyAdapter = new FakeControl(false, null);
        Control first = new Control("first", "First", firstAdapter);
        Control middle = new Control("middle", "Middle", middleAdapter);
        Control last = new Control("last", "Last", lastAdapter);
        Control readOnly = new Control("read-only", "Read only", readOnlyAdapter);
        List<Control> controls = Arrays.asList(first, middle, last, readOnly);
        Map<String, Integer> stored = new HashMap<>();
        stored.put("first", 10);
        stored.put("middle", 20);
        stored.put("last", 30);
        stored.put("read-only", 40);
        ControlBatch.Values values = new ControlBatch.Values() {
            @Override
            public boolean has(String id) { return stored.containsKey(id); }

            @Override
            public int getInt(String id) throws Exception {
                Integer value = stored.get(id);
                if (value == null) throw new Exception("Missing value");
                return value;
            }
        };
        ControlBatch.Result result = ControlBatch.apply(controls, values);
        check(result.applied == 2, "later controls must still apply");
        check(result.failures.size() == 1, "one failure must be reported");
        check(result.failures.get(0).startsWith("Middle: "), "failure must identify control");
        check(firstAdapter.lastWritten == 10 && lastAdapter.lastWritten == 30,
                "surrounding writes must land");
        check(readOnlyAdapter.writeCalls == 0, "read-only control must be skipped");

        stored.remove("last");
        ControlBatch.Result missingStored = ControlBatch.apply(Arrays.asList(last), values);
        check(missingStored.applied == 0 && missingStored.failures.isEmpty(),
                "missing stored ID must be skipped without failure");
        check(lastAdapter.writeCalls == 1, "missing stored ID must not write adapter");

        boolean immutable = false;
        try {
            result.failures.add("unexpected");
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "failure details must be immutable");

        Exception cause = new IllegalArgumentException("Vendor rejected value");
        Exception wrapped = new java.lang.reflect.InvocationTargetException(cause);
        check("Vendor rejected value".equals(Errors.describe(wrapped)),
                "reflective cause must be visible");
        Exception nested = new java.lang.reflect.InvocationTargetException(wrapped);
        check("Vendor rejected value".equals(Errors.describe(nested)),
                "nested reflective cause must be visible");
        Exception linkage = new java.lang.reflect.InvocationTargetException(
                new NoSuchMethodError("missingVendorMethod"));
        check("missingVendorMethod".equals(Errors.describe(linkage)),
                "reflective linkage error must be visible");
        check("IllegalStateException".equals(Errors.describe(new IllegalStateException())),
                "null message must use class name");
        check("IllegalStateException".equals(Errors.describe(new IllegalStateException("  "))),
                "blank message must use class name");

        testOemRecovery();
        testProjectionValidation();
        testAutomationSelection();
        testVendorAdapters();

        Map<String, String> valid = validValues();
        Keystone.PropertyReader validReader = valid::get;
        Keystone.Snapshot snapshot = Keystone.read(validReader);
        check(snapshot.inset[0] == 12 && snapshot.inset[1] == -7, "valid snapshot");
        check(snapshot.zoomH == 100f && snapshot.zoomV == 95.5f, "valid zoom");
        check(snapshot.matches(Keystone.read(validReader)), "equal snapshots must match");

        int[] differentInsets = snapshot.inset.clone();
        differentInsets[0]++;
        check(!snapshot.matches(new Keystone.Snapshot(differentInsets, 100f, 95.5f)),
                "different insets must not match");
        check(!snapshot.matches(new Keystone.Snapshot(snapshot.inset, 101f, 95.5f)),
                "different horizontal zoom must not match");
        check(!snapshot.matches(new Keystone.Snapshot(snapshot.inset, 100f, 96.5f)),
                "different vertical zoom must not match");

        Map<String, String> missing = validValues();
        missing.remove(INSET_KEYS[3]);
        expectThrows(() -> Keystone.read(missing::get), "missing inset");

        Map<String, String> malformed = validValues();
        malformed.put(INSET_KEYS[4], "bad");
        expectThrows(() -> Keystone.read(malformed::get), "malformed inset");

        Map<String, String> malformedDecimal = validValues();
        malformedDecimal.put(INSET_KEYS[0], "12.bad");
        expectThrows(() -> Keystone.read(malformedDecimal::get), "malformed decimal inset");

        Map<String, String> trailingJunk = validValues();
        trailingJunk.put(INSET_KEYS[0], "12.9junk");
        expectThrows(() -> Keystone.read(trailingJunk::get), "inset trailing junk");

        Map<String, String> repeatedDecimal = validValues();
        repeatedDecimal.put(INSET_KEYS[0], "1.2.3");
        expectThrows(() -> Keystone.read(repeatedDecimal::get), "repeated decimal point");

        Map<String, String> empty = validValues();
        empty.put(INSET_KEYS[0], "  ");
        expectThrows(() -> Keystone.read(empty::get), "empty inset");

        Map<String, String> nan = validValues();
        nan.put("persist.display.keystone_zoom_h", "NaN");
        expectThrows(() -> Keystone.read(nan::get), "NaN zoom");

        Map<String, String> infinite = validValues();
        infinite.put("persist.display.keystone_zoom_v", "Infinity");
        expectThrows(() -> Keystone.read(infinite::get), "infinite zoom");

        Map<String, String> missingZoom = validValues();
        missingZoom.remove("persist.display.keystone_zoom_h");
        expectThrows(() -> Keystone.read(missingZoom::get), "missing zoom");

        Map<String, String> malformedZoom = validValues();
        malformedZoom.put("persist.display.keystone_zoom_v", "bad");
        expectThrows(() -> Keystone.read(malformedZoom::get), "malformed zoom");

        java.io.File directory = new java.io.File(System.getProperty("java.io.tmpdir"),
                "hy450-json-file-store-" + System.nanoTime());
        check(directory.mkdirs(), "file-helper test directory must be created");
        try {
            expectThrows(() -> JsonFileStore.read(new java.io.File(directory, "missing.json")),
                    "missing file must throw");

            java.io.File target = new java.io.File(directory, "values.json");
            java.io.File temporary = new java.io.File(directory, "values.tmp");
            String unicode = "Keystone: Grüße, 投影, 🎬\n";
            JsonFileStore.write(target, temporary, unicode);
            check(unicode.equals(JsonFileStore.read(target)), "UTF-8 content must round trip");

            String replacement = "replacement";
            JsonFileStore.write(target, temporary, replacement);
            check(replacement.equals(JsonFileStore.read(target)),
                    "write must replace existing content");

            java.io.File regularFile = new java.io.File(directory, "regular-file");
            writeFile(regularFile, "not a directory");
            expectThrows(() -> JsonFileStore.write(
                            new java.io.File(regularFile, "target.json"),
                            new java.io.File(regularFile, "temporary.json"), "value"),
                    "regular-file target parent must throw");
        } finally {
            deleteRecursively(directory);
        }

        System.out.println("Host tests passed");
    }

    private static void testVendorAdapters() throws Exception {
        check(ControlAdapter.class.isAssignableFrom(VendorControl.class),
                "VendorControl must implement ControlAdapter");
        check(java.lang.reflect.Modifier.isAbstract(VendorControl.class.getModifiers()),
                "VendorControl must be abstract");
        check(java.lang.reflect.Modifier.isFinal(
                        VendorControl.class.getMethod("write", int.class).getModifiers()),
                "VendorControl write must be final");
        check(java.lang.reflect.Modifier.isAbstract(
                        VendorControl.class.getDeclaredMethod("rawWrite", int.class).getModifiers()),
                "VendorControl rawWrite must be abstract");

        testVendorIntControls(null, "integer");
        testVendorIntControls("speaker", "parameterized integer");
        testVendorBooleanControls();
        testVendorEnumControls();
    }

    private static void testVendorIntControls(Object parameter, String label) throws Exception {
        FakeIntManager manager = new FakeIntManager(5);
        VendorIntControl control = new VendorIntControl(
                manager, "getValue", "setValue", parameter, 0, 10);
        check(control.min() == 0 && control.max() == 10, label + " range");
        control.write(7);
        check(manager.value == 7, label + " successful write");
        check(manager.setCalls == 1, label + " successful setter count");
        check(parameter == null || parameter.equals(manager.parameter),
                label + " parameter must reach manager");

        manager = new FakeIntManager(5);
        control = new VendorIntControl(manager, "getValue", "setValue", parameter, 0, 10);
        VendorIntControl boundsControl = control;
        expectThrows(() -> boundsControl.write(-1), label + " lower bound");
        expectThrows(() -> boundsControl.write(11), label + " upper bound");
        check(manager.setCalls == 0, label + " bounds must not call setter");

        manager = new FakeIntManager(5);
        manager.readFailure = true;
        control = new VendorIntControl(manager, "getValue", "setValue", parameter, 0, 10);
        VendorIntControl readFailureControl = control;
        expectThrows(() -> readFailureControl.write(7), label + " initial read failure");
        check(manager.setCalls == 0, label + " read failure must not call setter");

        manager = new FakeIntManager(5);
        manager.setterFailure = true;
        control = new VendorIntControl(manager, "getValue", "setValue", parameter, 0, 10);
        VendorIntControl setterFailureControl = control;
        expectThrows(() -> setterFailureControl.write(7), label + " setter failure");
        check(manager.setCalls == 1 && manager.value == 5,
                label + " setter failure must not roll back");

        manager = new FakeIntManager(5);
        manager.mismatch = true;
        control = new VendorIntControl(manager, "getValue", "setValue", parameter, 0, 10);
        VendorIntControl mismatchControl = control;
        expectThrows(() -> mismatchControl.write(7), label + " readback mismatch");
        check(manager.value == 5, label + " mismatch must roll back");
        check(manager.setCalls == 2, label + " write plus rollback");

        manager = new FakeIntManager(5);
        manager.mismatch = true;
        manager.rollbackFailure = true;
        control = new VendorIntControl(manager, "getValue", "setValue", parameter, 0, 10);
        VendorIntControl rollbackFailureControl = control;
        expectThrows(() -> rollbackFailureControl.write(7), label + " rollback failure");
        check(manager.setCalls == 2, label + " rollback failure setter count");
    }

    private static void testVendorBooleanControls() throws Exception {
        FakeBooleanManager manager = new FakeBooleanManager(false);
        VendorBooleanControl control = new VendorBooleanControl(manager, "getValue", "setValue");
        check(control.min() == 0 && control.max() == 1, "boolean range");
        check(control.format(0).equals("Off") && control.format(1).equals("On"),
                "boolean formatting");
        control.write(1);
        check(manager.value, "boolean successful write");
        check(manager.setCalls == 1, "boolean successful setter count");

        manager = new FakeBooleanManager(false);
        control = new VendorBooleanControl(manager, "getValue", "setValue");
        VendorBooleanControl boundsControl = control;
        expectThrows(() -> boundsControl.write(-1), "boolean lower bound");
        expectThrows(() -> boundsControl.write(2), "boolean upper bound");
        check(manager.setCalls == 0, "boolean bounds must not call setter");

        manager = new FakeBooleanManager(false);
        manager.readFailure = true;
        control = new VendorBooleanControl(manager, "getValue", "setValue");
        VendorBooleanControl readFailureControl = control;
        expectThrows(() -> readFailureControl.write(1), "boolean initial read failure");
        check(manager.setCalls == 0, "boolean read failure must not call setter");

        manager = new FakeBooleanManager(false);
        manager.setterFailure = true;
        control = new VendorBooleanControl(manager, "getValue", "setValue");
        VendorBooleanControl setterFailureControl = control;
        expectThrows(() -> setterFailureControl.write(1), "boolean setter failure");
        check(manager.setCalls == 1 && !manager.value,
                "boolean setter failure must not roll back");

        manager = new FakeBooleanManager(false);
        manager.mismatch = true;
        control = new VendorBooleanControl(manager, "getValue", "setValue");
        VendorBooleanControl mismatchControl = control;
        expectThrows(() -> mismatchControl.write(1), "boolean readback mismatch");
        check(!manager.value, "boolean mismatch must roll back");
        check(manager.setCalls == 2, "boolean write plus rollback");

        manager = new FakeBooleanManager(false);
        manager.mismatch = true;
        manager.rollbackFailure = true;
        control = new VendorBooleanControl(manager, "getValue", "setValue");
        VendorBooleanControl rollbackFailureControl = control;
        expectThrows(() -> rollbackFailureControl.write(1), "boolean rollback failure");
        check(manager.setCalls == 2, "boolean rollback failure setter count");
    }

    private static void testVendorEnumControls() throws Exception {
        FakeEnumManager manager = new FakeEnumManager(FakeMode.E_AW_FIRST_MODE);
        VendorEnumControl control = new VendorEnumControl(manager, "getValue", "setValue",
                FakeMode.class, FakeMode.values());
        check(control.min() == 0 && control.max() == 2, "enum range");
        check(control.format(2).equals("Third Mode"), "enum formatting");
        control.write(2);
        check(manager.value == FakeMode.E_AW_THIRD_MODE, "enum successful write");
        check(manager.setCalls == 1, "enum successful setter count");

        manager = new FakeEnumManager(FakeMode.E_AW_FIRST_MODE);
        control = new VendorEnumControl(manager, "getValue", "setValue",
                FakeMode.class, FakeMode.values());
        VendorEnumControl boundsControl = control;
        expectThrows(() -> boundsControl.write(-1), "enum lower bound");
        expectThrows(() -> boundsControl.write(3), "enum upper bound");
        check(manager.setCalls == 0, "enum bounds must not call setter");

        manager = new FakeEnumManager(FakeMode.E_AW_FIRST_MODE);
        manager.readFailure = true;
        control = new VendorEnumControl(manager, "getValue", "setValue",
                FakeMode.class, FakeMode.values());
        VendorEnumControl readFailureControl = control;
        expectThrows(() -> readFailureControl.write(2), "enum initial read failure");
        check(manager.setCalls == 0, "enum read failure must not call setter");

        manager = new FakeEnumManager(FakeMode.E_AW_FIRST_MODE);
        manager.setterFailure = true;
        control = new VendorEnumControl(manager, "getValue", "setValue",
                FakeMode.class, FakeMode.values());
        VendorEnumControl setterFailureControl = control;
        expectThrows(() -> setterFailureControl.write(2), "enum setter failure");
        check(manager.setCalls == 1 && manager.value == FakeMode.E_AW_FIRST_MODE,
                "enum setter failure must not roll back");

        manager = new FakeEnumManager(FakeMode.E_AW_FIRST_MODE);
        manager.mismatch = true;
        control = new VendorEnumControl(manager, "getValue", "setValue",
                FakeMode.class, FakeMode.values());
        VendorEnumControl mismatchControl = control;
        expectThrows(() -> mismatchControl.write(2), "enum readback mismatch");
        check(manager.value == FakeMode.E_AW_FIRST_MODE, "enum mismatch must roll back");
        check(manager.setCalls == 2, "enum write plus rollback");

        manager = new FakeEnumManager(FakeMode.E_AW_FIRST_MODE);
        manager.mismatch = true;
        manager.rollbackFailure = true;
        control = new VendorEnumControl(manager, "getValue", "setValue",
                FakeMode.class, FakeMode.values());
        VendorEnumControl rollbackFailureControl = control;
        expectThrows(() -> rollbackFailureControl.write(2), "enum rollback failure");
        check(manager.setCalls == 2, "enum rollback failure setter count");
    }

    public static final class FakeIntManager {
        int value;
        int setCalls;
        String parameter;
        boolean readFailure;
        boolean setterFailure;
        boolean mismatch;
        boolean rollbackFailure;

        FakeIntManager(int value) {
            this.value = value;
        }

        public int getValue() throws Exception {
            if (readFailure) throw new Exception("read failure");
            return value;
        }

        public int getValue(String parameter) throws Exception {
            this.parameter = parameter;
            return getValue();
        }

        public void setValue(int value) throws Exception {
            setCalls++;
            if (setterFailure) throw new Exception("setter failure");
            if (mismatch && setCalls == 1) return;
            if (rollbackFailure && setCalls == 2) throw new Exception("rollback failure");
            this.value = value;
        }

        public void setValue(String parameter, int value) throws Exception {
            this.parameter = parameter;
            setValue(value);
        }
    }

    public static final class FakeBooleanManager {
        boolean value;
        int setCalls;
        boolean readFailure;
        boolean setterFailure;
        boolean mismatch;
        boolean rollbackFailure;

        FakeBooleanManager(boolean value) {
            this.value = value;
        }

        public boolean getValue() throws Exception {
            if (readFailure) throw new Exception("read failure");
            return value;
        }

        public void setValue(boolean value) throws Exception {
            setCalls++;
            if (setterFailure) throw new Exception("setter failure");
            if (mismatch && setCalls == 1) return;
            if (rollbackFailure && setCalls == 2) throw new Exception("rollback failure");
            this.value = value;
        }
    }

    public enum FakeMode {
        E_AW_FIRST_MODE,
        E_AW_SECOND_MODE,
        E_AW_THIRD_MODE
    }

    public static final class FakeEnumManager {
        FakeMode value;
        int setCalls;
        boolean readFailure;
        boolean setterFailure;
        boolean mismatch;
        boolean rollbackFailure;

        FakeEnumManager(FakeMode value) {
            this.value = value;
        }

        public FakeMode getValue() throws Exception {
            if (readFailure) throw new Exception("read failure");
            return value;
        }

        public void setValue(FakeMode value) throws Exception {
            setCalls++;
            if (setterFailure) throw new Exception("setter failure");
            if (mismatch && setCalls == 1) return;
            if (rollbackFailure && setCalls == 2) throw new Exception("rollback failure");
            this.value = value;
        }
    }

    private static void testAutomationSelection() throws Exception {
        int[] selected = {-1};
        int[] attempted = {-1};
        try {
            selected[0] = AutomationSelection.cycle(0, 3, index -> {
                attempted[0] = index;
                throw new SecurityException("Exact alarms denied");
            });
        } catch (SecurityException expected) {
            // The failed action must not produce a committed selection.
        }
        check(attempted[0] == 1, "selection must attempt the next index");
        check(selected[0] == -1, "failed action must not return a committed selection");
        selected[0] = AutomationSelection.cycle(0, 3, index -> { });
        check(selected[0] == 1, "successful selection returns the next index");
        selected[0] = AutomationSelection.cycle(2, 3, index -> { });
        check(selected[0] == 0, "selection must wrap after success");
    }

    private static void testProjectionValidation() throws Exception {
        Object jsonNull = new Object();
        ProjectionBackup.validateEntry("global:GsInitXYZ", jsonNull, jsonNull);
        ProjectionBackup.validateEntry("global:JbCamX", "", jsonNull);
        ProjectionBackup.validateEntry("property:persist.display.keystone_ltx", "12.5", jsonNull);
        expectThrows(() -> ProjectionBackup.validateEntry("global:Unknown", "1", jsonNull),
                "unknown global must be rejected");
        expectThrows(() -> ProjectionBackup.validateEntry(
                "property:persist.display.unknown", "1", jsonNull),
                "unknown property must be rejected");
        expectThrows(() -> ProjectionBackup.validateEntry("malformed", "1", jsonNull),
                "malformed key must be rejected");
        expectThrows(() -> ProjectionBackup.validateEntry("global:JbCamX", 1, jsonNull),
                "numeric global must be rejected");
        expectThrows(() -> ProjectionBackup.validateEntry(
                "property:persist.display.keystone_ltx", 12, jsonNull),
                "numeric property must be rejected");
        expectThrows(() -> ProjectionBackup.validateEntry(
                "property:persist.display.keystone_ltx", jsonNull, jsonNull),
                "null property must be rejected");
    }

    private static void testOemRecovery() throws Exception {
        FakeRecoveryStore store = new FakeRecoveryStore();
        FakeRecoveryValues values = new FakeRecoveryValues();
        OemRecovery preparation = new OemRecovery(
                store, new FakeRecoveryScheduler(), values);
        preparation.prepare();
        check(store.pending, "prepare must persist a pending snapshot");

        FakeRecoveryScheduler scheduler = new FakeRecoveryScheduler();
        OemRecovery recovery = new OemRecovery(store, scheduler, values);
        List<Exception> errors = new java.util.ArrayList<>();
        recovery.resume(errors::add);
        check(scheduler.count() == 3, "resume must schedule three passes");
        check(scheduler.delays.get(0) == 0L && scheduler.delays.get(1) == 400L
                && scheduler.delays.get(2) == 1200L, "recovery delays must be preserved");
        scheduler.run(0);
        scheduler.run(1);
        scheduler.run(2);
        check(values.applied == 3, "all recovery passes must apply");
        check(!store.pending && store.clears == 2, "verified recovery must clear pending");
        check(errors.isEmpty(), "successful recovery must not report errors");

        store = new FakeRecoveryStore();
        values = new FakeRecoveryValues();
        preparation = new OemRecovery(store, new FakeRecoveryScheduler(), values);
        preparation.prepare();
        values.mismatch = true;
        scheduler = new FakeRecoveryScheduler();
        recovery = new OemRecovery(store, scheduler, values);
        errors = new java.util.ArrayList<>();
        recovery.resume(errors::add);
        scheduler.run(0);
        scheduler.run(1);
        scheduler.run(2);
        check(store.pending && store.clears == 1,
                "failed final readback must retain pending recovery");
        check(errors.size() == 1, "failed final readback must be reported");

        store = new FakeRecoveryStore();
        values = new FakeRecoveryValues();
        preparation = new OemRecovery(store, new FakeRecoveryScheduler(), values);
        preparation.prepare();
        store.failClear = true;
        scheduler = new FakeRecoveryScheduler();
        recovery = new OemRecovery(store, scheduler, values);
        errors = new java.util.ArrayList<>();
        recovery.resume(errors::add);
        scheduler.run(0);
        scheduler.run(1);
        scheduler.run(2);
        check(store.pending && store.saves == 2,
                "failed clear must re-save the pending snapshot");
        check(errors.size() == 1, "failed clear must be reported");

        store = new FakeRecoveryStore();
        values = new FakeRecoveryValues();
        scheduler = new FakeRecoveryScheduler();
        recovery = new OemRecovery(store, scheduler, values);
        recovery.prepare();
        recovery.resume(errors::add);
        recovery.cancel();
        check(scheduler.count() == 0, "cancel must remove every queued callback");
        check(!store.pending, "cancel must clear persisted recovery");

        store = new FakeRecoveryStore();
        store.pending = true;
        store.readFailure = true;
        scheduler = new FakeRecoveryScheduler();
        recovery = new OemRecovery(store, scheduler, values);
        errors = new java.util.ArrayList<>();
        recovery.resume(errors::add);
        check(errors.size() == 1 && scheduler.count() == 0,
                "malformed persisted state must report without scheduling");

        store = new FakeRecoveryStore();
        store.failSave = true;
        recovery = new OemRecovery(store, new FakeRecoveryScheduler(), values);
        expectThrows(recovery::prepare, "prepare commit failure must throw");

        store = new FakeRecoveryStore();
        store.failClear = true;
        recovery = new OemRecovery(store, new FakeRecoveryScheduler(), values);
        expectThrows(recovery::cancel, "cancel commit failure must throw");
        boolean[] restored = {false};
        OemRecovery failingRecovery = recovery;
        expectThrows(() -> OemRecovery.cancelAndRun(failingRecovery,
                () -> restored[0] = true), "failed cancellation must stop restore");
        check(!restored[0], "failed cancellation must prevent a newer restore");

        store = new FakeRecoveryStore();
        recovery = new OemRecovery(store, new FakeRecoveryScheduler(), values);
        OemRecovery.cancelAndRun(recovery, () -> restored[0] = true);
        check(restored[0], "successful cancellation must allow a newer restore");
    }

    private static Keystone.Snapshot recoverySnapshot(int offset) {
        int[] inset = new int[8];
        for (int i = 0; i < inset.length; i++) inset[i] = i + offset;
        return new Keystone.Snapshot(inset, 100f, 95.5f);
    }

    private static final class FakeRecoveryStore implements OemRecovery.Store {
        Keystone.Snapshot saved = recoverySnapshot(0);
        boolean pending;
        boolean readFailure;
        boolean failSave;
        boolean failClear;
        int saves;
        int clears;

        @Override
        public boolean isPending() { return pending; }

        @Override
        public Keystone.Snapshot read() throws Exception {
            if (readFailure) throw new Exception("Malformed saved keystone");
            return saved;
        }

        @Override
        public boolean save(Keystone.Snapshot snapshot) {
            saves++;
            if (failSave) return false;
            saved = snapshot;
            pending = true;
            return true;
        }

        @Override
        public boolean clear() {
            clears++;
            if (failClear) return false;
            pending = false;
            return true;
        }
    }

    private static final class FakeRecoveryScheduler implements OemRecovery.Scheduler {
        final List<Runnable> callbacks = new java.util.ArrayList<>();
        final List<Long> delays = new java.util.ArrayList<>();

        @Override
        public void post(Runnable runnable) {
            callbacks.add(runnable);
            delays.add(0L);
        }

        @Override
        public void postDelayed(Runnable runnable, long delayMillis) {
            callbacks.add(runnable);
            delays.add(delayMillis);
        }

        @Override
        public void removeCallbacks(Runnable runnable) {
            for (int i = callbacks.size() - 1; i >= 0; i--) {
                if (callbacks.get(i) == runnable) {
                    callbacks.remove(i);
                    delays.remove(i);
                }
            }
        }

        int count() { return callbacks.size(); }
        void run(int index) { callbacks.get(index).run(); }
    }

    private static final class FakeRecoveryValues implements OemRecovery.Values {
        Keystone.Snapshot current = recoverySnapshot(0);
        boolean mismatch;
        int applied;

        @Override
        public Keystone.Snapshot read() {
            return mismatch ? recoverySnapshot(99) : current;
        }

        @Override
        public void apply(Keystone.Snapshot snapshot) {
            current = snapshot;
            applied++;
        }
    }

    private static void writeFile(java.io.File target, String value) throws Exception {
        java.io.FileOutputStream output = new java.io.FileOutputStream(target);
        try {
            output.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }

    private static void deleteRecursively(java.io.File file) {
        java.io.File[] children = file.listFiles();
        if (children != null) {
            for (java.io.File child : children) deleteRecursively(child);
        }
        file.delete();
    }

    private static Map<String, String> validValues() {
        Map<String, String> values = new HashMap<>();
        String[] insets = {"12", " -7.9 ", "3.8", "4", "5", "6", "7", "8"};
        for (int i = 0; i < INSET_KEYS.length; i++) values.put(INSET_KEYS[i], insets[i]);
        values.put("persist.display.keystone_zoom_h", "100.0");
        values.put("persist.display.keystone_zoom_v", "95.5");
        return values;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectThrows(ThrowingRunnable action, String message) throws Exception {
        boolean thrown = false;
        try {
            action.run();
        } catch (Exception expected) {
            thrown = true;
        }
        check(thrown, message);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
