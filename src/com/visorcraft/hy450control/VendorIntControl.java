package com.visorcraft.hy450control;

public final class VendorIntControl implements ControlAdapter {
    private final Object manager;
    private final String getter;
    private final String setter;
    private final Object parameter;
    private final int min;
    private final int max;

    public VendorIntControl(Object manager, String getter, String setter,
            Object parameter, int min, int max) {
        this.manager = manager;
        this.getter = getter;
        this.setter = setter;
        this.parameter = parameter;
        this.min = min;
        this.max = max;
    }

    public boolean isAvailable() {
        try {
            read();
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    public int min() { return min; }
    public int max() { return max; }

    public int read() throws Exception {
        if (parameter == null) {
            return ((Number) Vendor.call(manager, getter, new Class<?>[0])).intValue();
        }
        return ((Number) Vendor.call(manager, getter,
                new Class<?>[]{parameterType()}, parameter)).intValue();
    }

    public void write(int value) throws Exception {
        if (value < min || value > max) throw new IllegalArgumentException("Out of range");
        int original = read();
        if (parameter == null) {
            Vendor.call(manager, setter, new Class<?>[]{int.class}, value);
        } else {
            Vendor.call(manager, setter, new Class<?>[]{parameterType(), int.class},
                    parameter, value);
        }
        if (read() != value) {
            if (parameter == null) {
                Vendor.call(manager, setter, new Class<?>[]{int.class}, original);
            } else {
                Vendor.call(manager, setter, new Class<?>[]{parameterType(), int.class},
                        parameter, original);
            }
            throw new IllegalStateException("Readback mismatch");
        }
    }

    private Class<?> parameterType() {
        return parameter instanceof Integer ? int.class : parameter.getClass();
    }
}
