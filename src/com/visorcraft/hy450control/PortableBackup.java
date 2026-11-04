package com.visorcraft.hy450control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class PortableBackup {
    private static final String TYPE = "hy450-control-backup";
    private static final String[] ROOT_KEYS = {
        "type", "schema", "createdUtc", "appVersion", "firmware",
        "families", "warnings", "projection"
    };
    private static final String[] FAMILY_ORDER = {"picture", "audio", "source"};

    public static final class Preview {
        public final String summary;
        public final boolean firmwareMatches;
        public final List<String> warnings;

        private Preview(String summary, boolean firmwareMatches, List<String> warnings) {
            this.summary = summary;
            this.firmwareMatches = firmwareMatches;
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }
    }

    public static final class Validated {
        public final Map<String, Integer> values;
        public final JSONObject projection;
        public final List<String> warnings;
        public final String createdUtc;
        public final String appVersion;

        private Validated(Map<String, Integer> values, JSONObject projection,
                List<String> warnings, String createdUtc, String appVersion) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.projection = projection;
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
            this.createdUtc = createdUtc;
            this.appVersion = appVersion;
        }
    }

    private PortableBackup() {}

    public static JSONObject capture(Map<String, List<Control>> families,
            ProjectionBackup.ValueAccess access, String appVersion, String fingerprint,
            String createdUtc) throws Exception {
        RoutineEngine.Capture capture = RoutineEngine.capture(families);
        JSONObject storedFamilies = new JSONObject();
        for (String family : FAMILY_ORDER) {
            if (families.containsKey(family)) storedFamilies.put(family, new JSONObject());
        }
        for (Map.Entry<String, Integer> entry : capture.values.entrySet()) {
            String storedKey = entry.getKey();
            int separator = storedKey.indexOf(':');
            String family = storedKey.substring(0, separator);
            String id = storedKey.substring(separator + 1);
            storedFamilies.getJSONObject(family).put(id, entry.getValue());
        }
        JSONArray warnings = new JSONArray();
        for (String warning : capture.warnings) warnings.put(warning);

        JSONObject root = new JSONObject();
        root.put("type", TYPE);
        root.put("schema", 1);
        root.put("createdUtc", createdUtc);
        root.put("appVersion", appVersion);
        root.put("firmware", fingerprint);
        root.put("families", storedFamilies);
        root.put("warnings", warnings);
        root.put("projection", ProjectionBackup.captureValues(access));
        return root;
    }

    public static Preview preview(JSONObject root, String fingerprint) throws Exception {
        requireKeys(root, ROOT_KEYS);
        String type = string(root, "type");
        if (!TYPE.equals(type)) throw new IllegalStateException("Unsupported backup type");
        Object schema = root.get("schema");
        if (!(schema instanceof Integer) || (Integer) schema != 1) {
            throw new IllegalStateException("Unsupported backup schema");
        }
        String createdUtc = string(root, "createdUtc");
        String appVersion = string(root, "appVersion");
        String storedFirmware = string(root, "firmware");
        root.getJSONObject("families");
        root.getJSONObject("projection");
        List<String> warnings = readWarnings(root);
        boolean firmwareMatches = storedFirmware.equals(fingerprint);
        String summary = type + " schema " + schema
                + "\nCreated UTC: " + createdUtc
                + "\nApp version: " + appVersion
                + "\nFirmware: " + (firmwareMatches ? "matches" : "mismatch");
        return new Preview(summary, firmwareMatches, warnings);
    }

    public static Preview preview(JSONObject root, Map<String, List<Control>> families,
            String fingerprint) throws Exception {
        Preview base = preview(root, fingerprint);
        JSONObject storedFamilies = root.getJSONObject("families");
        List<String> warnings = new ArrayList<>(base.warnings);
        StringBuilder summary = new StringBuilder(base.summary);
        for (String family : FAMILY_ORDER) {
            List<Control> controls = families.get(family);
            if (controls == null) continue;
            JSONObject stored = storedFamilies.has(family)
                    ? storedFamilies.getJSONObject(family) : null;
            int included = 0;
            int writable = 0;
            for (Control control : controls) {
                if (!control.adapter.isWritable()) continue;
                writable++;
                if (stored != null && stored.has(control.id)) {
                    included++;
                } else {
                    String warning = "Missing writable control: " + family + ":" + control.id;
                    if (!warnings.contains(warning)) warnings.add(warning);
                }
            }
            summary.append("\nIncluded ").append(family).append(" controls: ")
                    .append(included).append('/').append(writable);
        }
        summary.append("\nProjection values: ").append(root.getJSONObject("projection").length());
        return new Preview(summary.toString(), base.firmwareMatches, warnings);
    }

    public static Validated validate(JSONObject root, Map<String, List<Control>> families,
            String fingerprint) throws Exception {
        Preview preview = preview(root, families, fingerprint);
        if (!preview.firmwareMatches) throw new IllegalStateException("Firmware mismatch");

        JSONObject storedFamilies = root.getJSONObject("families");
        Map<String, Integer> values = new LinkedHashMap<>();
        Iterator<String> familyNames = storedFamilies.keys();
        while (familyNames.hasNext()) {
            String family = familyNames.next();
            if (!families.containsKey(family)) {
                throw new IllegalStateException("Unknown backup family: " + family);
            }
            JSONObject controls = storedFamilies.getJSONObject(family);
            Iterator<String> ids = controls.keys();
            while (ids.hasNext()) {
                String id = ids.next();
                Object value = controls.get(id);
                if (!(value instanceof Integer)) {
                    throw new IllegalStateException("Invalid backup value: " + family + ":" + id);
                }
                values.put(family + ":" + id, (Integer) value);
            }
        }
        RoutineEngine.validate(families, values);
        JSONObject projection = root.getJSONObject("projection");
        ProjectionBackup.validateValues(projection);
        return new Validated(values, projection, preview.warnings,
                root.getString("createdUtc"), root.getString("appVersion"));
    }

    private static List<String> readWarnings(JSONObject root) throws JSONException {
        JSONArray stored = root.getJSONArray("warnings");
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < stored.length(); i++) {
            Object warning = stored.get(i);
            if (!(warning instanceof String)) {
                throw new IllegalStateException("Invalid backup warning");
            }
            warnings.add((String) warning);
        }
        return warnings;
    }

    private static String string(JSONObject root, String key) throws JSONException {
        Object value = root.get(key);
        if (!(value instanceof String)) {
            throw new IllegalStateException("Invalid backup metadata: " + key);
        }
        return (String) value;
    }

    private static void requireKeys(JSONObject object, String... expected) throws JSONException {
        if (object.length() != expected.length) {
            throw new IllegalStateException("Invalid backup root keys");
        }
        for (String key : expected) {
            if (!object.has(key)) throw new IllegalStateException("Missing backup key: " + key);
        }
    }
}
