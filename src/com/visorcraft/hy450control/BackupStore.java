package com.visorcraft.hy450control;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

public final class BackupStore {
    private final File directory;

    public BackupStore(Context context) {
        directory = new File(context.getFilesDir(), "backups");
    }

    public synchronized boolean hasSnapshot(String family) {
        return new File(directory, safeName(family) + ".json").isFile();
    }

    public synchronized void snapshot(String family, Map<String, Integer> values)
            throws Exception {
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
        File target = new File(directory, safeName(family) + ".json");
        File temporary = new File(directory, safeName(family) + ".tmp");
        write(temporary, root.toString(2));
        if (!temporary.renameTo(target)) throw new IllegalStateException("Backup rename failed");
    }

    public synchronized JSONObject readSnapshot(String family) throws Exception {
        File source = new File(directory, safeName(family) + ".json");
        byte[] data = new byte[(int) source.length()];
        FileInputStream input = new FileInputStream(source);
        try {
            int offset = 0;
            while (offset < data.length) {
                int count = input.read(data, offset, data.length - offset);
                if (count < 0) throw new IllegalStateException("Truncated backup");
                offset += count;
            }
        } finally {
            input.close();
        }
        JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
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

    private static void write(File file, String value) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } finally {
            output.close();
        }
    }
}
