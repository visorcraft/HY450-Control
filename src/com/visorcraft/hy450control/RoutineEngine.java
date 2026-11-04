package com.visorcraft.hy450control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RoutineEngine {
    private static final String[] FAMILIES = {"picture", "audio", "source"};
    private static final int MAX_WARNING_LENGTH = 160;

    public static final class Capture {
        public final Map<String, Integer> values;
        public final List<String> warnings;

        Capture(Map<String, Integer> values, List<String> warnings) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
        }
    }

    public static final class Result {
        public final int applied;
        public final List<String> failures;

        private Result(int applied, List<String> failures) {
            this.applied = applied;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }
    }

    private RoutineEngine() {}

    public static Capture capture(Map<String, List<Control>> families) {
        Map<String, Integer> values = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        for (String family : FAMILIES) {
            List<Control> controls = families.get(family);
            if (controls == null) continue;
            for (Control control : controls) {
                if (!control.adapter.isWritable()) {
                    String warning = control.title + ": unavailable (read-only)";
                    warnings.add(warning.length() <= MAX_WARNING_LENGTH
                            ? warning : warning.substring(0, MAX_WARNING_LENGTH));
                    continue;
                }
                try {
                    int value = control.adapter.read();
                    if (control.adapter.accepts(value)) {
                        values.put(key(family, control.id), value);
                    } else {
                        warnings.add(control.title + ": value " + value
                                + " outside accepted domain");
                    }
                } catch (Exception error) {
                    warnings.add(control.title + ": " + Errors.describe(error));
                }
            }
        }
        return new Capture(values, warnings);
    }

    public static void validate(Map<String, List<Control>> families, Map<String, Integer> values) {
        for (String family : families.keySet()) {
            boolean supported = false;
            for (String candidate : FAMILIES) {
                if (candidate.equals(family)) supported = true;
            }
            if (!supported) {
                throw new IllegalArgumentException("Unsupported routine family: " + family);
            }
        }
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            String storedKey = entry.getKey();
            int separator = storedKey.indexOf(':');
            if (separator <= 0 || separator != storedKey.lastIndexOf(':')
                    || separator == storedKey.length() - 1) {
                throw new IllegalArgumentException("Invalid routine key: " + storedKey);
            }
            String family = storedKey.substring(0, separator);
            String id = storedKey.substring(separator + 1);
            List<Control> controls = families.get(family);
            Control match = null;
            if (controls != null) {
                for (Control control : controls) {
                    if (control.id.equals(id)) {
                        match = control;
                        break;
                    }
                }
            }
            if (match == null) throw new IllegalArgumentException("Unknown routine: " + storedKey);
            if (!match.adapter.isWritable()) {
                throw new IllegalArgumentException("Read-only routine: " + storedKey);
            }
            Integer value = entry.getValue();
            if (value == null || !match.adapter.accepts(value)) {
                throw new IllegalArgumentException("Invalid routine value: " + storedKey);
            }
        }
    }

    public static Result apply(Map<String, List<Control>> families, Map<String, Integer> values) {
        validate(families, values);
        int applied = 0;
        List<String> failures = new ArrayList<>();
        for (String family : FAMILIES) {
            List<Control> controls = families.get(family);
            if (controls == null) continue;
            String prefix = family + ":";
            ControlBatch.Values familyValues = new ControlBatch.Values() {
                @Override
                public boolean has(String id) {
                    return values.containsKey(prefix + id);
                }

                @Override
                public int getInt(String id) {
                    return values.get(prefix + id);
                }
            };
            List<Control> ordered = controls;
            if ("picture".equals(family)) {
                ordered = new ArrayList<>();
                Control pictureMode = null;
                for (Control control : controls) {
                    if ("display.pictureMode".equals(control.id)) pictureMode = control;
                    else ordered.add(control);
                }
                if (pictureMode != null) ordered.add(pictureMode);
            }
            ControlBatch.Result result = ControlBatch.apply(ordered, familyValues);
            applied += result.applied;
            failures.addAll(result.failures);
        }
        return new Result(applied, failures);
    }

    private static String key(String family, String id) {
        return family + ":" + id;
    }
}
