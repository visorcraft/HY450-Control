package com.visorcraft.hy450control;

public final class Control {
    public final String id;
    public final String title;
    public final ControlAdapter adapter;

    public Control(String id, String title, ControlAdapter adapter) {
        this.id = id;
        this.title = title;
        this.adapter = adapter;
    }
}
