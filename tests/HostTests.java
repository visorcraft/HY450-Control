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
            public boolean isAvailable() { return true; }

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

        System.out.println("Host tests passed");
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
