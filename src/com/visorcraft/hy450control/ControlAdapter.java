package com.visorcraft.hy450control;

public interface ControlAdapter {
    int read() throws Exception;
    void write(int value) throws Exception;
    int min();
    int max();

    default boolean isWritable() { return true; }

    default boolean accepts(int value) {
        return value >= min() && value <= max();
    }

    default String format(int value) { return String.valueOf(value); }

    default int clamp(int value) {
        return Math.max(min(), Math.min(max(), value));
    }
}
