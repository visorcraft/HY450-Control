package com.visorcraft.hy450control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class CapabilityReport {
    public static final class Entry {
        public final String family;
        public final String id;
        public final String title;
        public final boolean writable;
        public final boolean readable;
        public final String value;
        public final String error;

        private Entry(String family, Control control, boolean readable, String value, String error) {
            this.family = family;
            this.id = control.id;
            this.title = control.title;
            this.writable = control.adapter.isWritable();
            this.readable = readable;
            this.value = value;
            this.error = error;
        }
    }

    public final List<Entry> entries;

    private CapabilityReport(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static CapabilityReport scan(Map<String, List<Control>> families) {
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, List<Control>> family : families.entrySet()) {
            for (Control control : family.getValue()) {
                boolean readable = false;
                String value = null;
                String error = null;
                try {
                    value = control.adapter.format(control.adapter.read());
                    readable = true;
                } catch (Exception failure) {
                    error = Errors.describe(failure);
                    if (error.length() > 160) error = error.substring(0, 160);
                }
                entries.add(new Entry(family.getKey(), control, readable, value, error));
            }
        }
        return new CapabilityReport(entries);
    }

    public JSONObject toDiagnosticJson(String appVersion, String fingerprint, int sdk,
            String createdUtc) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("type", "hy450-control-diagnostic");
        root.put("schema", 1);
        root.put("appVersion", appVersion);
        root.put("fingerprint", fingerprint);
        root.put("sdk", sdk);
        root.put("createdUtc", createdUtc);
        JSONArray controls = new JSONArray();
        for (Entry entry : entries) {
            JSONObject control = new JSONObject();
            control.put("family", entry.family);
            control.put("id", entry.id);
            control.put("title", entry.title);
            control.put("writable", entry.writable);
            control.put("readable", entry.readable);
            control.put("value", entry.value == null ? JSONObject.NULL : entry.value);
            control.put("error", entry.error == null ? JSONObject.NULL : entry.error);
            controls.put(control);
        }
        root.put("controls", controls);
        return root;
    }
}
