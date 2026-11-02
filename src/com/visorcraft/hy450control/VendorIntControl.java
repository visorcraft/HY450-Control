package com.visorcraft.hy450control;

public final class VendorIntControl extends VendorControl {
    private final Object parameter;
    private final int min;
    private final int max;

    public VendorIntControl(Object manager, String getter, String setter,
            Object parameter, int min, int max) {
        super(manager, getter, setter);
        this.parameter = parameter;
        this.min = min;
        this.max = max;
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

    protected void rawWrite(int value) throws Exception {
        if (parameter == null) {
            Vendor.call(manager, setter, new Class<?>[]{int.class}, value);
        } else {
            Vendor.call(manager, setter, new Class<?>[]{parameterType(), int.class},
                    parameter, value);
        }
    }

    private Class<?> parameterType() {
        return parameter instanceof Integer ? int.class : parameter.getClass();
    }
}
