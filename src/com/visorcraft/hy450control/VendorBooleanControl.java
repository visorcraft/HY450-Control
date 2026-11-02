package com.visorcraft.hy450control;

public final class VendorBooleanControl extends VendorControl {
    public VendorBooleanControl(Object manager, String getter, String setter) {
        super(manager, getter, setter);
    }

    public int min() { return 0; }
    public int max() { return 1; }

    @Override
    public String format(int value) {
        if (value == 0) return "Off";
        if (value == 1) return "On";
        throw new IllegalArgumentException("Invalid boolean value");
    }

    public int read() throws Exception {
        return ((Boolean) Vendor.call(manager, getter, new Class<?>[0])) ? 1 : 0;
    }

    protected void rawWrite(int value) throws Exception {
        Vendor.call(manager, setter, new Class<?>[]{boolean.class}, value == 1);
    }
}
