package com.visorcraft.hy450control;

import android.content.Context;
import java.io.File;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public final class BackupStore {
    private final File directory;

    public BackupStore(Context context) {
        this(new File(context.getFilesDir(), "backups"));
    }

    BackupStore(File directory) {
        this.directory = directory;
    }

    public synchronized boolean hasSnapshot(String family) {
        return new File(directory, safeName(family) + ".json").isFile();
    }

    public synchronized void snapshot(String family, Map<String, Integer> values)
            throws Exception {
        File target = new File(directory, safeName(family) + ".json");
        if (target.isFile()) throw new IllegalStateException("Original snapshot already exists");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("No backup directory");
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("family", family);
        root.put("firmware", android.os.Build.FINGERPRINT);
        JSONObject valuesJson = new JSONObject();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            valuesJson.put(entry.getKey(), entry.getValue());
        }
        root.put("values", valuesJson);
        File temporary = new File(directory, safeName(family) + ".tmp");
        JsonFileStore.write(target, temporary, root.toString(2));
    }

    public synchronized JSONObject readSnapshot(String family) throws Exception {
        File source = new File(directory, safeName(family) + ".json");
        JSONObject root = new JSONObject(JsonFileStore.read(source));
        if (root.getInt("schema") != 1) throw new JSONException("Unsupported schema");
        if (!android.os.Build.FINGERPRINT.equals(root.getString("firmware"))) {
            throw new JSONException("Firmware mismatch");
        }
        return root;
    }

    private static String safeName(String value) {
        if (!value.matches("[a-z0-9._-]+")) throw new IllegalArgumentException("Invalid family");
        return value;
    }
}
