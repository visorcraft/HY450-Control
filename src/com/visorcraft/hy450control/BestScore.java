package com.visorcraft.hy450control;

final class BestScore {
    private int best;

    BestScore(int initialBest) {
        best = Math.max(0, initialBest);
    }

    int get() {
        return best;
    }

    boolean update(int score) {
        if (score <= best) return false;
        best = score;
        return true;
    }
}
