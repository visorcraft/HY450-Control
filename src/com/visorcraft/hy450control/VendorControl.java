package com.visorcraft.hy450control;

abstract class VendorControl implements ControlAdapter {
    protected final Object manager;
    protected final String getter;
    protected final String setter;

    VendorControl(Object manager, String getter, String setter) {
        this.manager = manager;
        this.getter = getter;
        this.setter = setter;
    }

    public final void write(int value) throws Exception {
        if (value < min() || value > max()) throw new IllegalArgumentException("Out of range");
        int original = read();
        rawWrite(value);
        if (read() != value) {
            rawWrite(original);
            throw new IllegalStateException("Readback mismatch");
        }
    }

    protected abstract void rawWrite(int value) throws Exception;
}
