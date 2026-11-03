package com.visorcraft.hy450control;

final class AutomationSelection {
    interface Action {
        void run(int index) throws Exception;
    }

    private AutomationSelection() {}

    static int cycle(int current, int count, Action action) throws Exception {
        int next = (current + 1) % count;
        action.run(next);
        return next;
    }
}
