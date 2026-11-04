package com.visorcraft.hy450control;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class RoutineStore {
    private static final String[] SLOTS = {"movie", "day", "night", "custom"};
    private final File directory;

    public RoutineStore(Context context) {
        this(new File(context.getFilesDir(), "routines"));
    }

    public RoutineStore(File directory) {
        this.directory = directory;
    }

    public synchronized void saveSlot(String slot, RoutineEngine.Capture capture) throws Exception {
        checkSlot(slot);
        File target = new File(directory, "routines.json");
        JSONObject root = target.exists() ? readSlots(target) : newSlots();
        root.getJSONObject("slots").put(slot, captureJson(capture));
        write(target, new File(directory, "routines.tmp"), root);
    }

    public synchronized RoutineEngine.Capture readSlot(String slot) throws Exception {
        checkSlot(slot);
        File source = new File(directory, "routines.json");
        if (!source.exists()) return null;
        JSONObject slots = readSlots(source).getJSONObject("slots");
        return slots.has(slot) ? readCapture(slots.getJSONObject(slot)) : null;
    }

    public synchronized void saveUndo(RoutineEngine.Capture capture) throws Exception {
        JSONObject root = captureJson(capture);
        root.put("schema", 1);
        write(new File(directory, "routine-undo.json"),
                new File(directory, "routine-undo.tmp"), root);
    }

    public synchronized RoutineEngine.Capture readUndo() throws Exception {
        File source = new File(directory, "routine-undo.json");
        if (!source.exists()) return null;
        JSONObject root = new JSONObject(JsonFileStore.read(source));
        checkSchema(root);
        requireKeys(root, "schema", "values", "warnings");
        return readCapture(root);
    }

    private void write(File target, File temporary, JSONObject root) throws Exception {
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("No routine directory");
        }
        JsonFileStore.write(target, temporary, root.toString(2));
    }

    private static JSONObject newSlots() throws JSONException {
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("slots", new JSONObject());
        return root;
    }

    private static JSONObject readSlots(File source) throws Exception {
        JSONObject root = new JSONObject(JsonFileStore.read(source));
        checkSchema(root);
        requireKeys(root, "schema", "slots");
        JSONObject slots = root.getJSONObject("slots");
        Iterator<String> names = slots.keys();
        while (names.hasNext()) {
            String name = names.next();
            checkSlot(name);
            JSONObject capture = slots.getJSONObject(name);
            requireKeys(capture, "values", "warnings");
            readCapture(capture);
        }
        return root;
    }

    private static JSONObject captureJson(RoutineEngine.Capture capture) throws JSONException {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, Integer> entry : capture.values.entrySet()) {
            if (entry.getValue() == null) throw new JSONException("Null routine value");
            values.put(entry.getKey(), entry.getValue());
        }
        JSONArray warnings = new JSONArray();
        for (String warning : capture.warnings) {
            if (warning == null) throw new JSONException("Null routine warning");
            warnings.put(warning);
        }
        JSONObject root = new JSONObject();
        root.put("values", values);
        root.put("warnings", warnings);
        return root;
    }

    private static RoutineEngine.Capture readCapture(JSONObject root) throws JSONException {
        JSONObject valuesJson = root.getJSONObject("values");
        Map<String, Integer> values = new LinkedHashMap<>();
        Iterator<String> keys = valuesJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = valuesJson.get(key);
            if (!(value instanceof Integer)) throw new JSONException("Invalid routine value");
            values.put(key, (Integer) value);
        }
        JSONArray warningsJson = root.getJSONArray("warnings");
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < warningsJson.length(); i++) {
            Object warning = warningsJson.get(i);
            if (!(warning instanceof String)) throw new JSONException("Invalid routine warning");
            warnings.add((String) warning);
        }
        return new RoutineEngine.Capture(values, warnings);
    }

    private static void checkSchema(JSONObject root) throws JSONException {
        Object schema = root.get("schema");
        if (!(schema instanceof Integer) || (Integer) schema != 1) {
            throw new JSONException("Unsupported routine schema");
        }
    }

    private static void requireKeys(JSONObject object, String... expected) throws JSONException {
        if (object.length() != expected.length) throw new JSONException("Invalid routine keys");
        for (String key : expected) {
            if (!object.has(key)) throw new JSONException("Missing routine key: " + key);
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            boolean known = false;
            for (String candidate : expected) {
                if (candidate.equals(key)) known = true;
            }
            if (!known) throw new JSONException("Unknown routine key: " + key);
        }
    }

    private static void checkSlot(String slot) {
        for (String candidate : SLOTS) {
            if (candidate.equals(slot)) return;
        }
        throw new IllegalArgumentException("Unknown routine slot: " + slot);
    }
}
