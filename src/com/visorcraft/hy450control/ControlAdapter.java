package com.visorcraft.hy450control;

public interface ControlAdapter {
    boolean isAvailable();
    int read() throws Exception;
    void write(int value) throws Exception;
    int min();
    int max();
}
