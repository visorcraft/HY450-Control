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
        List<Control> written = new ArrayList<>();
        List<Integer> expected = new ArrayList<>();
        for (Control control : controls) {
            if (!control.adapter.isWritable() || !values.has(control.id)) continue;
            try {
                int value = values.getInt(control.id);
                control.adapter.write(value);
                applied++;
                written.add(control);
                expected.add(value);
            } catch (Exception error) {
                failures.add(control.title + ": " + Errors.describe(error));
            }
        }
        for (int i = 0; i < written.size(); i++) {
            Control control = written.get(i);
            int value = expected.get(i);
            try {
                int actual = control.adapter.read();
                if (actual != value) {
                    applied--;
                    failures.add(control.title + ": Final readback expected " + value
                            + " but got " + actual);
                }
            } catch (Exception error) {
                applied--;
                failures.add(control.title + ": Final readback: " + Errors.describe(error));
            }
        }
        return new Result(applied, failures);
    }
}
