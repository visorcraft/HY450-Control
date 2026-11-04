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

        testBoundedDocument();
        testRoutineEngine();
        testCapabilityReport();
        testOemRecovery();
        testProjectionValidation();
        testAutomationSelection();
        testSleepSchedule();
        testBestScore();
        testBreakoutGame();
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

    private static void testBoundedDocument() throws Exception {
        class TrackingInput extends java.io.ByteArrayInputStream {
            private boolean closed;

            TrackingInput(byte[] value) {
                super(value);
            }

            @Override
            public int available() {
                return 0;
            }

            @Override
            public void close() {
                closed = true;
            }
        }
        class TrackingOutput extends java.io.ByteArrayOutputStream {
            private boolean flushed;
            private boolean closed;

            @Override
            public void flush() {
                flushed = true;
            }

            @Override
            public void close() {
                closed = true;
            }
        }

        byte[] maximum = new byte[1024 * 1024];
        Arrays.fill(maximum, (byte) 'x');
        TrackingInput maximumInput = new TrackingInput(maximum);
        byte[] roundTrip = BoundedDocument.read(maximumInput);
        check(roundTrip.length == maximum.length, "exact maximum must be accepted");
        check(new String(roundTrip, java.nio.charset.StandardCharsets.UTF_8).equals(
                        new String(maximum, java.nio.charset.StandardCharsets.UTF_8)),
                "maximum document must round trip UTF-8");
        check(!maximumInput.closed, "document read must not close provider stream");

        TrackingInput oversizedInput = new TrackingInput(new byte[1024 * 1024 + 1]);
        boolean oversized = false;
        try {
            BoundedDocument.read(oversizedInput);
        } catch (IllegalStateException expected) {
            oversized = "Backup exceeds 1 MiB".equals(expected.getMessage());
        }
        check(oversized, "oversized document must fail before JSON parsing");
        check(!oversizedInput.closed, "oversized read must not close provider stream");

        TrackingOutput output = new TrackingOutput();
        BoundedDocument.write(output, roundTrip);
        check(Arrays.equals(maximum, output.toByteArray()), "document write must write all bytes");
        check(output.flushed && !output.closed, "document write must flush without closing");
    }

    private static void testRoutineEngine() throws Exception {
        class RoutineAdapter implements ControlAdapter {
            private int value;
            private final boolean writable;
            private final Exception readFailure;
            private final Exception writeFailure;
            private int writeCalls;

            RoutineAdapter(int value, boolean writable, Exception readFailure, Exception writeFailure) {
                this.value = value;
                this.writable = writable;
                this.readFailure = readFailure;
                this.writeFailure = writeFailure;
            }

            @Override
            public int read() throws Exception {
                if (readFailure != null) throw readFailure;
                return value;
            }

            @Override
            public void write(int value) throws Exception {
                writeCalls++;
                if (writeFailure != null) throw writeFailure;
                this.value = value;
            }

            @Override
            public int min() { return 0; }

            @Override
            public int max() { return 100; }

            @Override
            public boolean isWritable() { return writable; }
        }

        RoutineAdapter firstAdapter = new RoutineAdapter(10, true, null, null);
        RoutineAdapter outsideAdapter = new RoutineAdapter(105, true, null, null);
        RoutineAdapter brokenAdapter = new RoutineAdapter(0, true,
                new Exception("read failed"), null);
        Map<String, List<Control>> captureFamilies = new java.util.LinkedHashMap<>();
        captureFamilies.put("picture", Arrays.asList(
                new Control("first", "First", firstAdapter),
                new Control("outside", "Outside", outsideAdapter),
                new Control("second", "Second", brokenAdapter)));
        RoutineEngine.Capture capture = RoutineEngine.capture(captureFamilies);
        check(capture.values.get("picture:first") == 10, "valid routine value");
        check(!capture.values.containsKey("picture:outside"),
                "out-of-range capture must be omitted");
        check(capture.values.size() == 1 && capture.warnings.size() == 2,
                "capture omissions must be visible without clamping");

        boolean immutableValues = false;
        try {
            capture.values.put("picture:other", 20);
        } catch (UnsupportedOperationException expected) {
            immutableValues = true;
        }
        check(immutableValues, "routine values must be immutable");
        boolean immutableWarnings = false;
        try {
            capture.warnings.add("unexpected");
        } catch (UnsupportedOperationException expected) {
            immutableWarnings = true;
        }
        check(immutableWarnings, "routine warnings must be immutable");

        Map<String, Integer> invalid = new HashMap<>(capture.values);
        invalid.put("picture:second", 105);
        expectThrows(() -> RoutineEngine.apply(captureFamilies, invalid), "invalid routine");
        check(firstAdapter.writeCalls == 0 && outsideAdapter.writeCalls == 0
                        && brokenAdapter.writeCalls == 0,
                "validation must precede every routine write");

        RoutineAdapter middleAdapter = new RoutineAdapter(20, true, null,
                new Exception("write failed"));
        RoutineAdapter lastAdapter = new RoutineAdapter(30, true, null, null);
        RoutineAdapter readOnlyAdapter = new RoutineAdapter(40, false, null, null);
        Map<String, List<Control>> applyFamilies = new java.util.LinkedHashMap<>();
        applyFamilies.put("picture", Arrays.asList(
                new Control("first", "First", firstAdapter),
                new Control("middle", "Middle", middleAdapter),
                new Control("last", "Last", lastAdapter),
                new Control("read-only", "Read only", readOnlyAdapter)));
        Map<String, Integer> valid = new HashMap<>();
        valid.put("picture:first", 20);
        valid.put("picture:middle", 30);
        valid.put("picture:last", 40);
        RoutineEngine.Result result = RoutineEngine.apply(applyFamilies, valid);
        check(result.applied == 2 && result.failures.size() == 1,
                "routine apply must aggregate two writes and one failure");
        check(firstAdapter.value == 20 && lastAdapter.value == 40,
                "routine apply must continue after a middle write failure");
        check(readOnlyAdapter.writeCalls == 0, "read-only routine control must not write");

        Map<String, Integer> missingEntry = new HashMap<>(valid);
        missingEntry.put("picture:missing", 10);
        expectThrows(() -> RoutineEngine.validate(applyFamilies, missingEntry),
                "missing routine control must be rejected");
        Map<String, Integer> readOnlyEntry = new HashMap<>(valid);
        readOnlyEntry.put("picture:read-only", 10);
        expectThrows(() -> RoutineEngine.validate(applyFamilies, readOnlyEntry),
                "read-only routine control must be rejected");
        Map<String, Integer> unknownEntry = new HashMap<>(valid);
        unknownEntry.put("unknown:first", 10);
        expectThrows(() -> RoutineEngine.validate(applyFamilies, unknownEntry),
                "unknown routine family must be rejected");

        RoutineAdapter supportedAdapter = new RoutineAdapter(10, true, null, null);
        RoutineAdapter unsupportedAdapter = new RoutineAdapter(20, true, null, null);
        Map<String, List<Control>> unsupportedFamilies = new java.util.LinkedHashMap<>();
        unsupportedFamilies.put("picture", Arrays.asList(
                new Control("supported", "Supported", supportedAdapter)));
        unsupportedFamilies.put("unsupported", Arrays.asList(
                new Control("valid", "Valid but unsupported", unsupportedAdapter)));
        Map<String, Integer> unsupportedValues = new HashMap<>();
        unsupportedValues.put("picture:supported", 10);
        unsupportedValues.put("unsupported:valid", 20);
        expectThrows(() -> RoutineEngine.apply(unsupportedFamilies, unsupportedValues),
                "supplied unsupported family must be rejected");
        check(supportedAdapter.writeCalls == 0 && unsupportedAdapter.writeCalls == 0,
                "unsupported family rejection must precede every write");

        RoutineAdapter pictureFailure = new RoutineAdapter(1, true, null,
                new Exception("picture"));
        RoutineAdapter audioFailure = new RoutineAdapter(2, true, null,
                new Exception("audio"));
        RoutineAdapter sourceFailure = new RoutineAdapter(3, true, null,
                new Exception("source"));
        Map<String, List<Control>> reversed = new java.util.LinkedHashMap<>();
        reversed.put("source", Arrays.asList(new Control("source", "Source", sourceFailure)));
        reversed.put("audio", Arrays.asList(new Control("audio", "Audio", audioFailure)));
        reversed.put("picture", Arrays.asList(new Control("picture", "Picture", pictureFailure)));
        Map<String, Integer> orderedValues = new HashMap<>();
        orderedValues.put("picture:picture", 1);
        orderedValues.put("audio:audio", 2);
        orderedValues.put("source:source", 3);
        RoutineEngine.Result ordered = RoutineEngine.apply(reversed, orderedValues);
        check(ordered.failures.size() == 3
                        && ordered.failures.get(0).startsWith("Picture: ")
                        && ordered.failures.get(1).startsWith("Audio: ")
                        && ordered.failures.get(2).startsWith("Source: "),
                "routine families must apply in fixed order");

        RoutineAdapter finalReadFailure = new RoutineAdapter(10, true,
                new Exception("final read failed"), null);
        RoutineAdapter selectiveSuccess = new RoutineAdapter(20, true, null, null);
        Map<String, List<Control>> readFailureFamilies = new java.util.LinkedHashMap<>();
        readFailureFamilies.put("picture", Arrays.asList(
                new Control("broken", "Broken read", finalReadFailure),
                new Control("successful", "Successful", selectiveSuccess)));
        Map<String, Integer> readFailureValues = new HashMap<>();
        readFailureValues.put("picture:broken", 11);
        readFailureValues.put("picture:successful", 21);
        RoutineEngine.Result readFailureResult = RoutineEngine.apply(
                readFailureFamilies, readFailureValues);
        check(readFailureResult.applied == 1 && readFailureResult.failures.size() == 1
                        && readFailureResult.failures.get(0).equals(
                                "Broken read: Final readback: final read failed")
                        && finalReadFailure.value == 11 && selectiveSuccess.value == 21,
                "final read failure must decrement only its own successful write");

        class LinkedAdapter implements ControlAdapter {
            private final String title;
            private final java.util.function.IntSupplier getter;
            private final java.util.function.IntConsumer setter;
            private final List<String> order;

            LinkedAdapter(String title, java.util.function.IntSupplier getter,
                    java.util.function.IntConsumer setter, List<String> order) {
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

        int[] linkedMode = {21};
        int[] linkedLamp = {100};
        int[] linkedTemperature = {0};
        List<String> linkedOrder = new java.util.ArrayList<>();
        Map<String, List<Control>> linkedFamilies = new java.util.LinkedHashMap<>();
        linkedFamilies.put("picture", Arrays.asList(
                new Control("display.backlight", "Backlight", new LinkedAdapter("Backlight",
                        () -> linkedLamp[0], value -> {
                            linkedLamp[0] = value;
                            linkedMode[0] = 21;
                        }, linkedOrder)),
                new Control("display.pictureMode", "Picture mode", new LinkedAdapter("Picture mode",
                        () -> linkedMode[0], value -> {
                            linkedMode[0] = value;
                            linkedLamp[0] = 100;
                        }, linkedOrder)),
                new Control("display.colorTemp", "Colour temperature", new LinkedAdapter(
                        "Colour temperature", () -> linkedTemperature[0], value -> {
                            linkedTemperature[0] = value;
                            linkedMode[0] = 21;
                        }, linkedOrder))));
        Map<String, Integer> linkedValues = new HashMap<>();
        linkedValues.put("picture:display.backlight", 100);
        linkedValues.put("picture:display.pictureMode", 5);
        linkedValues.put("picture:display.colorTemp", 0);
        RoutineEngine.Result linked = RoutineEngine.apply(linkedFamilies, linkedValues);
        check(linkedOrder.equals(Arrays.asList("Backlight", "Colour temperature", "Picture mode"))
                        && linkedMode[0] == 5 && linkedLamp[0] == 100
                        && linked.applied == 3 && linked.failures.isEmpty(),
                "picture mode must apply last and preserve a valid captured state");

        linkedOrder.clear();
        linkedValues.put("picture:display.backlight", 99);
        RoutineEngine.Result conflicting = RoutineEngine.apply(linkedFamilies, linkedValues);
        check(conflicting.applied == 2 && conflicting.failures.size() == 1
                        && conflicting.failures.get(0).contains("Backlight")
                        && conflicting.failures.get(0).contains("Final readback"),
                "final batch readback must report cross-control restore conflicts");
    }

    private static void testCapabilityReport() {
        class ScanAdapter implements ControlAdapter {
            private final int value;
            private final boolean writable;
            private final Exception failure;
            private int writeCalls;

            ScanAdapter(int value, boolean writable, Exception failure) {
                this.value = value;
                this.writable = writable;
                this.failure = failure;
            }

            @Override
            public int read() throws Exception {
                if (failure != null) throw failure;
                return value;
            }

            @Override
            public void write(int value) {
                writeCalls++;
            }

            @Override
            public int min() { return 0; }

            @Override
            public int max() { return 100; }

            @Override
            public boolean isWritable() { return writable; }
        }

        ScanAdapter validAdapter = new ScanAdapter(42, true, null);
        check(!validAdapter.accepts(-1), "value below range must be rejected");
        check(validAdapter.accepts(0), "range minimum must be accepted");
        check(validAdapter.accepts(100), "range maximum must be accepted");
        check(!validAdapter.accepts(101), "value above range must be rejected");

        String longError = new String(new char[240]).replace('\0', 'x');
        ScanAdapter outOfRangeAdapter = new ScanAdapter(105, true, null);
        ScanAdapter brokenAdapter = new ScanAdapter(0, true, new Exception(longError));
        ScanAdapter readOnlyAdapter = new ScanAdapter(60, false, null);
        ScanAdapter formatBrokenAdapter = new ScanAdapter(20, true, null) {
            @Override
            public String format(int value) {
                throw new IllegalStateException(longError);
            }
        };
        Control valid = new Control("valid", "Valid", validAdapter);
        Control outOfRange = new Control("out-of-range", "Out of range", outOfRangeAdapter);
        Control broken = new Control("broken", "Broken", brokenAdapter);
        Control readOnly = new Control("read-only", "Read only", readOnlyAdapter);
        Control formatBroken = new Control("format-broken", "Format broken", formatBrokenAdapter);
        Map<String, List<Control>> families = new java.util.LinkedHashMap<>();
        families.put("picture", Arrays.asList(valid, outOfRange, broken, readOnly, formatBroken));

        CapabilityReport report = CapabilityReport.scan(families);
        check(report.entries.size() == 5, "scan must include every control");
        check("valid".equals(report.entries.get(0).id)
                        && "out-of-range".equals(report.entries.get(1).id)
                        && "broken".equals(report.entries.get(2).id)
                        && "read-only".equals(report.entries.get(3).id)
                        && "format-broken".equals(report.entries.get(4).id),
                "scan must preserve control order");
        check("picture".equals(report.entries.get(0).family)
                        && "Valid".equals(report.entries.get(0).title),
                "scan must expose family and title");
        check(report.entries.get(0).readable && "42".equals(report.entries.get(0).value)
                        && report.entries.get(0).error == null,
                "valid value must be readable");
        check(report.entries.get(1).readable && "105".equals(report.entries.get(1).value),
                "scan must report actual out-of-range vendor value");
        check(!report.entries.get(2).readable && report.entries.get(2).value == null
                        && report.entries.get(2).error.length() == 160,
                "read errors must be bounded to 160 UTF-16 code units");
        check(report.entries.get(3).readable && !report.entries.get(3).writable,
                "read-only controls must remain readable and non-writable");
        check(!report.entries.get(4).readable && report.entries.get(4).error.length() == 160,
                "format errors must be isolated and bounded");
        check(validAdapter.writeCalls == 0 && outOfRangeAdapter.writeCalls == 0
                        && brokenAdapter.writeCalls == 0 && readOnlyAdapter.writeCalls == 0
                        && formatBrokenAdapter.writeCalls == 0,
                "capability scan must not write");
        boolean immutable = false;
        try {
            report.entries.add(report.entries.get(0));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "capability entries must be immutable");
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

    private static void testBreakoutGame() throws Exception {
        BreakoutGame game = new BreakoutGame();
        check(game.phase() == BreakoutGame.Phase.READY, "round starts ready");
        check(game.score() == 0 && game.lives() == 3 && game.bricksRemaining() == 60,
                "round initial state");
        check(game.isBrickAlive(0) && game.isBrickAlive(59), "all bricks initially alive");
        game.setDirection(-1);
        advance(game, 10);
        check(game.paddleX() == 80f && game.ballX() == 80f,
                "paddle and attached ball clamp at left edge");
        game.setDirection(1);
        advance(game, 20);
        check(game.paddleX() == 920f, "paddle clamps at right edge");
        game.setDirection(0);
        game.centre();
        check(game.phase() == BreakoutGame.Phase.RUNNING, "centre launches");
        game.centre();
        check(game.phase() == BreakoutGame.Phase.PAUSED, "centre pauses");
        float pausedY = game.ballY();
        advance(game, 1);
        check(game.ballY() == pausedY, "paused ball does not move");
        game.centre();
        check(game.phase() == BreakoutGame.Phase.RUNNING, "centre resumes");

        game = new BreakoutGame();
        arrangeBall(game, 5f, 300f, -480f, 0f);
        game.update(1f / 120);
        check(game.ballVelocityX() > 0, "side wall reflects horizontal velocity");
        arrangeBall(game, 500f, 5f, 0f, -480f);
        game.update(1f / 120);
        check(game.ballVelocityY() > 0, "top wall reflects vertical velocity");

        game = new BreakoutGame();
        arrangeBall(game, 540f, 540f, 0f, 480f);
        game.update(1f / 120);
        check(game.ballVelocityX() > 0 && game.ballVelocityY() < 0,
                "off-centre paddle hit aims the rebound");

        game = new BreakoutGame();
        arrangeBall(game, 50f, 110f, 0f, -480f);
        game.update(1f / 120);
        check(game.score() == 10 && game.bricksRemaining() == 59,
                "one brick scores exactly ten");
        check(!game.isBrickAlive(0), "hit brick is removed");
        check(game.ballVelocityY() > 0, "brick reflects the ball");
        arrangeBall(game, 50f, 110f, 0f, -480f);
        game.update(1f / 120);
        check(game.score() == 10 && game.bricksRemaining() == 59 && !game.isBrickAlive(0),
                "colliding with a removed brick cannot score or remove it again");

        game = new BreakoutGame();
        boolean[] lastBrick = new boolean[60];
        lastBrick[0] = true;
        setGameField(game, "bricks", lastBrick);
        setGameField(game, "score", 590);
        arrangeBall(game, 50f, 110f, 0f, -480f);
        game.update(1f / 120);
        check(game.phase() == BreakoutGame.Phase.WON && game.score() == 600,
                "last brick wins the round");
        game.centre();
        check(game.phase() == BreakoutGame.Phase.READY && game.score() == 0
                && game.lives() == 3 && game.bricksRemaining() == 60,
                "centre resets a won round");

        game = new BreakoutGame();
        for (int lives = 2; lives >= 0; lives--) {
            arrangeBall(game, 500f, 605f, 0f, 480f);
            game.update(1f / 120);
            check(game.lives() == lives, "bottom loss decrements one life");
            if (lives > 0) {
                check(game.phase() == BreakoutGame.Phase.READY,
                        "remaining lives return to READY");
                check(game.ballX() == game.paddleX() && game.ballY() == 540f,
                        "remaining lives reattach the ball to the paddle");
                check(game.ballVelocityX() == 0f && game.ballVelocityY() == -480f,
                        "remaining lives reset launch velocity");
                game.centre();
                check(game.phase() == BreakoutGame.Phase.RUNNING,
                        "centre relaunches after life loss");
            }
        }
        check(game.phase() == BreakoutGame.Phase.GAME_OVER,
                "zero lives ends the round");
        game.centre();
        check(game.phase() == BreakoutGame.Phase.READY && game.score() == 0
                && game.lives() == 3 && game.bricksRemaining() == 60,
                "centre resets a lost round");
    }

    private static void advance(BreakoutGame game, float seconds) {
        int steps = Math.round(seconds * 120);
        for (int i = 0; i < steps; i++) game.update(1f / 120);
    }

    private static void arrangeBall(BreakoutGame game, float x, float y,
            float velocityX, float velocityY) throws Exception {
        setGameField(game, "phase", BreakoutGame.Phase.RUNNING);
        setGameField(game, "ballX", x);
        setGameField(game, "ballY", y);
        setGameField(game, "ballVelocityX", velocityX);
        setGameField(game, "ballVelocityY", velocityY);
    }

    private static void setGameField(BreakoutGame game, String name, Object value)
            throws Exception {
        java.lang.reflect.Field field = BreakoutGame.class.getDeclaredField(name);
        field.setAccessible(true);
        if (value instanceof Integer) {
            field.setInt(game, (Integer) value);
        } else if (value instanceof Float) {
            field.setFloat(game, (Float) value);
        } else {
            field.set(game, value);
        }
    }

    private static void testBestScore() {
        BestScore best = new BestScore(40);
        check(best.get() == 40, "stored best initializes the tracker");
        check(!best.update(40) && !best.update(30),
                "equal and lower scores do not notify");
        check(best.update(50) && best.get() == 50,
                "higher score updates and notifies once");
        check(!best.update(50), "repeated score does not notify again");
        check(!best.update(0) && best.get() == 50,
                "new-round zero score preserves the best");
    }

    private static void testSleepSchedule() throws Exception {
        FakeSleepScheduler scheduler = new FakeSleepScheduler();
        scheduler.existing = true;
        scheduler.fail = true;
        expectThrows(() -> SleepSchedule.set(15, scheduler),
                "replacement scheduling failure must propagate");
        check(scheduler.existing && scheduler.cancelCalls == 0,
                "failed replacement must preserve the previous alarm");
        scheduler.fail = false;
        SleepSchedule.set(30, scheduler);
        check(scheduler.scheduledMinutes == 30 && scheduler.cancelCalls == 0,
                "positive durations replace without cancelling first");
        SleepSchedule.set(0, scheduler);
        check(!scheduler.existing && scheduler.cancelCalls == 1,
                "disabling explicitly cancels the alarm");
    }

    private static final class FakeSleepScheduler implements SleepSchedule.Scheduler {
        boolean existing;
        boolean fail;
        int scheduledMinutes = -1;
        int cancelCalls;

        @Override
        public void schedule(int minutes) {
            if (fail) throw new SecurityException("Exact alarms denied");
            scheduledMinutes = minutes;
            existing = true;
        }

        @Override
        public void cancel() {
            cancelCalls++;
            existing = false;
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
