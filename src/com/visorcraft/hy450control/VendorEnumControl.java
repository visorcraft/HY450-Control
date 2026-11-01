package com.visorcraft.hy450control;

public final class VendorEnumControl implements ControlAdapter {
    private final Object manager;
    private final String getter;
    private final String setter;
    private final Class<?> enumType;
    private final Object[] values;

    public VendorEnumControl(Object manager, String getter, String setter,
            Class<?> enumType, Object[] values) {
        this.manager = manager;
        this.getter = getter;
        this.setter = setter;
        this.enumType = enumType;
        this.values = values;
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
    public int max() { return values.length - 1; }

    @Override
    public String format(int value) {
        if (value < min() || value > max()) throw new IllegalArgumentException("Out of range");
        String name = ((Enum<?>) values[value]).name();
        if (name.startsWith("E_AW_")) name = name.substring(5);
        String[] words = name.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (label.length() > 0) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) label.append(word.substring(1));
        }
        return label.toString();
    }

    public int read() throws Exception {
        Object current = Vendor.call(manager, getter, new Class<?>[0]);
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) return i;
        }
        throw new IllegalStateException("Unknown enum value: " + current);
    }

    public void write(int value) throws Exception {
        if (value < min() || value > max()) throw new IllegalArgumentException("Out of range");
        int original = read();
        Vendor.call(manager, setter, new Class<?>[]{enumType}, values[value]);
        if (read() != value) {
            Vendor.call(manager, setter, new Class<?>[]{enumType}, values[original]);
            throw new IllegalStateException("Readback mismatch");
        }
    }
}
