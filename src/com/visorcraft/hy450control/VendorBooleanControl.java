package com.visorcraft.hy450control;

public final class VendorBooleanControl implements ControlAdapter {
    private final Object manager;
    private final String getter;
    private final String setter;

    public VendorBooleanControl(Object manager, String getter, String setter) {
        this.manager = manager;
        this.getter = getter;
        this.setter = setter;
    }

    public boolean isAvailable() {
        try {
            read();
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    public int min() { return 0; }
    public int max() { return 1; }

    public int read() throws Exception {
        return ((Boolean) Vendor.call(manager, getter, new Class<?>[0])) ? 1 : 0;
    }

    public void write(int value) throws Exception {
        if (value < 0 || value > 1) throw new IllegalArgumentException("Out of range");
        int original = read();
        Vendor.call(manager, setter, new Class<?>[]{boolean.class}, value == 1);
        if (read() != value) {
            Vendor.call(manager, setter, new Class<?>[]{boolean.class}, original == 1);
            throw new IllegalStateException("Readback mismatch");
        }
    }
}
