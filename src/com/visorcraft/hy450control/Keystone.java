package com.visorcraft.hy450control;

import java.lang.reflect.Method;

/** Live keystone insets. Order is ltx, lty, rtx, rty, lbx, lby, rbx, rby. */
final class Keystone {
    private static final String[] DISPLAY = {
        "persist.display.keystone_ltx", "persist.display.keystone_lty",
        "persist.display.keystone_rtx", "persist.display.keystone_rty",
        "persist.display.keystone_lbx", "persist.display.keystone_lby",
        "persist.display.keystone_rbx", "persist.display.keystone_rby"
    };
    private static final String[] HTC = {
        "persist.htc.keystone.ltx", "persist.htc.keystone.lty",
        "persist.htc.keystone.rtx", "persist.htc.keystone.rty",
        "persist.htc.keystone.lbx", "persist.htc.keystone.lby",
        "persist.htc.keystone.rbx", "persist.htc.keystone.rby"
    };
    private static final boolean[] HORIZONTAL = {
        true, false, true, false, true, false, true, false
    };

    static final class Snapshot {
        final int[] inset = new int[8];
        final float zoomH;
        final float zoomV;

        Snapshot(int[] inset, float zoomH, float zoomV) {
            System.arraycopy(inset, 0, this.inset, 0, 8);
            this.zoomH = zoomH;
            this.zoomV = zoomV;
        }
    }

    private Keystone() {}

    static Snapshot read() throws Exception {
        int[] inset = new int[8];
        for (int i = 0; i < DISPLAY.length; i++) inset[i] = parse(property(DISPLAY[i]), 0);
        float zoomH = parseFloat(property("persist.display.keystone_zoom_h"), 100f);
        float zoomV = parseFloat(property("persist.display.keystone_zoom_v"), 100f);
        return new Snapshot(inset, zoomH, zoomV);
    }

    static void apply(Snapshot snapshot) throws Exception {
        apply(snapshot.inset, snapshot.zoomH, snapshot.zoomV);
    }

    static void apply(int[] inset) throws Exception {
        apply(inset, 100f, 100f);
    }

    private static void apply(int[] inset, float zoomH, float zoomV) throws Exception {
        Object display = Vendor.display();
        Method setter = display.getClass().getMethod("setKeystoreValue",
                float.class, float.class,
                int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, int.class);
        // Bottom corners, then top corners. Raising lbx moves the bottom-left corner right.
        setter.invoke(display, zoomH, zoomV,
                inset[4], inset[5], inset[6], inset[7],
                inset[0], inset[1], inset[2], inset[3]);
        int[] screen = screenSize();
        for (int i = 0; i < HTC.length; i++) {
            int nominal = HORIZONTAL[i] ? 1920 : 1080;
            int axis = HORIZONTAL[i] ? screen[0] : screen[1];
            int scaled = inset[i] * axis / nominal;
            setProperty(HTC[i], Integer.toString(Math.max(0, scaled)));
        }
    }

    private static int[] screenSize() throws Exception {
        String raw = property("persist.vendor.disp.screensize");
        int split = raw.indexOf('x');
        if (split <= 0) return new int[] {1920, 1080};
        return new int[] {
            parse(raw.substring(0, split), 1920),
            parse(raw.substring(split + 1), 1080)
        };
    }

    private static String property(String key) throws Exception {
        String value = (String) Class.forName("android.os.SystemProperties")
                .getMethod("get", String.class, String.class).invoke(null, key, "");
        return value == null ? "" : value;
    }

    private static void setProperty(String key, String value) throws Exception {
        Class.forName("android.os.SystemProperties")
                .getMethod("set", String.class, String.class).invoke(null, key, value);
    }

    private static int parse(String raw, int fallback) {
        if (raw == null || raw.length() == 0) return fallback;
        int dot = raw.indexOf('.');
        if (dot >= 0) raw = raw.substring(0, dot);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        if (raw == null || raw.length() == 0) return fallback;
        try {
            return Float.parseFloat(raw.trim());
        } catch (NumberFormatException error) {
            return fallback;
        }
    }
}
