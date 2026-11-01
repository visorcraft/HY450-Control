package com.visorcraft.hy450control;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public final class PresetStore {
    private final File directory;

    public PresetStore(Context context) {
        directory = new File(context.getFilesDir(), "presets");
    }

    public synchronized void save(String name, Map<String, Integer> values) throws Exception {
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("No preset directory");
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("name", name);
        JSONObject valuesJson = new JSONObject();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            valuesJson.put(entry.getKey(), entry.getValue());
        }
        root.put("values", valuesJson);
        File target = new File(directory, safeName(name) + ".json");
        File temporary = new File(directory, safeName(name) + ".tmp");
        FileOutputStream output = new FileOutputStream(temporary);
        try {
            output.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } finally {
            output.close();
        }
        if (!temporary.renameTo(target)) throw new IllegalStateException("Preset rename failed");
    }

    public synchronized JSONObject read(String name) throws Exception {
        File source = new File(directory, safeName(name) + ".json");
        byte[] data = new byte[(int) source.length()];
        FileInputStream input = new FileInputStream(source);
        try {
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) throw new IllegalStateException("Truncated preset");
                offset += count;
            }
        } finally {
            input.close();
        }
        JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
        if (root.getInt("schema") != 1) throw new JSONException("Unsupported schema");
        return root;
    }

    private static String safeName(String value) {
        if (!value.matches("[a-z0-9._-]+")) throw new IllegalArgumentException("Invalid preset name");
        return value;
    }
}
