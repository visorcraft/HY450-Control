package com.visorcraft.hy450control;

import android.content.Context;
import java.io.File;
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
        JsonFileStore.write(target, temporary, root.toString(2));
    }

    public synchronized JSONObject read(String name) throws Exception {
        File source = new File(directory, safeName(name) + ".json");
        JSONObject root = new JSONObject(JsonFileStore.read(source));
        if (root.getInt("schema") != 1) throw new JSONException("Unsupported schema");
        return root;
    }

    private static String safeName(String value) {
        if (!value.matches("[a-z0-9._-]+")) throw new IllegalArgumentException("Invalid preset name");
        return value;
    }
}
