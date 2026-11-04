package com.visorcraft.hy450control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

public final class RestoreEngine {
    private static final int MAX_FAILURE_LENGTH = 160;

    public interface ProjectionRestore {
        void restore(JSONObject projection) throws Exception;
    }

    public static final class Result {
        public final int applied;
        public final List<String> failures;

        private Result(int applied, List<String> failures) {
            this.applied = applied;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }
    }

    private RestoreEngine() {}

    public static Result restore(JSONObject root, Map<String, List<Control>> families,
            String fingerprint, ProjectionRestore projectionRestore) throws Exception {
        PortableBackup.Validated valid = PortableBackup.validate(root, families, fingerprint);
        RoutineEngine.Result applied = RoutineEngine.apply(families, valid.values);
        List<String> failures = new ArrayList<>();
        for (String failure : applied.failures) failures.add(bound(failure));
        try {
            projectionRestore.restore(valid.projection);
        } catch (Exception error) {
            failures.add(bound("Projection: " + Errors.describe(error)));
        }
        return new Result(applied.applied, failures);
    }

    private static String bound(String failure) {
        return failure.length() <= MAX_FAILURE_LENGTH
                ? failure : failure.substring(0, MAX_FAILURE_LENGTH);
    }
}
