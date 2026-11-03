package com.visorcraft.hy450control;

final class OemRecovery {
    interface Store {
        boolean isPending();
        Keystone.Snapshot read() throws Exception;
        boolean save(Keystone.Snapshot snapshot);
        boolean clear();
    }

    interface Scheduler {
        void post(Runnable runnable);
        void postDelayed(Runnable runnable, long delayMillis);
        void removeCallbacks(Runnable runnable);
    }

    interface Values {
        Keystone.Snapshot read() throws Exception;
        void apply(Keystone.Snapshot snapshot) throws Exception;
    }

    interface ErrorReporter {
        void report(Exception error);
    }

    interface Action {
        void run() throws Exception;
    }

    private final Store store;
    private final Scheduler scheduler;
    private final Values values;
    private Runnable restore;

    OemRecovery(Store store, Scheduler scheduler, Values values) {
        this.store = store;
        this.scheduler = scheduler;
        this.values = values;
    }

    void prepare() throws Exception {
        cancel();
        Keystone.Snapshot saved = values.read();
        if (!store.save(saved)) throw new IllegalStateException("Keystone recovery save failed");
    }

    void resume(ErrorReporter reporter) {
        final Keystone.Snapshot saved;
        try {
            if (!store.isPending()) return;
            saved = store.read();
        } catch (Exception error) {
            cancelCallbacks();
            reporter.report(error);
            return;
        }
        cancelCallbacks();
        final int[] passes = {0};
        restore = () -> {
            int pass = ++passes[0];
            try {
                values.apply(saved);
                if (pass == 3) {
                    if (!saved.matches(values.read())) {
                        throw new IllegalStateException("Keystone readback mismatch");
                    }
                    clearRestored(saved);
                }
            } catch (Exception error) {
                reporter.report(error);
            } finally {
                if (pass == 3) restore = null;
            }
        };
        scheduler.post(restore);
        scheduler.postDelayed(restore, 400);
        scheduler.postDelayed(restore, 1200);
    }

    void cancel() throws Exception {
        cancelCallbacks();
        if (!store.clear()) throw new IllegalStateException("Keystone recovery clear failed");
    }

    static void cancelAndRun(OemRecovery recovery, Action action) throws Exception {
        recovery.cancel();
        action.run();
    }

    private void clearRestored(Keystone.Snapshot saved) {
        if (store.clear()) return;
        boolean retrySaved = store.save(saved);
        IllegalStateException error = new IllegalStateException("Keystone recovery clear failed");
        if (!retrySaved) {
            error.addSuppressed(new IllegalStateException("Keystone recovery retry save failed"));
        }
        throw error;
    }

    private void cancelCallbacks() {
        if (restore != null) scheduler.removeCallbacks(restore);
        restore = null;
    }
}
