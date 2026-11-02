package com.visorcraft.hy450control;

import java.util.HashMap;
import java.util.Map;

public final class HostTests {
    private static final String[] INSET_KEYS = {
        "persist.display.keystone_ltx", "persist.display.keystone_lty",
        "persist.display.keystone_rtx", "persist.display.keystone_rty",
        "persist.display.keystone_lbx", "persist.display.keystone_lby",
        "persist.display.keystone_rbx", "persist.display.keystone_rby"
    };

    public static void main(String[] args) throws Exception {
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
