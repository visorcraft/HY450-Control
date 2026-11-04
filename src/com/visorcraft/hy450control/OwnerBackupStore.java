package com.visorcraft.hy450control;

import android.content.Context;
import java.io.File;
import java.util.Iterator;
import org.json.JSONObject;

public final class OwnerBackupStore {
    private final File directory;

    public OwnerBackupStore(Context context) {
        this(new File(context.getFilesDir(), "owner-backup"));
    }

    public OwnerBackupStore(File directory) {
        this.directory = directory;
    }

    public synchronized void saveSafety(JSONObject root) throws Exception {
        validate(root);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("No owner backup directory");
        }
        JsonFileStore.write(new File(directory, "pre-restore-backup.json"),
                new File(directory, "pre-restore-backup.tmp"), root.toString(2));
    }

    public synchronized JSONObject readSafety() throws Exception {
        File source = new File(directory, "pre-restore-backup.json");
        if (!source.exists()) return null;
        JSONObject root = new JSONObject(JsonFileStore.read(source));
        validate(root);
        return root;
    }

    private static void validate(JSONObject root) throws Exception {
        PortableBackup.preview(root, root.getString("firmware"));
        JSONObject families = root.getJSONObject("families");
        Iterator<String> names = families.keys();
        while (names.hasNext()) {
            String family = names.next();
            if (!"picture".equals(family) && !"audio".equals(family)
                    && !"source".equals(family)) {
                throw new IllegalStateException("Unknown backup family: " + family);
            }
            JSONObject controls = families.getJSONObject(family);
            Iterator<String> ids = controls.keys();
            while (ids.hasNext()) {
                String id = ids.next();
                if (!(controls.get(id) instanceof Integer)) {
                    throw new IllegalStateException("Invalid backup value: " + family + ":" + id);
                }
            }
        }
        ProjectionBackup.validateValues(root.getJSONObject("projection"));
    }
}
