package com.visorcraft.hy450control;

final class SleepSchedule {
    interface Scheduler {
        void schedule(int minutes);
        void cancel();
    }

    private SleepSchedule() {}

    static void set(int minutes, Scheduler scheduler) {
        if (minutes > 0) {
            scheduler.schedule(minutes);
        } else {
            scheduler.cancel();
        }
    }
}
