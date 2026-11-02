package com.visorcraft.hy450control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ControlBatch {
    public interface Values {
        boolean has(String id);
        int getInt(String id) throws Exception;
    }

    public static final class Result {
        public final int applied;
        public final List<String> failures;

        private Result(int applied, List<String> failures) {
            this.applied = applied;
            this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        }
    }

    private ControlBatch() {}

    public static Result apply(List<Control> controls, Values values) {
        int applied = 0;
        List<String> failures = new ArrayList<>();
        for (Control control : controls) {
            if (!control.adapter.isWritable() || !values.has(control.id)) continue;
            try {
                control.adapter.write(values.getInt(control.id));
                applied++;
            } catch (Exception error) {
                failures.add(control.title + ": " + Errors.describe(error));
            }
        }
        return new Result(applied, failures);
    }
}
