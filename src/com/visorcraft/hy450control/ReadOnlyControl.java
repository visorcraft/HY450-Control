package com.visorcraft.hy450control;

public final class ReadOnlyControl implements ControlAdapter {
    private final Object manager;
    private final String getter;

    public ReadOnlyControl(Object manager, String getter) {
        this.manager = manager;
        this.getter = getter;
    }

    public int min() { return 0; }
    public int max() { return 0; }

    @Override
    public boolean isWritable() { return false; }

    public int read() throws Exception {
        return ((Number) Vendor.call(manager, getter, new Class<?>[0])).intValue();
    }

    public void write(int value) {
        throw new UnsupportedOperationException("Read only");
    }
}
